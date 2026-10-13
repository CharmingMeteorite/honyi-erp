package com.haucheng.erp.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class WhWarehouse {
    private Long id;
    private String warehouseCode;
    private String warehouseName;
    private Long departmentId;
    private String remark;
    private Boolean status = true;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
