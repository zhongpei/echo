package com.virjar.echo.server.common.auth;

public class NoneAuthenticator implements IAuthenticator {
    @Override
    public boolean authenticate(AuthenticateDeviceInfo authenticateDeviceInfo) {
        return true;
    }

    @Override
    public boolean authenticateWithIp(AuthenticateDeviceInfo authenticateDeviceInfo) {
        return true;
    }
}
