package com.haucheng.erp.service;

import com.haucheng.erp.auth.CurrentUserContext;
import com.haucheng.erp.exception.BusinessException;
import com.haucheng.erp.mapper.UserMapper;
import com.haucheng.erp.vo.CurrentUserView;
import com.haucheng.erp.vo.DepartmentBriefView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InventoryScopeServiceTest {
    private final UserMapper users = mock(UserMapper.class);
    private final InventoryScopeService scope = new InventoryScopeService(users);
    @AfterEach void clear() { CurrentUserContext.clear(); }

    @Test void selfOrMissingDepartmentNeverExpandsToAll() {
        CurrentUserView current = new CurrentUserView(); current.setUserId(1L); CurrentUserContext.set(current);
        assertNotNull(scope.departmentIds());
        assertThrows(BusinessException.class, () -> scope.ensureDepartment(null));
        current.setDataScope("DEPARTMENT");
        assertThrows(BusinessException.class, () -> scope.ensureDepartment(1L));
        verifyNoInteractions(users);
    }

    @Test void departmentIncludesChildrenButRejectsOutsideWarehouse() {
        CurrentUserView current = new CurrentUserView(); current.setDataScope("DEPARTMENT");
        DepartmentBriefView department = new DepartmentBriefView(); department.setId(1L); current.setDepartment(department);
        CurrentUserContext.set(current); when(users.selectDepartmentTreeIds(1L)).thenReturn(List.of(1L, 2L));
        scope.ensureDepartment(2L);
        assertThrows(BusinessException.class, () -> scope.ensureDepartment(3L));
    }

    @Test void unauthenticatedIsRejectedAndAllCanAccessGlobalWarehouse() {
        assertThrows(BusinessException.class, scope::departmentIds);
        CurrentUserView current = new CurrentUserView(); current.setDataScope("ALL"); CurrentUserContext.set(current);
        assertNull(scope.departmentIds()); scope.ensureDepartment(null);
    }
}
