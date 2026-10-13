package com.haucheng.erp.service;

import com.haucheng.erp.auth.CurrentUserContext;
import com.haucheng.erp.mapper.AuthMapper;
import com.haucheng.erp.mapper.RoleMapper;
import com.haucheng.erp.mapper.UserMapper;
import com.haucheng.erp.vo.CurrentUserView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock UserMapper users;
    @Mock RoleMapper roles;
    @Mock PasswordEncoder encoder;
    @Mock AuthMapper auth;
    @Mock OperationLogService operationLog;
    @InjectMocks UserService service;

    @AfterEach
    void clearContext() { CurrentUserContext.clear(); }

    @Test
    void selfDataScopeRestrictsUserPageToCurrentUser() {
        CurrentUserView current = new CurrentUserView();
        current.setUserId(9L);
        current.setDataScope("SELF");
        CurrentUserContext.set(current);
        when(users.selectPage(any(), any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(List.of());
        when(users.count(any(), any(), any(), any(), any())).thenReturn(0L);

        service.getPage(null, null, null, 1, 20);

        verify(users).selectPage(any(), any(), eq(null), eq(null), eq(9L), eq(20), eq(0));
        verify(users).count(any(), any(), eq(null), eq(null), eq(9L));
    }
}
