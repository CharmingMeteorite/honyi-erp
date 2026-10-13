package com.haucheng.erp.controller;

import com.haucheng.erp.auth.RequirePermission;
import com.haucheng.erp.common.ApiResponse;
import com.haucheng.erp.common.PageResult;
import com.haucheng.erp.dto.WarehouseRequest;
import com.haucheng.erp.service.WarehouseService;
import com.haucheng.erp.service.AssetDeviceService;
import com.haucheng.erp.vo.DepartmentBriefView;
import java.util.List;
import com.haucheng.erp.vo.WarehouseView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/system/warehouses")
@RequiredArgsConstructor
@RequirePermission("system.warehouse.view")
public class WarehouseController {
    private final WarehouseService service;
    private final AssetDeviceService devices;
    @GetMapping("/options/departments") @RequirePermission({"system.warehouse.create","system.warehouse.update"})
    public ApiResponse<List<DepartmentBriefView>> departments() { return ApiResponse.success(devices.getDepartmentOptions(true)); }
    @GetMapping public ApiResponse<PageResult<WarehouseView>> getPage(@RequestParam(required = false) String keyword, @RequestParam(required = false) Boolean status, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) { return ApiResponse.success(service.getPage(keyword, status, page, size)); }
    @GetMapping("/{id}") public ApiResponse<WarehouseView> get(@PathVariable Long id) { return ApiResponse.success(service.getById(id)); }
    @PostMapping @RequirePermission("system.warehouse.create") public ApiResponse<WarehouseView> create(@Valid @RequestBody WarehouseRequest request) { return ApiResponse.success(service.create(request)); }
    @PutMapping("/{id}") @RequirePermission("system.warehouse.update") public ApiResponse<WarehouseView> update(@PathVariable Long id, @Valid @RequestBody WarehouseRequest request) { return ApiResponse.success(service.update(id, request)); }
    @PostMapping("/{id}/enable") @RequirePermission("system.warehouse.disable") public ApiResponse<Void> enable(@PathVariable Long id) { service.updateStatus(id, true); return ApiResponse.success(); }
    @PostMapping("/{id}/disable") @RequirePermission("system.warehouse.disable") public ApiResponse<Void> disable(@PathVariable Long id) { service.updateStatus(id, false); return ApiResponse.success(); }
}
