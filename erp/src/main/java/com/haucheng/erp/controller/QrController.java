package com.haucheng.erp.controller;

import com.haucheng.erp.auth.RequirePermission;
import com.haucheng.erp.common.ApiResponse;
import com.haucheng.erp.dto.QrBatchRequest;
import com.haucheng.erp.dto.QrScanRequest;
import com.haucheng.erp.service.QrService;
import com.haucheng.erp.vo.QrLabelView;
import com.haucheng.erp.vo.QrScanView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/assets/qr")
@RequiredArgsConstructor
@RequirePermission("asset.qr.view")
public class QrController {
    private final QrService service;

    @GetMapping("/{type}/{id}")
    public ApiResponse<QrLabelView> get(@PathVariable String type, @PathVariable Long id) {
        return ApiResponse.success(service.label(type, id, false));
    }

    @PostMapping("/{type}/{id}")
    @RequirePermission("asset.qr.generate")
    public ApiResponse<QrLabelView> generate(@PathVariable String type, @PathVariable Long id) {
        return ApiResponse.success(service.label(type, id, true));
    }

    @PostMapping("/{type}/labels")
    @RequirePermission("asset.qr.print")
    public ApiResponse<List<QrLabelView>> labels(@PathVariable String type, @Valid @RequestBody QrBatchRequest request) {
        return ApiResponse.success(service.batch(type, request.getIds()));
    }

    @PostMapping("/scan")
    @RequirePermission("asset.qr.scan")
    public ApiResponse<QrScanView> scan(@Valid @RequestBody QrScanRequest request) {
        return ApiResponse.success(service.scan(request.getContent()));
    }
}
