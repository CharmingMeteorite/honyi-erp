package com.haucheng.erp.controller;

import com.haucheng.erp.auth.RequirePermission;
import com.haucheng.erp.common.*;
import com.haucheng.erp.dto.*;
import com.haucheng.erp.service.AssetServiceOrderService;
import com.haucheng.erp.vo.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/assets/warranties")
@RequiredArgsConstructor
@RequirePermission("asset.warranty.view")
public class WarrantyController {
    private final AssetServiceOrderService service;
    @GetMapping
    public ApiResponse<PageResult<AssetServiceOrderView>> getPage(@RequestParam(required=false) String keyword,
            @RequestParam(required=false) String status,@RequestParam(required=false) Long deviceId,
            @RequestParam(required=false) Long productId,@RequestParam(required=false) Long applicantId,
            @RequestParam(required=false) LocalDate fromDate,@RequestParam(required=false) LocalDate toDate,
            @RequestParam(required=false) Integer page,@RequestParam(required=false) Integer size) {
        return ApiResponse.success(service.getPage("warranty",keyword,status,deviceId,productId,applicantId,fromDate,toDate,page,size));
    }
    @GetMapping("/{id}")
    public ApiResponse<AssetServiceOrderView> getById(@PathVariable Long id) { return ApiResponse.success(service.getById("warranty",id)); }
    @GetMapping("/options/devices")
    public ApiResponse<PageResult<DeviceView>> getOptions(@RequestParam(defaultValue="false") boolean replacement,
            @RequestParam(required=false) Long productId,@RequestParam(required=false) String keyword,
            @RequestParam(required=false) Integer page,@RequestParam(required=false) Integer size) {
        return ApiResponse.success(service.getDeviceOptions("warranty",replacement,productId,keyword,page,size));
    }
    @PostMapping @RequirePermission("asset.warranty.create")
    public ApiResponse<AssetServiceOrderView> create(@Valid @RequestBody AssetServiceOrderRequest request) { return ApiResponse.success(service.create("warranty",request)); }
    @PutMapping("/{id}") @RequirePermission("asset.warranty.update")
    public ApiResponse<AssetServiceOrderView> update(@PathVariable Long id,@Valid @RequestBody AssetServiceOrderRequest request) { return ApiResponse.success(service.update("warranty",id,request)); }
    @PostMapping("/{id}/submit") @RequirePermission("asset.warranty.submit")
    public ApiResponse<AssetServiceOrderView> submit(@PathVariable Long id,@Valid @RequestBody AssetServiceActionRequest request) { return ApiResponse.success(service.action("warranty",id,"submit",request)); }
    @PostMapping("/{id}/accept") @RequirePermission("asset.warranty.process")
    public ApiResponse<AssetServiceOrderView> accept(@PathVariable Long id,@Valid @RequestBody AssetServiceActionRequest request) { return ApiResponse.success(service.action("warranty",id,"accept",request)); }
    @PostMapping("/{id}/approve") @RequirePermission("asset.warranty.approve")
    public ApiResponse<AssetServiceOrderView> approve(@PathVariable Long id,@Valid @RequestBody AssetServiceActionRequest request) { return ApiResponse.success(service.action("warranty",id,"approve",request)); }
    @PostMapping("/{id}/paid-repair") @RequirePermission("asset.warranty.approve")
    public ApiResponse<AssetServiceOrderView> paidRepair(@PathVariable Long id,@Valid @RequestBody AssetServiceActionRequest request) { return ApiResponse.success(service.action("warranty",id,"paid-repair",request)); }
    @PostMapping("/{id}/reject") @RequirePermission("asset.warranty.reject")
    public ApiResponse<AssetServiceOrderView> reject(@PathVariable Long id,@Valid @RequestBody AssetServiceActionRequest request) { return ApiResponse.success(service.action("warranty",id,"reject",request)); }
    @PostMapping("/{id}/complete") @RequirePermission("asset.warranty.complete")
    public ApiResponse<AssetServiceOrderView> complete(@PathVariable Long id,@Valid @RequestBody AssetServiceActionRequest request) { return ApiResponse.success(service.action("warranty",id,"complete",request)); }
    @PostMapping("/{id}/cancel") @RequirePermission("asset.warranty.cancel")
    public ApiResponse<AssetServiceOrderView> cancel(@PathVariable Long id,@Valid @RequestBody AssetServiceActionRequest request) { return ApiResponse.success(service.action("warranty",id,"cancel",request)); }
}
