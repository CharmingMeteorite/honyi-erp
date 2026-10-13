package com.haucheng.erp.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class UnitRequest {
    @NotBlank(message = "单位编号不能为空") @Size(max = 64, message = "单位编号不能超过64个字符") private String unitCode;
    @NotBlank(message = "单位名称不能为空") @Size(max = 100, message = "单位名称不能超过100个字符") private String unitName;
    @NotNull(message = "精度不能为空") @Min(value = 0, message = "精度不能小于0") @Max(value = 8, message = "精度不能大于8") private Integer precisionScale = 0;
    private Boolean status = true;
}
