package com.haucheng.erp.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class StocktakeItemView {
    private Long id;
    private Long stocktakeId;
    private Long productId;
    private String productCode;
    private String productName;
    private String productType;
    private BigDecimal snapshotQuantity;
    private BigDecimal snapshotLockedQuantity;
    private Long snapshotLedgerId;
    private BigDecimal countedQuantity;
    private BigDecimal differenceQuantity;
    private List<StocktakeDeviceCheckView> devices = new ArrayList<>();
}
