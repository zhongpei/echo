package com.virjar.echo.meta.server.utils;

import com.google.common.base.Splitter;

public class CommonUtils {

    private static final String clientAndAccountSplitterStr = "|@--@--@|";//"@@@---@@@";
    private static final Splitter clientAndAccountSplitter = Splitter.on(clientAndAccountSplitterStr).omitEmptyStrings().trimResults();
    private static final String DEFAULT_ACCOUNT = "echo-default-none";

    public static String extractAccountFromClientId(String clientId) {
        if (clientId.contains(clientAndAccountSplitterStr)) {
            return clientAndAccountSplitter.split(clientId).iterator().next();
        }
        return DEFAULT_ACCOUNT;
    }
}
