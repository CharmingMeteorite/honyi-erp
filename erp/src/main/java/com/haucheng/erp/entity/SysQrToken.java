package com.haucheng.erp.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SysQrToken {
    private Long id;
    private String token;
    private String resourceType;
    private Long productId;
    private Long deviceId;
    private Integer formatVersion;
    private Boolean active;
    private LocalDateTime createdAt;
}
