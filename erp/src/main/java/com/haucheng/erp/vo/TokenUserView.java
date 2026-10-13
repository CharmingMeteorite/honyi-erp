package com.haucheng.erp.vo;
import lombok.Data;
@Data
public class TokenUserView {
    private Long userId;
    private String username;
    private String realName;
    private String employeeNo;
    private Boolean status;
    private Long departmentId;
    private String departmentCode;
    private String departmentName;
    private java.time.LocalDateTime lastLoginAt;
}
