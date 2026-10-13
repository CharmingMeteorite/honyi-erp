package com.haucheng.erp.dto;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;
@Data
public class UserRolesRequest {
    @NotEmpty(message = "至少选择一个角色")
    private List<@NotNull Long> roleIds;
}
