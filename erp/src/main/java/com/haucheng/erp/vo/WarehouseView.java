package com.haucheng.erp.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class WarehouseView {
    private Long id;
    private String warehouseCode;
    private String warehouseName;
    private Long departmentId;
    private String departmentName;
    private String remark;
    private Boolean status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
