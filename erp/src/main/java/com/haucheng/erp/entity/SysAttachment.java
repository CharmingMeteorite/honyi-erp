package com.haucheng.erp.entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class SysAttachment {
    private Long id;
    private String businessType;
    private Long businessId;
    private String displayName;
    private String objectKey;
    private Long sizeBytes;
    private String contentType;
    private String sha256;
    private Long uploaderUserId;
    private String clientRequestId;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime deletedAt;
}
