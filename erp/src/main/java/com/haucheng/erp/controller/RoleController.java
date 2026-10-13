package com.haucheng.erp.controller;
import com.haucheng.erp.auth.RequirePermission; import com.haucheng.erp.common.ApiResponse; import com.haucheng.erp.dto.*; import com.haucheng.erp.service.RoleService; import com.haucheng.erp.vo.*; import jakarta.validation.Valid; import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*; import java.util.List;
@RestController @RequestMapping("/api/system/roles") @RequiredArgsConstructor public class RoleController { private final RoleService s;
 @GetMapping @RequirePermission("system.role.view") public ApiResponse<List<RoleView>> list(){return ApiResponse.success(s.getAll());}
 @GetMapping("/options") @RequirePermission({"system.user.view","system.user.create","system.user.update"}) public ApiResponse<List<RoleBriefView>> options(){return ApiResponse.success(s.getOptions());}
 @GetMapping("/{id}") @RequirePermission("system.role.view") public ApiResponse<RoleView> get(@PathVariable Long id){return ApiResponse.success(s.getById(id));}
 @PostMapping @RequirePermission("system.role.create") public ApiResponse<RoleView> create(@Valid @RequestBody RoleRequest r){return ApiResponse.success(s.create(r));}
 @PutMapping("/{id}") @RequirePermission("system.role.update") public ApiResponse<RoleView> update(@PathVariable Long id,@Valid @RequestBody RoleRequest r){return ApiResponse.success(s.update(id,r));}
 @PostMapping("/{id}/disable") @RequirePermission("system.role.disable") public ApiResponse<Void> disable(@PathVariable Long id){s.updateStatus(id,false);return ApiResponse.success();}
 @PostMapping("/{id}/enable") @RequirePermission("system.role.disable") public ApiResponse<Void> enable(@PathVariable Long id){s.updateStatus(id,true);return ApiResponse.success();}
 @DeleteMapping("/{id}") @RequirePermission("system.role.delete") public ApiResponse<Void> delete(@PathVariable Long id){s.delete(id);return ApiResponse.success();}
 @PutMapping("/{id}/permissions") @RequirePermission("system.role.permissions") public ApiResponse<RoleView> permissions(@PathVariable Long id,@Valid @RequestBody RolePermissionRequest r){return ApiResponse.success(s.replacePermissions(id,r));}
}
