package com.haucheng.erp.mapper;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class QueryFilter {
    private String type;
    private String keyword;
    private String status;
    private LocalDateTime from;
    private LocalDateTime to;
    private int size;
    private int offset;
    private boolean activityOnly;
    private boolean canProduct;
    private boolean canDevice;
    private boolean canWarehouse;
    private boolean canInbound;
    private boolean canOutbound;
    private boolean canRequisition;
    private boolean canStocktake;
    private boolean canRepair;
    private boolean canWarranty;
    private boolean canExchange;
    private List<Long> warehouseDepartments;
    private List<Long> deviceDepartments;
    private List<Long> orderDepartments;
    private Long selfId;
}
