package com.haucheng.erp.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class MdProduct {
    private Long id;
    private String productCode;
    private String productName;
    private Long categoryId;
    private Long brandId;
    private String model;
    private String specification;
    private Long unitId;
    private String productType;
    private Boolean trackSn = false;
    private Boolean trackMac = false;
    private Boolean isFixedAsset = false;
    private Boolean enableQrcode = false;
    private BigDecimal safetyStock = BigDecimal.ZERO;
    private BigDecimal minStock = BigDecimal.ZERO;
    private BigDecimal maxStock = BigDecimal.ZERO;
    private String remark;
    private Boolean status = true;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
