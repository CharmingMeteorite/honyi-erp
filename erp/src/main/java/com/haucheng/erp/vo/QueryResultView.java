package com.haucheng.erp.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class QueryResultView {
    private String type;
    private Long id;
    private String title;
    private String subtitle;
    private String status;
    private LocalDateTime createdAt;
    private String route;
}
