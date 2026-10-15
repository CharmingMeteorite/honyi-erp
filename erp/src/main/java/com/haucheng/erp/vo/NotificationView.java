package com.haucheng.erp.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class NotificationView {
    private Long id;
    private String notificationType;
    private String title;
    private String summary;
    private String businessType;
    private Long businessId;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;
}
