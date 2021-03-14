package com.virjar.echo.server.common.auth;

import com.virjar.echo.server.common.NatUpstreamMeta;
import lombok.Data;

/**
 * 授权判定任务对象，主要是终端侧信息和访问时传递的凭证信息。<br>
 * 根据终端数据和鉴权配置数据进行match计算，得出是否授权凭证访问以及明确当前访问属于那个echo系统账户
 */
@Data
public class AuthenticateDeviceInfo {
    /**
     * 本次授权对应的终端id
     */
    private String clientId;

    /**
     * 本次授权对应终端的所属账户
     */
    private String clientAccount;

    /**
     * 终端是否是共享的
     */
    private boolean shared;
    /**
     * 本次访问，远端出口ip
     */
    private String remoteHost;

    /**
     * 本次访问所带有的鉴权账户
     */
    private String userName;

    /**
     * 本次访问所带有的鉴权密码
     */
    private String password;


    /**
     * 鉴权通过之后，判定访问该流量的账户
     */
    private AuthenticateAccountInfo gussAccessAccount;

    /**
     * 鉴权调用之后，鉴权器设置此flag，决定是否因为限流拒绝响应
     */
    private Boolean rateLimited = false;


    public static AuthenticateDeviceInfo create(NatUpstreamMeta natUpstreamMeta, String remoteHost, String clientId) {
        AuthenticateDeviceInfo authenticateDeviceInfo = new AuthenticateDeviceInfo();
        authenticateDeviceInfo.setRemoteHost(remoteHost);
        authenticateDeviceInfo.setClientId(clientId);
        authenticateDeviceInfo.setClientAccount(natUpstreamMeta.getClientAccount());
        authenticateDeviceInfo.setShared(natUpstreamMeta.isShared());
        return authenticateDeviceInfo;
    }

}
