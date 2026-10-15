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
public class RequisitionService {
    private static final Set<String> STATUSES = Set.of("DRAFT", "PENDING_APPROVAL", "APPROVED", "REJECTED", "COMPLETED", "CANCELLED");
    private static final BigDecimal MAX_QUANTITY = new BigDecimal("99999999999999.9999");
    private final RequisitionMapper orders;
    private final WarehouseMapper warehouses;
    private final UserMapper users;
    private final ProductMapper products;
    private final UnitMapper units;
    private final AssetDeviceMapper devices;
    private final StockMapper stocks;
    private final InventoryOptionService options;
    private final InventoryScopeService scope;
    private final OperationLogService operationLog;
    private final NotificationService notifications;

    public PageResult<RequisitionView> getPage(Long warehouseId, String status, String keyword,
                                                  LocalDate fromDate, LocalDate toDate, Integer page, Integer size) {
        if (warehouseId != null) requireWarehouse(warehouseId, false);
        String state = trim(status);
        if (state != null && !STATUSES.contains(state)) throw BusinessException.badRequest("单据状态不正确");
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) throw BusinessException.badRequest("时间范围不正确");
        LocalDateTime from = fromDate == null ? null : fromDate.atStartOfDay();
        LocalDateTime to = toDate == null ? null : toDate.plusDays(1).atStartOfDay();
        Pagination p = Pagination.of(page, size);
        var departments = selfUserId() == null ? scope.departmentIds() : null;
        return new PageResult<>(orders.selectPage(warehouseId, state, trim(keyword), from, to, departments, selfUserId(), p),
                p.page(), p.size(), orders.count(warehouseId, state, trim(keyword), from, to, departments, selfUserId()));
    }

    @Transactional(readOnly=true)
    public RequisitionView getById(Long id) {
        RequisitionView view = orders.selectView(id);
        if (view == null) throw BusinessException.notFound("单据不存在");
        ensureAccess(view.getDepartmentId(), view.getCreatorUserId());
        var items = orders.selectItems(id);
        for (var item : items) item.setDevices(orders.selectDevices(item.getId()));
        view.setItems(items);
        view.setLogs(orders.selectLogs(String.valueOf(id), "REQUISITION"));
        view.setLedgers(orders.selectLedgers("REQUISITION", view.getOrderNo()));
        var stockDepartments = scope.departmentIds();
        var warehouse = warehouses.selectById(view.getWarehouseId());
        if (stockDepartments != null && !stockDepartments.contains(warehouse.getDepartmentId())) {
            // 申请人可以看自己的发放结果，但不能由流水余额推算整仓库存。
            view.getLedgers().forEach(ledger -> { ledger.setBeforeQuantity(null); ledger.setAfterQuantity(null); });
        }
        return view;
    }

    public PageResult<InventoryOrderDeviceView> getDeviceOptions(Long warehouseId, Long productId,
                                                                 String keyword, Integer page, Integer size) {
        if (warehouseId == null) throw BusinessException.badRequest("请先选择仓库");
        requireWarehouse(warehouseId, false);
        Pagination p = Pagination.of(page, size);
        var departments = optionDepartments();
        return new PageResult<>(orders.selectDeviceOptions(warehouseId, productId, trim(keyword), departments, p),
                p.page(), p.size(), orders.countDeviceOptions(warehouseId, productId, trim(keyword), departments));
    }

    @Transactional
    public RequisitionView create(RequisitionRequest request) {
        SysUser applicant = requireApplicant(CurrentUserContext.require().getUserId(), null);
        requireActiveWarehouse(request.getWarehouseId());
        validateRequest(request);
        ApplyRequisition order = new ApplyRequisition();
        order.setOrderNo("REQ-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT));
        order.setClientRequestId(request.getClientRequestId()); order.setWarehouseId(request.getWarehouseId());
        order.setCreatorUserId(CurrentUserContext.require().getUserId());
        order.setDepartmentId(applicant.getDepartmentId());
        order.setRemark(trim(request.getRemark())); order.setRequestId(operationLog.currentRequestId());
        orders.insert(order);
        insertItems(order, request);
        operationLog.record("REQUISITION_CREATE", String.valueOf(order.getId()), "创建草稿 " + order.getOrderNo());
        return getById(order.getId());
    }

    @Transactional
    public RequisitionView update(Long id, RequisitionRequest request) {
        ApplyRequisition order = requireLocked(id, request.getVersion(), "DRAFT", "REJECTED");
        ensureApplicantOwner(order);
        requireApplicant(order.getCreatorUserId(), order.getDepartmentId());
        requireActiveWarehouse(request.getWarehouseId());
        validateRequest(request);
        if (!order.getClientRequestId().equals(request.getClientRequestId())) throw BusinessException.conflict("客户端请求标识不能修改");
        orders.releaseClaims(id);
        orders.deleteDevices(id); orders.deleteItems(id);
        order.setWarehouseId(request.getWarehouseId()); order.setRemark(trim(request.getRemark()));
        order.setStatus("DRAFT"); order.setSubmittedAt(null); order.setApproverUserId(null); order.setApprovedAt(null);
        insertItems(order, request);
        save(order, "UPDATE", "保存草稿（拒绝后需重新提交审批）");
        return getById(id);
    }

    @Transactional
    public RequisitionView submit(Long id, InventoryOrderActionRequest request) {
        ApplyRequisition order = requireLocked(id, request.getVersion(), "DRAFT");
        ensureApplicantOwner(order);
        requireApplicant(order.getCreatorUserId(), order.getDepartmentId());
        requireActiveWarehouse(order.getWarehouseId());
        validateStoredItems(order);
        order.setStatus("PENDING_APPROVAL"); order.setSubmittedAt(LocalDateTime.now());
        save(order, "SUBMIT", "提交审批，业务明细冻结");
        return getById(id);
    }

    @Transactional
    public RequisitionView approve(Long id, InventoryOrderActionRequest request, boolean accepted) {
        ApplyRequisition order = requireLocked(id, request.getVersion(), "PENDING_APPROVAL");
        ensureStaff();
        String reason = trim(request.getReason());
        if (!accepted && reason == null) throw BusinessException.badRequest("请填写拒绝原因");
        if (reason != null && reason.length() > 200) throw BusinessException.badRequest("审批说明最多200字");
        order.setStatus(accepted ? "APPROVED" : "REJECTED");
        order.setApproverUserId(CurrentUserContext.require().getUserId()); order.setApprovedAt(LocalDateTime.now());
        if (!accepted) orders.releaseClaims(id);
        save(order, accepted ? "APPROVE" : "REJECT", reason == null ? "审批通过" : reason);
        return getById(id);
    }

    @Transactional
    public RequisitionView cancel(Long id, InventoryOrderActionRequest request) {
        ApplyRequisition order = requireLocked(id, request.getVersion(), "DRAFT", "REJECTED", "PENDING_APPROVAL", "APPROVED");
        ensureApplicantOwner(order);
        order.setStatus("CANCELLED"); orders.releaseClaims(id);
        save(order, "CANCEL", "取消单据，释放设备占用");
        return getById(id);
    }

    @Transactional
    public RequisitionView issue(Long id, InventoryOrderActionRequest request) {
        ApplyRequisition order = requireLocked(id, request.getVersion(), "APPROVED");
        ensureStaff();
        requireApplicant(order.getCreatorUserId(), order.getDepartmentId());
        requireActiveWarehouse(order.getWarehouseId());
        var items = orders.selectItems(id);
        if (items.isEmpty()) throw BusinessException.conflict("单据缺少明细");
        // 与期初业务一致：单据 -> 仓库 -> 产品ID升序 -> Stock -> Device。产品锁也串行化跨仓库设备竞争。
        for (var item : items) issueItem(order, item);
        orders.releaseClaims(id);
        order.setStatus("COMPLETED"); order.setExecutorUserId(CurrentUserContext.require().getUserId());
        order.setExecutedAt(LocalDateTime.now());
        // 单据、库存、设备、历史、流水和审计一起提交；最后审计失败同样整体回滚。
        save(order, "ISSUE", "仓库发放 " + order.getOrderNo());
        notifications.send(order.getCreatorUserId(), "REQUISITION", order.getId(), "领用已发放", order.getOrderNo());
        return getById(id);
    }

    private void issueItem(ApplyRequisition order, InventoryOrderItemView item) {
        MdProduct product = requireProduct(item.getProductId());
        item.setDevices(orders.selectDevices(item.getId()));
        validateItem(product, item.getProductType(), item.getQuantity(), item.getDevices().size());
        stocks.ensureStock(order.getWarehouseId(), product.getId());
        InvStock stock = stocks.selectForUpdate(order.getWarehouseId(), product.getId());
        BigDecimal before = stock.getQuantity();
        if (before.subtract(stock.getLockedQuantity()).compareTo(item.getQuantity()) < 0) {
            throw BusinessException.conflict("可用库存不足");
        }
        BigDecimal delta = item.getQuantity().negate();
        BigDecimal after = before.add(delta);
        if (after.compareTo(MAX_QUANTITY) > 0) throw BusinessException.badRequest("库存数量超出允许范围");
        if ("DEVICE".equals(product.getProductType())) {
            ensureConsistent(order.getWarehouseId(), product.getId(), before);
            for (var link : item.getDevices()) {
                AssetDevice device = requireDevice(order, product.getId(), link.getDeviceId());
                ensureOwned(order, link, device);
                int changed = devices.issueFromWarehouse(device.getId(), order.getWarehouseId(), order.getCreatorUserId(), order.getDepartmentId());
                if (changed != 1) throw BusinessException.conflict("设备库存状态已变化，请刷新");
                recordHistory(order, device);
            }
        } else if (devices.countByProduct(product.getId()) > 0) throw BusinessException.conflict("数量型物资关联了设备，请核查");
        if (stocks.addQuantity(stock.getId(), before, delta) != 1) throw BusinessException.conflict("库存已变化或可用库存不足");
        if ("DEVICE".equals(product.getProductType())) ensureConsistent(order.getWarehouseId(), product.getId(), after);
        InvStockLedger ledger = new InvStockLedger();
        ledger.setWarehouseId(order.getWarehouseId()); ledger.setProductId(product.getId());
        ledger.setBusinessType("REQUISITION"); ledger.setBusinessNo(order.getOrderNo());
        ledger.setDirection("OUT"); ledger.setQuantity(item.getQuantity());
        ledger.setBeforeQuantity(before); ledger.setAfterQuantity(after);
        ledger.setOperatorUserId(CurrentUserContext.require().getUserId()); ledger.setRequestId(operationLog.currentRequestId());
        stocks.insertLedger(ledger);
    }

    private void insertItems(ApplyRequisition order, RequisitionRequest request) {
        for (var r : request.getItems().stream().sorted(Comparator.comparing(InventoryOrderItemRequest::getProductId)).toList()) {
            MdProduct product = requireProduct(r.getProductId());
            validateItem(product, product.getProductType(), r.getQuantity(), r.getDeviceIds().size());
            InventoryOrderItemView item = new InventoryOrderItemView();
            item.setOrderId(order.getId()); item.setProductId(product.getId()); item.setProductType(product.getProductType()); item.setQuantity(r.getQuantity());
            orders.insertItem(item);
            for (Long deviceId : r.getDeviceIds().stream().sorted().toList()) {
                AssetDevice device = requireDevice(order, product.getId(), deviceId);
                if (orders.selectClaim(deviceId) != null) throw BusinessException.conflict("设备已被其他未完成单据占用");
                if (orders.selectOpeningClaim(deviceId) != null) throw BusinessException.conflict("设备已被期初草稿引用，请先处理该单据");
                InventoryOrderDeviceView link = new InventoryOrderDeviceView();
                link.setOrderId(order.getId()); link.setItemId(item.getId()); link.setDeviceId(deviceId);
                link.setDeviceCode(device.getDeviceCode()); link.setSerialNumber(device.getSerialNumber());
                link.setMacAddress(device.getMacAddress()); link.setAssetNo(device.getAssetNo());
                orders.insertDevice(link); orders.claim(link);
            }
        }
    }

    private void validateStoredItems(ApplyRequisition order) {
        var items = orders.selectItems(order.getId());
        if (items.isEmpty()) throw BusinessException.conflict("单据缺少明细");
        for (var item : items) {
            var product = requireProduct(item.getProductId());
            var links = orders.selectDevices(item.getId());
            validateItem(product, item.getProductType(), item.getQuantity(), links.size());
            for (var link : links) ensureOwned(order, link, requireDevice(order, product.getId(), link.getDeviceId()));
        }
    }

    private void ensureOwned(ApplyRequisition order, InventoryOrderDeviceView link, AssetDevice device) {
        if (orders.selectClaim(device.getId()) == null || orders.ownsClaim(order.getId(), device.getId()) != 1) throw BusinessException.conflict("设备占用信息不一致，请核查");
        if (!Objects.equals(link.getDeviceCode(), device.getDeviceCode()) || !Objects.equals(link.getSerialNumber(), device.getSerialNumber())
                || !Objects.equals(link.getMacAddress(), device.getMacAddress()) || !Objects.equals(link.getAssetNo(), device.getAssetNo())) {
            throw BusinessException.conflict("设备身份信息与审批明细不一致，请核查");
        }
    }

    private AssetDevice requireDevice(ApplyRequisition order, Long productId, Long id) {
        AssetDevice device = devices.selectByIdForUpdate(id);
        if (device == null) throw BusinessException.notFound("设备不存在");
        ensureOptionDepartment(device.getDepartmentId());
        if (!productId.equals(device.getProductId())) throw BusinessException.badRequest("设备不属于该产品");
        if (!"IN_STOCK".equals(device.getStatus())) throw BusinessException.conflict("只能领用当前仓库的在库设备");
        if (!order.getWarehouseId().equals(device.getWarehouseId())) throw BusinessException.conflict("设备仓库不符合单据要求");
        return device;
    }

    private void ensureConsistent(Long warehouseId, Long productId, BigDecimal quantity) {
        if (quantity.compareTo(BigDecimal.valueOf(devices.selectStockDeviceIdsForUpdate(warehouseId, productId).size())) != 0) {
            throw BusinessException.conflict("库存与设备数量不一致，请核查；系统不会自动修复");
        }
    }

    private ApplyRequisition requireLocked(Long id, Long version, String... states) {
        ApplyRequisition order = orders.selectForUpdate(id);
        if (order == null) throw BusinessException.notFound("单据不存在");
        ensureAccess(order.getDepartmentId(), order.getCreatorUserId());
        if (!Objects.equals(version, order.getVersion())) throw BusinessException.conflict("单据版本已变化，请刷新后重试");
        if (!Arrays.asList(states).contains(order.getStatus())) throw BusinessException.conflict("当前单据状态不允许此操作");
        return order;
    }

    private void save(ApplyRequisition order, String action, String detail) {
        order.setRequestId(operationLog.currentRequestId());
        if (orders.update(order) != 1) throw BusinessException.conflict("单据已被其他操作修改");
        operationLog.record("REQUISITION_" + action, String.valueOf(order.getId()), detail);
    }

    public PageResult<WarehouseView> getWarehouseOptions(String keyword, Integer page, Integer size) {
        Pagination p = Pagination.of(page, size);
        var departments = optionDepartments();
        return new PageResult<>(warehouses.selectPage(trim(keyword), true, departments, p), p.page(), p.size(),
                warehouses.count(trim(keyword), true, departments));
    }

    public PageResult<ProductView> getProducts(String keyword, Integer page, Integer size) {
        return options.getProducts(keyword, page, size);
    }

    private WhWarehouse requireActiveWarehouse(Long id) { return requireWarehouse(id, true); }

    private WhWarehouse requireWarehouse(Long id, boolean lock) {
        WhWarehouse warehouse = lock ? warehouses.selectByIdForUpdate(id) : warehouses.selectById(id);
        if (warehouse == null) throw BusinessException.notFound("仓库不存在");
        ensureOptionDepartment(warehouse.getDepartmentId());
        if (lock && !Boolean.TRUE.equals(warehouse.getStatus())) throw BusinessException.conflict("停用仓库不能接受领用业务");
        return warehouse;
    }

    private Long selfUserId() {
        var current = CurrentUserContext.require();
        return "SELF".equals(current.getDataScope()) ? current.getUserId() : null;
    }

    private List<Long> optionDepartments() {
        if (selfUserId() == null) return scope.departmentIds();
        var user = users.selectById(selfUserId());
        return user == null || user.getDepartmentId() == null ? List.of(Long.MIN_VALUE) : List.of(user.getDepartmentId());
    }

    private void ensureOptionDepartment(Long departmentId) {
        var allowed = optionDepartments();
        if (allowed != null && (departmentId == null || !allowed.contains(departmentId))) {
            throw BusinessException.forbidden("没有访问该部门领用数据的权限");
        }
    }

    private void ensureAccess(Long departmentId, Long applicantId) {
        if (selfUserId() != null) {
            if (!selfUserId().equals(applicantId)) throw BusinessException.forbidden("只能访问本人的领用申请");
        } else scope.ensureDepartment(departmentId);
    }

    private void ensureApplicantOwner(ApplyRequisition order) {
        if (!CurrentUserContext.require().getUserId().equals(order.getCreatorUserId())) {
            throw BusinessException.forbidden("只有申请人可以编辑、提交或取消领用申请");
        }
    }

    private void ensureStaff() {
        if (selfUserId() != null) throw BusinessException.forbidden("本人数据范围不能审批或发放");
    }

    private SysUser requireApplicant(Long id, Long departmentId) {
        SysUser user = orders.selectApplicantForShare(id);
        if (user == null || !Boolean.TRUE.equals(user.getStatus()) || user.getDepartmentId() == null) {
            throw BusinessException.conflict("申请人必须启用并归属部门");
        }
        if (departmentId != null && !departmentId.equals(user.getDepartmentId())) {
            throw BusinessException.conflict("申请人部门已变化，请取消并重新申请");
        }
        var department = orders.selectDepartmentForShare(user.getDepartmentId());
        if (department == null || !Boolean.TRUE.equals(department.getStatus())) throw BusinessException.conflict("申请人部门已停用");
        return user;
    }

    private MdProduct requireProduct(Long id) {
        MdProduct product = products.selectByIdForUpdate(id);
        if (product == null) throw BusinessException.notFound("物资不存在");
        if (!Boolean.TRUE.equals(product.getStatus())) throw BusinessException.conflict("物资已停用");
        return product;
    }

    private void validateRequest(RequisitionRequest request) {
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

    private void recordHistory(ApplyRequisition order, AssetDevice device) {
        AssetDeviceHistory h = new AssetDeviceHistory();
        h.setDeviceId(device.getId()); h.setEventType("STATUS");
        h.setFromStatus(device.getStatus()); h.setToStatus("IN_USE");
        h.setFromWarehouseId(device.getWarehouseId()); h.setToWarehouseId(null);
        h.setFromDepartmentId(device.getDepartmentId()); h.setToDepartmentId(order.getDepartmentId());
        h.setFromUserId(device.getUserId()); h.setToUserId(order.getCreatorUserId());
        h.setFromCustodianId(device.getCustodianId()); h.setToCustodianId(order.getCreatorUserId());
        h.setBusinessType("REQUISITION"); h.setBusinessNo(order.getOrderNo());
        h.setDetail("领用发放 " + order.getOrderNo());
        h.setOperatorUserId(CurrentUserContext.require().getUserId()); h.setRequestId(operationLog.currentRequestId());
        devices.insertHistory(h);
    }

    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
