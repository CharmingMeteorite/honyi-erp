package com.haucheng.erp.vo;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class CategoryView {
    private Long id;
    private Long parentId;
    private String categoryCode;
    private String categoryName;
    private Integer sortOrder;
    private Boolean status;
    private List<CategoryView> children = new ArrayList<>();
}
