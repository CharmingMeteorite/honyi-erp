package com.haucheng.erp.vo;

import lombok.Data;
import com.haucheng.erp.common.PageResult;

@Data
public class QrScanView {
    private String resourceType;
    private ProductView product;
    private DeviceView device;
    private PageResult<StockView> stocks;
}
