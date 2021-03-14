package com.virjar.echo.server.common.auth;

import com.google.common.collect.Maps;
import com.virjar.echo.server.common.Md5Util;
import lombok.Getter;

import java.util.Map;

public class AuthenticateConfigBuilder {

    /**
     * IP匹配前缀树
     */
    @Getter
    private IpTrie ipTrie = new IpTrie();

    /**
     * hash(账户+密码),bindAccount
     */
    @Getter
    private Map<String, AuthenticateAccountInfo> usernameAccountMap = Maps.newHashMap();

    void clear() {
        ipTrie = new IpTrie();
        usernameAccountMap = Maps.newHashMap();
    }

    /**
     * 增加一个ip出口白名单配置
     *
     * @param ipConfig    ip出口白名单配置，支持cidr写法
     * @param bindAccount 本ip配置属于的账户(echo管理端账户，非授权账户)
     */
    public AuthenticateConfigBuilder addCidrIpConfig(String ipConfig, AuthenticateAccountInfo bindAccount) {
        ipTrie.insert(ipConfig, bindAccount);
        return this;
    }

    public AuthenticateConfigBuilder addUserNameConfig(String authUserName, String authPassword, AuthenticateAccountInfo bindAccount) {
        usernameAccountMap.put(Md5Util.md5Str(authUserName + "+" + authPassword), bindAccount);
        return this;
    }

}
