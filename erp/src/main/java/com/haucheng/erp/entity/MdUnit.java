package com.haucheng.erp.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class MdUnit {
    private Long id;
    private String unitCode;
    private String unitName;
    private Integer precisionScale = 0;
    private Boolean status = true;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
