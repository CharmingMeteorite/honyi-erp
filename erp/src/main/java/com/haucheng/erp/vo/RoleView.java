package com.haucheng.erp.vo;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;
@Data public class RoleView { private Long id; private String roleCode; private String roleName; private String description; private String dataScope; private Boolean status; private Long userCount; private List<Long> permissionIds = new ArrayList<>(); }
