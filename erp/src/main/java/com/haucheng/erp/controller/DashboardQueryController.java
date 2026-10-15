package com.haucheng.erp.controller;

import com.haucheng.erp.auth.RequirePermission;
import com.haucheng.erp.common.ApiResponse;
import com.haucheng.erp.common.PageResult;
import com.haucheng.erp.service.DashboardQueryService;
import com.haucheng.erp.vo.DashboardSummaryView;
import com.haucheng.erp.vo.QueryResultView;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequirePermission
public class DashboardQueryController {
    private final DashboardQueryService service;

    @GetMapping("/api/dashboard/summary")
    public ApiResponse<DashboardSummaryView> summary() {
        return ApiResponse.success(service.summary());
    }

    @GetMapping("/api/query")
    public ApiResponse<PageResult<QueryResultView>> query(@RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return ApiResponse.success(service.query(type, keyword, status, from, to, page, size));
    }
}
