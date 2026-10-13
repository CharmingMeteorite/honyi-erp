package com.haucheng.erp.entity;
import lombok.Data;
import java.time.LocalDateTime;
@Data public class SysPermission { private Long id; private Long parentId; private String permissionCode; private String permissionName; private String permissionType; private String routePath; private Integer sortOrder; private Boolean status = true; private LocalDateTime createdAt; private LocalDateTime updatedAt; }
