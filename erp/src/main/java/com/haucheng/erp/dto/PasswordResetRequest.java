package com.haucheng.erp.dto;
import jakarta.validation.constraints.Size; import jakarta.validation.constraints.NotBlank; import lombok.Data;
@Data public class PasswordResetRequest { @NotBlank(message="新密码不能为空") @Size(min=8,max=128,message="密码长度需为8到128位") private String newPassword; }
