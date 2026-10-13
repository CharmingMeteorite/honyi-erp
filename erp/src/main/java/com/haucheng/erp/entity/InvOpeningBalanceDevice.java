package com.haucheng.erp.entity;

import lombok.Data;

@Data
public class InvOpeningBalanceDevice {
    private Long id;
    private Long openingId;
    private Long itemId;
    private Long deviceId;
    private Long initializedDeviceId;
}
