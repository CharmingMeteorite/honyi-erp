package com.haucheng.erp.controller;

import com.haucheng.erp.auth.RequirePermission;
import com.haucheng.erp.common.ApiResponse;
import com.haucheng.erp.dto.ReportRequest;
import com.haucheng.erp.service.ReportService;
import com.haucheng.erp.vo.ReportPageView;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@RequirePermission
public class ReportController {
    private static final Map<String, String> NAMES = Map.of("STOCK", "库存", "INBOUND", "入库", "OUTBOUND", "出库",
            "REQUISITION", "领用", "DEVICE", "设备资产", "STOCKTAKE", "盘点差异", "SERVICE", "维修保修换货");
    private final ReportService reports;

    @GetMapping("/{type}")
    public ApiResponse<ReportPageView> page(@PathVariable String type, @Valid ReportRequest request) {
        return ApiResponse.success(reports.page(type, request));
    }

    @GetMapping("/{type}/export")
    public ResponseEntity<byte[]> export(@PathVariable String type, @Valid ReportRequest request) {
        byte[] content = reports.export(type, request);
        String name = NAMES.get(type) + "报表_" + LocalDate.now() + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(content.length).body(content);
    }
}
