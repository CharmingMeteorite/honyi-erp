package com.haucheng.erp.mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
@Mapper
public interface OperationLogMapper {
    @Insert("INSERT INTO sys_operation_log(operator_user_id,operation_type,operation_target,detail,request_id) VALUES(#{userId},#{type},#{target},#{detail},#{requestId})")
    int insert(@Param("userId") Long userId, @Param("type") String type, @Param("target") String target, @Param("detail") String detail, @Param("requestId") String requestId);
}
