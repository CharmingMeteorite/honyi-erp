package com.haucheng.erp.controller;

import com.haucheng.erp.auth.RequirePermission;
import com.haucheng.erp.common.ApiResponse;
import com.haucheng.erp.common.PageResult;
import com.haucheng.erp.dto.DeviceCreateRequest;
import com.haucheng.erp.dto.DeviceStatusRequest;
import com.haucheng.erp.dto.DeviceUpdateRequest;
import com.haucheng.erp.service.AssetDeviceService;
import com.haucheng.erp.vo.DeviceHistoryView;
import com.haucheng.erp.vo.DeviceView;
import com.haucheng.erp.vo.DepartmentBriefView;
import com.haucheng.erp.vo.UserOptionView;
import com.haucheng.erp.vo.ProductView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/assets/devices")
@RequiredArgsConstructor
@RequirePermission("system.device.view")
public class AssetDeviceController {
    private final AssetDeviceService service;

    @GetMapping
    public ApiResponse<PageResult<DeviceView>> getPage(@RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) String status,
                                                    @RequestParam(required = false) Long productId,
                                                    @RequestParam(required = false) Long departmentId,
                                                    @RequestParam(required = false) Long userId,
                                                    @RequestParam(required = false) Integer page,
                                                    @RequestParam(required = false) Integer size) {
        return ApiResponse.success(service.getPage(keyword, status, productId, departmentId, userId, page, size));
    }

    @GetMapping("/options")
    public ApiResponse<List<ProductView>> getOptions(@RequestParam(required = false) String keyword,
                                                     @RequestParam(defaultValue = "true") boolean activeOnly) {
        return ApiResponse.success(service.getProductOptions(keyword, activeOnly));
    }

    @GetMapping("/options/departments")
    public ApiResponse<List<DepartmentBriefView>> departmentOptions(@RequestParam(defaultValue = "true") boolean activeOnly) {
        return ApiResponse.success(service.getDepartmentOptions(activeOnly));
    }

    @GetMapping("/options/users")
    public ApiResponse<PageResult<UserOptionView>> userOptions(@RequestParam(required = false) String keyword,
                                                              @RequestParam(required = false) Long departmentId,
                                                              @RequestParam(defaultValue = "true") boolean activeOnly,
                                                              @RequestParam(required = false) Integer page,
                                                              @RequestParam(required = false) Integer size) {
        return ApiResponse.success(service.getUserOptions(keyword, departmentId, activeOnly, page, size));
    }

    @GetMapping("/{id}") public ApiResponse<DeviceView> get(@PathVariable Long id) { return ApiResponse.success(service.getById(id)); }
    @GetMapping("/{id}/history") public ApiResponse<List<DeviceHistoryView>> history(@PathVariable Long id) { return ApiResponse.success(service.getHistory(id)); }
    @PostMapping @RequirePermission("system.device.create") public ApiResponse<DeviceView> create(@Valid @RequestBody DeviceCreateRequest request) { return ApiResponse.success(service.create(request)); }
    @PutMapping("/{id}") @RequirePermission("system.device.update") public ApiResponse<DeviceView> update(@PathVariable Long id, @Valid @RequestBody DeviceUpdateRequest request) { return ApiResponse.success(service.update(id, request)); }
    @PatchMapping("/{id}/status") @RequirePermission("system.device.status") public ApiResponse<DeviceView> status(@PathVariable Long id, @Valid @RequestBody DeviceStatusRequest request) { return ApiResponse.success(service.updateStatus(id, request)); }
}
