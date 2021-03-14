package com.virjar.echo.server.common.auth;

import com.google.common.util.concurrent.RateLimiter;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 鉴权账号配置对象
 */

public class AuthenticateAccountInfo implements Comparable<AuthenticateAccountInfo> {

    /**
     * 隶属账户，在echo后台注册的账号
     */
    @Getter
    @Setter
    private String account;

    @Getter
    @Setter
    private boolean shared;

    /**
     * 本账号是否是管理员
     */
    @Getter
    @Setter
    private boolean isAdmin;

    /**
     * 限流，最大并发,可以为小数
     */
    @Getter
    @Setter
    private double maxQps;

    /**
     * 鉴权账号；登录后，在echo管理后台设置的账号，可以配置到代码中
     */
    @Getter
    @Setter
    private String authAccount;
    /**
     * 鉴权密码；登录后，在echo管理后台设置的密码，可以配置到代码中
     */
    @Getter
    @Setter
    private String authPwd;


    /**
     * 出口ip白名单，支持CIDR记法
     */
    @Getter
    @Setter
    private List<String> outWhiteIp;

    private RateLimiter rateLimiter = null;

    private RateLimiter createOrGetRateLimiter() {
        if (rateLimiter == null) {
            synchronized (this) {
                if (rateLimiter == null) {
                    if (maxQps < 0.1) {
                        maxQps = 0.1;
                    }
                    rateLimiter = RateLimiter.create(maxQps);
                }
            }
        }
        return rateLimiter;
    }

    public double nowRate() {
        return createOrGetRateLimiter().getRate();
    }


    public boolean rateLimited() {
        return !createOrGetRateLimiter().tryAcquire();
    }

    @Override
    public int compareTo(AuthenticateAccountInfo o) {
        return account.compareTo(o.account);
    }
}
