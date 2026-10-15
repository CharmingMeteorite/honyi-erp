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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class InventoryOrderService {
    private static final Set<String> STATUSES = Set.of("DRAFT", "PENDING_APPROVAL", "APPROVED", "REJECTED", "COMPLETED", "CANCELLED");
    private static final BigDecimal MAX_QUANTITY = new BigDecimal("99999999999999.9999");
    private final InventoryOrderMapper orders;
    private final WarehouseMapper warehouses;
    private final WarehouseService warehouseService;
    private final ProductMapper products;
    private final UnitMapper units;
    private final AssetDeviceMapper devices;
    private final StockMapper stocks;
    private final InventoryScopeService scope;
    private final OperationLogService operationLog;
    private final NotificationService notifications;

    public PageResult<InventoryOrderView> getPage(boolean inbound, Long warehouseId, String status, String keyword,
                                                  LocalDate fromDate, LocalDate toDate, Integer page, Integer size) {
        if (warehouseId != null) warehouseService.getById(warehouseId);
        String state = trim(status);
        if (state != null && !STATUSES.contains(state)) throw BusinessException.badRequest("单据状态不正确");
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) throw BusinessException.badRequest("时间范围不正确");
        LocalDateTime from = fromDate == null ? null : fromDate.atStartOfDay();
        LocalDateTime to = toDate == null ? null : toDate.plusDays(1).atStartOfDay();
        Pagination p = Pagination.of(page, size);
        var departments = scope.departmentIds();
        return new PageResult<>(orders.selectPage(inbound, warehouseId, state, trim(keyword), from, to, departments, p),
                p.page(), p.size(), orders.count(inbound, warehouseId, state, trim(keyword), from, to, departments));
    }

    @Transactional(readOnly=true)
    public InventoryOrderView getById(boolean inbound, Long id) {
        InventoryOrderView view = orders.selectView(inbound, id);
        if (view == null) throw BusinessException.notFound("单据不存在");
        warehouseService.getById(view.getWarehouseId());
        var items = orders.selectItems(inbound, id);
        for (var item : items) item.setDevices(orders.selectDevices(inbound, item.getId()));
        view.setItems(items);
        view.setLogs(orders.selectLogs(String.valueOf(id), kind(inbound)));
        view.setLedgers(orders.selectLedgers(kind(inbound), view.getOrderNo()));
        return view;
    }

    public PageResult<InventoryOrderDeviceView> getDeviceOptions(boolean inbound, Long warehouseId, Long productId,
                                                                 String keyword, Integer page, Integer size) {
        if (warehouseId == null) throw BusinessException.badRequest("请先选择仓库");
        warehouseService.getById(warehouseId);
        Pagination p = Pagination.of(page, size);
        var departments = scope.departmentIds();
        return new PageResult<>(orders.selectDeviceOptions(inbound, warehouseId, productId, trim(keyword), departments, p),
                p.page(), p.size(), orders.countDeviceOptions(inbound, warehouseId, productId, trim(keyword), departments));
    }

    @Transactional
    public InventoryOrderView create(boolean inbound, InventoryOrderRequest request) {
        requireActiveWarehouse(request.getWarehouseId());
        validateRequest(request);
        InvInventoryOrder order = new InvInventoryOrder();
        order.setOrderNo((inbound ? "IN-" : "OUT-") + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT));
        order.setClientRequestId(request.getClientRequestId()); order.setWarehouseId(request.getWarehouseId());
        order.setCreatorUserId(CurrentUserContext.require().getUserId());
        order.setRemark(trim(request.getRemark())); order.setRequestId(operationLog.currentRequestId());
        orders.insert(inbound, order);
        insertItems(inbound, order, request);
        operationLog.record(kind(inbound) + "_CREATE", String.valueOf(order.getId()), "创建草稿 " + order.getOrderNo());
        return getById(inbound, order.getId());
    }

    @Transactional
    public InventoryOrderView update(boolean inbound, Long id, InventoryOrderRequest request) {
        InvInventoryOrder order = requireLocked(inbound, id, request.getVersion(), "DRAFT", "REJECTED");
        requireActiveWarehouse(request.getWarehouseId());
        validateRequest(request);
        if (!order.getClientRequestId().equals(request.getClientRequestId())) throw BusinessException.conflict("客户端请求标识不能修改");
        orders.releaseClaims(inbound, id);
        orders.deleteDevices(inbound, id); orders.deleteItems(inbound, id);
        order.setWarehouseId(request.getWarehouseId()); order.setRemark(trim(request.getRemark()));
        order.setStatus("DRAFT"); order.setSubmittedAt(null); order.setApproverUserId(null); order.setApprovedAt(null);
        insertItems(inbound, order, request);
        save(inbound, order, "UPDATE", "保存草稿（拒绝后需重新提交审批）");
        return getById(inbound, id);
    }

    @Transactional
    public InventoryOrderView submit(boolean inbound, Long id, InventoryOrderActionRequest request) {
        InvInventoryOrder order = requireLocked(inbound, id, request.getVersion(), "DRAFT");
        requireActiveWarehouse(order.getWarehouseId());
        validateStoredItems(inbound, order);
        order.setStatus("PENDING_APPROVAL"); order.setSubmittedAt(LocalDateTime.now());
        save(inbound, order, "SUBMIT", "提交审批，业务明细冻结");
        return getById(inbound, id);
    }

    @Transactional
    public InventoryOrderView approve(boolean inbound, Long id, InventoryOrderActionRequest request, boolean accepted) {
        InvInventoryOrder order = requireLocked(inbound, id, request.getVersion(), "PENDING_APPROVAL");
        String reason = trim(request.getReason());
        if (!accepted && reason == null) throw BusinessException.badRequest("请填写拒绝原因");
        if (reason != null && reason.length() > 200) throw BusinessException.badRequest("审批说明最多200字");
        order.setStatus(accepted ? "APPROVED" : "REJECTED");
        order.setApproverUserId(CurrentUserContext.require().getUserId()); order.setApprovedAt(LocalDateTime.now());
        if (!accepted) orders.releaseClaims(inbound, id);
        save(inbound, order, accepted ? "APPROVE" : "REJECT", reason == null ? "审批通过" : reason);
        return getById(inbound, id);
    }

    @Transactional
    public InventoryOrderView cancel(boolean inbound, Long id, InventoryOrderActionRequest request) {
        InvInventoryOrder order = requireLocked(inbound, id, request.getVersion(), "DRAFT", "REJECTED");
        order.setStatus("CANCELLED"); orders.releaseClaims(inbound, id);
        save(inbound, order, "CANCEL", "取消单据，释放设备占用");
        return getById(inbound, id);
    }

    @Transactional
    public InventoryOrderView execute(boolean inbound, Long id, InventoryOrderActionRequest request) {
        InvInventoryOrder order = requireLocked(inbound, id, request.getVersion(), "APPROVED");
        requireActiveWarehouse(order.getWarehouseId());
        var items = orders.selectItems(inbound, id);
        if (items.isEmpty()) throw BusinessException.conflict("单据缺少明细");
        // 与期初业务一致：单据 -> 仓库 -> 产品ID升序 -> Stock -> Device。产品锁也串行化跨仓库设备竞争。
        for (var item : items) executeItem(inbound, order, item);
        orders.releaseClaims(inbound, id);
        order.setStatus("COMPLETED"); order.setExecutorUserId(CurrentUserContext.require().getUserId());
        order.setExecutedAt(LocalDateTime.now());
        // 单据、库存、设备、历史、流水和审计一起提交；最后审计失败同样整体回滚。
        save(inbound, order, "EXECUTE", "执行完成 " + order.getOrderNo());
        notifications.send(order.getCreatorUserId(), inbound ? "INBOUND" : "OUTBOUND", order.getId(),
                inbound ? "入库已完成" : "出库已完成", order.getOrderNo());
        return getById(inbound, id);
    }

    private void executeItem(boolean inbound, InvInventoryOrder order, InventoryOrderItemView item) {
        MdProduct product = requireProduct(item.getProductId());
        item.setDevices(orders.selectDevices(inbound, item.getId()));
        validateItem(product, item.getProductType(), item.getQuantity(), item.getDevices().size());
        stocks.ensureStock(order.getWarehouseId(), product.getId());
        InvStock stock = stocks.selectForUpdate(order.getWarehouseId(), product.getId());
        BigDecimal before = stock.getQuantity();
        if (!inbound && before.subtract(stock.getLockedQuantity()).compareTo(item.getQuantity()) < 0) {
            throw BusinessException.conflict("可用库存不足");
        }
        BigDecimal delta = inbound ? item.getQuantity() : item.getQuantity().negate();
        BigDecimal after = before.add(delta);
        if (after.compareTo(MAX_QUANTITY) > 0) throw BusinessException.badRequest("库存数量超出允许范围");
        if ("DEVICE".equals(product.getProductType())) {
            ensureConsistent(order.getWarehouseId(), product.getId(), before);
            for (var link : item.getDevices()) {
                AssetDevice device = requireDevice(inbound, order, product.getId(), link.getDeviceId());
                ensureOwned(inbound, order, link, device);
                int changed = inbound ? devices.updateWarehouse(device.getId(), order.getWarehouseId())
                        : devices.shipFromWarehouse(device.getId(), order.getWarehouseId());
                if (changed != 1) throw BusinessException.conflict("设备库存状态已变化，请刷新");
                recordHistory(inbound, order, device);
            }
        } else if (devices.countByProduct(product.getId()) > 0) throw BusinessException.conflict("数量型物资关联了设备，请核查");
        if (stocks.addQuantity(stock.getId(), before, delta) != 1) throw BusinessException.conflict("库存已变化或可用库存不足");
        if ("DEVICE".equals(product.getProductType())) ensureConsistent(order.getWarehouseId(), product.getId(), after);
        InvStockLedger ledger = new InvStockLedger();
        ledger.setWarehouseId(order.getWarehouseId()); ledger.setProductId(product.getId());
        ledger.setBusinessType(kind(inbound)); ledger.setBusinessNo(order.getOrderNo());
        ledger.setDirection(inbound ? "IN" : "OUT"); ledger.setQuantity(item.getQuantity());
        ledger.setBeforeQuantity(before); ledger.setAfterQuantity(after);
        ledger.setOperatorUserId(CurrentUserContext.require().getUserId()); ledger.setRequestId(operationLog.currentRequestId());
        stocks.insertLedger(ledger);
    }

    private void insertItems(boolean inbound, InvInventoryOrder order, InventoryOrderRequest request) {
        for (var r : request.getItems().stream().sorted(Comparator.comparing(InventoryOrderItemRequest::getProductId)).toList()) {
            MdProduct product = requireProduct(r.getProductId());
            validateItem(product, product.getProductType(), r.getQuantity(), r.getDeviceIds().size());
            InventoryOrderItemView item = new InventoryOrderItemView();
            item.setOrderId(order.getId()); item.setProductId(product.getId()); item.setProductType(product.getProductType()); item.setQuantity(r.getQuantity());
            orders.insertItem(inbound, item);
            for (Long deviceId : r.getDeviceIds().stream().sorted().toList()) {
                AssetDevice device = requireDevice(inbound, order, product.getId(), deviceId);
                if (orders.selectClaim(deviceId) != null) throw BusinessException.conflict("设备已被其他未完成单据占用");
                if (orders.selectOpeningClaim(deviceId) != null) throw BusinessException.conflict("设备已被期初草稿引用，请先处理该单据");
                InventoryOrderDeviceView link = new InventoryOrderDeviceView();
                link.setOrderId(order.getId()); link.setItemId(item.getId()); link.setDeviceId(deviceId);
                link.setDeviceCode(device.getDeviceCode()); link.setSerialNumber(device.getSerialNumber());
                link.setMacAddress(device.getMacAddress()); link.setAssetNo(device.getAssetNo());
                orders.insertDevice(inbound, link); orders.claim(inbound, link);
            }
        }
    }

    private void validateStoredItems(boolean inbound, InvInventoryOrder order) {
        var items = orders.selectItems(inbound, order.getId());
        if (items.isEmpty()) throw BusinessException.conflict("单据缺少明细");
        for (var item : items) {
            var product = requireProduct(item.getProductId());
            var links = orders.selectDevices(inbound, item.getId());
            validateItem(product, item.getProductType(), item.getQuantity(), links.size());
            for (var link : links) ensureOwned(inbound, order, link, requireDevice(inbound, order, product.getId(), link.getDeviceId()));
        }
    }

    private void ensureOwned(boolean inbound, InvInventoryOrder order, InventoryOrderDeviceView link, AssetDevice device) {
        if (orders.selectClaim(device.getId()) == null || orders.ownsClaim(inbound, order.getId(), device.getId()) != 1) throw BusinessException.conflict("设备占用信息不一致，请核查");
        if (!Objects.equals(link.getDeviceCode(), device.getDeviceCode()) || !Objects.equals(link.getSerialNumber(), device.getSerialNumber())
                || !Objects.equals(link.getMacAddress(), device.getMacAddress()) || !Objects.equals(link.getAssetNo(), device.getAssetNo())) {
            throw BusinessException.conflict("设备身份信息与审批明细不一致，请核查");
        }
    }

    private AssetDevice requireDevice(boolean inbound, InvInventoryOrder order, Long productId, Long id) {
        AssetDevice device = devices.selectByIdForUpdate(id);
        if (device == null) throw BusinessException.notFound("设备不存在");
        scope.ensureDeviceDepartment(device.getDepartmentId());
        if (!productId.equals(device.getProductId())) throw BusinessException.badRequest("设备不属于该产品");
        if (!"IN_STOCK".equals(device.getStatus())) throw BusinessException.conflict("仅IN_STOCK设备可执行入出库");
        if (inbound ? device.getWarehouseId() != null : !order.getWarehouseId().equals(device.getWarehouseId())) throw BusinessException.conflict("设备仓库不符合单据要求");
        return device;
    }

    private void ensureConsistent(Long warehouseId, Long productId, BigDecimal quantity) {
        if (quantity.compareTo(BigDecimal.valueOf(devices.selectStockDeviceIdsForUpdate(warehouseId, productId).size())) != 0) {
            throw BusinessException.conflict("库存与设备数量不一致，请核查；系统不会自动修复");
        }
    }

    private InvInventoryOrder requireLocked(boolean inbound, Long id, Long version, String... states) {
        InvInventoryOrder order = orders.selectForUpdate(inbound, id);
        if (order == null) throw BusinessException.notFound("单据不存在");
        warehouseService.getById(order.getWarehouseId());
        if (!Objects.equals(version, order.getVersion())) throw BusinessException.conflict("单据版本已变化，请刷新后重试");
        if (!Arrays.asList(states).contains(order.getStatus())) throw BusinessException.conflict("当前单据状态不允许此操作");
        return order;
    }

    private void save(boolean inbound, InvInventoryOrder order, String action, String detail) {
        order.setRequestId(operationLog.currentRequestId());
        if (orders.update(inbound, order) != 1) throw BusinessException.conflict("单据已被其他操作修改");
        operationLog.record(kind(inbound) + "_" + action, String.valueOf(order.getId()), detail);
    }

    private WhWarehouse requireActiveWarehouse(Long id) {
        WhWarehouse warehouse = warehouses.selectByIdForUpdate(id);
        if (warehouse == null) throw BusinessException.notFound("仓库不存在");
        scope.ensureDepartment(warehouse.getDepartmentId());
        if (!Boolean.TRUE.equals(warehouse.getStatus())) throw BusinessException.conflict("停用仓库不能接受库存业务");
        return warehouse;
    }

    private MdProduct requireProduct(Long id) {
        MdProduct product = products.selectByIdForUpdate(id);
        if (product == null) throw BusinessException.notFound("物资不存在");
        if (!Boolean.TRUE.equals(product.getStatus())) throw BusinessException.conflict("物资已停用");
        return product;
    }

    private void validateRequest(InventoryOrderRequest request) {
        if (request.getClientRequestId() == null || !request.getClientRequestId().matches("[A-Za-z0-9._-]{1,64}")) throw BusinessException.badRequest("请求标识格式不正确");
        if (request.getItems() == null || request.getItems().isEmpty() || request.getItems().size() > 100) throw BusinessException.badRequest("明细须为1至100行");
        if (request.getRemark() != null && request.getRemark().length() > 500) throw BusinessException.badRequest("备注最多500字");
        Set<Long> productIds = new HashSet<>(), deviceIds = new HashSet<>();
        for (var item : request.getItems()) {
            if (item == null || item.getProductId() == null || !productIds.add(item.getProductId())) throw BusinessException.badRequest("物资不能为空或重复");
            if (item.getDeviceIds() == null || item.getDeviceIds().size() > 1000) throw BusinessException.badRequest("每行设备最多1000台");
            for (Long id : item.getDeviceIds()) if (id == null || !deviceIds.add(id)) throw BusinessException.badRequest("设备不能为空或重复");
        }
    }

    private void validateItem(MdProduct product, String type, BigDecimal quantity, int deviceCount) {
        if (!Objects.equals(type, product.getProductType())) throw BusinessException.conflict("物资管理类型已变化");
        if (quantity == null || quantity.signum() <= 0 || quantity.compareTo(MAX_QUANTITY) > 0 || quantity.stripTrailingZeros().scale() > 4) throw BusinessException.badRequest("数量须为正数，最多14位整数和4位小数");
        if (quantity.stripTrailingZeros().scale() > units.selectById(product.getUnitId()).getPrecisionScale()) throw BusinessException.badRequest("数量超出计量单位精度");
        if ("DEVICE".equals(type)) {
            if (deviceCount == 0 || quantity.compareTo(BigDecimal.valueOf(deviceCount)) != 0) throw BusinessException.badRequest("设备数量必须等于所选具体设备台数");
        } else if (!"QUANTITY".equals(type) || deviceCount != 0) throw BusinessException.badRequest("数量型物资不能绑定设备");
        else if (devices.countByProduct(product.getId()) > 0) throw BusinessException.conflict("数量型物资关联了设备，请核查");
    }

    private void recordHistory(boolean inbound, InvInventoryOrder order, AssetDevice device) {
        AssetDeviceHistory h = new AssetDeviceHistory();
        h.setDeviceId(device.getId()); h.setEventType(inbound ? "INBOUND" : "STATUS");
        h.setFromStatus(device.getStatus()); h.setToStatus(inbound ? "IN_STOCK" : "SHIPPED");
        h.setFromWarehouseId(device.getWarehouseId()); h.setToWarehouseId(inbound ? order.getWarehouseId() : null);
        h.setFromDepartmentId(device.getDepartmentId()); h.setToDepartmentId(device.getDepartmentId());
        h.setFromUserId(device.getUserId()); h.setToUserId(device.getUserId());
        h.setFromCustodianId(device.getCustodianId()); h.setToCustodianId(device.getCustodianId());
        h.setBusinessType(kind(inbound)); h.setBusinessNo(order.getOrderNo());
        h.setDetail((inbound ? "正式入库 " : "正式出库 ") + order.getOrderNo());
        h.setOperatorUserId(CurrentUserContext.require().getUserId()); h.setRequestId(operationLog.currentRequestId());
        devices.insertHistory(h);
    }

    private String kind(boolean inbound) { return inbound ? "INBOUND" : "OUTBOUND"; }
    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
