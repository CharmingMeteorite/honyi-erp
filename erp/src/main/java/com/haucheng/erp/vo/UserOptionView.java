package com.haucheng.erp.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserOptionView {
    private Long id;
    private String realName;
    private String username;
    private Long departmentId;
    private Boolean status;
}
