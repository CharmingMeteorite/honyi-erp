package com.haucheng.erp.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class OpeningBalanceItemRequest {
    @NotNull(message = "物资不能为空") private Long productId;
    @NotNull(message = "期初数量不能为空") @Positive(message = "期初数量必须大于0") @jakarta.validation.constraints.Digits(integer = 14, fraction = 4, message = "数量最多14位整数和4位小数") private BigDecimal quantity;
    @NotNull(message = "设备列表不能为空")
    @jakarta.validation.constraints.Size(max = 1000, message = "单行设备不能超过1000台")
    private List<@NotNull(message = "设备不能为空") Long> deviceIds = new ArrayList<>();
}
