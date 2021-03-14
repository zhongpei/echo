package com.virjar.echo.meta.server.utils;

import java.net.MalformedURLException;
import java.net.URL;

public class IPUtils {
    public static boolean isLocalHost(String host) {
        if ("127.0.0.1".equals(host)) {
            return true;
        }
        if ("0.0.0.0".equals(host)) {
            return true;
        }
        return "localhost".equals(host);
    }

    public static boolean isPrivateIp(String host) {
        return host.equals("localhost")
                || host.startsWith("192.168.")
                || host.startsWith("10.")
                || host.startsWith("172.");
    }

    public static String extractHostFromUrl(String url) {
        try {
            return new URL(url).getHost();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    public static String genURL(String base, String api) {
        base = base.trim();
        api = api.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (api.startsWith("/")) {
            api = api.substring(1);
        }
        return base + "/" + api;
    }
}
