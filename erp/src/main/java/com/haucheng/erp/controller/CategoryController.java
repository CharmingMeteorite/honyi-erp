package com.haucheng.erp.controller;
import com.haucheng.erp.auth.RequirePermission; import com.haucheng.erp.common.ApiResponse; import com.haucheng.erp.dto.CategoryRequest; import com.haucheng.erp.service.CategoryService; import com.haucheng.erp.vo.CategoryView; import jakarta.validation.Valid; import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*; import java.util.List;
@RestController @RequestMapping("/api/master-data/categories") @RequiredArgsConstructor public class CategoryController { private final CategoryService service;
 @GetMapping @RequirePermission("system.category.view") public ApiResponse<List<CategoryView>> list(){return ApiResponse.success(service.getAll());}
 @GetMapping("/tree") @RequirePermission("system.category.view") public ApiResponse<List<CategoryView>> tree(){return ApiResponse.success(service.tree());}
 @GetMapping("/{id}") @RequirePermission("system.category.view") public ApiResponse<CategoryView> get(@PathVariable Long id){return ApiResponse.success(service.getById(id));}
 @PostMapping @RequirePermission("system.category.create") public ApiResponse<CategoryView> create(@Valid @RequestBody CategoryRequest r){return ApiResponse.success(service.create(r));}
 @PutMapping("/{id}") @RequirePermission("system.category.update") public ApiResponse<CategoryView> update(@PathVariable Long id,@Valid @RequestBody CategoryRequest r){return ApiResponse.success(service.update(id,r));}
 @PostMapping("/{id}/status") @RequirePermission("system.category.disable") public ApiResponse<Void> status(@PathVariable Long id,@RequestParam Boolean enabled){service.updateStatus(id,enabled);return ApiResponse.success();}
 @PostMapping("/{id}/enable") @RequirePermission("system.category.disable") public ApiResponse<Void> enable(@PathVariable Long id){service.updateStatus(id,true);return ApiResponse.success();}
 @PostMapping("/{id}/disable") @RequirePermission("system.category.disable") public ApiResponse<Void> disable(@PathVariable Long id){service.updateStatus(id,false);return ApiResponse.success();}
 @DeleteMapping("/{id}") @RequirePermission("system.category.delete") public ApiResponse<Void> delete(@PathVariable Long id){service.delete(id);return ApiResponse.success();}
}
