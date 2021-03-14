package com.virjar.echo.server.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class Md5Util {

    public static byte[] md5(String inputString) {
        try {
            byte[] buffer = inputString.getBytes(StandardCharsets.UTF_8);
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            md5.update(buffer, 0, buffer.length);
            return md5.digest();
        } catch (Exception var4) {
            throw new IllegalStateException(var4);
        }
    }

    public static String md5Str(String input) {
        return toHexString(md5(input));
    }

    public static String toHexString(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        byte[] var2 = b;
        int var3 = b.length;

        for (int var4 = 0; var4 < var3; ++var4) {
            byte b1 = var2[var4];
            sb.append(hexChar[(b1 & 240) >>> 4]);
            sb.append(hexChar[b1 & 15]);
        }

        return sb.toString();
    }

    private static final char[] hexChar = new char[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

}
