package com.haucheng.erp.dto;

import lombok.Data;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

@Data
public class RequisitionRequest {
    @NotBlank @Pattern(regexp="[A-Za-z0-9._-]{1,64}", message="请求标识格式不正确")
    private String clientRequestId;
    @NotNull(message="请选择仓库")
    private Long warehouseId;
    @Min(0)
    private Long version;
    @Size(max=500, message="备注最多500字")
    private String remark;
    @Valid @NotEmpty @Size(max=100)
    private List<InventoryOrderItemRequest> items;
}
