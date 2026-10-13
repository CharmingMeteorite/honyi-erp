package com.haucheng.erp.mapper;
import com.haucheng.erp.entity.SysDepartment; import com.haucheng.erp.vo.DepartmentView; import org.apache.ibatis.annotations.*; import java.util.List;
@Mapper public interface DepartmentMapper {
 @Select("SELECT d.*, u.real_name leader_name, (SELECT COUNT(*) FROM sys_user x WHERE x.department_id=d.id) member_count FROM sys_department d LEFT JOIN sys_user u ON u.id=d.leader_id ORDER BY d.parent_id,d.sort_order,d.id") List<DepartmentView> selectAll();
 @Select("SELECT * FROM sys_department WHERE id=#{id}") SysDepartment selectById(@Param("id") Long id);
 @Select("SELECT d.*, u.real_name leader_name, (SELECT COUNT(*) FROM sys_user x WHERE x.department_id=d.id) member_count FROM sys_department d LEFT JOIN sys_user u ON u.id=d.leader_id WHERE d.id=#{id}") DepartmentView selectViewById(@Param("id") Long id);
 @Select("SELECT COUNT(*) FROM sys_department WHERE department_code=#{code} AND (#{excludeId} IS NULL OR id!=#{excludeId})") int countByCode(@Param("code") String code,@Param("excludeId") Long excludeId);
 @Select("SELECT COUNT(*) FROM sys_department WHERE department_name=#{name} AND (#{excludeId} IS NULL OR id!=#{excludeId})") int countByName(@Param("name") String name,@Param("excludeId") Long excludeId);
 @Select("SELECT COUNT(*) FROM sys_department WHERE parent_id=#{id}") int countChildren(@Param("id") Long id);
    @Select("SELECT COUNT(*) FROM sys_user WHERE department_id=#{id}") int countUsers(@Param("id") Long id);
 @Select("SELECT COUNT(*) FROM sys_user WHERE id=#{id} AND status=1") int countLeader(@Param("id") Long id);
 @Select("SELECT COUNT(*) FROM sys_user WHERE id=#{userId} AND department_id=#{departmentId} AND status=1") int countLeaderInDepartment(@Param("userId") Long userId,@Param("departmentId") Long departmentId);
 @Select("SELECT COUNT(*) FROM sys_department WHERE id=#{id} AND status=1") int countActive(@Param("id") Long id);
 @Select("WITH RECURSIVE department_tree AS (SELECT id FROM sys_department WHERE id=#{id} UNION ALL SELECT d.id FROM sys_department d JOIN department_tree t ON d.parent_id=t.id) SELECT id FROM department_tree") List<Long> selectSubtreeIds(@Param("id") Long id);
 @Insert("INSERT INTO sys_department(parent_id,department_code,department_name,leader_id,sort_order,status) VALUES(#{parentId},#{departmentCode},#{departmentName},#{leaderId},#{sortOrder},#{status})") @Options(useGeneratedKeys=true,keyProperty="id") int insert(SysDepartment d);
 @Update("UPDATE sys_department SET parent_id=#{parentId},department_code=#{departmentCode},department_name=#{departmentName},leader_id=#{leaderId},sort_order=#{sortOrder},status=#{status} WHERE id=#{id}") int update(SysDepartment d);
}
