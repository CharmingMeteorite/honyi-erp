package com.haucheng.erp.entity;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class SysUser {
    private Long id;
    private String username;
    private String passwordHash;
    private String realName;
    private String employeeNo;
    private String phone;
    private String email;
    private Long departmentId;
    private Boolean status = true;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
