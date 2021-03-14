package com.virjar.echo.meta.server.utils;


import com.virjar.echo.meta.server.entity.UserInfo;

public class AppContext {
    private static final ThreadLocal<UserInfo> userInfoThreadLocal = new ThreadLocal<>();

    public static UserInfo getUser() {
        return userInfoThreadLocal.get();
    }

    public static void setUser(UserInfo user) {
        userInfoThreadLocal.set(user);
    }

    public static void removeUser() {
        userInfoThreadLocal.remove();
    }
}
