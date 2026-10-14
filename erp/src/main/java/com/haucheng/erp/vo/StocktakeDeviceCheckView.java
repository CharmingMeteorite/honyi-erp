package com.haucheng.erp.vo;

import lombok.Data;

@Data
public class StocktakeDeviceCheckView {
    private Long id;
    private Long stocktakeId;
    private Long itemId;
    private Long deviceId;
    private String deviceCode;
    private String serialNumber;
    private String macAddress;
    private String assetNo;
    private Boolean expected;
    private Boolean observed;
    private String differenceCode;
    private Long actualProductId;
    private Long actualWarehouseId;
    private String actualStatus;
}
