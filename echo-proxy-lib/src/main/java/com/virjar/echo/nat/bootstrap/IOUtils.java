package com.virjar.echo.nat.bootstrap;

import com.virjar.echo.nat.log.EchoLogger;

import java.io.*;

public class IOUtils {
    public static String toString(InputStream inputStream) {
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[512];
            int readSize;
            while ((readSize = inputStream.read(buffer)) > 0) {
                byteArrayOutputStream.write(buffer, 0, readSize);
            }
            byteArrayOutputStream.flush();
            return byteArrayOutputStream.toString();
        } catch (IOException e) {
            EchoLogger.getLogger().warn("io exception", e);
            return null;
        }
    }

    public static void writeFile(File file, String content) {
        try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
            fileOutputStream.write(content.getBytes());
        } catch (IOException e) {
            EchoLogger.getLogger().warn("io exception", e);
        }
    }
}
