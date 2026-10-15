package com.haucheng.erp.mapper;

import com.haucheng.erp.vo.NotificationView;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface NotificationMapper {
    @Insert("""
            INSERT IGNORE INTO sys_notifications(recipient_user_id,event_key,notification_type,title,summary,business_type,business_id)
            SELECT id,#{eventKey},#{type},#{title},#{summary},#{businessType},#{businessId}
            FROM sys_user WHERE id=#{recipientId} AND status=1
            """)
    int insertActive(@Param("recipientId") Long recipientId,@Param("eventKey") String eventKey,
                     @Param("type") String type,@Param("title") String title,@Param("summary") String summary,
                     @Param("businessType") String businessType,@Param("businessId") Long businessId);

    @Select("SELECT id,notification_type,title,summary,business_type,business_id,created_at,read_at FROM sys_notifications WHERE recipient_user_id=#{userId} ORDER BY created_at DESC,id DESC LIMIT #{limit} OFFSET #{offset}")
    List<NotificationView> selectPage(@Param("userId") Long userId,@Param("limit") int limit,@Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM sys_notifications WHERE recipient_user_id=#{userId}")
    long count(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM sys_notifications WHERE recipient_user_id=#{userId} AND read_at IS NULL")
    long countUnread(@Param("userId") Long userId);

    @Select("SELECT id,notification_type,title,summary,business_type,business_id,created_at,read_at FROM sys_notifications WHERE id=#{id} AND recipient_user_id=#{userId}")
    NotificationView selectOwn(@Param("id") Long id,@Param("userId") Long userId);

    @Update("UPDATE sys_notifications SET read_at=NOW() WHERE id=#{id} AND recipient_user_id=#{userId} AND read_at IS NULL")
    int markRead(@Param("id") Long id,@Param("userId") Long userId);

    @Update("UPDATE sys_notifications SET read_at=NOW() WHERE recipient_user_id=#{userId} AND read_at IS NULL")
    int markAllRead(@Param("userId") Long userId);
}
