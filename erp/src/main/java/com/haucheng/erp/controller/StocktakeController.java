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
@RequestMapping("/api/inventory/stocktakes")
@RequiredArgsConstructor
@RequirePermission("inventory.stocktake.view")
public class StocktakeController {
    private final StocktakeService stocktakes;
    private final WarehouseService warehouses;
    private final InventoryOptionService options;

    @GetMapping
    public ApiResponse<PageResult<StocktakeView>> getPage(@RequestParam(required=false) Long warehouseId,
            @RequestParam(required=false) String status, @RequestParam(required=false) String keyword,
            @RequestParam(required=false) LocalDate fromDate, @RequestParam(required=false) LocalDate toDate,
            @RequestParam(required=false) Integer page, @RequestParam(required=false) Integer size) {
        return ApiResponse.success(stocktakes.getPage(warehouseId, status, keyword, fromDate, toDate, page, size));
    }
    @GetMapping("/{id}")
    public ApiResponse<StocktakeView> getById(@PathVariable Long id) { return ApiResponse.success(stocktakes.getById(id)); }
    @PostMapping @RequirePermission("inventory.stocktake.create")
    public ApiResponse<StocktakeView> create(@Valid @RequestBody StocktakeRequest request) { return ApiResponse.success(stocktakes.create(request)); }
    @PutMapping("/{id}") @RequirePermission("inventory.stocktake.update")
    public ApiResponse<StocktakeView> update(@PathVariable Long id, @Valid @RequestBody StocktakeRequest request) { return ApiResponse.success(stocktakes.update(id, request)); }
    @PostMapping("/{id}/submit") @RequirePermission("inventory.stocktake.submit")
    public ApiResponse<StocktakeView> submit(@PathVariable Long id, @Valid @RequestBody StocktakeActionRequest request) { return ApiResponse.success(stocktakes.submit(id, request)); }
    @PutMapping("/{id}/items/{itemId}/count") @RequirePermission("inventory.stocktake.count")
    public ApiResponse<StocktakeView> count(@PathVariable Long id, @PathVariable Long itemId,
            @Valid @RequestBody StocktakeCountRequest request) { return ApiResponse.success(stocktakes.recordCount(id, itemId, request)); }
    @PostMapping("/{id}/confirm") @RequirePermission("inventory.stocktake.confirm")
    public ApiResponse<StocktakeView> confirm(@PathVariable Long id, @Valid @RequestBody StocktakeActionRequest request) { return ApiResponse.success(stocktakes.confirm(id, request)); }
    @PostMapping("/{id}/cancel") @RequirePermission("inventory.stocktake.update")
    public ApiResponse<StocktakeView> cancel(@PathVariable Long id, @Valid @RequestBody StocktakeActionRequest request) { return ApiResponse.success(stocktakes.cancel(id, request)); }
    @GetMapping("/options/warehouses")
    public ApiResponse<PageResult<WarehouseView>> warehouses(@RequestParam(required=false) String keyword,
            @RequestParam(required=false) Integer page, @RequestParam(required=false) Integer size) {
        return ApiResponse.success(warehouses.getPage(keyword, null, page, size));
    }
    @GetMapping("/options/products")
    public ApiResponse<PageResult<ProductView>> products(@RequestParam(required=false) String keyword,
            @RequestParam(required=false) Integer page, @RequestParam(required=false) Integer size) {
        return ApiResponse.success(options.getProducts(keyword, page, size));
    }
}
