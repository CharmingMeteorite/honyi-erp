package com.haucheng.erp.service;

import com.haucheng.erp.entity.SysUser;
import com.haucheng.erp.exception.BusinessException;
import com.haucheng.erp.mapper.AuthMapper;
import com.haucheng.erp.dto.LoginRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock AuthMapper authMapper;
    @Mock PasswordEncoder passwordEncoder;
    @InjectMocks AuthService service;

    @Test
    void disabledUserCannotLogin() {
        SysUser user = new SysUser();
        user.setId(7L);
        user.setUsername("disabled");
        user.setPasswordHash("hash");
        user.setStatus(false);
        when(authMapper.selectByUsername("disabled")).thenReturn(user);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        LoginRequest request = new LoginRequest();
        request.setUsername("disabled");
        request.setPassword("password");

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.login(request));

        assertEquals(40300, error.getCode());
    }
}
