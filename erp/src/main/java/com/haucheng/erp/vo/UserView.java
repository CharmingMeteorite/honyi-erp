package com.haucheng.erp.vo;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
@Data public class UserView { private Long id; private String username; private String realName; private String employeeNo; private String phone; private String email; private Long departmentId; private String departmentName; private Boolean status; private LocalDateTime lastLoginAt; private LocalDateTime createdAt; private List<RoleBriefView> roles = new ArrayList<>(); private List<String> permissions = new ArrayList<>(); private String dataScope; }
