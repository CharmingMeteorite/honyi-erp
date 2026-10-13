package com.haucheng.erp.controller;
import com.haucheng.erp.auth.RequirePermission; import com.haucheng.erp.common.*; import com.haucheng.erp.dto.*; import com.haucheng.erp.service.UserService; import com.haucheng.erp.vo.UserView; import jakarta.validation.Valid; import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*; import java.util.List;
@RestController @RequestMapping("/api/system/users") @RequiredArgsConstructor @RequirePermission("system.user.view") public class UserController { private final UserService service;
 @GetMapping public ApiResponse<PageResult<UserView>> page(@RequestParam(required=false)String keyword,@RequestParam(required=false)Boolean status,@RequestParam(required=false)Long departmentId,@RequestParam(required=false)Integer page,@RequestParam(required=false)Integer size){return ApiResponse.success(service.getPage(keyword,status,departmentId,page,size));}
 @GetMapping("/{id}") public ApiResponse<UserView> get(@PathVariable Long id){return ApiResponse.success(service.getById(id));}
 @PostMapping @RequirePermission("system.user.create") public ApiResponse<UserView> create(@Valid @RequestBody UserCreateRequest r){return ApiResponse.success(service.create(r));}
 @PutMapping("/{id}") @RequirePermission("system.user.update") public ApiResponse<UserView> update(@PathVariable Long id,@Valid @RequestBody UserUpdateRequest r){return ApiResponse.success(service.update(id,r));}
 @PostMapping("/{id}/disable") @RequirePermission("system.user.disable") public ApiResponse<Void> disable(@PathVariable Long id){service.status(id,false);return ApiResponse.success();}
 @PostMapping("/{id}/enable") @RequirePermission("system.user.disable") public ApiResponse<Void> enable(@PathVariable Long id){service.status(id,true);return ApiResponse.success();}
 @PostMapping("/{id}/reset-password") @RequirePermission("system.user.reset-password") public ApiResponse<Void> reset(@PathVariable Long id,@Valid @RequestBody PasswordResetRequest r){service.reset(id,r);return ApiResponse.success();}
 @PutMapping("/{id}/roles") @RequirePermission("system.user.roles") public ApiResponse<Void> roles(@PathVariable Long id,@Valid @RequestBody UserRolesRequest r){service.roles(id,r.getRoleIds());return ApiResponse.success();}
}
