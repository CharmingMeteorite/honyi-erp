package com.haucheng.erp.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AssetServiceOrderRequest {
    @NotNull(message="请选择具体设备") private Long deviceId;
    private Long newDeviceId;
    @NotBlank(message="请填写申请/换货原因") @Size(max=500) private String reason;
    @NotBlank @Pattern(regexp="[A-Za-z0-9._-]{1,64}") private String clientRequestId;
    private Long version;
    @Size(max=200) private String faultPhenomenon;
    @Size(max=1000) private String faultDescription;
    @Size(max=500) private String remark;
    @Size(max=1000) private String notes;
    @Size(max=200) private String serviceProvider;
    @Size(max=200) private String serviceLocation;
    private LocalDateTime expectedCompleteAt;
}
