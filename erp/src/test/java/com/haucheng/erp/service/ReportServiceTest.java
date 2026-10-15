package com.haucheng.erp.service;

import com.haucheng.erp.auth.CurrentUserContext;
import com.haucheng.erp.dto.ReportRequest;
import com.haucheng.erp.exception.BusinessException;
import com.haucheng.erp.mapper.ReportFilter;
import com.haucheng.erp.mapper.ReportMapper;
import com.haucheng.erp.vo.CurrentUserView;
import com.haucheng.erp.vo.ReportSummaryView;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReportServiceTest {
    @AfterEach void clear() { CurrentUserContext.clear(); }

    @Test void exportRefusesRowsBeyondLimitBeforeWritingWorkbook() {
        var mapper = mock(ReportMapper.class);
        var excel = mock(ReportExcelService.class);
        var scope = mock(InventoryScopeService.class);
        var summary = new ReportSummaryView(); summary.setRowCount(50_001L);
        when(mapper.summary(any(ReportFilter.class))).thenReturn(summary);
        var current = new CurrentUserView(); current.setDataScope("ALL");
        current.setPermissions(List.of("inventory.stock.view")); CurrentUserContext.set(current);
        assertThrows(BusinessException.class, () -> new ReportService(mapper, excel, scope).export("STOCK", new ReportRequest()));
        verifyNoInteractions(excel);
    }

    @Test void databaseFailurePropagatesAndDoesNotCreateFile() {
        var mapper = mock(ReportMapper.class);
        var excel = mock(ReportExcelService.class);
        var scope = mock(InventoryScopeService.class);
        when(mapper.summary(any(ReportFilter.class))).thenThrow(new DataAccessResourceFailureException("test-only database error"));
        var current = new CurrentUserView(); current.setDataScope("ALL");
        current.setPermissions(List.of("inventory.stock.view")); CurrentUserContext.set(current);
        assertThrows(DataAccessResourceFailureException.class,
                () -> new ReportService(mapper, excel, scope).export("STOCK", new ReportRequest()));
        verifyNoInteractions(excel);
    }
}
