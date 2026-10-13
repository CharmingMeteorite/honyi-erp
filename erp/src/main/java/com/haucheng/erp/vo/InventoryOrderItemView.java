package com.haucheng.erp.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.ArrayList;

@Data
public class InventoryOrderItemView {
    private Long id;
    private Long orderId;
    private Long productId;
    private String productCode;
    private String productName;
    private String productType;
    private BigDecimal quantity;
    private List<InventoryOrderDeviceView> devices = new ArrayList<>();
}
