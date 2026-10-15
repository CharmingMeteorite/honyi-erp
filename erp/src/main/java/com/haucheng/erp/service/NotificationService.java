package com.haucheng.erp.service;

import com.haucheng.erp.auth.CurrentUserContext;
import com.haucheng.erp.common.PageResult;
import com.haucheng.erp.common.Pagination;
import com.haucheng.erp.exception.BusinessException;
import com.haucheng.erp.mapper.NotificationMapper;
import com.haucheng.erp.vo.NotificationView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationMapper mapper;

    public void send(Long recipientId,String type,Long businessId,String title,String summary) {
        if (recipientId == null) return;
        String eventKey = type + ":" + businessId;
        mapper.insertActive(recipientId,eventKey,type,title,summary,type,businessId);
    }

    public PageResult<NotificationView> getPage(Integer page,Integer size) {
        Long userId = CurrentUserContext.require().getUserId();
        Pagination p = Pagination.of(page,size);
        return new PageResult<>(mapper.selectPage(userId,p.size(),p.offset()),p.page(),p.size(),mapper.count(userId));
    }

    public long unreadCount() { return mapper.countUnread(CurrentUserContext.require().getUserId()); }

    public NotificationView get(Long id) {
        NotificationView view = mapper.selectOwn(id,CurrentUserContext.require().getUserId());
        if (view == null) throw BusinessException.notFound("通知不存在");
        return view;
    }

    @Transactional
    public NotificationView markRead(Long id) {
        get(id);
        mapper.markRead(id,CurrentUserContext.require().getUserId());
        return get(id);
    }

    @Transactional
    public int markAllRead() { return mapper.markAllRead(CurrentUserContext.require().getUserId()); }
}
