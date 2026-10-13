package com.haucheng.erp.service;

import com.haucheng.erp.auth.CurrentUserContext;
import com.haucheng.erp.common.PageResult;
import com.haucheng.erp.common.Pagination;
import com.haucheng.erp.dto.*;
import com.haucheng.erp.entity.SysUser;
import com.haucheng.erp.exception.BusinessException;
import com.haucheng.erp.mapper.AuthMapper;
import com.haucheng.erp.mapper.RoleMapper;
import com.haucheng.erp.mapper.UserMapper;
import com.haucheng.erp.vo.UserView;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserMapper users;
    private final RoleMapper roles;
    private final PasswordEncoder encoder;
    private final AuthMapper auth;
    private final OperationLogService operationLog;

    public PageResult<UserView> getPage(String keyword, Boolean status, Long departmentId, Integer page, Integer size) {
        Pagination pagination = Pagination.of(page, size);
        var current = CurrentUserContext.require();
        Long department = departmentId;
        List<Long> departmentIds = null;
        Long self = null;
        if ("SELF".equals(current.getDataScope())) {
            self = current.getUserId();
        } else if ("DEPARTMENT".equals(current.getDataScope())) {
            department = null;
            departmentIds = current.getDepartment() == null ? List.of(Long.MIN_VALUE)
                    : users.selectDepartmentTreeIds(current.getDepartment().getId());
            if (departmentId != null) {
                if (!departmentIds.contains(departmentId)) {
                    throw BusinessException.forbidden("没有访问目标部门数据的权限");
                }
                departmentIds = List.of(departmentId);
            }
        }
        String normalizedKeyword = trim(keyword);
        List<UserView> list = users.selectPage(normalizedKeyword, status, department, departmentIds, self, pagination.size(), pagination.offset());
        list.forEach(view -> {
            view.setRoles(users.selectRoles(view.getId()));
            view.setDataScope(users.selectDataScope(view.getId()));
        });
        return new PageResult<>(list, pagination.page(), pagination.size(), users.count(normalizedKeyword, status, department, departmentIds, self));
    }

    public UserView getById(Long id) {
        ensureDataAccess(require(id));
        return loadView(id);
    }

    @Transactional
    public UserView create(UserCreateRequest request) {
        ensureCreateAccess(request.getDepartmentId());
        String username = request.getUsername().trim();
        ensureAvailable(username, request.getEmployeeNo(), null);
        validateDepartment(request.getDepartmentId());
        List<Long> roleIds = validateRoles(request.getRoleIds());
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setRealName(request.getRealName().trim());
        user.setPasswordHash(encoder.encode(request.getPassword()));
        user.setEmployeeNo(trim(request.getEmployeeNo()));
        user.setPhone(trim(request.getPhone()));
        user.setEmail(trim(request.getEmail()));
        user.setDepartmentId(request.getDepartmentId());
        user.setStatus(request.getStatus() == null || request.getStatus());
        users.insert(user);
        roleIds.forEach(roleId -> users.insertRole(user.getId(), roleId));
        operationLog.record("USER_CREATE", String.valueOf(user.getId()), "创建用户");
        return loadView(user.getId());
    }

    @Transactional
    public UserView update(Long id, UserUpdateRequest request) {
        SysUser old = requireAccessible(id);
        var current = CurrentUserContext.require();
        if (request.getStatus() != null && !Objects.equals(request.getStatus(), old.getStatus())) {
            requirePermission("system.user.disable");
            if (current.getUserId().equals(id) && !request.getStatus()) {
                throw BusinessException.conflict("不能停用当前登录账号");
            }
        }
        List<Long> oldRoleIds = users.selectRoleIds(id);
        if (!new LinkedHashSet<>(oldRoleIds).equals(new LinkedHashSet<>(request.getRoleIds()))) {
            requirePermission("system.user.roles");
        }
        ensureTargetDepartmentAccess(request.getDepartmentId());
        ensureAvailable(old.getUsername(), request.getEmployeeNo(), id);
        validateDepartment(request.getDepartmentId());
        List<Long> roleIds = validateRoles(request.getRoleIds());
        SysUser user = new SysUser();
        user.setId(id);
        user.setRealName(request.getRealName().trim());
        user.setEmployeeNo(trim(request.getEmployeeNo()));
        user.setPhone(trim(request.getPhone()));
        user.setEmail(trim(request.getEmail()));
        user.setDepartmentId(request.getDepartmentId());
        user.setStatus(request.getStatus() == null ? old.getStatus() : request.getStatus());
        users.update(user);
        users.deleteRoles(id);
        roleIds.forEach(roleId -> users.insertRole(id, roleId));
        auth.deleteUserTokens(id);
        operationLog.record("USER_UPDATE", String.valueOf(id), "修改用户");
        return loadView(id);
    }

    @Transactional
    public void status(Long id, boolean enabled) {
        requireAccessible(id);
        if (CurrentUserContext.require().getUserId().equals(id) && !enabled) {
            throw BusinessException.conflict("不能停用当前登录账号");
        }
        users.updateStatus(id, enabled);
        if (!enabled) auth.deleteUserTokens(id);
        operationLog.record(enabled ? "USER_ENABLE" : "USER_DISABLE", String.valueOf(id), enabled ? "启用用户" : "停用用户");
    }

    @Transactional
    public void reset(Long id, PasswordResetRequest request) {
        requireAccessible(id);
        users.resetPassword(id, encoder.encode(request.getNewPassword()));
        auth.deleteUserTokens(id);
        operationLog.record("USER_RESET_PASSWORD", String.valueOf(id), "重置用户密码");
    }

    @Transactional
    public void roles(Long id, List<Long> roleIds) {
        requireAccessible(id);
        List<Long> validIds = validateRoles(roleIds);
        users.deleteRoles(id);
        validIds.forEach(roleId -> users.insertRole(id, roleId));
        auth.deleteUserTokens(id);
        operationLog.record("USER_ROLES_UPDATE", String.valueOf(id), "修改用户角色");
    }

    private UserView loadView(Long id) {
        UserView view = users.selectViewById(id);
        if (view == null) throw BusinessException.notFound("用户不存在");
        view.setRoles(users.selectRoles(id));
        view.setPermissions(auth.selectPermissions(id));
        view.setDataScope(users.selectDataScope(id));
        return view;
    }

    private SysUser require(Long id) {
        SysUser user = users.selectById(id);
        if (user == null) throw BusinessException.notFound("用户不存在");
        return user;
    }

    private SysUser requireAccessible(Long id) {
        SysUser user = require(id);
        ensureDataAccess(user);
        return user;
    }

    private void ensureDataAccess(SysUser target) {
        var current = CurrentUserContext.get();
        if (current == null || "ALL".equals(current.getDataScope())) return;
        if ("SELF".equals(current.getDataScope()) && !target.getId().equals(current.getUserId())) {
            throw BusinessException.forbidden("没有访问该用户数据的权限");
        }
        Long currentDepartment = current.getDepartment() == null ? null : current.getDepartment().getId();
        if ("DEPARTMENT".equals(current.getDataScope()) && (currentDepartment == null
                || !users.selectDepartmentTreeIds(currentDepartment).contains(target.getDepartmentId()))) {
            throw BusinessException.forbidden("没有访问该用户数据的权限");
        }
    }

    private void ensureCreateAccess(Long departmentId) {
        var current = CurrentUserContext.require();
        if ("SELF".equals(current.getDataScope())) {
            throw BusinessException.forbidden("当前数据范围不允许创建用户");
        }
        ensureTargetDepartmentAccess(departmentId);
    }

    private void ensureTargetDepartmentAccess(Long departmentId) {
        var current = CurrentUserContext.require();
        if ("ALL".equals(current.getDataScope())) return;
        if ("DEPARTMENT".equals(current.getDataScope())) {
            Long ownDepartment = current.getDepartment() == null ? null : current.getDepartment().getId();
            if (departmentId == null || ownDepartment == null
                    || !users.selectDepartmentTreeIds(ownDepartment).contains(departmentId)) {
                throw BusinessException.forbidden("没有访问目标部门数据的权限");
            }
        }
    }

    private void requirePermission(String permission) {
        var current = CurrentUserContext.require();
        if (!current.getRoles().contains("SYSTEM_ADMIN") && !current.getPermissions().contains(permission)) {
            throw BusinessException.forbidden("没有执行该操作的权限");
        }
    }

    private List<Long> validateRoles(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) throw BusinessException.badRequest("至少选择一个角色");
        List<Long> uniqueIds = new ArrayList<>(new LinkedHashSet<>(roleIds));
        if (uniqueIds.size() != roleIds.size()) throw BusinessException.badRequest("角色不能重复选择");
        var current = CurrentUserContext.require();
        boolean systemAdmin = current.getRoles().contains("SYSTEM_ADMIN");
        for (Long roleId : uniqueIds) {
            var role = roles.selectById(roleId);
            if (role == null || !Boolean.TRUE.equals(role.getStatus())) throw BusinessException.badRequest("包含不存在或停用的角色");
            if (!systemAdmin && ("SYSTEM_ADMIN".equals(role.getRoleCode())
                    || scopeRank(role.getDataScope()) > scopeRank(current.getDataScope()))) {
                throw BusinessException.forbidden("不能授予超出自身范围的角色");
            }
            if (!systemAdmin) {
                List<String> rolePermissions = roles.selectPermissionCodes(roleId);
                if (rolePermissions != null && rolePermissions.stream().anyMatch(permission ->
                        !current.getPermissions().contains(permission))) {
                    throw BusinessException.forbidden("不能授予超出自身权限的角色");
                }
            }
        }
        return uniqueIds;
    }

    private int scopeRank(String scope) {
        return "ALL".equals(scope) ? 3 : "DEPARTMENT".equals(scope) ? 2 : 1;
    }

    private void ensureAvailable(String username, String employeeNo, Long excludeId) {
        if (users.countUsername(username, excludeId) > 0) throw BusinessException.conflict("用户名已存在");
        String normalizedEmployeeNo = trim(employeeNo);
        if (normalizedEmployeeNo != null && users.countEmployeeNo(normalizedEmployeeNo, excludeId) > 0) throw BusinessException.conflict("工号已存在");
    }

    private void validateDepartment(Long departmentId) {
        if (departmentId != null && users.countDepartment(departmentId) == 0) throw BusinessException.badRequest("部门不存在或已停用");
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
