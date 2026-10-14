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
@RequestMapping("/api/assets/exchanges")
@RequiredArgsConstructor
@RequirePermission("asset.exchange.view")
public class ExchangeController {
    private final AssetServiceOrderService service;
    @GetMapping
    public ApiResponse<PageResult<AssetServiceOrderView>> getPage(@RequestParam(required=false) String keyword,
            @RequestParam(required=false) String status,@RequestParam(required=false) Long deviceId,
            @RequestParam(required=false) Long productId,@RequestParam(required=false) Long applicantId,
            @RequestParam(required=false) LocalDate fromDate,@RequestParam(required=false) LocalDate toDate,
            @RequestParam(required=false) Integer page,@RequestParam(required=false) Integer size) {
        return ApiResponse.success(service.getPage("exchange",keyword,status,deviceId,productId,applicantId,fromDate,toDate,page,size));
    }
    @GetMapping("/{id}")
    public ApiResponse<AssetServiceOrderView> getById(@PathVariable Long id) { return ApiResponse.success(service.getById("exchange",id)); }
    @GetMapping("/options/devices")
    public ApiResponse<PageResult<DeviceView>> getOptions(@RequestParam(defaultValue="false") boolean replacement,
            @RequestParam(required=false) Long productId,@RequestParam(required=false) String keyword,
            @RequestParam(required=false) Integer page,@RequestParam(required=false) Integer size) {
        return ApiResponse.success(service.getDeviceOptions("exchange",replacement,productId,keyword,page,size));
    }
    @PostMapping @RequirePermission("asset.exchange.create")
    public ApiResponse<AssetServiceOrderView> create(@Valid @RequestBody AssetServiceOrderRequest request) { return ApiResponse.success(service.create("exchange",request)); }
    @PutMapping("/{id}") @RequirePermission("asset.exchange.update")
    public ApiResponse<AssetServiceOrderView> update(@PathVariable Long id,@Valid @RequestBody AssetServiceOrderRequest request) { return ApiResponse.success(service.update("exchange",id,request)); }
    @PostMapping("/{id}/submit") @RequirePermission("asset.exchange.submit")
    public ApiResponse<AssetServiceOrderView> submit(@PathVariable Long id,@Valid @RequestBody AssetServiceActionRequest request) { return ApiResponse.success(service.action("exchange",id,"submit",request)); }
    @PostMapping("/{id}/approve") @RequirePermission("asset.exchange.approve")
    public ApiResponse<AssetServiceOrderView> approve(@PathVariable Long id,@Valid @RequestBody AssetServiceActionRequest request) { return ApiResponse.success(service.action("exchange",id,"approve",request)); }
    @PostMapping("/{id}/reject") @RequirePermission("asset.exchange.reject")
    public ApiResponse<AssetServiceOrderView> reject(@PathVariable Long id,@Valid @RequestBody AssetServiceActionRequest request) { return ApiResponse.success(service.action("exchange",id,"reject",request)); }
    @PostMapping("/{id}/execute") @RequirePermission("asset.exchange.execute")
    public ApiResponse<AssetServiceOrderView> execute(@PathVariable Long id,@Valid @RequestBody AssetServiceActionRequest request) { return ApiResponse.success(service.action("exchange",id,"execute",request)); }
    @PostMapping("/{id}/cancel") @RequirePermission("asset.exchange.cancel")
    public ApiResponse<AssetServiceOrderView> cancel(@PathVariable Long id,@Valid @RequestBody AssetServiceActionRequest request) { return ApiResponse.success(service.action("exchange",id,"cancel",request)); }
}
