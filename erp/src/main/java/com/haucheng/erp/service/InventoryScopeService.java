package com.haucheng.erp.service;

import com.haucheng.erp.auth.CurrentUserContext;
import com.haucheng.erp.exception.BusinessException;
import com.haucheng.erp.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryScopeService {
    private final UserMapper users;

    // 库存是仓库整体余额，不能按设备使用人拆分后当成真实库存展示。
    public List<Long> departmentIds() {
        var current = CurrentUserContext.require();
        if ("ALL".equals(current.getDataScope())) return null;
        if (!"DEPARTMENT".equals(current.getDataScope()) || current.getDepartment() == null) return List.of(Long.MIN_VALUE);
        List<Long> ids = users.selectDepartmentTreeIds(current.getDepartment().getId());
        return ids.isEmpty() ? List.of(Long.MIN_VALUE) : ids;
    }

    public void ensureDepartment(Long departmentId) {
        List<Long> allowed = departmentIds();
        if (allowed != null && (departmentId == null || !allowed.contains(departmentId))) {
            throw BusinessException.forbidden("没有访问该仓库所属部门数据的权限");
        }
    }

    public void ensureDeviceDepartment(Long departmentId) {
        ensureDepartment(departmentId);
    }
}
