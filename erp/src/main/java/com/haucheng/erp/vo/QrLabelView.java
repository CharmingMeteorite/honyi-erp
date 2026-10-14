package com.haucheng.erp.vo;

import lombok.Data;

@Data
public class QrLabelView {
    private String resourceType;
    private Long resourceId;
    private String code;
    private String name;
    private String detail;
    private String content;
    private String svg;
}
