package com.haucheng.erp.vo;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class DashboardSummaryView {
    private Map<String, Long> metrics = new LinkedHashMap<>();
    private Map<String, Long> deviceStatuses = new LinkedHashMap<>();
    private BigDecimal stockQuantity;
    private List<QueryResultView> recent;
}
