package com.haucheng.erp.controller;

import com.haucheng.erp.auth.RequirePermission;
import com.haucheng.erp.common.*;
import com.haucheng.erp.dto.*;
import com.haucheng.erp.service.*;
import com.haucheng.erp.vo.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/inventory/inbounds")
@RequiredArgsConstructor
@RequirePermission("inventory.inbound.view")
public class InboundOrderController {
    private final InventoryOrderService orders;
    private final WarehouseService warehouses;
    private final InventoryOptionService options;

    @GetMapping
    public ApiResponse<PageResult<InventoryOrderView>> getPage(
            @RequestParam(required=false) Long warehouseId, @RequestParam(required=false) String status,
            @RequestParam(required=false) String keyword, @RequestParam(required=false) LocalDate fromDate,
            @RequestParam(required=false) LocalDate toDate, @RequestParam(required=false) Integer page,
            @RequestParam(required=false) Integer size) {
        return ApiResponse.success(orders.getPage(true, warehouseId, status, keyword, fromDate, toDate, page, size));
    }
    @GetMapping("/{id}")
    public ApiResponse<InventoryOrderView> getById(@PathVariable Long id) { return ApiResponse.success(orders.getById(true, id)); }

    @PostMapping @RequirePermission("inventory.inbound.create")
    public ApiResponse<InventoryOrderView> create(@Valid @RequestBody InventoryOrderRequest request) { return ApiResponse.success(orders.create(true, request)); }
    @PutMapping("/{id}") @RequirePermission("inventory.inbound.update")
    public ApiResponse<InventoryOrderView> update(@PathVariable Long id, @Valid @RequestBody InventoryOrderRequest request) { return ApiResponse.success(orders.update(true, id, request)); }
    @PostMapping("/{id}/submit") @RequirePermission("inventory.inbound.submit")
    public ApiResponse<InventoryOrderView> submit(@PathVariable Long id, @Valid @RequestBody InventoryOrderActionRequest request) { return ApiResponse.success(orders.submit(true, id, request)); }
    @PostMapping("/{id}/approve") @RequirePermission("inventory.inbound.approve")
    public ApiResponse<InventoryOrderView> approve(@PathVariable Long id, @Valid @RequestBody InventoryOrderActionRequest request) { return ApiResponse.success(orders.approve(true, id, request, true)); }
    @PostMapping("/{id}/reject") @RequirePermission("inventory.inbound.approve")
    public ApiResponse<InventoryOrderView> reject(@PathVariable Long id, @Valid @RequestBody InventoryOrderActionRequest request) { return ApiResponse.success(orders.approve(true, id, request, false)); }
    @PostMapping("/{id}/execute") @RequirePermission("inventory.inbound.execute")
    public ApiResponse<InventoryOrderView> execute(@PathVariable Long id, @Valid @RequestBody InventoryOrderActionRequest request) { return ApiResponse.success(orders.execute(true, id, request)); }
    @PostMapping("/{id}/cancel") @RequirePermission("inventory.inbound.update")
    public ApiResponse<InventoryOrderView> cancel(@PathVariable Long id, @Valid @RequestBody InventoryOrderActionRequest request) { return ApiResponse.success(orders.cancel(true, id, request)); }

    @GetMapping("/options/warehouses") @RequirePermission({"inventory.inbound.view","inventory.inbound.create","inventory.inbound.update"})
    public ApiResponse<PageResult<WarehouseView>> warehouses(@RequestParam(required=false) String keyword, @RequestParam(required=false) Integer page, @RequestParam(required=false) Integer size) { return ApiResponse.success(warehouses.getPage(keyword, null, page, size)); }
    @GetMapping("/options/products") @RequirePermission({"inventory.inbound.create","inventory.inbound.update"})
    public ApiResponse<PageResult<ProductView>> products(@RequestParam(required=false) String keyword, @RequestParam(required=false) Integer page, @RequestParam(required=false) Integer size) { return ApiResponse.success(options.getProducts(keyword, page, size)); }
    @GetMapping("/options/devices") @RequirePermission({"inventory.inbound.create","inventory.inbound.update"})
    public ApiResponse<PageResult<InventoryOrderDeviceView>> devices(@RequestParam Long warehouseId, @RequestParam Long productId, @RequestParam(required=false) String keyword, @RequestParam(required=false) Integer page, @RequestParam(required=false) Integer size) { return ApiResponse.success(orders.getDeviceOptions(true, warehouseId, productId, keyword, page, size)); }
}
