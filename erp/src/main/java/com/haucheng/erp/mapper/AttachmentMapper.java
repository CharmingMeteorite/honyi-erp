package com.haucheng.erp.mapper;

import com.haucheng.erp.entity.SysAttachment;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AttachmentMapper {
    @Select("SELECT * FROM sys_attachments WHERE id=#{id}")
    SysAttachment selectById(@Param("id") Long id);

    @Select("SELECT * FROM sys_attachments WHERE uploader_user_id=#{userId} AND client_request_id=#{requestId}")
    SysAttachment selectByRequest(@Param("userId") Long userId, @Param("requestId") String requestId);

    @Select("SELECT * FROM sys_attachments WHERE business_type=#{type} AND business_id=#{businessId} AND status IN ('READY','DELETE_PENDING') ORDER BY created_at DESC,id DESC")
    List<SysAttachment> selectBusiness(@Param("type") String type, @Param("businessId") Long businessId);

    @Select("SELECT id FROM sys_attachments WHERE business_type=#{type} AND business_id=#{businessId} AND status='READY' FOR UPDATE")
    List<Long> selectReadyForUpdate(@Param("type") String type, @Param("businessId") Long businessId);

    @Insert("""
            INSERT INTO sys_attachments(business_type,business_id,display_name,object_key,size_bytes,content_type,sha256,uploader_user_id,client_request_id)
            VALUES(#{businessType},#{businessId},#{displayName},#{objectKey},#{sizeBytes},#{contentType},#{sha256},#{uploaderUserId},#{clientRequestId})
            """)
    @Options(useGeneratedKeys=true,keyProperty="id")
    int insert(SysAttachment attachment);

    @Update("UPDATE sys_attachments SET status='DELETE_PENDING' WHERE id=#{id} AND status='READY'")
    int markPending(@Param("id") Long id);

    @Update("UPDATE sys_attachments SET status='DELETED',deleted_at=NOW() WHERE id=#{id} AND status='DELETE_PENDING'")
    int markDeleted(@Param("id") Long id);
}
