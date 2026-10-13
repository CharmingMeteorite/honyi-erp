package com.haucheng.erp.service;

import com.haucheng.erp.auth.CurrentUserContext;
import com.haucheng.erp.dto.LoginRequest;
import com.haucheng.erp.entity.SysUser;
import com.haucheng.erp.exception.BusinessException;
import com.haucheng.erp.mapper.AuthMapper;
import com.haucheng.erp.vo.CurrentUserView;
import com.haucheng.erp.vo.LoginView;
import com.haucheng.erp.vo.TokenUserView;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class AuthService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final AuthMapper authMapper;
    private final PasswordEncoder passwordEncoder;

    @Value("${erp.auth.token-hours:24}")
    private long tokenHours;

    @Transactional
    public LoginView login(LoginRequest request) {
        SysUser user = authMapper.selectByUsername(request.getUsername().trim());
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw BusinessException.unauthorized("账号或密码错误");
        }
        if (!Boolean.TRUE.equals(user.getStatus())) {
            throw BusinessException.forbidden("账号已停用");
        }
        String token = generateToken();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(tokenHours);
        authMapper.deleteExpiredTokens();
        authMapper.insertToken(hashToken(token), user.getId(), expiresAt);
        authMapper.updateLastLogin(user.getId());
        return new LoginView(token, "Bearer", expiresAt, authenticate(token));
    }

    @Transactional
    public void logout(String authorizationHeader) {
        String token = extractBearerToken(authorizationHeader);
        if (token != null) authMapper.deleteToken(hashToken(token));
        CurrentUserContext.clear();
    }

    public CurrentUserView me() { return CurrentUserContext.require(); }

    public CurrentUserView authenticate(String accessToken) {
        TokenUserView tokenUser = authMapper.selectTokenUser(hashToken(accessToken));
        if (tokenUser == null || !Boolean.TRUE.equals(tokenUser.getStatus())) return null;
        CurrentUserView user = new CurrentUserView();
        user.setUserId(tokenUser.getUserId());
        user.setUsername(tokenUser.getUsername());
        user.setRealName(tokenUser.getRealName());
        user.setEmployeeNo(tokenUser.getEmployeeNo());
        user.setStatus(tokenUser.getStatus());
        user.setDepartment(toDepartment(tokenUser));
        user.setRoles(authMapper.selectRoles(tokenUser.getUserId()).stream().map(x -> x.getRoleCode()).toList());
        user.setPermissions(authMapper.selectPermissions(tokenUser.getUserId()));
        String dataScope = authMapper.selectDataScope(tokenUser.getUserId());
        user.setDataScope(dataScope == null ? "SELF" : dataScope);
        return user;
    }

    public String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) return null;
        String prefix = "Bearer ";
        if (!authorizationHeader.regionMatches(true, 0, prefix, 0, prefix.length())) return null;
        String token = authorizationHeader.substring(prefix.length()).trim();
        return token.isEmpty() ? null : token;
    }

    @Transactional
    public void seedAdmin(String username, String password, String realName, String employeeNo) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) return;
        SysUser existing = authMapper.selectByUsername(username.trim());
        Long adminRoleId = authMapper.selectSystemAdminRoleId();
        if (existing != null) {
            if (adminRoleId != null) authMapper.insertUserRole(existing.getId(), adminRoleId);
            return;
        }
        SysUser user = new SysUser();
        user.setUsername(username.trim());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRealName(realName == null || realName.isBlank() ? username.trim() : realName.trim());
        user.setEmployeeNo(employeeNo == null || employeeNo.isBlank() ? null : employeeNo.trim());
        user.setStatus(true);
        authMapper.insert(user);
        if (adminRoleId != null) authMapper.insertUserRole(user.getId(), adminRoleId);
    }

    private com.haucheng.erp.vo.DepartmentBriefView toDepartment(TokenUserView user) { if (user.getDepartmentId() == null) return null; com.haucheng.erp.vo.DepartmentBriefView d = new com.haucheng.erp.vo.DepartmentBriefView(); d.setId(user.getDepartmentId()); d.setDepartmentCode(user.getDepartmentCode()); d.setDepartmentName(user.getDepartmentName()); return d; }
    private String generateToken() { byte[] bytes = new byte[32]; SECURE_RANDOM.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private String hashToken(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("JVM does not support SHA-256", e); }
    }
}
