package com.haucheng.erp.dto;
import jakarta.validation.constraints.*; import lombok.Data; import java.util.List;
@Data public class UserUpdateRequest { @NotBlank(message="姓名不能为空") @Size(max=100) private String realName; @Size(max=64) private String employeeNo; @Size(max=32) private String phone; @Email(message="邮箱格式不正确") @Size(max=128) private String email; private Long departmentId; @NotEmpty(message="至少选择一个角色") private List<@NotNull Long> roleIds; private Boolean status; }
