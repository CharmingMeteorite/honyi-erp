package com.haucheng.erp.entity;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class SysAccessToken {
    private String tokenHash;
    private Long userId;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}
