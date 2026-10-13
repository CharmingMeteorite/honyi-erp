package com.haucheng.erp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DeviceStatusRequest {
    @NotBlank(message = "目标状态不能为空")
    @Pattern(regexp = "IN_STOCK|IN_USE|BORROWED|REPAIRING|PENDING_EXCHANGE|SHIPPED|SCRAPPED", message = "设备状态不正确")
    private String status;
    @NotBlank(message = "状态变更原因不能为空")
    @Size(max = 200, message = "状态变更原因不能超过200个字符")
    private String reason;
}
