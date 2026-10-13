package com.haucheng.erp.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ProductView {
    private Long id;
    private String productCode;
    private String productName;
    private Long categoryId;
    private String categoryName;
    private Long brandId;
    private String brandName;
    private String model;
    private String specification;
    private Long unitId;
    private String unitName;
    private String productType;
    private Boolean trackSn;
    private Boolean trackMac;
    private Boolean isFixedAsset;
    private Boolean enableQrcode;
    private BigDecimal safetyStock;
    private BigDecimal minStock;
    private BigDecimal maxStock;
    private String remark;
    private Boolean status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
