package com.haucheng.erp.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.haucheng.erp.auth.CurrentUserContext;
import com.haucheng.erp.entity.SysQrToken;
import com.haucheng.erp.exception.BusinessException;
import com.haucheng.erp.mapper.QrTokenMapper;
import com.haucheng.erp.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class QrService {
    private static final String PREFIX = "ERPQR1:";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final QrTokenMapper tokens;
    private final ProductService products;
    private final AssetDeviceService devices;
    private final StockService stocks;
    private final OperationLogService operationLog;

    @Transactional
    public QrLabelView label(String type, Long id, boolean create) {
        requirePermission("asset.qr.view");
        if (create) requirePermission("asset.qr.generate");
        // The locking read must precede any consistent read in this transaction.
        if (create) {
            if (!"PRODUCT".equals(type) && !"DEVICE".equals(type)) throw BusinessException.badRequest("资源类型不正确");
            if (id == null || id <= 0) throw BusinessException.badRequest("资源编号不正确");
            Long locked = "PRODUCT".equals(type) ? tokens.lockProduct(id) : tokens.lockDevice(id);
            if (locked == null) throw BusinessException.notFound("资源不存在");
        }
        Resource resource = resource(type, id);
        if (resource.product() != null && !Boolean.TRUE.equals(resource.product().getStatus())) {
            throw BusinessException.conflict("物资已停用");
        }
        SysQrToken token = tokens.selectResource(type, id);
        if (token == null && !create) throw BusinessException.notFound("该资源尚无二维码");
        if (token == null) {
            if (resource.product() != null && !Boolean.TRUE.equals(resource.product().getEnableQrcode())) {
                throw BusinessException.conflict("该物资未启用二维码");
            }
            token = new SysQrToken();
            byte[] bytes = new byte[32];
            RANDOM.nextBytes(bytes);
            token.setToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
            token.setResourceType(type);
            token.setProductId("PRODUCT".equals(type) ? id : null);
            token.setDeviceId("DEVICE".equals(type) ? id : null);
            tokens.insert(token);
            operationLog.record("QR_GENERATE", type + ":" + id, "生成二维码");
            token = tokens.selectResource(type, id);
        }
        if (!Boolean.TRUE.equals(token.getActive())) throw BusinessException.conflict("二维码已停用");
        return toLabel(type, id, resource, token);
    }

    @Transactional
    public List<QrLabelView> batch(String type, List<Long> ids) {
        requirePermission("asset.qr.view");
        requirePermission("asset.qr.print");
        requirePermission("asset.qr.generate");
        if (ids == null || ids.isEmpty() || ids.size() > 30 || new HashSet<>(ids).size() != ids.size()
                || ids.stream().anyMatch(id -> id == null || id <= 0)) {
            throw BusinessException.badRequest("请选择1至30个不重复的资源");
        }
        List<QrLabelView> labels = new ArrayList<>();
        // Lock resources in a stable order so concurrent batch requests do not invert row locks.
        for (Long id : ids.stream().sorted().toList()) labels.add(label(type, id, true));
        operationLog.record("QR_PRINT_PREVIEW", type, "生成" + labels.size() + "张标签预览");
        return labels;
    }

    public QrScanView scan(String content) {
        requirePermission("asset.qr.view");
        requirePermission("asset.qr.scan");
        if (content == null || content.length() > 256) throw BusinessException.badRequest("二维码内容格式不正确");
        String value = content.trim();
        if (!value.matches("ERPQR1:[PD]:[A-Za-z0-9_-]{43}")) {
            if (value.startsWith("ERPQR")) throw BusinessException.badRequest("不支持的二维码版本或格式");
            throw BusinessException.badRequest("二维码内容格式不正确");
        }
        String type = value.charAt(PREFIX.length()) == 'P' ? "PRODUCT" : "DEVICE";
        SysQrToken token = tokens.selectToken(value.substring(PREFIX.length() + 2));
        if (token == null || !type.equals(token.getResourceType())) throw BusinessException.notFound("未找到对应的二维码资源");
        if (!Boolean.TRUE.equals(token.getActive())) throw BusinessException.conflict("二维码已停用");
        if (token.getFormatVersion() == null || token.getFormatVersion() != 1) throw BusinessException.badRequest("不支持的二维码版本");
        Long id = "PRODUCT".equals(type) ? token.getProductId() : token.getDeviceId();
        if (id == null) throw BusinessException.notFound("二维码关联资源不存在");
        Resource resource = resource(type, id);
        if (resource.product() != null && !Boolean.TRUE.equals(resource.product().getStatus())) {
            throw BusinessException.conflict("物资已停用");
        }
        QrScanView result = new QrScanView();
        result.setResourceType(type);
        result.setProduct(resource.product());
        result.setDevice(resource.device());
        if (resource.product() != null && hasPermission("inventory.stock.view")) {
            result.setStocks(stocks.getPage(null, id, null, 1, 100));
        }
        operationLog.record("QR_SCAN", type + ":" + id, "扫码查询");
        return result;
    }

    private Resource resource(String type, Long id) {
        if (id == null || id <= 0) throw BusinessException.badRequest("资源编号不正确");
        return switch (type) {
            case "PRODUCT" -> {
                requirePermission("system.product.view");
                yield new Resource(products.getById(id), null);
            }
            case "DEVICE" -> {
                requirePermission("system.device.view");
                yield new Resource(null, devices.getById(id));
            }
            default -> throw BusinessException.badRequest("资源类型不正确");
        };
    }

    private QrLabelView toLabel(String type, Long id, Resource resource, SysQrToken token) {
        String content = PREFIX + ("PRODUCT".equals(type) ? "P:" : "D:") + token.getToken();
        QrLabelView view = new QrLabelView();
        view.setResourceType(type);
        view.setResourceId(id);
        view.setContent(content);
        view.setSvg(svg(content));
        if (resource.product() != null) {
            ProductView p = resource.product();
            view.setCode(p.getProductCode());
            view.setName(p.getProductName());
            view.setDetail(p.getBrandName());
        } else {
            DeviceView d = resource.device();
            view.setCode(d.getDeviceCode());
            view.setName(d.getProductName());
            view.setDetail(d.getAssetNo() != null ? d.getAssetNo() : d.getSerialNumber());
        }
        return view;
    }

    private String svg(String content) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 4));
            int size = matrix.getWidth();
            StringBuilder path = new StringBuilder();
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    if (matrix.get(x, y)) path.append('M').append(x).append(' ').append(y).append("h1v1h-1z");
                }
            }
            return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + size + " " + size
                    + "\" role=\"img\"><rect width=\"100%\" height=\"100%\" fill=\"white\"/><path fill=\"black\" d=\""
                    + path + "\"/></svg>";
        } catch (WriterException e) {
            throw BusinessException.badRequest("无法生成二维码");
        }
    }

    private boolean hasPermission(String code) {
        return CurrentUserContext.require().getPermissions().contains(code);
    }

    private void requirePermission(String code) {
        if (!hasPermission(code)) throw BusinessException.forbidden("没有访问该资源的权限");
    }

    private record Resource(ProductView product, DeviceView device) { }
}
