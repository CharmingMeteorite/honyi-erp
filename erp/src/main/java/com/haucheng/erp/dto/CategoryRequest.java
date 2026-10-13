package com.haucheng.erp.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class CategoryRequest {
    private Long parentId;
    @NotBlank(message = "分类编号不能为空") @Size(max = 64, message = "分类编号不能超过64个字符") private String categoryCode;
    @NotBlank(message = "分类名称不能为空") @Size(max = 100, message = "分类名称不能超过100个字符") private String categoryName;
    @Min(value = 0, message = "排序不能小于0") private Integer sortOrder = 0;
    private Boolean status = true;
}
