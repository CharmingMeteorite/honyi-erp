package com.haucheng.erp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class QrScanRequest {
    @NotBlank(message = "请输入二维码内容")
    @Size(max = 256, message = "二维码内容过长")
    private String content;
}
