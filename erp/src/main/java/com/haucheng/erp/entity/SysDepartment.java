package com.haucheng.erp.entity;
import lombok.Data;
import java.time.LocalDateTime;
@Data public class SysDepartment { private Long id; private Long parentId; private String departmentCode; private String departmentName; private Long leaderId; private Integer sortOrder = 0; private Boolean status = true; private LocalDateTime createdAt; private LocalDateTime updatedAt; }
