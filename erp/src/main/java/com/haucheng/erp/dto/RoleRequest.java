package com.haucheng.erp.dto;
import jakarta.validation.constraints.*; import lombok.Data;
@Data public class RoleRequest { @NotBlank @Size(max=64) private String roleCode; @NotBlank @Size(max=100) private String roleName; @Size(max=255) private String description; @Pattern(regexp="ALL|DEPARTMENT|SELF", message="数据范围不正确") private String dataScope = "SELF"; private Boolean status = true; }
