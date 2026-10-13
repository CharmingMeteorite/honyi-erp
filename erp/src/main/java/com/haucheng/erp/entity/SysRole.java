package com.haucheng.erp.entity;
import lombok.Data;
import java.time.LocalDateTime;
@Data public class SysRole { private Long id; private String roleCode; private String roleName; private String description; private String dataScope = "SELF"; private Boolean status = true; private LocalDateTime createdAt; private LocalDateTime updatedAt; }
