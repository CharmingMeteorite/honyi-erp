package com.haucheng.erp.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class ProductRequest {
    @NotBlank(message = "物资编号不能为空") @Size(max = 64, message = "物资编号不能超过64个字符") private String productCode;
    @NotBlank(message = "物资名称不能为空") @Size(max = 150, message = "物资名称不能超过150个字符") private String productName;
    @NotNull(message = "分类不能为空") private Long categoryId;
    @NotNull(message = "品牌不能为空") private Long brandId;
    @Size(max = 150, message = "型号不能超过150个字符") private String model;
    @Size(max = 500, message = "规格不能超过500个字符") private String specification;
    @NotNull(message = "单位不能为空") private Long unitId;
    @NotBlank(message = "物资类型不能为空") @Pattern(regexp = "QUANTITY|DEVICE", message = "物资类型不正确") private String productType;
    private Boolean trackSn = false;
    private Boolean trackMac = false;
    private Boolean isFixedAsset = false;
    private Boolean enableQrcode = false;
    @NotNull(message = "安全库存不能为空") @DecimalMin(value = "0", message = "安全库存不能小于0") private BigDecimal safetyStock = BigDecimal.ZERO;
    @NotNull(message = "最低库存不能为空") @DecimalMin(value = "0", message = "最低库存不能小于0") private BigDecimal minStock = BigDecimal.ZERO;
    @NotNull(message = "最高库存不能为空") @DecimalMin(value = "0", message = "最高库存不能小于0") private BigDecimal maxStock = BigDecimal.ZERO;
    @Size(max = 500, message = "备注不能超过500个字符") private String remark;
    private Boolean status = true;
}
