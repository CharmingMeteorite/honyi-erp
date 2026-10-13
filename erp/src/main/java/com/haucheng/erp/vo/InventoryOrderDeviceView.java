package com.haucheng.erp.vo;

import lombok.Data;

@Data
public class InventoryOrderDeviceView {
    private Long id;
    private Long deviceId;
    private Long orderId;
    private Long itemId;
    private Long productId;
    private String deviceCode;
    private String serialNumber;
    private String macAddress;
    private String assetNo;
}
