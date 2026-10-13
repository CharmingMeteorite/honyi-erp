package com.haucheng.erp.dto;

import lombok.Data;
import jakarta.validation.constraints.*;

@Data
public class InventoryOrderActionRequest {
    @NotNull(message="请刷新单据版本") @Min(0)
    private Long version;
    @Size(max=200, message="审批说明最多200字")
    private String reason;
}
