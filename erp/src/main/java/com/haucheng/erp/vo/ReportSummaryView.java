package com.haucheng.erp.vo;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class ReportSummaryView {
    private Long rowCount;
    private Long documentCount;
    private BigDecimal requestedQuantity;
    private BigDecimal executedQuantity;
    private BigDecimal finalDifferenceQuantity;
    private Long deviceCount;
    private Long deviceExceptions;
    private Long lowStockCount;
}
