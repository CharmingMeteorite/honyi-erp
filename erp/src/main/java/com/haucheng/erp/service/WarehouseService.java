package com.haucheng.erp.service;

import com.haucheng.erp.auth.CurrentUserContext;
import com.haucheng.erp.common.PageResult;
import com.haucheng.erp.common.Pagination;
import com.haucheng.erp.dto.WarehouseRequest;
import com.haucheng.erp.entity.WhWarehouse;
import com.haucheng.erp.exception.BusinessException;
import com.haucheng.erp.mapper.WarehouseMapper;
import com.haucheng.erp.mapper.DepartmentMapper;
import java.util.Objects;
import com.haucheng.erp.vo.WarehouseView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WarehouseService {
    private final WarehouseMapper mapper;
    private final OperationLogService operationLog;
    private final InventoryScopeService scope;
    private final DepartmentMapper departments;

    public PageResult<WarehouseView> getPage(String keyword, Boolean status, Integer page, Integer size) {
        Pagination pagination = Pagination.of(page, size);
        String normalized = trim(keyword);
        return new PageResult<>(mapper.selectPage(normalized, status, scope.departmentIds(), pagination), pagination.page(), pagination.size(), mapper.count(normalized, status, scope.departmentIds()));
    }

    public WarehouseView getById(Long id) {
        WhWarehouse warehouse = require(id);
        scope.ensureDepartment(warehouse.getDepartmentId());
        return toView(warehouse);
    }

    @Transactional
    public WarehouseView create(WarehouseRequest request) {
        scope.ensureDepartment(request.getDepartmentId());
        String code = required(request.getWarehouseCode());
        String name = required(request.getWarehouseName());
        ensureUnique(code, name, null);
        WhWarehouse warehouse = new WhWarehouse();
        warehouse.setWarehouseCode(code);
        warehouse.setWarehouseName(name);
        warehouse.setRemark(trim(request.getRemark()));
        if (request.getDepartmentId() != null && departments.countActive(request.getDepartmentId()) == 0) throw BusinessException.badRequest("仓库所属部门不存在或已停用");
        warehouse.setDepartmentId(request.getDepartmentId());
        mapper.insert(warehouse);
        operationLog.record("WAREHOUSE_CREATE", String.valueOf(warehouse.getId()), "创建仓库");
        return getById(warehouse.getId());
    }

    @Transactional
    public WarehouseView update(Long id, WarehouseRequest request) {
        CurrentUserContext.require();
        WhWarehouse old = mapper.selectByIdForUpdate(id);
        if (old == null) throw BusinessException.notFound("仓库不存在");
        scope.ensureDepartment(old.getDepartmentId());
        String code = required(request.getWarehouseCode());
        String name = required(request.getWarehouseName());
        ensureUnique(code, name, id);
        WhWarehouse warehouse = new WhWarehouse();
        warehouse.setId(id);
        warehouse.setWarehouseCode(code);
        warehouse.setWarehouseName(name);
        warehouse.setRemark(trim(request.getRemark()));
        if (!Objects.equals(old.getDepartmentId(), request.getDepartmentId())) throw BusinessException.conflict("仓库所属部门创建后不能更改");
        mapper.update(warehouse);
        operationLog.record("WAREHOUSE_UPDATE", String.valueOf(id), "修改仓库");
        return getById(id);
    }

    @Transactional
    public void updateStatus(Long id, boolean status) {
        CurrentUserContext.require();
        WhWarehouse warehouse = mapper.selectByIdForUpdate(id);
        if (warehouse == null) throw BusinessException.notFound("仓库不存在");
        scope.ensureDepartment(warehouse.getDepartmentId());
        if (Boolean.TRUE.equals(warehouse.getStatus()) == status) throw BusinessException.conflict("仓库状态未发生变化");
        mapper.updateStatus(id, status);
        operationLog.record(status ? "WAREHOUSE_ENABLE" : "WAREHOUSE_DISABLE", String.valueOf(id), status ? "启用仓库" : "停用仓库");
    }

    private WhWarehouse require(Long id) {
        WhWarehouse warehouse = mapper.selectById(id);
        if (warehouse == null) throw BusinessException.notFound("仓库不存在");
        return warehouse;
    }

    private void ensureUnique(String code, String name, Long excludeId) {
        if (mapper.selectByCode(code, excludeId) != null) throw BusinessException.conflict("仓库编码已存在");
        if (mapper.selectByName(name, excludeId) != null) throw BusinessException.conflict("仓库名称已存在");
    }

    private WarehouseView toView(WhWarehouse warehouse) {
        WarehouseView view = new WarehouseView();
        view.setId(warehouse.getId()); view.setWarehouseCode(warehouse.getWarehouseCode());
        view.setWarehouseName(warehouse.getWarehouseName()); view.setRemark(warehouse.getRemark());
        if (warehouse.getDepartmentId() != null) view.setDepartmentName(departments.selectById(warehouse.getDepartmentId()).getDepartmentName());
        view.setDepartmentId(warehouse.getDepartmentId()); view.setStatus(warehouse.getStatus()); view.setCreatedAt(warehouse.getCreatedAt()); view.setUpdatedAt(warehouse.getUpdatedAt());
        return view;
    }

    private String required(String value) {
        String normalized = trim(value);
        if (normalized == null) throw BusinessException.badRequest("仓库编码和名称不能为空");
        return normalized;
    }

    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
