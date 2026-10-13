package com.haucheng.erp.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class OpeningBalanceRequest {
    @jakarta.validation.constraints.NotBlank(message = "请求标识不能为空")
    @jakarta.validation.constraints.Pattern(regexp = "[A-Za-z0-9._-]{1,64}", message = "请求标识格式不正确")
    private String clientRequestId;
    @NotNull(message = "仓库不能为空") private Long warehouseId;
    @Size(max = 500, message = "备注不能超过500个字符") private String remark;
    @NotEmpty(message = "期初明细不能为空") @Size(max = 100, message = "期初明细不能超过100行") @Valid private List<@NotNull OpeningBalanceItemRequest> items = new ArrayList<>();
}
