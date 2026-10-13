package com.haucheng.erp.controller;

import com.haucheng.erp.auth.RequirePermission;
import com.haucheng.erp.common.ApiResponse;
import com.haucheng.erp.common.PageResult;
import com.haucheng.erp.dto.OpeningBalanceRequest;
import com.haucheng.erp.service.OpeningBalanceService;
import com.haucheng.erp.service.StockService;
import com.haucheng.erp.service.InventoryOptionService;
import com.haucheng.erp.service.WarehouseService;
import com.haucheng.erp.vo.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class StockController {
    private final StockService stocks;
    private final OpeningBalanceService openings;
    private final InventoryOptionService options;
    private final WarehouseService warehouses;

    @GetMapping("/options/warehouses") @RequirePermission({"inventory.stock.view", "inventory.stock.opening", "inventory.stock.ledger"})
    public ApiResponse<PageResult<WarehouseView>> warehouseOptions(@RequestParam(required = false) String keyword, @RequestParam(required = false) Boolean status, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) { return ApiResponse.success(warehouses.getPage(keyword, status, page, size)); }
    @GetMapping("/options/products") @RequirePermission("inventory.stock.opening")
    public ApiResponse<PageResult<ProductView>> productOptions(@RequestParam(required = false) String keyword, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) { return ApiResponse.success(options.getProducts(keyword, page, size)); }
    @GetMapping("/options/devices") @RequirePermission("inventory.stock.opening")
    public ApiResponse<PageResult<OpeningDeviceOptionView>> deviceOptions(@RequestParam Long productId, @RequestParam(required = false) String keyword, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) { return ApiResponse.success(options.getDevices(productId, keyword, page, size)); }

    @GetMapping("/stocks") @RequirePermission("inventory.stock.view")
    public ApiResponse<PageResult<StockView>> stockPage(@RequestParam(required = false) Long warehouseId, @RequestParam(required = false) Long productId, @RequestParam(required = false) String keyword, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) { return ApiResponse.success(stocks.getPage(warehouseId, productId, keyword, page, size)); }
    @GetMapping("/stocks/consistency") @RequirePermission("inventory.stock.view")
    public ApiResponse<List<StockConsistencyIssueView>> consistency(@RequestParam(required = false) Long warehouseId) { return ApiResponse.success(stocks.checkConsistency(warehouseId)); }
    @GetMapping("/ledgers") @RequirePermission("inventory.stock.ledger")
    public ApiResponse<PageResult<StockLedgerView>> ledgerPage(@RequestParam(required = false) Long warehouseId, @RequestParam(required = false) Long productId, @RequestParam(required = false) String businessType, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) { return ApiResponse.success(stocks.getLedgerPage(warehouseId, productId, businessType, page, size)); }
    @GetMapping("/openings") @RequirePermission("inventory.stock.opening")
    public ApiResponse<PageResult<OpeningBalanceView>> openingList(@RequestParam(required = false) Long warehouseId, @RequestParam(required = false) String status, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) { return ApiResponse.success(openings.getPage(warehouseId, status, page, size)); }
    @GetMapping("/openings/{id}") @RequirePermission("inventory.stock.opening")
    public ApiResponse<OpeningBalanceView> opening(@PathVariable Long id) { return ApiResponse.success(openings.getById(id)); }
    @PostMapping("/openings") @RequirePermission("inventory.stock.opening")
    public ApiResponse<OpeningBalanceView> createOpening(@Valid @RequestBody OpeningBalanceRequest request) { return ApiResponse.success(openings.create(request)); }
    @PutMapping("/openings/{id}") @RequirePermission("inventory.stock.opening")
    public ApiResponse<OpeningBalanceView> updateOpening(@PathVariable Long id, @Valid @RequestBody OpeningBalanceRequest request) { return ApiResponse.success(openings.update(id, request)); }
    @PostMapping("/openings/{id}/complete") @RequirePermission("inventory.stock.opening")
    public ApiResponse<OpeningBalanceView> completeOpening(@PathVariable Long id) { return ApiResponse.success(openings.complete(id)); }
}
