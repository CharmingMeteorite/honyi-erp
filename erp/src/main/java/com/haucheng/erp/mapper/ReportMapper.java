package com.haucheng.erp.mapper;

import com.haucheng.erp.vo.ReportRowView;
import com.haucheng.erp.vo.ReportSummaryView;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.SelectProvider;

@Mapper
public interface ReportMapper {
    @SelectProvider(type = ReportSqlProvider.class, method = "page")
    List<ReportRowView> selectPage(ReportFilter filter);

    @SelectProvider(type = ReportSqlProvider.class, method = "summary")
    ReportSummaryView summary(ReportFilter filter);
}
