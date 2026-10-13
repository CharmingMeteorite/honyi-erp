package com.haucheng.erp.auth;

import com.haucheng.erp.exception.BusinessException;
import com.haucheng.erp.vo.CurrentUserView;

public final class CurrentUserContext {
    private static final ThreadLocal<CurrentUserView> HOLDER = new ThreadLocal<>();
    private CurrentUserContext() { }
    public static void set(CurrentUserView user) { HOLDER.set(user); }
    public static CurrentUserView get() { return HOLDER.get(); }
    public static CurrentUserView require() { CurrentUserView user = get(); if (user == null) throw BusinessException.unauthorized("请先登录"); return user; }
    public static void clear() { HOLDER.remove(); }
}
