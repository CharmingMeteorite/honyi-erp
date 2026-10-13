package com.haucheng.erp.vo;

import lombok.Data;

@Data
public class OpeningDeviceOptionView {
    private Long id;
    private Long productId;
    private String deviceCode;
    private String serialNumber;
    private String assetNo;
}
