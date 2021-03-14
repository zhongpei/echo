package com.virjar.echo.hproxy.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONPath;
import com.virjar.echo.server.common.SimpleHttpInvoker;
import com.virjar.echo.server.common.auth.AuthenticateAccountInfo;
import com.virjar.echo.server.common.auth.AuthenticateConfigBuilder;
import com.virjar.echo.server.common.auth.BasicAuthenticator;
import com.virjar.echo.server.common.auth.IAuthenticator;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class AuthConfigManager {
    @Getter
    private final IAuthenticator authenticator;

    private ScheduledExecutorService refreshScheduler;

    private final String authInfoLoadUrl;

    public AuthConfigManager(IAuthenticator authenticator, String authInfoLoadUrl) {
        this.authenticator = authenticator;
        this.authInfoLoadUrl = authInfoLoadUrl;
    }


    public synchronized void schedulerRefreshAuthConfig() {
        if (authenticator instanceof BasicAuthenticator) {
            if (refreshScheduler != null) {
                return;
            }
            refreshScheduler = Executors.newScheduledThreadPool(1);
            refreshScheduler.scheduleAtFixedRate(
                    () -> doRefreshAuthConfig((BasicAuthenticator) authenticator),
                    0, 5, TimeUnit.MINUTES
            );
        }
    }

    private void doRefreshAuthConfig(BasicAuthenticator basicAuthenticator) {
        log.info("load auth config from url:{}", authInfoLoadUrl);
        String response = SimpleHttpInvoker.get(authInfoLoadUrl);
        log.info("auth config response:{}", response);
        if (StringUtils.isBlank(response)) {
            return;
        }
        JSONObject jsonObject = JSONObject.parseObject(response);
        JSONArray array = (JSONArray) JSONPath.compile("$.data").eval(jsonObject);
        if (array == null) {
            return;
        }
        AuthenticateConfigBuilder authenticateConfigBuilder = new AuthenticateConfigBuilder();
        for (int i = 0; i < array.size(); i++) {
            JSONObject configJson = array.getJSONObject(i);
            AuthenticateAccountInfo authenticateAccountInfo = configJson.toJavaObject(AuthenticateAccountInfo.class);
            String authAccount = authenticateAccountInfo.getAuthAccount();
            String authPwd = authenticateAccountInfo.getAuthPwd();
            String account = authenticateAccountInfo.getAccount();
            if (StringUtils.isNotBlank(authAccount)
                    && StringUtils.isNotBlank(authPwd)
                    ) {
                authenticateConfigBuilder.addUserNameConfig(authAccount, authPwd, authenticateAccountInfo);
            }
            List<String> outWhiteIp = authenticateAccountInfo.getOutWhiteIp();
            if (outWhiteIp != null) {
                for (String outIp : outWhiteIp) {
                    authenticateConfigBuilder.addCidrIpConfig(outIp, authenticateAccountInfo);
                }
            }
        }

        basicAuthenticator.refreshAuthenticateConfig(authenticateConfigBuilder);
    }
}
