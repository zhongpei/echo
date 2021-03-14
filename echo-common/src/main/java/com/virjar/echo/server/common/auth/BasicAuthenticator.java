package com.virjar.echo.server.common.auth;

import com.google.common.collect.Maps;
import com.virjar.echo.server.common.Md5Util;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

/**
 * 基础鉴权器
 */
public class BasicAuthenticator implements IAuthenticator {

    /**
     * IP匹配前缀树
     */
    private IpTrie ipTrie;

    /**
     * hash(账户+密码),bindAccount
     */
    private Map<String, AuthenticateAccountInfo> usernameAccountMap;

    private boolean loadConfig;

    public BasicAuthenticator() {
        this.ipTrie = new IpTrie();
        this.usernameAccountMap = Maps.newHashMap();
        loadConfig = false;
    }


    public void refreshAuthenticateConfig(AuthenticateConfigBuilder authenticateConfigBuilder) {
        IpTrie tmpIpTrie = authenticateConfigBuilder.getIpTrie().copy();
        Map<String, AuthenticateAccountInfo> tmpUsernameAccountMap = Maps.newHashMap(authenticateConfigBuilder.getUsernameAccountMap());
        this.ipTrie = tmpIpTrie;
        this.usernameAccountMap = tmpUsernameAccountMap;

        /**
         * clear避免被重复添加配置
         */
        authenticateConfigBuilder.clear();
        loadConfig = true;
    }

    @Override
    public boolean authenticate(AuthenticateDeviceInfo deviceInfo) {
        if (!loadConfig) {
            //系统刚刚启动，鉴权配置还没有同步进来
            deviceInfo.setGussAccessAccount(null);
            return true;
        }

        // 首先使用账户密码鉴权
        AuthenticateAccountInfo accountInfo = authenticateFromUserName(deviceInfo);
        if (accountInfo == null) {
            //然后使用ip白名单出口鉴权
            accountInfo = authenticateFromTrie(deviceInfo);
        }

        return authenticateForAccount(deviceInfo, accountInfo);
    }

    private boolean authenticateForAccount(AuthenticateDeviceInfo deviceInfo, AuthenticateAccountInfo accountInfo) {
        if (accountInfo == null) {
            // 鉴权失败
            return false;
        }

        deviceInfo.setGussAccessAccount(accountInfo);

        if (!isAuthenticate(deviceInfo, accountInfo)) {
            return false;
        }

        if (accountInfo.rateLimited()) {
            deviceInfo.setRateLimited(true);
            return false;
        }
        return true;
    }

    @Override
    public boolean authenticateWithIp(AuthenticateDeviceInfo authenticateDeviceInfo) {
        if (!loadConfig) {
            //系统刚刚启动，鉴权配置还没有同步进来
            authenticateDeviceInfo.setGussAccessAccount(null);
            return true;
        }
        AuthenticateAccountInfo accountInfo = authenticateFromTrie(authenticateDeviceInfo);

        return authenticateForAccount(authenticateDeviceInfo, accountInfo);
    }


    private AuthenticateAccountInfo authenticateFromTrie(AuthenticateDeviceInfo authenticateDeviceInfo) {
        return ipTrie.find(authenticateDeviceInfo.getRemoteHost());
    }

    private AuthenticateAccountInfo authenticateFromUserName(AuthenticateDeviceInfo authenticateDeviceInfo) {

        if (StringUtils.isBlank(authenticateDeviceInfo.getUserName())
                || StringUtils.isBlank(authenticateDeviceInfo.getPassword())) {
            //没账户密码
            return null;
        }
        String usernameAuthenticateKey = Md5Util.md5Str(authenticateDeviceInfo.getUserName() + "+" + authenticateDeviceInfo.getPassword());
        return usernameAccountMap.get(usernameAuthenticateKey);
    }

    private boolean isAuthenticate(AuthenticateDeviceInfo deviceInfo, AuthenticateAccountInfo accountInfo) {
        if (StringUtils.isBlank(deviceInfo.getClientAccount())) {
            //设备无主，此时直接允许访问
            return true;
        }

        //设备有主，此时判断是否是echo特权账户，如果是，那么允许通过
        if (accountInfo.isAdmin()) {
            return true;
        }

        // 设备是共享设备，同时账户允许共享
        if (deviceInfo.isShared() && accountInfo.isShared()) {
            return true;
        }

        // 本设备不是共享的，那么需要check当前设备是否隶属于当前后台账户
        return accountInfo.getAccount().equals(deviceInfo.getClientAccount());
    }
}
