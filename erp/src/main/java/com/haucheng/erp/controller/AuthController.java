package com.haucheng.erp.controller;

import com.haucheng.erp.auth.RequirePermission;
import com.haucheng.erp.common.ApiResponse;
import com.haucheng.erp.dto.LoginRequest;
import com.haucheng.erp.service.AuthService;
import com.haucheng.erp.vo.CurrentUserView;
import com.haucheng.erp.vo.LoginView;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<LoginView> login(@Valid @RequestBody LoginRequest request) { return ApiResponse.success(authService.login(request)); }

    @PostMapping("/logout")
    @RequirePermission
    public ApiResponse<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) { authService.logout(authorization); return ApiResponse.success(); }

    @GetMapping("/me")
    @RequirePermission
    public ApiResponse<CurrentUserView> me() { return ApiResponse.success(authService.me()); }
}
