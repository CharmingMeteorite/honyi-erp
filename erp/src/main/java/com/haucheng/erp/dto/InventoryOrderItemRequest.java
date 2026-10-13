package com.haucheng.erp.dto;

import lombok.Data;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class InventoryOrderItemRequest {
    @NotNull private Long productId;
    @NotNull @DecimalMin(value="0", inclusive=false) @Digits(integer=14, fraction=4)
    private BigDecimal quantity;
    @NotNull @Size(max=1000)
    private List<@NotNull Long> deviceIds = new ArrayList<>();
}
