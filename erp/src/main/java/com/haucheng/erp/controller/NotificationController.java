package com.haucheng.erp.controller;

import com.haucheng.erp.auth.RequirePermission;
import com.haucheng.erp.common.ApiResponse;
import com.haucheng.erp.common.PageResult;
import com.haucheng.erp.service.NotificationService;
import com.haucheng.erp.vo.NotificationView;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@RequirePermission("notification.view")
public class NotificationController {
    private final NotificationService service;
    @GetMapping public ApiResponse<PageResult<NotificationView>> getPage(@RequestParam(required=false) Integer page,@RequestParam(required=false) Integer size) { return ApiResponse.success(service.getPage(page,size)); }
    @GetMapping("/unread-count") public ApiResponse<Long> unreadCount() { return ApiResponse.success(service.unreadCount()); }
    @GetMapping("/{id}") public ApiResponse<NotificationView> get(@PathVariable Long id) { return ApiResponse.success(service.get(id)); }
    @PatchMapping("/{id}/read") public ApiResponse<NotificationView> markRead(@PathVariable Long id) { return ApiResponse.success(service.markRead(id)); }
    @PostMapping("/read-all") public ApiResponse<Integer> markAllRead() { return ApiResponse.success(service.markAllRead()); }
}
