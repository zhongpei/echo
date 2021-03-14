package com.virjar.echo.meta.server.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CommonRes<T> {
    private int status = statusOK;
    private String message;
    private T data;
    private String token;

    public static final int statusOK = 0;
    public static final int statusError = -1;
    public static final int statusNeedLogin = -2;
    public static final int statusLoginExpire = -3;

    public static <T> CommonRes<T> success(T t) {
        CommonRes<T> ret = new CommonRes<>();
        ret.status = statusOK;
        ret.message = null;
        ret.data = t;
        return ret;

    }


    public static <T> CommonRes<T> failed(String message) {
        return failed(statusError, message);
    }

    public static <T> CommonRes<T> failed(int status, String message) {
        CommonRes<T> ret = new CommonRes<>();
        ret.status = status;
        ret.message = message;
        return ret;
    }

    public boolean isOk() {
        return status == statusOK;
    }
}
