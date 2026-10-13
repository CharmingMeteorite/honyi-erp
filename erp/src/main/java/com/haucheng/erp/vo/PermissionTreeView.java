package com.haucheng.erp.vo;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;
@Data public class PermissionTreeView { private Long id; private Long parentId; private String permissionCode; private String permissionName; private String permissionType; private String routePath; private Integer sortOrder; private Boolean status; private List<PermissionTreeView> children = new ArrayList<>(); }
