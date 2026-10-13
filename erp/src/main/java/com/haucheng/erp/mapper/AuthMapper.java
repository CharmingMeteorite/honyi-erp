package com.haucheng.erp.mapper;

import com.haucheng.erp.entity.SysUser;
import com.haucheng.erp.vo.TokenUserView;
import com.haucheng.erp.vo.RoleBriefView;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AuthMapper {
    @Select("SELECT * FROM sys_user WHERE username = #{username}")
    SysUser selectByUsername(@Param("username") String username);

    @Select("SELECT * FROM sys_user WHERE id = #{userId}")
    SysUser selectById(@Param("userId") Long userId);

    @Insert("""
            INSERT INTO sys_user (username, password_hash, real_name, employee_no, status)
            VALUES (#{username}, #{passwordHash}, #{realName}, #{employeeNo}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SysUser user);

    @Insert("""
            INSERT INTO sys_access_token (token_hash, user_id, expires_at)
            VALUES (#{tokenHash}, #{userId}, #{expiresAt})
            """)
    int insertToken(@Param("tokenHash") String tokenHash, @Param("userId") Long userId,
                    @Param("expiresAt") LocalDateTime expiresAt);

    @Select("""
            SELECT u.id AS user_id, u.username, u.real_name, u.employee_no, u.status,
                   u.department_id, d.department_code, d.department_name, u.last_login_at
            FROM sys_access_token t JOIN sys_user u ON u.id = t.user_id
            LEFT JOIN sys_department d ON d.id = u.department_id
            WHERE t.token_hash = #{tokenHash} AND t.expires_at > CURRENT_TIMESTAMP
            """)
    TokenUserView selectTokenUser(@Param("tokenHash") String tokenHash);

    @Delete("DELETE FROM sys_access_token WHERE token_hash = #{tokenHash}")
    int deleteToken(@Param("tokenHash") String tokenHash);

    @Delete("DELETE FROM sys_access_token WHERE expires_at <= CURRENT_TIMESTAMP")
    int deleteExpiredTokens();

    @Update("UPDATE sys_user SET last_login_at=CURRENT_TIMESTAMP WHERE id=#{userId}")
    int updateLastLogin(@Param("userId") Long userId);

    @Delete("DELETE FROM sys_access_token WHERE user_id=#{userId}")
    int deleteUserTokens(@Param("userId") Long userId);
    @Select("SELECT id FROM sys_role WHERE role_code='SYSTEM_ADMIN'") Long selectSystemAdminRoleId();
    @Insert("INSERT IGNORE INTO sys_user_role(user_id,role_id) VALUES(#{userId},#{roleId})") int insertUserRole(@Param("userId") Long userId,@Param("roleId") Long roleId);

    @Select("SELECT r.id,r.role_code,r.role_name FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id WHERE ur.user_id=#{userId} AND r.status=1 ORDER BY r.id")
    List<RoleBriefView> selectRoles(@Param("userId") Long userId);
    @Select("SELECT DISTINCT p.permission_code FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id JOIN sys_role_permission rp ON rp.role_id=r.id JOIN sys_permission p ON p.id=rp.permission_id WHERE ur.user_id=#{userId} AND r.status=1 AND p.status=1")
    List<String> selectPermissions(@Param("userId") Long userId);
    @Select("SELECT r.data_scope FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id WHERE ur.user_id=#{userId} AND r.status=1 ORDER BY FIELD(r.data_scope,'ALL','DEPARTMENT','SELF') LIMIT 1")
    String selectDataScope(@Param("userId") Long userId);
}
