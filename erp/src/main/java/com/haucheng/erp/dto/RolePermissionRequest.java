package com.haucheng.erp.dto;
import jakarta.validation.constraints.NotNull; import lombok.Data; import java.util.List;
@Data public class RolePermissionRequest { @NotNull private List<@NotNull Long> permissionIds; }
