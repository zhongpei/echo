package com.virjar.echo.server.common.auth;

public interface IAuthenticator {

    /**
     * 是否鉴权通过
     *
     * @param authenticateDeviceInfo 鉴权判定需要的所有信息
     * @return 是否通过
     */
    boolean authenticate(AuthenticateDeviceInfo authenticateDeviceInfo);

    /**
     * 对于socks代理来说，ip鉴权和密码鉴权是两个步骤，所以需要提前判定
     *
     * @param authenticateDeviceInfo 鉴权判定需要的所有信息,但是没有账户和密码字段
     * @return 是否通过
     */
    boolean authenticateWithIp(AuthenticateDeviceInfo authenticateDeviceInfo);
}
