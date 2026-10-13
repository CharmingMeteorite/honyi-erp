package com.haucheng.erp.controller;

import com.haucheng.erp.auth.RequirePermission;
import com.haucheng.erp.common.ApiResponse;
import com.haucheng.erp.common.PageResult;
import com.haucheng.erp.dto.InventoryOrderActionRequest;
import com.haucheng.erp.dto.RequisitionRequest;
import com.haucheng.erp.service.RequisitionService;
import com.haucheng.erp.vo.InventoryOrderDeviceView;
import com.haucheng.erp.vo.ProductView;
import com.haucheng.erp.vo.RequisitionView;
import com.haucheng.erp.vo.WarehouseView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/apply/requisitions")
@RequiredArgsConstructor
@RequirePermission("apply.requisition.view")
public class RequisitionController {
    private final RequisitionService requisitions;

    @GetMapping
    public ApiResponse<PageResult<RequisitionView>> getPage(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.success(requisitions.getPage(warehouseId, status, keyword, fromDate, toDate, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<RequisitionView> getById(@PathVariable Long id) {
        return ApiResponse.success(requisitions.getById(id));
    }

    @PostMapping
    @RequirePermission("apply.requisition.create")
    public ApiResponse<RequisitionView> create(@Valid @RequestBody RequisitionRequest request) {
        return ApiResponse.success(requisitions.create(request));
    }

    @PutMapping("/{id}")
    @RequirePermission("apply.requisition.update")
    public ApiResponse<RequisitionView> update(@PathVariable Long id, @Valid @RequestBody RequisitionRequest request) {
        return ApiResponse.success(requisitions.update(id, request));
    }

    @PostMapping("/{id}/submit")
    @RequirePermission("apply.requisition.submit")
    public ApiResponse<RequisitionView> submit(@PathVariable Long id, @Valid @RequestBody InventoryOrderActionRequest request) {
        return ApiResponse.success(requisitions.submit(id, request));
    }

    @PostMapping("/{id}/approve")
    @RequirePermission("apply.requisition.approve")
    public ApiResponse<RequisitionView> approve(@PathVariable Long id, @Valid @RequestBody InventoryOrderActionRequest request) {
        return ApiResponse.success(requisitions.approve(id, request, true));
    }

    @PostMapping("/{id}/reject")
    @RequirePermission("apply.requisition.approve")
    public ApiResponse<RequisitionView> reject(@PathVariable Long id, @Valid @RequestBody InventoryOrderActionRequest request) {
        return ApiResponse.success(requisitions.approve(id, request, false));
    }

    @PostMapping("/{id}/issue")
    @RequirePermission("apply.requisition.issue")
    public ApiResponse<RequisitionView> issue(@PathVariable Long id, @Valid @RequestBody InventoryOrderActionRequest request) {
        return ApiResponse.success(requisitions.issue(id, request));
    }

    @PostMapping("/{id}/cancel")
    @RequirePermission("apply.requisition.update")
    public ApiResponse<RequisitionView> cancel(@PathVariable Long id, @Valid @RequestBody InventoryOrderActionRequest request) {
        return ApiResponse.success(requisitions.cancel(id, request));
    }

    @GetMapping("/options/warehouses")
    @RequirePermission({"apply.requisition.view", "apply.requisition.create", "apply.requisition.update"})
    public ApiResponse<PageResult<WarehouseView>> warehouses(@RequestParam(required = false) String keyword,
                                                              @RequestParam(required = false) Integer page,
                                                              @RequestParam(required = false) Integer size) {
        return ApiResponse.success(requisitions.getWarehouseOptions(keyword, page, size));
    }

    @GetMapping("/options/products")
    @RequirePermission({"apply.requisition.create", "apply.requisition.update"})
    public ApiResponse<PageResult<ProductView>> products(@RequestParam(required = false) String keyword,
                                                         @RequestParam(required = false) Integer page,
                                                         @RequestParam(required = false) Integer size) {
        return ApiResponse.success(requisitions.getProducts(keyword, page, size));
    }

    @GetMapping("/options/devices")
    @RequirePermission({"apply.requisition.create", "apply.requisition.update"})
    public ApiResponse<PageResult<InventoryOrderDeviceView>> devices(@RequestParam Long warehouseId,
                                                                     @RequestParam Long productId,
                                                                     @RequestParam(required = false) String keyword,
                                                                     @RequestParam(required = false) Integer page,
                                                                     @RequestParam(required = false) Integer size) {
        return ApiResponse.success(requisitions.getDeviceOptions(warehouseId, productId, keyword, page, size));
    }
}
