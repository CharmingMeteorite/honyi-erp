package com.haucheng.erp.mapper;

import com.haucheng.erp.dto.ReportRequest;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ReportFilter extends ReportRequest {
    private String type;
    private LocalDateTime fromTime;
    private LocalDateTime toTime;
    private List<Long> scopeDepartments;
    private Long scopeSelf;
    private int limit;
    private int offset;
}
