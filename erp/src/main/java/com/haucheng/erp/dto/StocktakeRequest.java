package com.haucheng.erp.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;

@Data
public class StocktakeRequest {
    @NotBlank @Pattern(regexp="[A-Za-z0-9._-]{1,64}")
    private String clientRequestId;
    @NotNull private Long warehouseId;
    @Min(0) private Long version;
    @Size(max=500) private String remark;
    @NotEmpty @Size(max=100) private List<@NotNull Long> productIds;
}
