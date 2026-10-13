package com.haucheng.erp.mapper;
import com.haucheng.erp.entity.SysUser; import com.haucheng.erp.vo.*; import org.apache.ibatis.annotations.*; import java.util.List;
@Mapper public interface UserMapper {
 @Select("""
 <script>SELECT u.*,d.department_name FROM sys_user u LEFT JOIN sys_department d ON d.id=u.department_id <where><if test="keyword!=null and keyword!=''"> AND (u.username LIKE CONCAT('%',#{keyword},'%') OR u.real_name LIKE CONCAT('%',#{keyword},'%') OR u.employee_no LIKE CONCAT('%',#{keyword},'%')) </if><if test="status!=null"> AND u.status=#{status} </if><choose><when test="departmentIds!=null"> AND u.department_id IN <foreach collection='departmentIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> </when><when test="departmentId!=null"> AND u.department_id=#{departmentId} </when></choose><if test="userId!=null"> AND u.id=#{userId} </if></where> ORDER BY u.status DESC,u.id LIMIT #{limit} OFFSET #{offset}</script>
 """) List<UserView> selectPage(@Param("keyword") String keyword,@Param("status") Boolean status,@Param("departmentId") Long departmentId,@Param("departmentIds") List<Long> departmentIds,@Param("userId") Long userId,@Param("limit") int limit,@Param("offset") int offset);
 @Select("""
 <script>SELECT COUNT(*) FROM sys_user u <where><if test="keyword!=null and keyword!=''"> AND (u.username LIKE CONCAT('%',#{keyword},'%') OR u.real_name LIKE CONCAT('%',#{keyword},'%') OR u.employee_no LIKE CONCAT('%',#{keyword},'%')) </if><if test="status!=null"> AND u.status=#{status} </if><choose><when test="departmentIds!=null"> AND u.department_id IN <foreach collection='departmentIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> </when><when test="departmentId!=null"> AND u.department_id=#{departmentId} </when></choose><if test="userId!=null"> AND u.id=#{userId} </if></where></script>
 """) long count(@Param("keyword") String keyword,@Param("status") Boolean status,@Param("departmentId") Long departmentId,@Param("departmentIds") List<Long> departmentIds,@Param("userId") Long userId);
 @Select("SELECT u.*,d.department_name FROM sys_user u LEFT JOIN sys_department d ON d.id=u.department_id WHERE u.id=#{id}") UserView selectViewById(@Param("id") Long id);
 @Select("SELECT * FROM sys_user WHERE id=#{id}") SysUser selectById(@Param("id") Long id);
 @Select("SELECT r.id,r.role_code,r.role_name FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id WHERE ur.user_id=#{id} ORDER BY r.id") List<RoleBriefView> selectRoles(@Param("id") Long id);
 @Select("SELECT COUNT(*) FROM sys_user WHERE username=#{value} AND (#{excludeId} IS NULL OR id!=#{excludeId})") int countUsername(@Param("value") String value,@Param("excludeId") Long excludeId);
 @Select("SELECT COUNT(*) FROM sys_user WHERE employee_no=#{value} AND (#{excludeId} IS NULL OR id!=#{excludeId})") int countEmployeeNo(@Param("value") String value,@Param("excludeId") Long excludeId);
 @Select("SELECT COUNT(*) FROM sys_department WHERE id=#{id} AND status=1") int countDepartment(@Param("id") Long id);
 @Select("SELECT COUNT(*) FROM sys_user WHERE id=#{id} AND status=1") int countActive(@Param("id") Long id);
 @Select("SELECT COUNT(*) FROM sys_user WHERE id=#{userId} AND department_id=#{departmentId} AND status=1") int countActiveInDepartment(@Param("userId") Long userId,@Param("departmentId") Long departmentId);
 @Select("<script>WITH RECURSIVE department_tree AS (SELECT id FROM sys_department WHERE id=#{id} UNION ALL SELECT d.id FROM sys_department d JOIN department_tree t ON d.parent_id=t.id) SELECT id FROM department_tree</script>") List<Long> selectDepartmentTreeIds(@Param("id") Long id);
 @Insert("INSERT INTO sys_user(username,password_hash,real_name,employee_no,phone,email,department_id,status) VALUES(#{username},#{passwordHash},#{realName},#{employeeNo},#{phone},#{email},#{departmentId},#{status})") @Options(useGeneratedKeys=true,keyProperty="id") int insert(SysUser u);
 @Update("UPDATE sys_user SET real_name=#{realName},employee_no=#{employeeNo},phone=#{phone},email=#{email},department_id=#{departmentId},status=#{status} WHERE id=#{id}") int update(SysUser u);
 @Update("UPDATE sys_user SET status=#{status} WHERE id=#{id}") int updateStatus(@Param("id") Long id,@Param("status") Boolean status);
 @Update("UPDATE sys_user SET password_hash=#{hash} WHERE id=#{id}") int resetPassword(@Param("id") Long id,@Param("hash") String hash);
 @Delete("DELETE FROM sys_user_role WHERE user_id=#{id}") int deleteRoles(@Param("id") Long id);
 @Insert("INSERT INTO sys_user_role(user_id,role_id) VALUES(#{userId},#{roleId})") int insertRole(@Param("userId") Long userId,@Param("roleId") Long roleId);
 @Select("SELECT COUNT(*) FROM sys_role WHERE id=#{id} AND status=1") int countRole(@Param("id") Long id);
 @Select("SELECT id FROM sys_role WHERE id IN (SELECT role_id FROM sys_user_role WHERE user_id=#{userId}) AND status=1") List<Long> selectRoleIds(@Param("userId") Long userId);
 @Select("SELECT DISTINCT p.permission_code FROM sys_user_role ur JOIN sys_role_permission rp ON rp.role_id=ur.role_id JOIN sys_permission p ON p.id=rp.permission_id AND p.status=1 JOIN sys_role r ON r.id=ur.role_id AND r.status=1 WHERE ur.user_id=#{id}") List<String> selectPermissions(@Param("id") Long id);
 @Select("SELECT r.data_scope FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id WHERE ur.user_id=#{id} AND r.status=1 ORDER BY FIELD(r.data_scope,'ALL','DEPARTMENT','SELF') LIMIT 1") String selectDataScope(@Param("id") Long id);
}
