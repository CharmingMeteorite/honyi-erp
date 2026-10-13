package com.haucheng.erp.vo;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;
@Data public class DepartmentView { private Long id; private Long parentId; private String departmentCode; private String departmentName; private Long leaderId; private String leaderName; private Integer sortOrder; private Boolean status; private Long memberCount; private List<DepartmentView> children = new ArrayList<>(); }
