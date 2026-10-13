package com.haucheng.erp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WarehouseRequest {
    @NotBlank(message = "仓库编码不能为空") @Size(max = 64, message = "仓库编码不能超过64个字符")
    private String warehouseCode;
    @NotBlank(message = "仓库名称不能为空") @Size(max = 100, message = "仓库名称不能超过100个字符")
    private String warehouseName;
    @Size(max = 500, message = "备注不能超过500个字符") private String remark;
    private Long departmentId;
}
