package com.virjar.echo.server.common;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

@Slf4j
public class SimpleHttpInvoker {

    public static String post(String url, JSONObject body) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("connection", "Keep-Alive");
            connection.setRequestProperty("Content-Type", "application/json");

            try (OutputStream os = connection.getOutputStream()) {
                os.write(body.toJSONString().getBytes(StandardCharsets.UTF_8));
                return readResponse(connection);
            }
        } catch (Exception e) {
            log.error("error for url:" + url, e);
            return null;
        }
    }

    private static String readResponse(HttpURLConnection connection) throws IOException {
        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            connection.disconnect();
            return null;
        }
        try (InputStream inputStream = connection.getInputStream()) {
            return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }

    public static String get(String url) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("connection", "Keep-Alive");
            connection.connect();
            return readResponse(connection);
        } catch (Exception e) {
            log.error("error for url:" + url, e);
            return null;
        }
    }
}
