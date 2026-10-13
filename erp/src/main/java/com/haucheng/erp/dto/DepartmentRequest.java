package com.haucheng.erp.dto;
import jakarta.validation.constraints.*; import lombok.Data;
@Data public class DepartmentRequest { private Long parentId; @NotBlank @Size(max=64) private String departmentCode; @NotBlank @Size(max=100) private String departmentName; private Long leaderId; private Integer sortOrder = 0; private Boolean status = true; }
