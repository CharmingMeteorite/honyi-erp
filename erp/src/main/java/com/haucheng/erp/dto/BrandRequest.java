package com.haucheng.erp.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class BrandRequest {
    @NotBlank(message = "品牌编号不能为空") @Size(max = 64, message = "品牌编号不能超过64个字符") private String brandCode;
    @NotBlank(message = "品牌名称不能为空") @Size(max = 100, message = "品牌名称不能超过100个字符") private String brandName;
    @Size(max = 500, message = "备注不能超过500个字符") private String remark;
    private Boolean status = true;
}
