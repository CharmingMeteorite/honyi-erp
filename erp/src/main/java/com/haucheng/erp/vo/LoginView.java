package com.haucheng.erp.vo;
import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDateTime;
@Data
@AllArgsConstructor
public class LoginView {
    private String accessToken;
    private String tokenType;
    private LocalDateTime expiresAt;
    private CurrentUserView user;
}
