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
@RequestMapping("/api/assets/repairs")
@RequiredArgsConstructor
@RequirePermission("asset.repair.view")
public class RepairController {
    private final AssetServiceOrderService service;
    @GetMapping
    public ApiResponse<PageResult<AssetServiceOrderView>> getPage(@RequestParam(required=false) String keyword,
            @RequestParam(required=false) String status,@RequestParam(required=false) Long deviceId,
            @RequestParam(required=false) Long productId,@RequestParam(required=false) Long applicantId,
            @RequestParam(required=false) LocalDate fromDate,@RequestParam(required=false) LocalDate toDate,
            @RequestParam(required=false) Integer page,@RequestParam(required=false) Integer size) {
        return ApiResponse.success(service.getPage("repair",keyword,status,deviceId,productId,applicantId,fromDate,toDate,page,size));
    }
    @GetMapping("/{id}")
    public ApiResponse<AssetServiceOrderView> getById(@PathVariable Long id) { return ApiResponse.success(service.getById("repair",id)); }
    @GetMapping("/options/devices")
    public ApiResponse<PageResult<DeviceView>> getOptions(@RequestParam(defaultValue="false") boolean replacement,
            @RequestParam(required=false) Long productId,@RequestParam(required=false) String keyword,
            @RequestParam(required=false) Integer page,@RequestParam(required=false) Integer size) {
        return ApiResponse.success(service.getDeviceOptions("repair",replacement,productId,keyword,page,size));
    }
    @PostMapping @RequirePermission("asset.repair.create")
    public ApiResponse<AssetServiceOrderView> create(@Valid @RequestBody AssetServiceOrderRequest request) { return ApiResponse.success(service.create("repair",request)); }
    @PutMapping("/{id}") @RequirePermission("asset.repair.update")
    public ApiResponse<AssetServiceOrderView> update(@PathVariable Long id,@Valid @RequestBody AssetServiceOrderRequest request) { return ApiResponse.success(service.update("repair",id,request)); }
    @PostMapping("/{id}/submit") @RequirePermission("asset.repair.submit")
    public ApiResponse<AssetServiceOrderView> submit(@PathVariable Long id,@Valid @RequestBody AssetServiceActionRequest request) { return ApiResponse.success(service.action("repair",id,"submit",request)); }
    @PostMapping("/{id}/accept") @RequirePermission("asset.repair.process")
    public ApiResponse<AssetServiceOrderView> accept(@PathVariable Long id,@Valid @RequestBody AssetServiceActionRequest request) { return ApiResponse.success(service.action("repair",id,"accept",request)); }
    @PostMapping("/{id}/start") @RequirePermission("asset.repair.process")
    public ApiResponse<AssetServiceOrderView> start(@PathVariable Long id,@Valid @RequestBody AssetServiceActionRequest request) { return ApiResponse.success(service.action("repair",id,"start",request)); }
    @PostMapping("/{id}/complete") @RequirePermission("asset.repair.complete")
    public ApiResponse<AssetServiceOrderView> complete(@PathVariable Long id,@Valid @RequestBody AssetServiceActionRequest request) { return ApiResponse.success(service.action("repair",id,"complete",request)); }
    @PostMapping("/{id}/unrepairable") @RequirePermission("asset.repair.complete")
    public ApiResponse<AssetServiceOrderView> unrepairable(@PathVariable Long id,@Valid @RequestBody AssetServiceActionRequest request) { return ApiResponse.success(service.action("repair",id,"unrepairable",request)); }
    @PostMapping("/{id}/close") @RequirePermission("asset.repair.complete")
    public ApiResponse<AssetServiceOrderView> close(@PathVariable Long id,@Valid @RequestBody AssetServiceActionRequest request) { return ApiResponse.success(service.action("repair",id,"close",request)); }
    @PostMapping("/{id}/cancel") @RequirePermission("asset.repair.cancel")
    public ApiResponse<AssetServiceOrderView> cancel(@PathVariable Long id,@Valid @RequestBody AssetServiceActionRequest request) { return ApiResponse.success(service.action("repair",id,"cancel",request)); }
}
