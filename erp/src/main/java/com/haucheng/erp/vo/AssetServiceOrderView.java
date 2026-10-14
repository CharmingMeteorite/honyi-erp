package com.haucheng.erp.vo;

import com.haucheng.erp.entity.AssetServiceOrder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper=true)
public class AssetServiceOrderView extends AssetServiceOrder {
    private Long linkedRepairId;
    private String linkedRepairNo;
    private String linkedRepairStatus;
    private List<DeviceHistoryView> deviceHistory;
    private List<DeviceHistoryView> newDeviceHistory;
    private List<InventoryOrderLogView> logs;
    private List<StockLedgerView> ledgers;
}
