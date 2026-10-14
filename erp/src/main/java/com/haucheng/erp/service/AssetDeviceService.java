package com.haucheng.erp.service;

import com.haucheng.erp.auth.CurrentUserContext;
import com.haucheng.erp.common.PageResult;
import com.haucheng.erp.common.Pagination;
import com.haucheng.erp.dto.DeviceCreateRequest;
import com.haucheng.erp.dto.DeviceStatusRequest;
import com.haucheng.erp.dto.DeviceUpdateRequest;
import com.haucheng.erp.entity.AssetDevice;
import com.haucheng.erp.entity.AssetDeviceHistory;
import com.haucheng.erp.entity.MdProduct;
import com.haucheng.erp.exception.BusinessException;
import com.haucheng.erp.mapper.AssetDeviceMapper;
import com.haucheng.erp.mapper.DepartmentMapper;
import com.haucheng.erp.mapper.ProductMapper;
import com.haucheng.erp.mapper.UserMapper;
import com.haucheng.erp.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AssetDeviceService {
    private static final Set<String> STATUSES = Set.of("IN_STOCK", "IN_USE", "BORROWED", "REPAIRING", "PENDING_EXCHANGE", "SHIPPED", "SCRAPPED", "MISSING");
    // Phase 3 只登记基础状态；借用、维修、调换、发货状态由后续单据动作接管。
    private static final Map<String, List<String>> TRANSITIONS = Map.of(
            "IN_STOCK", List.of("IN_USE", "SCRAPPED"),
            "IN_USE", List.of("IN_STOCK", "SCRAPPED"));

    private final AssetDeviceMapper devices;
    private final ProductMapper products;
    private final DepartmentMapper departments;
    private final UserMapper users;
    private final OperationLogService operationLog;
    private final DepartmentService departmentService;
    private final UserService userService;

    public PageResult<DeviceView> getPage(String keyword, String status, Long productId, Long departmentId,
                                         Long userId, Integer page, Integer size) {
        Pagination pagination = Pagination.of(page, size);
        String normalizedKeyword = trim(keyword);
        if (normalizedKeyword != null && normalizedKeyword.matches("(?i)([0-9a-f]{2}[:-]){5}[0-9a-f]{2}|([0-9a-f]{4}[.]){2}[0-9a-f]{4}|[0-9a-f]{12}")) {
            normalizedKeyword = normalizeMac(normalizedKeyword);
        }
        String normalizedStatus = trim(status);
        if (normalizedStatus != null && !STATUSES.contains(normalizedStatus)) throw BusinessException.badRequest("设备状态不正确");
        var current = CurrentUserContext.require();
        List<Long> departmentIds = null;
        Long self = null;
        if ("SELF".equals(current.getDataScope())) {
            self = current.getUserId();
        } else if (!"ALL".equals(current.getDataScope())) {
            departmentIds = allowedDepartments();
            if (departmentId != null && !departmentIds.contains(departmentId)) throw BusinessException.forbidden("没有访问目标部门数据的权限");
        }
        List<DeviceView> list = devices.selectPage(normalizedKeyword, normalizedStatus, productId, departmentId,
                userId, departmentIds, self, pagination);
        list.forEach(view -> view.setAllowedStatuses(view.getWarehouseId() == null && !Boolean.TRUE.equals(view.getRequisitionIssued()) ? TRANSITIONS.getOrDefault(view.getStatus(), List.of()) : List.of()));
        return new PageResult<>(list, pagination.page(), pagination.size(), devices.count(normalizedKeyword,
                normalizedStatus, productId, departmentId, userId, departmentIds, self));
    }

    public DeviceView getById(Long id) {
        DeviceView view = devices.selectViewById(id);
        if (view == null) throw BusinessException.notFound("设备不存在");
        ensureReadAccess(view.getDepartmentId(), view.getUserId(), view.getCustodianId());
        view.setHistory(devices.selectHistory(id));
        view.setIdentityEditable(devices.countInventoryClaim(id) == 0 && "IN_STOCK".equals(view.getStatus())
                && view.getHistory().stream().noneMatch(h -> "STATUS".equals(h.getEventType())));
        view.setAllowedStatuses(view.getWarehouseId() == null && !Boolean.TRUE.equals(view.getRequisitionIssued()) ? TRANSITIONS.getOrDefault(view.getStatus(), List.of()) : List.of());
        return view;
    }

    public List<DeviceHistoryView> getHistory(Long id) {
        return getById(id).getHistory();
    }

    public List<ProductView> getProductOptions(String keyword, boolean activeOnly) {
        CurrentUserContext.require();
        return products.selectPage(null, trim(keyword), null, null, null, activeOnly ? true : null,
                "DEVICE", Pagination.of(1, 100));
    }

    public List<DepartmentBriefView> getDepartmentOptions(boolean activeOnly) {
        CurrentUserContext.require();
        List<DepartmentBriefView> result = new ArrayList<>();
        flattenDepartments(departmentService.tree(), activeOnly, result);
        return result;
    }

    public PageResult<UserOptionView> getUserOptions(String keyword, Long departmentId, boolean activeOnly,
                                                    Integer page, Integer size) {
        var data = userService.getPage(keyword, activeOnly ? true : null, departmentId, page, size);
        var list = data.getList().stream().map(u ->
                new UserOptionView(u.getId(), u.getRealName(), u.getUsername(), u.getDepartmentId(), u.getStatus())).toList();
        return new PageResult<>(list, data.getPage(), data.getSize(), data.getTotal());
    }

    @Transactional
    public DeviceView create(DeviceCreateRequest request) {
        ensureWriteAccess(request.getDepartmentId());
        // 与 Product 编辑/停用共用父行锁，防止创建时管理属性被并发改写。
        MdProduct product = requireProduct(request.getProductId(), true);
        String requestedCode = trim(request.getDeviceCode());
        if (requestedCode != null && devices.countByDeviceCode(requestedCode, null) > 0) throw BusinessException.conflict("设备编号已存在");
        if (requestedCode != null && (!requestedCode.matches("[A-Za-z0-9._-]{1,64}")
                || requestedCode.toUpperCase(Locale.ROOT).startsWith("DEV")
                || requestedCode.toUpperCase(Locale.ROOT).startsWith("TMP-"))) {
            throw BusinessException.badRequest("自定义编号仅允许英文、数字、点、短横线和下划线，DEV和TMP-前缀由系统保留");
        }
        AssetDevice device = new AssetDevice();
        device.setDeviceCode(requestedCode == null ? "TMP-" + UUID.randomUUID() : requestedCode);
        device.setProductId(product.getId());
        device.setSerialNumber(trim(request.getSerialNumber()));
        device.setMacAddress(normalizeMac(request.getMacAddress()));
        device.setAssetNo(trim(request.getAssetNo()));
        copy(device, request);
        validate(device, product);
        devices.insert(device);
        if (requestedCode == null) {
            device.setDeviceCode(String.format(Locale.ROOT, "DEV%06d", device.getId()));
            devices.updateDeviceCode(device.getId(), device.getDeviceCode());
        }
        insertHistory("CREATE", null, device, "设备建档（公司持有状态，不产生库存数量）");
        recordDates(null, device);
        operationLog.record("DEVICE_CREATE", String.valueOf(device.getId()), "创建设备");
        return getById(device.getId());
    }

    @Transactional
    public DeviceView update(Long id, DeviceUpdateRequest request) {
        AssetDevice old = requireLocked(id);
        ensureWriteAccess(old.getDepartmentId());
        ensureWriteAccess(request.getDepartmentId());
        MdProduct product = requireProduct(old.getProductId(), false);
        AssetDevice updated = new AssetDevice();
        updated.setId(id);
        updated.setProductId(old.getProductId());
        updated.setStatus(old.getStatus());
        updated.setSerialNumber(trim(request.getSerialNumber()));
        updated.setMacAddress(normalizeMac(request.getMacAddress()));
        updated.setAssetNo(trim(request.getAssetNo()));
        copy(updated, request);
        if (devices.countRequisitionHistory(id) > 0 && !sameAssignments(old, updated)) throw BusinessException.conflict("已领用设备的归属须由业务单据变更");
        validateIdentityEdit(old, updated);
        validate(updated, product);
        if ("SCRAPPED".equals(old.getStatus()) && !sameAssignments(old, updated)) throw BusinessException.conflict("报废设备不能修改归属");
        if ("MISSING".equals(old.getStatus()) && !sameAssignments(old, updated)) throw BusinessException.conflict("盘点缺失设备的归属不能通过资料编辑修改");
        if ("IN_USE".equals(old.getStatus()) && (updated.getUserId() == null || updated.getDepartmentId() == null)) {
            throw BusinessException.badRequest("使用中的设备必须保留使用人和部门");
        }
        devices.update(updated);
        if (!sameAssignments(old, updated)) insertHistory("UPDATE", old, updated, "更正设备归属资料（非领用或调拨）");
        recordChange(old, updated, "SN", old.getSerialNumber(), updated.getSerialNumber());
        recordChange(old, updated, "MAC", old.getMacAddress(), updated.getMacAddress());
        recordChange(old, updated, "固定资产编号", old.getAssetNo(), updated.getAssetNo());
        recordDates(old, updated);
        operationLog.record("DEVICE_UPDATE", String.valueOf(id), "修改设备基础信息");
        return getById(id);
    }

    @Transactional
    public DeviceView updateStatus(Long id, DeviceStatusRequest request) {
        AssetDevice old = requireLocked(id);
        ensureWriteAccess(old.getDepartmentId());
        if (old.getWarehouseId() != null) throw BusinessException.conflict("已建立库存的设备须由后续库存单据改变状态");
        if (devices.countRequisitionHistory(id) > 0) throw BusinessException.conflict("已领用设备的状态须由业务单据变更");
        String target = trim(request.getStatus());
        String reason = trim(request.getReason());
        if (target == null || !STATUSES.contains(target)) throw BusinessException.badRequest("设备状态不正确");
        if (reason == null || reason.length() > 200) throw BusinessException.badRequest("请填写不超过200字的状态变更原因");
        if (!TRANSITIONS.getOrDefault(old.getStatus(), List.of()).contains(target)) {
            throw BusinessException.conflict("当前阶段不允许此状态转换");
        }
        if ("IN_USE".equals(target)) {
            if (old.getUserId() == null || old.getDepartmentId() == null) throw BusinessException.badRequest("请先设置使用人和部门");
            validateAssignments(old.getDepartmentId(), old.getUserId(), old.getCustodianId());
        }
        if (devices.updateStatusFrom(id, old.getStatus(), target) == 0) throw BusinessException.conflict("设备状态已变化，请刷新后重试");
        AssetDevice updated = devices.selectById(id);
        insertHistory("STATUS", old, updated, reason);
        operationLog.record("DEVICE_STATUS", String.valueOf(id), "设备状态：" + old.getStatus() + " -> " + target);
        return getById(id);
    }

    private MdProduct requireProduct(Long id, boolean creating) {
        MdProduct product = creating ? products.selectByIdForUpdate(id) : products.selectById(id);
        if (product == null) throw BusinessException.notFound("物资不存在");
        if (creating && !Boolean.TRUE.equals(product.getStatus())) throw BusinessException.badRequest("物资已停用，不能创建设备");
        if (!"DEVICE".equals(product.getProductType())) throw BusinessException.badRequest("数量型物资不能关联设备");
        return product;
    }

    private void validate(AssetDevice device, MdProduct product) {
        if (device.getWarehouseId() != null || device.getLocationId() != null) throw BusinessException.badRequest("普通设备接口不能设置仓库或库位");
        if (device.getWarrantyStartAt() != null && device.getWarrantyEndAt() != null
                && device.getWarrantyEndAt().isBefore(device.getWarrantyStartAt())) throw BusinessException.badRequest("保修结束不能早于保修开始");
        if (Boolean.TRUE.equals(product.getTrackSn()) && device.getSerialNumber() == null) throw BusinessException.badRequest("该物资要求填写SN");
        if (Boolean.TRUE.equals(product.getTrackMac()) && device.getMacAddress() == null) throw BusinessException.badRequest("该物资要求填写MAC");
        if (device.getSerialNumber() != null && devices.countBySerial(device.getSerialNumber(), device.getId()) > 0) throw BusinessException.conflict("SN已存在");
        if (device.getMacAddress() != null && devices.countByMac(device.getMacAddress(), device.getId()) > 0) throw BusinessException.conflict("MAC已存在");
        if (device.getAssetNo() != null && devices.countByAssetNo(device.getAssetNo(), device.getId()) > 0) throw BusinessException.conflict("固定资产编号已存在");
        validateAssignments(device.getDepartmentId(), device.getUserId(), device.getCustodianId());
    }

    private void validateIdentityEdit(AssetDevice old, AssetDevice updated) {
        boolean changed = !Objects.equals(old.getSerialNumber(), updated.getSerialNumber())
                || !Objects.equals(old.getMacAddress(), updated.getMacAddress())
                || !Objects.equals(old.getAssetNo(), updated.getAssetNo());
        // 首次状态操作后永久锁定身份；后续业务应继续通过此入口保护身份。
        if (changed && (!"IN_STOCK".equals(old.getStatus()) || devices.countStatusHistory(old.getId()) > 0)) {
            throw BusinessException.conflict("设备已有生命周期状态记录，不能修改SN、MAC或固定资产编号");
        }
    }

    private void validateAssignments(Long departmentId, Long userId, Long custodianId) {
        if (departmentId != null && departments.countActive(departmentId) == 0) throw BusinessException.badRequest("部门不存在或已停用");
        if ((userId != null || custodianId != null) && departmentId == null) throw BusinessException.badRequest("设置使用人或保管人时必须指定归属部门");
        validateUserDepartment(userId, departmentId, "使用人");
        validateUserDepartment(custodianId, departmentId, "保管人");
    }

    private void validateUserDepartment(Long userId, Long departmentId, String label) {
        if (userId == null) return;
        if (users.countActive(userId) == 0) throw BusinessException.badRequest(label + "不存在或已停用");
        if (users.countActiveInDepartment(userId, departmentId) == 0) throw BusinessException.badRequest(label + "不属于目标部门");
    }

    private void ensureReadAccess(Long departmentId, Long userId, Long custodianId) {
        var current = CurrentUserContext.require();
        if ("ALL".equals(current.getDataScope())) return;
        if ("SELF".equals(current.getDataScope())) {
            if (!current.getUserId().equals(userId) && !current.getUserId().equals(custodianId)) throw BusinessException.forbidden("没有访问该设备数据的权限");
        } else if (departmentId == null || !allowedDepartments().contains(departmentId)) {
            throw BusinessException.forbidden("没有访问该设备数据的权限");
        }
    }

    private void ensureWriteAccess(Long departmentId) {
        var current = CurrentUserContext.require();
        if ("ALL".equals(current.getDataScope())) return;
        if ("SELF".equals(current.getDataScope())) throw BusinessException.forbidden("本人数据范围仅允许查看设备");
        if (departmentId == null || !allowedDepartments().contains(departmentId)) throw BusinessException.forbidden("没有访问目标部门数据的权限");
    }

    private List<Long> allowedDepartments() {
        var current = CurrentUserContext.require();
        if (current.getDepartment() == null) return List.of(Long.MIN_VALUE);
        List<Long> ids = users.selectDepartmentTreeIds(current.getDepartment().getId());
        return ids.isEmpty() ? List.of(Long.MIN_VALUE) : ids;
    }

    private AssetDevice requireLocked(Long id) {
        AssetDevice device = devices.selectByIdForUpdate(id);
        if (device == null) throw BusinessException.notFound("设备不存在");
        if (devices.selectInventoryClaim(id) != null) throw BusinessException.conflict("设备已被正式库存单据占用，请先处理该单据");
        return device;
    }

    private void flattenDepartments(List<DepartmentView> source, boolean activeOnly, List<DepartmentBriefView> result) {
        for (DepartmentView d : source) {
            if (!activeOnly || Boolean.TRUE.equals(d.getStatus())) {
                DepartmentBriefView view = new DepartmentBriefView();
                view.setId(d.getId());
                view.setDepartmentCode(d.getDepartmentCode());
                view.setDepartmentName(d.getDepartmentName());
                result.add(view);
            }
            flattenDepartments(d.getChildren(), activeOnly, result);
        }
    }

    private boolean sameAssignments(AssetDevice a, AssetDevice b) {
        return Objects.equals(a.getDepartmentId(), b.getDepartmentId())
                && Objects.equals(a.getUserId(), b.getUserId()) && Objects.equals(a.getCustodianId(), b.getCustodianId());
    }

    private void recordChange(AssetDevice old, AssetDevice next, String field, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            insertHistory("UPDATE", old, next, field + "：" + (before == null ? "未设置" : before)
                    + " -> " + (after == null ? "未设置" : after));
        }
    }

    private void recordDates(AssetDevice old, AssetDevice next) {
        recordChange(old, next, "购入时间", old == null ? null : old.getPurchaseAt(), next.getPurchaseAt());
        recordChange(old, next, "入库时间（资料登记）", old == null ? null : old.getInboundAt(), next.getInboundAt());
        recordChange(old, next, "首次使用时间", old == null ? null : old.getFirstUseAt(), next.getFirstUseAt());
        recordChange(old, next, "保修开始", old == null ? null : old.getWarrantyStartAt(), next.getWarrantyStartAt());
        recordChange(old, next, "保修结束", old == null ? null : old.getWarrantyEndAt(), next.getWarrantyEndAt());
    }

    private void insertHistory(String type, AssetDevice old, AssetDevice next, String detail) {
        AssetDeviceHistory history = new AssetDeviceHistory();
        history.setDeviceId(next.getId());
        history.setEventType(type);
        if (old != null) {
            history.setFromStatus(old.getStatus());
            history.setFromDepartmentId(old.getDepartmentId());
            history.setFromUserId(old.getUserId());
            history.setFromCustodianId(old.getCustodianId());
        }
        history.setToStatus(next.getStatus());
        history.setToDepartmentId(next.getDepartmentId());
        history.setToUserId(next.getUserId());
        history.setToCustodianId(next.getCustodianId());
        history.setDetail(detail);
        history.setOperatorUserId(CurrentUserContext.require().getUserId());
        history.setRequestId(operationLog.currentRequestId());
        devices.insertHistory(history);
    }

    private void copy(AssetDevice d, DeviceCreateRequest r) {
        d.setDepartmentId(r.getDepartmentId());
        d.setUserId(r.getUserId());
        d.setCustodianId(r.getCustodianId());
        d.setWarehouseId(r.getWarehouseId());
        d.setLocationId(r.getLocationId());
        d.setPurchaseAt(r.getPurchaseAt());
        d.setInboundAt(r.getInboundAt());
        d.setFirstUseAt(r.getFirstUseAt());
        d.setWarrantyStartAt(r.getWarrantyStartAt());
        d.setWarrantyEndAt(r.getWarrantyEndAt());
        d.setRemark(trim(r.getRemark()));
        d.setNotes(trim(r.getNotes()));
    }

    private void copy(AssetDevice d, DeviceUpdateRequest r) {
        d.setDepartmentId(r.getDepartmentId());
        d.setUserId(r.getUserId());
        d.setCustodianId(r.getCustodianId());
        d.setWarehouseId(r.getWarehouseId());
        d.setLocationId(r.getLocationId());
        d.setPurchaseAt(r.getPurchaseAt());
        d.setInboundAt(r.getInboundAt());
        d.setFirstUseAt(r.getFirstUseAt());
        d.setWarrantyStartAt(r.getWarrantyStartAt());
        d.setWarrantyEndAt(r.getWarrantyEndAt());
        d.setRemark(trim(r.getRemark()));
        d.setNotes(trim(r.getNotes()));
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeMac(String value) {
        String v = trim(value);
        if (v == null) return null;
        if (!v.matches("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}|([0-9a-f]{2}-){5}[0-9a-f]{2}|([0-9a-f]{4}[.]){2}[0-9a-f]{4}|[0-9a-f]{12}")) {
            throw BusinessException.badRequest("MAC格式不正确");
        }
        String hex = v.replace(":", "").replace("-", "").replace(".", "").toUpperCase(Locale.ROOT);
        return hex.replaceAll("(..)(?=.)", "$1:");
    }
}
