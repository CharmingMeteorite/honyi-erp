package com.haucheng.erp.vo;

import com.haucheng.erp.common.PageResult;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ReportPageView {
    private PageResult<ReportRowView> page;
    private ReportSummaryView summary;
}
