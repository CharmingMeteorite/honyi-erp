package com.haucheng.erp.service;

import com.haucheng.erp.auth.CurrentUserContext;
import com.haucheng.erp.entity.SysAttachment;
import com.haucheng.erp.exception.BusinessException;
import com.haucheng.erp.mapper.AttachmentMapper;
import com.haucheng.erp.vo.AttachmentView;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class AttachmentService {
    private static final int MAX_BYTES = 20 * 1024 * 1024;
    private static final Map<String,String> TYPES = Map.of(
            "pdf","application/pdf", "png","image/png", "jpg","image/jpeg", "jpeg","image/jpeg",
            "webp","image/webp", "docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "txt","text/plain");
    private static final Set<String> BUSINESSES = Set.of("PRODUCT","DEVICE","INBOUND","OUTBOUND","REQUISITION","STOCKTAKE","REPAIR","WARRANTY","EXCHANGE");
    private final AttachmentMapper mapper;
    private final MinioObjectStorageService storage;
    private final ProductService products;
    private final AssetDeviceService devices;
    private final InventoryOrderService inventoryOrders;
    private final RequisitionService requisitions;
    private final StocktakeService stocktakes;
    private final AssetServiceOrderService serviceOrders;
    private final OperationLogService audit;
    private final PlatformTransactionManager transactionManager;

    public List<AttachmentView> list(String type,Long businessId) {
        access(type,businessId,false);
        return mapper.selectBusiness(type,businessId).stream().map(this::view).toList();
    }

    public AttachmentView get(Long id) { return view(requireReady(id)); }

    public Download download(Long id) {
        SysAttachment attachment = requireReady(id);
        byte[] bytes = storage.get(attachment.getObjectKey(),attachment.getSizeBytes());
        if (!sha256(bytes).equals(attachment.getSha256())) throw BusinessException.conflict("附件对象校验失败，请联系管理员");
        return new Download(view(attachment),bytes);
    }

    @Transactional
    public AttachmentView upload(String type,Long businessId,String requestId,MultipartFile file) {
        access(type,businessId,true);
        if (requestId == null || !requestId.matches("[A-Za-z0-9._-]{1,64}")) throw BusinessException.badRequest("上传请求标识不正确");
        String name = safeName(file.getOriginalFilename());
        if (file.isEmpty() || file.getSize() > MAX_BYTES) throw BusinessException.badRequest("文件须非空且不超过20 MiB");
        if (mapper.selectReadyForUpdate(type,businessId).size() >= 20) throw BusinessException.conflict("单个业务对象最多20个附件");
        byte[] bytes;
        try { bytes = file.getBytes(); } catch (IOException ex) { throw BusinessException.badRequest("读取上传文件失败"); }
        if (bytes.length == 0 || bytes.length > MAX_BYTES) throw BusinessException.badRequest("文件须非空且不超过20 MiB");
        int dot = name.lastIndexOf('.');
        String ext = dot < 0 ? "" : name.substring(dot+1).toLowerCase(Locale.ROOT);
        String mime = TYPES.get(ext);
        if (mime == null || !mime.equalsIgnoreCase(file.getContentType()) || !validContent(ext,bytes)) {
            throw BusinessException.badRequest("文件格式、内容或类型不支持");
        }
        String hash = sha256(bytes);
        Long userId = CurrentUserContext.require().getUserId();
        SysAttachment previous = mapper.selectByRequest(userId,requestId);
        if (previous != null) {
            if ("READY".equals(previous.getStatus()) && previous.getBusinessType().equals(type)
                    && previous.getBusinessId().equals(businessId) && previous.getSha256().equals(hash)) return view(previous);
            throw BusinessException.conflict("上传请求标识已使用");
        }
        String key = "attachments/" + UUID.randomUUID();
        storage.put(key,bytes,mime);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    try { storage.remove(key); }
                    catch (RuntimeException ex) { log.error("上传回滚补偿失败，对象键={}",key); }
                }
            }
        });
        SysAttachment row = new SysAttachment();
        row.setBusinessType(type); row.setBusinessId(businessId); row.setDisplayName(name); row.setObjectKey(key);
        row.setSizeBytes((long)bytes.length); row.setContentType(mime); row.setSha256(hash);
        row.setUploaderUserId(userId); row.setClientRequestId(requestId);
        mapper.insert(row);
        audit.record("ATTACHMENT_UPLOAD",String.valueOf(row.getId()),type + ":" + businessId);
        return view(row);
    }

    @Transactional
    public void delete(Long id) {
        SysAttachment row = mapper.selectById(id);
        if (row == null || "DELETED".equals(row.getStatus())) throw BusinessException.notFound("附件不存在");
        access(row.getBusinessType(),row.getBusinessId(),true);
        if ("READY".equals(row.getStatus())) mapper.markPending(id);
        audit.record("ATTACHMENT_DELETE",String.valueOf(id),row.getBusinessType() + ":" + row.getBusinessId());
        String key = row.getObjectKey();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try {
                    storage.remove(key);
                    var transaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
                    transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                    transaction.executeWithoutResult(status -> mapper.markDeleted(id));
                } catch (RuntimeException ex) { log.warn("附件删除待重试，附件ID={}，对象键={}",id,key); }
            }
        });
    }

    private SysAttachment requireReady(Long id) {
        SysAttachment row = mapper.selectById(id);
        if (row == null || !"READY".equals(row.getStatus())) throw BusinessException.notFound("附件不存在");
        access(row.getBusinessType(),row.getBusinessId(),false);
        return row;
    }

    private void access(String type,Long id,boolean write) {
        if (type == null || !BUSINESSES.contains(type) || id == null || id <= 0) throw BusinessException.badRequest("业务关联不正确");
        String permission = switch(type) {
            case "PRODUCT" -> "system.product.view"; case "DEVICE" -> "system.device.view";
            case "INBOUND" -> "inventory.inbound.view"; case "OUTBOUND" -> "inventory.outbound.view";
            case "REQUISITION" -> "apply.requisition.view"; case "STOCKTAKE" -> "inventory.stocktake.view";
            default -> "asset." + type.toLowerCase(Locale.ROOT) + ".view";
        };
        if (!CurrentUserContext.require().getPermissions().contains(permission)) throw BusinessException.forbidden("没有查看关联业务的权限");
        if (write) {
            String updatePermission = switch(type) {
                case "PRODUCT" -> "system.product.update"; case "DEVICE" -> "system.device.update";
                case "INBOUND" -> "inventory.inbound.update"; case "OUTBOUND" -> "inventory.outbound.update";
                case "REQUISITION" -> "apply.requisition.update"; case "STOCKTAKE" -> "inventory.stocktake.update";
                default -> "asset." + type.toLowerCase(Locale.ROOT) + ".update";
            };
            if (!CurrentUserContext.require().getPermissions().contains(updatePermission)) throw BusinessException.forbidden("没有维护关联业务的权限");
        }
        switch(type) {
            case "PRODUCT" -> products.getById(id); case "DEVICE" -> devices.getById(id);
            case "INBOUND" -> inventoryOrders.getById(true,id); case "OUTBOUND" -> inventoryOrders.getById(false,id);
            case "REQUISITION" -> requisitions.getById(id); case "STOCKTAKE" -> stocktakes.getById(id);
            default -> serviceOrders.getById(type.toLowerCase(Locale.ROOT),id);
        }
    }

    private AttachmentView view(SysAttachment a) {
        return new AttachmentView(a.getId(),a.getBusinessType(),a.getBusinessId(),a.getDisplayName(),a.getSizeBytes(),a.getContentType(),a.getSha256(),a.getUploaderUserId(),a.getStatus(),a.getCreatedAt());
    }

    private String safeName(String name) {
        if (name == null || name.isBlank() || name.length() > 200 || name.contains("/") || name.contains("\\")
                || name.contains(":") || name.contains("..") || name.chars().anyMatch(c -> c < 32 || c == 127)) {
            throw BusinessException.badRequest("文件名不正确");
        }
        return name.trim();
    }

    private boolean validContent(String ext,byte[] data) {
        switch(ext) {
            case "pdf": return starts(data,"%PDF-");
            case "png": return data.length > 24 && data[0]==(byte)137 && data[1]==80 && data[2]==78 && data[3]==71;
            case "jpg": case "jpeg": return data.length > 4 && data[0]==(byte)0xff && data[1]==(byte)0xd8 && data[2]==(byte)0xff;
            case "webp": return data.length > 12 && starts(data,"RIFF") && new String(data,8,4,StandardCharsets.US_ASCII).equals("WEBP");
            case "docx": case "xlsx": return zipContains(data,"docx".equals(ext)?"word/document.xml":"xl/workbook.xml");
            case "txt": try {
                StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(data));
                for (byte b : data) if (b >= 0 && b < 32 && b != 9 && b != 10 && b != 13) return false;
                return true;
            } catch (CharacterCodingException ex) { return false; }
            default: return false;
        }
    }

    private boolean zipContains(byte[] data,String expected) {
        if (data.length < 4 || data[0]!='P' || data[1]!='K') return false;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry entry;
            int count=0;
            while ((entry=zip.getNextEntry())!=null && ++count<5000) if (expected.equals(entry.getName())) return true;
        } catch (IOException ex) { return false; }
        return false;
    }

    private boolean starts(byte[] data,String prefix) {
        byte[] expected=prefix.getBytes(StandardCharsets.US_ASCII);
        if (data.length<expected.length) return false;
        for(int i=0;i<expected.length;i++) if(data[i]!=expected[i]) return false;
        return true;
    }

    private String sha256(byte[] data) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    public record Download(AttachmentView metadata,byte[] bytes) { }
}
