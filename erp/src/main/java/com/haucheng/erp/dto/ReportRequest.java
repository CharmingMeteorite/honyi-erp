package com.haucheng.erp.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.Data;

@Data
public class ReportRequest {
    @Size(max = 100, message = "关键词不能超过 100 字符")
    private String keyword;
    @Pattern(regexp = "[A-Z_]+", message = "状态格式不正确")
    private String status;
    private String productType;
    private String serviceKind;
    private String warrantyStatus;
    private Long warehouseId;
    private Long productId;
    private Long categoryId;
    private Long brandId;
    private Long departmentId;
    private Long userId;
    private Boolean lowStock;
    private LocalDate from;
    private LocalDate to;
    @Min(value = 1, message = "页码必须大于零")
    @Max(value = 1000, message = "页码不能超过 1000")
    private Integer page;
    @Min(value = 1, message = "每页数量必须大于零")
    @Max(value = 100, message = "每页数量不能超过 100")
    private Integer size;
}
