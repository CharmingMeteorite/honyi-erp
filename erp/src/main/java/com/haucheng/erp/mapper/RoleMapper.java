package com.haucheng.erp.mapper;
import com.haucheng.erp.entity.SysPermission; import com.haucheng.erp.entity.SysRole; import com.haucheng.erp.vo.RoleBriefView; import com.haucheng.erp.vo.RoleView; import org.apache.ibatis.annotations.*; import java.util.List;
@Mapper public interface RoleMapper {
 @Select("SELECT r.*, (SELECT COUNT(*) FROM sys_user_role ur WHERE ur.role_id=r.id) user_count FROM sys_role r ORDER BY r.id") List<RoleView> selectAll();
 @Select("SELECT * FROM sys_role WHERE id=#{id}") SysRole selectById(@Param("id") Long id);
 @Select("SELECT r.*, (SELECT COUNT(*) FROM sys_user_role ur WHERE ur.role_id=r.id) user_count FROM sys_role r WHERE r.id=#{id}") RoleView selectViewById(@Param("id") Long id);
 @Select("SELECT id,role_code,role_name FROM sys_role WHERE status=1 ORDER BY id") List<RoleBriefView> selectOptions();
 @Select("SELECT permission_id FROM sys_role_permission WHERE role_id=#{roleId} ORDER BY permission_id") List<Long> selectPermissionIds(@Param("roleId") Long roleId);
 @Select("SELECT p.permission_code FROM sys_role_permission rp JOIN sys_permission p ON p.id=rp.permission_id WHERE rp.role_id=#{roleId} AND p.status=1") List<String> selectPermissionCodes(@Param("roleId") Long roleId);
 @Select("SELECT * FROM sys_permission WHERE status=1 ORDER BY parent_id,sort_order,id") List<SysPermission> selectPermissions();
 @Select("SELECT * FROM sys_permission WHERE id=#{id}") SysPermission selectPermission(@Param("id") Long id);
 @Select("SELECT COUNT(*) FROM sys_role WHERE role_code=#{code} AND (#{excludeId} IS NULL OR id!=#{excludeId})") int countByCode(@Param("code") String code,@Param("excludeId") Long excludeId);
 @Select("SELECT COUNT(*) FROM sys_role WHERE role_name=#{name} AND (#{excludeId} IS NULL OR id!=#{excludeId})") int countByName(@Param("name") String name,@Param("excludeId") Long excludeId);
 @Select("SELECT COUNT(*) FROM sys_user_role WHERE role_id=#{id}") int countUsers(@Param("id") Long id);
 @Insert("INSERT INTO sys_role(role_code,role_name,description,data_scope,status) VALUES(#{roleCode},#{roleName},#{description},#{dataScope},#{status})") @Options(useGeneratedKeys=true,keyProperty="id") int insert(SysRole role);
 @Update("UPDATE sys_role SET role_code=#{roleCode},role_name=#{roleName},description=#{description},data_scope=#{dataScope},status=#{status} WHERE id=#{id}") int update(SysRole role);
 @Update("UPDATE sys_role SET status=#{status} WHERE id=#{id}") int updateStatus(@Param("id") Long id,@Param("status") Boolean status);
 @Delete("DELETE FROM sys_role WHERE id=#{id}") int deleteById(Long id);
 @Delete("DELETE FROM sys_role_permission WHERE role_id=#{id}") int deletePermissions(@Param("id") Long id);
 @Insert("INSERT INTO sys_role_permission(role_id,permission_id) VALUES(#{roleId},#{permissionId})") int insertPermission(@Param("roleId") Long roleId,@Param("permissionId") Long permissionId);
 @Select("<script>SELECT COUNT(*) FROM sys_permission WHERE id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>") int countPermissionIds(@Param("ids") List<Long> ids);
 @Select("<script>SELECT COUNT(*) FROM sys_permission WHERE status=1 AND id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>") int countActivePermissionIds(@Param("ids") List<Long> ids);
}
