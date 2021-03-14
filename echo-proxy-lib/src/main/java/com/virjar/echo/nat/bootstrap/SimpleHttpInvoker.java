package com.virjar.echo.nat.bootstrap;

import com.virjar.echo.nat.log.EchoLogger;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;


public class SimpleHttpInvoker {
    public static String get(String url) {
        EchoLogger.getLogger().info("access url: " + url);
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.connect();
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                connection.disconnect();
                return null;
            }
            try (InputStream inputStream = connection.getInputStream()) {
                String ret = IOUtils.toString(inputStream);
                EchoLogger.getLogger().info("response: " + ret);
                return ret;
            } finally {
                connection.disconnect();
            }
        } catch (Exception e) {
            EchoLogger.getLogger().error("error for url:" + url, e);
            return null;
        }
    }

    private static String apiEntryPoint;
    private static String apiToken;

    public static void setupEchoParam(String apiEntryPoint, String apiToken) {
        if (!apiEntryPoint.endsWith("/")) {
            apiEntryPoint = apiEntryPoint + "/";
        }
        SimpleHttpInvoker.apiEntryPoint = apiEntryPoint;
        SimpleHttpInvoker.apiToken = apiToken;
    }

    public static String echoAccess(String api) {
        return echoAccess(api, new HashMap<>());
    }

    public static String echoAccess(String api, Map<String, String> params) {
        if (api.startsWith("/")) {
            api = api.substring(1);
        }

        if (params == null) {
            params = new HashMap<>();
        }

        params = new HashMap<>(params);
        params.put("Token", apiToken);

        StringBuilder urlBuilder = new StringBuilder(apiEntryPoint);
        urlBuilder.append(api);
        urlBuilder.append("?");
        for (String key : params.keySet()) {
            urlBuilder.append(URLEncoder.encode(key));
            urlBuilder.append("=");
            String value = params.get(key);
            if (value != null) {
                urlBuilder.append(URLEncoder.encode(value));
            }
            urlBuilder.append("&");
        }

        urlBuilder.setLength(urlBuilder.length() - 1);
        String finalUrl = urlBuilder.toString();

        return get(finalUrl);
    }

}
