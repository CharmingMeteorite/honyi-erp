package com.haucheng.erp.controller;
import com.haucheng.erp.auth.RequirePermission; import com.haucheng.erp.common.ApiResponse; import com.haucheng.erp.dto.DepartmentRequest; import com.haucheng.erp.service.DepartmentService; import com.haucheng.erp.vo.DepartmentView; import jakarta.validation.Valid; import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*; import java.util.List;
@RestController @RequestMapping("/api/system/departments") @RequiredArgsConstructor public class DepartmentController { private final DepartmentService s;
 @GetMapping("/tree") @RequirePermission("system.department.view") public ApiResponse<List<DepartmentView>> tree(){return ApiResponse.success(s.tree());}
 @GetMapping @RequirePermission("system.department.view") public ApiResponse<List<DepartmentView>> list(){return ApiResponse.success(s.tree());}
 @GetMapping("/{id}") @RequirePermission("system.department.view") public ApiResponse<DepartmentView> get(@PathVariable Long id){return ApiResponse.success(s.find(id));}
 @PostMapping @RequirePermission("system.department.create") public ApiResponse<DepartmentView> create(@Valid @RequestBody DepartmentRequest r){return ApiResponse.success(s.create(r));}
 @PutMapping("/{id}") @RequirePermission("system.department.update") public ApiResponse<DepartmentView> update(@PathVariable Long id,@Valid @RequestBody DepartmentRequest r){return ApiResponse.success(s.update(id,r));}
 @PostMapping("/{id}/disable") @RequirePermission("system.department.disable") public ApiResponse<Void> disable(@PathVariable Long id){s.updateStatus(id,false);return ApiResponse.success();}
 @PostMapping("/{id}/enable") @RequirePermission("system.department.disable") public ApiResponse<Void> enable(@PathVariable Long id){s.updateStatus(id,true);return ApiResponse.success();}
}
