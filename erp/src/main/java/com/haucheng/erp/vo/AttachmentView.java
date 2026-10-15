package com.haucheng.erp.vo;

import java.time.LocalDateTime;

public record AttachmentView(Long id, String businessType, Long businessId, String displayName,
                             Long sizeBytes, String contentType, String sha256, Long uploaderUserId,
                             String status, LocalDateTime createdAt) { }
