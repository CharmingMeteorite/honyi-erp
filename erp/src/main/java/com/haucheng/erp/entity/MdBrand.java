package com.haucheng.erp.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class MdBrand {
    private Long id;
    private String brandCode;
    private String brandName;
    private String remark;
    private Boolean status = true;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
