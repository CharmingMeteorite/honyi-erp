package com.haucheng.erp.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class MdCategory {
    private Long id;
    private Long parentId;
    private String categoryCode;
    private String categoryName;
    private Integer sortOrder = 0;
    private Boolean status = true;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
