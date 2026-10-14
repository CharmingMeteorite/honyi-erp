package com.haucheng.erp.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class StocktakeCountRequest {
    @NotNull @Min(0) private Long version;
    @DecimalMin("0") private BigDecimal quantity;
    @Size(max=5000) private List<@NotBlank @Size(max=64) String> deviceCodes;
}
