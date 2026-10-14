package com.haucheng.erp.service;

import com.haucheng.erp.auth.CurrentUserContext;
import com.haucheng.erp.common.*;
import com.haucheng.erp.dto.*;
import com.haucheng.erp.entity.*;
import com.haucheng.erp.exception.BusinessException;
import com.haucheng.erp.mapper.*;
import com.haucheng.erp.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AssetServiceOrderService {
    private final AssetServiceOrderMapper orders;
    private final AssetDeviceMapper devices;
    private final ProductMapper products;
    private final WarehouseMapper warehouses;
    private final StockMapper stocks;
    private final UserMapper users;
    private final DepartmentMapper departments;
    private final InventoryScopeService inventoryScope;
    private final InventoryOrderMapper inventoryOrders;
    private final OperationLogService audit;

    public PageResult<AssetServiceOrderView> getPage(String kind, String keyword, String status, Long deviceId,
            Long productId, Long applicantId, LocalDate fromDate, LocalDate toDate, Integer page, Integer size) {
        permission(kind, "view");
        Pagination p = Pagination.of(page, size);
        LocalDateTime from = fromDate == null ? null : fromDate.atStartOfDay();
        LocalDateTime to = toDate == null ? null : toDate.plusDays(1).atStartOfDay();
        if (from != null && to != null && !from.isBefore(to)) throw BusinessException.badRequest("日期范围不正确");
        return new PageResult<>(orders.selectPage(kind, trim(keyword), trim(status), deviceId, productId, applicantId,
                from, to, departmentIds(), selfId(), p), p.page(), p.size(),
                orders.count(kind, trim(keyword), trim(status), deviceId, productId, applicantId, from, to, departmentIds(), selfId()));
    }

    public AssetServiceOrderView getById(String kind, Long id) {
        permission(kind, "view");
        return detail(kind, id);
    }

    public PageResult<DeviceView> getDeviceOptions(String kind, boolean replacement, Long productId,
            String keyword, Integer page, Integer size) {
        permission(kind, "view");
        if (replacement && (!"exchange".equals(kind) || productId == null)) throw BusinessException.badRequest("换货新设备须按旧设备产品查询");
        Pagination p = Pagination.of(page, size);
        return new PageResult<>(orders.selectDeviceOptions(kind, replacement, productId, trim(keyword), departmentIds(), selfId(), p),
                p.page(), p.size(), orders.countDeviceOptions(kind, replacement, productId, trim(keyword), departmentIds(), selfId()));
    }

    @Transactional
    public AssetServiceOrderView create(String kind, AssetServiceOrderRequest request) {
        permission(kind, "create");
        validateRequest(kind, request);
        String hash = hash(request.toString());
        Long repeated = repeated(request.getClientRequestId(), kind, null, "create", hash);
        if (repeated != null) return detail(kind, repeated);
        AssetServiceOrder order = createOrder(kind, request, null, null);
        orders.insertRequest(request.getClientRequestId(), kind, order.getId(), "create", hash, currentId());
        audit.record(kind.toUpperCase(Locale.ROOT) + "_CREATE", order.getId().toString(), "创建" + order.getOrderNo());
        return detail(kind, order.getId());
    }

    private AssetServiceOrder createOrder(String kind, AssetServiceOrderRequest request, Long warrantyId, AssetServiceOrder applicant) {
        AssetDevice observed = requireDevice(request.getDeviceId(), false);
        AssetDevice replacement = "exchange".equals(kind) ? requireDevice(request.getNewDeviceId(), false) : null;
        lockResources(observed.getProductId(), observed.getWarehouseId(), replacement == null ? null : replacement.getWarehouseId());
        TreeSet<Long> ids = new TreeSet<>(); ids.add(observed.getId()); if (replacement != null) ids.add(replacement.getId());
        Map<Long,AssetDevice> locked = new HashMap<>();
        for (Long id : ids) locked.put(id, requireDevice(id, true));
        AssetDevice device = locked.get(observed.getId());
        if (!Objects.equals(device.getWarehouseId(), observed.getWarehouseId())) throw BusinessException.conflict("设备仓库已变化，请刷新");
        ensureDeviceScope(device);
        MdProduct product = products.selectById(device.getProductId());
        if (product == null || !Boolean.TRUE.equals(product.getStatus()) || !"DEVICE".equals(product.getProductType())) throw BusinessException.conflict("仅启用的设备型产品可以处理");
        if (!"warranty".equals(kind)) ensureUnclaimed(device.getId());
        if ("warranty".equals(kind) && (orders.countActiveRepair(device.getId())>0 || orders.countActiveExchange(device.getId())>0)) throw BusinessException.conflict("设备正在维修或换货，不能重复申请保修");
        AssetServiceOrder order = new AssetServiceOrder();
        order.setOrderNo(kind.substring(0,1).toUpperCase(Locale.ROOT) + "-" + UUID.randomUUID());
        order.setClientRequestId(request.getClientRequestId()); order.setStatus("DRAFT"); order.setVersion(0L);
        order.setDeviceId(device.getId()); order.setDeviceCode(device.getDeviceCode()); order.setProductId(product.getId());
        order.setProductCode(product.getProductCode()); order.setProductName(product.getProductName());
        order.setSerialNumber(device.getSerialNumber()); order.setMacAddress(device.getMacAddress()); order.setAssetNo(device.getAssetNo());
        order.setApplicantUserId(applicant == null ? currentId() : applicant.getApplicantUserId());
        order.setDepartmentId(device.getDepartmentId() != null ? device.getDepartmentId() : warehouseDepartment(device.getWarehouseId()));
        copyDraft(order, request); order.setRequestId(audit.currentRequestId());
        if ("repair".equals(kind)) {
            requireRepairable(device); snapshotOrigin(order, device); order.setDeviceVersion(device.getVersion()); order.setWarrantyId(warrantyId);
        } else if ("warranty".equals(kind)) {
            if (!Set.of("IN_STOCK","IN_USE","REPAIRING","PENDING_EXCHANGE").contains(device.getStatus())) throw BusinessException.conflict("当前设备状态不能申请保修");
            order.setWarrantyStartAt(device.getWarrantyStartAt()); order.setWarrantyEndAt(device.getWarrantyEndAt());
            order.setWarrantyStatus(warrantyStatus(order, LocalDateTime.now()));
        } else {
            if (!"PENDING_EXCHANGE".equals(device.getStatus()) || device.getWarehouseId() != null) throw BusinessException.conflict("旧设备必须处于无正常库存归属的待换货状态");
            AssetServiceOrder repair = orders.selectFailedRepair(device.getId());
            if (repair == null) throw BusinessException.conflict("缺少可追溯的无法修复维修单");
            AssetDevice next = locked.get(replacement.getId());
            validateReplacement(device, next); ensureUnclaimed(next.getId());
            if (!Objects.equals(next.getWarehouseId(), replacement.getWarehouseId())) throw BusinessException.conflict("新设备仓库已变化");
            snapshotOrigin(order, device); order.setDeviceVersion(device.getVersion()); order.setOriginalStatus(repair.getOriginalStatus()); order.setOriginalWarehouseId(repair.getOriginalWarehouseId());
            order.setNewDeviceId(next.getId()); order.setNewDeviceCode(next.getDeviceCode());
            order.setNewSerialNumber(next.getSerialNumber()); order.setNewMacAddress(next.getMacAddress()); order.setNewAssetNo(next.getAssetNo());
            order.setNewWarehouseId(next.getWarehouseId()); order.setNewDeviceVersion(next.getVersion()); order.setNewDepartmentId(next.getDepartmentId());
            order.setNewUserId(next.getUserId()); order.setNewCustodianId(next.getCustodianId());
        }
        orders.insert(kind, order);
        if ("repair".equals(kind)) orders.claimRepair(order.getId(), device.getId());
        if ("exchange".equals(kind)) {
            orders.insertExchangeDevice(order.getId(), device.getId(), "OLD");
            orders.insertExchangeDevice(order.getId(), order.getNewDeviceId(), "NEW");
            orders.claimExchange(order.getId());
        }
        return order;
    }

    @Transactional
    public AssetServiceOrderView update(String kind, Long id, AssetServiceOrderRequest request) {
        permission(kind,"update"); validateRequest(kind,request);
        AssetServiceOrder order = requireOrder(kind,id);
        String hash = hash(request.toString());
        String editKey=hash("edit/"+kind+"/"+id+"/"+request.getVersion()+"/"+request.getClientRequestId());
        if (repeated(editKey, kind, id, "update", hash) != null) return detail(kind,id);
        requireVersion(order,request.getVersion()); requireState(order,"DRAFT"); ensureOwner(order);
        if (!Objects.equals(order.getDeviceId(),request.getDeviceId()) || !Objects.equals(order.getNewDeviceId(),request.getNewDeviceId())
                || !order.getClientRequestId().equals(request.getClientRequestId())) throw BusinessException.conflict("设备关系和创建请求标识不可更改，请取消后新建");
        copyDraft(order,request); save(kind,order,"UPDATE");
        orders.insertRequest(editKey,kind,id,"update",hash,currentId());
        return detail(kind,id);
    }

    @Transactional
    public AssetServiceOrderView action(String kind, Long id, String action, AssetServiceActionRequest request) {
        permission(kind, actionPermission(kind,action));
        if (request.getClientRequestId() == null || !request.getClientRequestId().matches("[A-Za-z0-9._-]{1,64}")) throw BusinessException.badRequest("动作请求标识不正确");
        AssetServiceOrder order = requireOrder(kind,id);
        String hash=hash(request.toString());
        if (repeated(request.getClientRequestId(),kind,id,action,hash) != null) return detail(kind,id);
        requireVersion(order,request.getVersion());
        if (Set.of("submit","cancel").contains(action)) ensureOwner(order); else ensureStaff();
        if ("submit".equals(action)) {
            requireState(order,"DRAFT"); verifyDevices(kind,order,false);
            order.setStatus("SUBMITTED"); order.setSubmittedAt(LocalDateTime.now());
        } else if ("cancel".equals(action)) {
            requireState(order,"DRAFT","SUBMITTED","ACCEPTED");
            order.setStatus("CANCELLED"); release(kind,id);
        } else if ("repair".equals(kind)) repairAction(order,action,request);
        else if ("warranty".equals(kind)) warrantyAction(order,action,request);
        else exchangeAction(order,action,request);
        if (request.getRemark()!=null) order.setRemark(trim(request.getRemark()));
        if ("repair".equals(kind) && request.getNotes()!=null) order.setNotes(trim(request.getNotes()));
        if (request.getResult()!=null) order.setResult(trim(request.getResult()));
        save(kind,order,action.toUpperCase(Locale.ROOT));
        orders.insertRequest(request.getClientRequestId(),kind,id,action,hash,currentId());
        return detail(kind,id);
    }

    private void repairAction(AssetServiceOrder order,String action,AssetServiceActionRequest request) {
        switch (action) {
            case "accept" -> { requireState(order,"SUBMITTED"); verifyDevices("repair",order,false); order.setStatus("ACCEPTED"); order.setHandlerUserId(currentId()); order.setAcceptedAt(LocalDateTime.now()); }
            case "start" -> {
                requireState(order,"ACCEPTED"); AssetDevice device=verifyDevices("repair",order,false);
                AssetDevice before=copyDevice(device);
                if (order.getOriginalWarehouseId()!=null) adjustStock(order,device,order.getOriginalWarehouseId(),-1,"REPAIR_OUT");
                device.setStatus("REPAIRING"); device.setWarehouseId(null); changeDevice(before,device,"REPAIR",order,"送修");
                if (order.getOriginalWarehouseId()!=null) consistency(order.getOriginalWarehouseId(),order.getProductId());
                order.setStatus("REPAIRING"); order.setSentAt(LocalDateTime.now());
            }
            case "complete", "unrepairable" -> {
                requireState(order,"REPAIRING"); requireResult(request);
                AssetDevice device=verifyDevices("repair",order,true); AssetDevice before=copyDevice(device);
                if ("unrepairable".equals(action)) { device.setStatus("PENDING_EXCHANGE"); order.setStatus("UNREPAIRABLE"); }
                else {
                    if ("IN_USE".equals(order.getOriginalStatus())) validateUser(order.getOriginalUserId(),order.getOriginalDepartmentId());
                    device.setStatus(order.getOriginalStatus()); device.setWarehouseId(order.getOriginalWarehouseId());
                    if (order.getOriginalWarehouseId()!=null) adjustStock(order,device,order.getOriginalWarehouseId(),1,"REPAIR_RETURN");
                    order.setStatus("COMPLETED");
                }
                changeDevice(before,device,"REPAIR",order,request.getResult());
                if (order.getOriginalWarehouseId()!=null) consistency(order.getOriginalWarehouseId(),order.getProductId());
                order.setCompletedAt(LocalDateTime.now()); release("repair",order.getId());
            }
            case "close" -> { requireState(order,"COMPLETED","UNREPAIRABLE"); order.setStatus("CLOSED"); }
            default -> throw BusinessException.badRequest("不支持的维修动作");
        }
        order.setProcessedAt(LocalDateTime.now());
    }

    private void warrantyAction(AssetServiceOrder order,String action,AssetServiceActionRequest request) {
        switch(action) {
            case "accept" -> { requireState(order,"SUBMITTED"); order.setStatus("ACCEPTED"); order.setHandlerUserId(currentId()); order.setAcceptedAt(LocalDateTime.now()); }
            case "approve", "paid-repair", "reject" -> {
                requireState(order,"ACCEPTED"); requireResult(request);
                if (!"reject".equals(action) && (orders.countActiveRepair(order.getDeviceId())>0 || orders.countActiveExchange(order.getDeviceId())>0)) throw BusinessException.conflict("设备已进入互斥的维修或换货流程");
                if ("approve".equals(action) && !"VALID".equals(warrantyStatus(order,LocalDateTime.now()))) throw BusinessException.conflict("当前不在保修期内，请明确拒绝或转自费维修");
                order.setApproverUserId(currentId());
                order.setDecision("reject".equals(action)?"REJECTED":"approve".equals(action)?"WARRANTY":"PAID_REPAIR");
                order.setStatus("reject".equals(action)?"REJECTED":"APPROVED");
                if (!"reject".equals(action)) {
                    AssetServiceOrderRequest repair=new AssetServiceOrderRequest(); repair.setDeviceId(order.getDeviceId());
                    repair.setClientRequestId("WARRANTY-"+order.getId()); repair.setReason("保修处理："+order.getDecision());
                    repair.setFaultPhenomenon("保修申请转维修"); repair.setFaultDescription(order.getFaultDescription()); repair.setRemark(request.getResult());
                    AssetServiceOrder linked=createOrder("repair",repair,order.getId(),order);
                    audit.record("REPAIR_CREATE",linked.getId().toString(),"保修单转维修 "+order.getOrderNo());
                }
            }
            case "complete" -> {
                requireState(order,"APPROVED"); requireResult(request);
                AssetServiceOrder repair=orders.selectWarrantyRepair(order.getId());
                if (repair==null || !Set.of("COMPLETED","UNREPAIRABLE","CLOSED").contains(repair.getStatus())) throw BusinessException.conflict("关联维修尚未完成处理");
                order.setStatus("COMPLETED"); order.setCompletedAt(LocalDateTime.now());
            }
            default -> throw BusinessException.badRequest("不支持的保修动作");
        }
        order.setProcessedAt(LocalDateTime.now());
    }

    private void exchangeAction(AssetServiceOrder order,String action,AssetServiceActionRequest request) {
        switch(action) {
            case "approve", "reject" -> {
                requireState(order,"SUBMITTED"); verifyDevices("exchange",order,false); requireResult(request);
                order.setApproverUserId(currentId()); order.setStatus("approve".equals(action)?"APPROVED":"REJECTED");
                if ("reject".equals(action)) release("exchange",order.getId());
            }
            case "execute" -> {
                requireState(order,"APPROVED"); AssetDevice old=verifyDevices("exchange",order,false);
                AssetDevice next=requireDevice(order.getNewDeviceId(),true);
                AssetDevice beforeOld=copyDevice(old),beforeNext=copyDevice(next);
                boolean inUse="IN_USE".equals(order.getOriginalStatus());
                if (inUse) validateUser(order.getOriginalUserId(),order.getOriginalDepartmentId());
                adjustStock(order,next,order.getNewWarehouseId(),-1,"EXCHANGE");
                next.setStatus(inUse?"IN_USE":"SHIPPED"); next.setWarehouseId(null);
                next.setUserId(inUse?order.getOriginalUserId():null); next.setCustodianId(inUse?order.getOriginalCustodianId():null);
                if (inUse) next.setDepartmentId(order.getOriginalDepartmentId());
                old.setStatus("SHIPPED"); old.setWarehouseId(null); old.setUserId(null); old.setCustodianId(null);
                changeDevice(beforeOld,old,"EXCHANGE",order,"旧设备交回："+order.getReason());
                changeDevice(beforeNext,next,"EXCHANGE",order,"替换 "+old.getDeviceCode()+"："+order.getReason());
                consistency(order.getNewWarehouseId(),order.getProductId()); release("exchange",order.getId());
                order.setStatus("COMPLETED"); order.setExecutorUserId(currentId()); order.setCompletedAt(LocalDateTime.now());
            }
            default -> throw BusinessException.badRequest("不支持的换货动作");
        }
        order.setProcessedAt(LocalDateTime.now());
    }

    private AssetDevice verifyDevices(String kind,AssetServiceOrder order,boolean repairing) {
        lockResources(order.getProductId(),order.getOriginalWarehouseId(),order.getNewWarehouseId());
        TreeSet<Long> ids=new TreeSet<>(); ids.add(order.getDeviceId()); if(order.getNewDeviceId()!=null) ids.add(order.getNewDeviceId());
        Map<Long,AssetDevice> rows=new HashMap<>(); for(Long id:ids) rows.put(id,requireDevice(id,true));
        AssetDevice device=rows.get(order.getDeviceId());
        if (!Objects.equals(device.getProductId(),order.getProductId()) || !sameIdentity(device,order.getDeviceCode(),order.getSerialNumber(),order.getMacAddress(),order.getAssetNo())) throw BusinessException.conflict("设备身份或产品已变化");
        if ("warranty".equals(kind)) return device;
        if ("repair".equals(kind)) {
            if (orders.ownsRepair(order.getId(),device.getId())!=1) throw BusinessException.conflict("维修设备占用不一致");
            if (!Objects.equals(device.getStatus(),repairing?"REPAIRING":order.getOriginalStatus())
                    || !Objects.equals(device.getWarehouseId(),repairing?null:order.getOriginalWarehouseId()) || !sameOrigin(device,order)) throw BusinessException.conflict("送修基准或归属已变化，禁止覆盖，请先核查");
            if (!repairing) requireRepairable(device);
            if (!Objects.equals(device.getVersion(),repairing?order.getDeviceVersion()+1:order.getDeviceVersion())) throw BusinessException.conflict("设备版本已变化，禁止覆盖维修前归属");
        } else {
            if (orders.ownsExchange(order.getId(),device.getId())!=1 || orders.ownsExchange(order.getId(),order.getNewDeviceId())!=1) throw BusinessException.conflict("换货设备占用不一致");
            if (!"PENDING_EXCHANGE".equals(device.getStatus()) || device.getWarehouseId()!=null || !sameOrigin(device,order) || !Objects.equals(device.getVersion(),order.getDeviceVersion())) throw BusinessException.conflict("旧设备状态或归属已变化");
            AssetDevice next=rows.get(order.getNewDeviceId()); validateReplacement(device,next);
            if (!Objects.equals(next.getVersion(),order.getNewDeviceVersion()) || !Objects.equals(next.getWarehouseId(),order.getNewWarehouseId()) || !Objects.equals(next.getDepartmentId(),order.getNewDepartmentId())
                    || !Objects.equals(next.getUserId(),order.getNewUserId()) || !Objects.equals(next.getCustodianId(),order.getNewCustodianId())
                    || !sameIdentity(next,order.getNewDeviceCode(),order.getNewSerialNumber(),order.getNewMacAddress(),order.getNewAssetNo())) throw BusinessException.conflict("新设备基准已变化");
        }
        return device;
    }

    // 与正式库存业务保持同一锁顺序：仓库、产品、库存、设备。
    private void lockResources(Long productId,Long... warehouseIds) {
        TreeSet<Long> ids=new TreeSet<>(); for(Long id:warehouseIds) if(id!=null) ids.add(id);
        for(Long id:ids) {
            WhWarehouse w=warehouses.selectByIdForUpdate(id);
            if(w==null || !Boolean.TRUE.equals(w.getStatus())) throw BusinessException.conflict("仓库不存在或停用");
            if (selfId()==null) inventoryScope.ensureDepartment(w.getDepartmentId());
        }
        MdProduct p=products.selectByIdForUpdate(productId);
        if(p==null || !"DEVICE".equals(p.getProductType())) throw BusinessException.conflict("设备产品类型不正确");
        for(Long id:ids) stocks.selectForUpdate(id,productId);
    }

    private void adjustStock(AssetServiceOrder order,AssetDevice device,Long warehouseId,int delta,String type) {
        InvStock stock=stocks.selectForUpdate(warehouseId,device.getProductId());
        if(stock==null) throw BusinessException.conflict("设备仓库缺少库存行");
        consistency(warehouseId,device.getProductId());
        BigDecimal before=stock.getQuantity(),change=BigDecimal.valueOf(delta);
        if(stocks.addQuantity(stock.getId(),before,change)!=1) throw BusinessException.conflict("库存不足、锁定或已变化");
        InvStockLedger ledger=new InvStockLedger(); ledger.setWarehouseId(warehouseId); ledger.setProductId(device.getProductId());
        ledger.setBusinessType(type); ledger.setBusinessNo(order.getOrderNo()); ledger.setDirection(delta>0?"IN":"OUT");
        ledger.setQuantity(change.abs()); ledger.setBeforeQuantity(before); ledger.setAfterQuantity(before.add(change));
        ledger.setOperatorUserId(currentId()); ledger.setRequestId(audit.currentRequestId()); stocks.insertLedger(ledger);
    }

    private void consistency(Long warehouseId,Long productId) {
        InvStock stock=stocks.selectForUpdate(warehouseId,productId);
        int count=devices.selectStockDeviceIdsForUpdate(warehouseId,productId).size();
        if(stock==null || stock.getQuantity().compareTo(BigDecimal.valueOf(count))!=0) throw BusinessException.conflict("设备台数与库存不一致，请先核查");
    }

    private void changeDevice(AssetDevice before,AssetDevice after,String type,AssetServiceOrder order,String detail) {
        if(orders.updateDevice(after)!=1) throw BusinessException.conflict("设备版本已变化");
        AssetDeviceHistory h=new AssetDeviceHistory(); h.setDeviceId(after.getId()); h.setEventType("STATUS");
        h.setFromStatus(before.getStatus()); h.setToStatus(after.getStatus());
        h.setFromWarehouseId(before.getWarehouseId()); h.setToWarehouseId(after.getWarehouseId());
        h.setFromDepartmentId(before.getDepartmentId()); h.setToDepartmentId(after.getDepartmentId());
        h.setFromUserId(before.getUserId()); h.setToUserId(after.getUserId());
        h.setFromCustodianId(before.getCustodianId()); h.setToCustodianId(after.getCustodianId());
        h.setBusinessType(type); h.setBusinessNo(order.getOrderNo()); h.setOperatorUserId(currentId()); h.setRequestId(audit.currentRequestId());
        h.setDetail(detail.length()>500?detail.substring(0,500):detail); devices.insertHistory(h);
    }

    private AssetServiceOrderView detail(String kind,Long id) {
        AssetServiceOrderView view=orders.selectView(kind,id);
        if(view==null) throw BusinessException.notFound("业务单据不存在"); ensureOrderScope(view);
        view.setDeviceHistory(devices.selectHistory(view.getDeviceId()));
        if(view.getNewDeviceId()!=null) view.setNewDeviceHistory(devices.selectHistory(view.getNewDeviceId()));
        view.setLogs(inventoryOrders.selectLogs(id.toString(),kind.toUpperCase(Locale.ROOT)));
        List<StockLedgerView> ledgers=new ArrayList<>();
        for(String type: "repair".equals(kind)?List.of("REPAIR_OUT","REPAIR_RETURN"):List.of("EXCHANGE")) ledgers.addAll(inventoryOrders.selectLedgers(type,view.getOrderNo()));
        view.setLedgers(ledgers);
        if("warranty".equals(kind)) { AssetServiceOrder repair=orders.selectWarrantyRepair(id); if(repair!=null){view.setLinkedRepairId(repair.getId());view.setLinkedRepairNo(repair.getOrderNo());view.setLinkedRepairStatus(repair.getStatus());} }
        return view;
    }

    private AssetServiceOrder requireOrder(String kind,Long id) { AssetServiceOrder o=orders.selectForUpdate(kind,id); if(o==null) throw BusinessException.notFound("单据不存在"); ensureOrderScope(o); return o; }
    private AssetDevice requireDevice(Long id,boolean lock) { AssetDevice d=id==null?null:lock?devices.selectByIdForUpdate(id):devices.selectById(id); if(d==null) throw BusinessException.notFound("设备不存在"); return d; }
    private void requireRepairable(AssetDevice d) { if(!Set.of("IN_STOCK","IN_USE").contains(d.getStatus()) || ("IN_USE".equals(d.getStatus()) && (d.getWarehouseId()!=null || d.getUserId()==null || d.getDepartmentId()==null))) throw BusinessException.conflict("设备状态或使用归属不允许送修"); }
    private void validateReplacement(AssetDevice old,AssetDevice next) {
        if(old.getId().equals(next.getId()) || !old.getProductId().equals(next.getProductId()) || !"IN_STOCK".equals(next.getStatus()) || next.getWarehouseId()==null) throw BusinessException.conflict("新设备须为同产品、不同身份的可用库存设备");
        ensureDeviceScope(next); inventoryScope.ensureDepartment(warehouseDepartment(next.getWarehouseId()));
    }
    private void ensureUnclaimed(Long id) { if(devices.selectInventoryClaim(id)!=null || orders.openingClaim(id)!=null) throw BusinessException.conflict("设备已被互斥业务占用"); }
    private void release(String kind,Long id) { if("repair".equals(kind)) orders.releaseRepair(id); if("exchange".equals(kind)) orders.releaseExchange(id); }
    private void save(String kind,AssetServiceOrder order,String action) { order.setRequestId(audit.currentRequestId()); if(orders.update(kind,order)!=1) throw BusinessException.conflict("单据版本冲突"); audit.record(kind.toUpperCase(Locale.ROOT)+"_"+action,order.getId().toString(),order.getOrderNo()+" → "+order.getStatus()); }
    private void requireVersion(AssetServiceOrder o,Long v) { if(!Objects.equals(o.getVersion(),v)) throw BusinessException.conflict("单据版本已变化，请刷新"); }
    private void requireState(AssetServiceOrder o,String... states) { if(!Arrays.asList(states).contains(o.getStatus())) throw BusinessException.conflict("当前状态不允许此动作"); }
    private void requireResult(AssetServiceActionRequest r) { if(trim(r.getResult())==null) throw BusinessException.badRequest("请明确填写处理结果"); }
    private String warrantyStatus(AssetServiceOrder o,LocalDateTime now) { if(o.getWarrantyStartAt()==null || o.getWarrantyEndAt()==null)return "UNKNOWN"; if(now.isBefore(o.getWarrantyStartAt()))return "NOT_STARTED"; return now.isAfter(o.getWarrantyEndAt())?"EXPIRED":"VALID"; }
    private void validateUser(Long userId,Long departmentId) { if(userId==null || departmentId==null || users.countActiveInDepartment(userId,departmentId)!=1 || departments.countActive(departmentId)!=1) throw BusinessException.conflict("原使用归属已失效，请先核查"); }
    private void snapshotOrigin(AssetServiceOrder o,AssetDevice d) { o.setOriginalStatus(d.getStatus());o.setOriginalWarehouseId(d.getWarehouseId());o.setOriginalDepartmentId(d.getDepartmentId());o.setOriginalUserId(d.getUserId());o.setOriginalCustodianId(d.getCustodianId()); }
    private boolean sameOrigin(AssetDevice d,AssetServiceOrder o) { return Objects.equals(d.getDepartmentId(),o.getOriginalDepartmentId()) && Objects.equals(d.getUserId(),o.getOriginalUserId()) && Objects.equals(d.getCustodianId(),o.getOriginalCustodianId()); }
    private boolean sameIdentity(AssetDevice d,String code,String sn,String mac,String asset) { return Objects.equals(d.getDeviceCode(),code)&&Objects.equals(d.getSerialNumber(),sn)&&Objects.equals(d.getMacAddress(),mac)&&Objects.equals(d.getAssetNo(),asset); }
    private AssetDevice copyDevice(AssetDevice d) { AssetDevice c=new AssetDevice(); c.setStatus(d.getStatus());c.setWarehouseId(d.getWarehouseId());c.setDepartmentId(d.getDepartmentId());c.setUserId(d.getUserId());c.setCustodianId(d.getCustodianId());return c; }
    private void copyDraft(AssetServiceOrder o,AssetServiceOrderRequest r) { o.setReason(trim(r.getReason()));o.setRemark(trim(r.getRemark()));o.setFaultPhenomenon(trim(r.getFaultPhenomenon()));o.setFaultDescription(trim(r.getFaultDescription()));o.setNotes(trim(r.getNotes()));o.setServiceProvider(trim(r.getServiceProvider()));o.setServiceLocation(trim(r.getServiceLocation()));o.setExpectedCompleteAt(r.getExpectedCompleteAt()); }
    private void validateRequest(String kind,AssetServiceOrderRequest r) {
        if(r.getDeviceId()==null || trim(r.getReason())==null || r.getClientRequestId()==null || !r.getClientRequestId().matches("[A-Za-z0-9._-]{1,64}")) throw BusinessException.badRequest("设备、原因和请求标识必填");
        if("repair".equals(kind) && (trim(r.getFaultPhenomenon())==null || trim(r.getFaultDescription())==null)) throw BusinessException.badRequest("故障现象和故障描述必填");
        if("warranty".equals(kind) && trim(r.getFaultDescription())==null) throw BusinessException.badRequest("故障描述必填");
        if("exchange".equals(kind) && r.getNewDeviceId()==null) throw BusinessException.badRequest("请选择独立新设备");
        if(!"exchange".equals(kind) && r.getNewDeviceId()!=null) throw BusinessException.badRequest("当前业务不接收新设备关系");
    }
    private Long repeated(String key,String kind,Long id,String action,String hash) {
        Map<String,Object> row=orders.selectRequest(key); if(row==null)return null;
        if(!kind.equals(row.get("kind")) || !action.equals(row.get("action")) || !hash.equals(row.get("request_hash"))
                || currentId()!=((Number)row.get("operator_user_id")).longValue() || (id!=null && id.longValue()!=((Number)row.get("order_id")).longValue())) throw BusinessException.conflict("请求标识已用于不同请求");
        return ((Number)row.get("order_id")).longValue();
    }
    private String hash(String text) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); } catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); } }
    private List<Long> departmentIds() { return selfId()!=null?null:inventoryScope.departmentIds(); }
    private Long selfId() { return "SELF".equals(CurrentUserContext.require().getDataScope())?currentId():null; }
    private Long currentId() { return CurrentUserContext.require().getUserId(); }
    private Long warehouseDepartment(Long id) { if(id==null)return null;WhWarehouse w=warehouses.selectById(id);if(w==null)throw BusinessException.conflict("仓库不存在");return w.getDepartmentId(); }
    private void ensureOrderScope(AssetServiceOrder o) { if(selfId()!=null){if(!selfId().equals(o.getApplicantUserId()))throw BusinessException.forbidden("只能访问本人的申请");}else inventoryScope.ensureDepartment(o.getDepartmentId()); }
    private void ensureDeviceScope(AssetDevice d) { if(selfId()!=null){if(!selfId().equals(d.getUserId())&&!selfId().equals(d.getCustodianId()))throw BusinessException.forbidden("只能申请本人使用或保管的设备");}else inventoryScope.ensureDepartment(d.getDepartmentId()!=null?d.getDepartmentId():warehouseDepartment(d.getWarehouseId())); }
    private void ensureOwner(AssetServiceOrder o) { if(selfId()!=null&&!currentId().equals(o.getApplicantUserId()))throw BusinessException.forbidden("只能操作本人申请"); }
    private void ensureStaff() { if(selfId()!=null)throw BusinessException.forbidden("本人数据范围不能处理、审批或执行"); }
    private void permission(String kind,String action) { if(!Set.of("repair","warranty","exchange").contains(kind))throw BusinessException.badRequest("业务类型不正确"); if(!CurrentUserContext.require().getPermissions().contains("asset."+kind+"."+action))throw BusinessException.forbidden("没有此业务操作权限"); }
    private String actionPermission(String kind,String action) { return switch(action){case "accept","start" -> "process";case "unrepairable","close" -> "complete";case "paid-repair" -> "approve";default -> action;}; }
    private String trim(String s) { return s==null||s.isBlank()?null:s.trim(); }
}
