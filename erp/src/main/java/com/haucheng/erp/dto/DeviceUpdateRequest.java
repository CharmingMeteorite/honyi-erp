package com.haucheng.erp.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Null;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class DeviceUpdateRequest {
    @Size(max = 128, message = "SN不能超过128个字符") private String serialNumber;
    @Size(max = 32, message = "MAC不能超过32个字符") private String macAddress;
    @Size(max = 64, message = "固定资产编号不能超过64个字符") private String assetNo;
    private Long departmentId;
    private Long userId;
    private Long custodianId;
    @Null(message = "仓库归属由期初库存或后续库存业务控制") private Long warehouseId;
    @Null(message = "当前阶段不支持设置库位") private Long locationId;
    private LocalDateTime purchaseAt;
    private LocalDateTime inboundAt;
    private LocalDateTime firstUseAt;
    private LocalDateTime warrantyStartAt;
    private LocalDateTime warrantyEndAt;
    @Size(max = 500, message = "备注不能超过500个字符") private String remark;
    @Size(max = 1000, message = "注意事项不能超过1000个字符") private String notes;
}
