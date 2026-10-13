package com.haucheng.erp.vo;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;
@Data
public class CurrentUserView {
    private Long userId;
    private String username;
    private String realName;
    private String employeeNo;
    private Boolean status;
    private DepartmentBriefView department;
    private List<String> roles = new ArrayList<>();
    private List<String> permissions = new ArrayList<>();
    private String dataScope = "SELF";
}
