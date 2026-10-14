package com.haucheng.erp.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class AssetServiceActionRequest {
    @NotNull @Min(0) private Long version;
    @NotBlank @Pattern(regexp="[A-Za-z0-9._-]{1,64}") private String clientRequestId;
    @Size(max=1000) private String result;
    @Size(max=500) private String remark;
    @Size(max=1000) private String notes;
}
