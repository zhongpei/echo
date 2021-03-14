package com.virjar.echo.meta.server.utils;


import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class Md5Util {
    private static final char[] hexChar = new char[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    public Md5Util() {
    }

    public static String md5(String input) {
        return getHashWithInputStream(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
    }

    public static String getHashWithInputStream(InputStream inputStream) {
        try {
            byte[] buffer = new byte[1000];
            MessageDigest md5 = MessageDigest.getInstance("MD5");

            int numRead;
            while ((numRead = inputStream.read(buffer)) > 0) {
                md5.update(buffer, 0, numRead);
            }

            inputStream.close();
            return toHexString(md5.digest());
        } catch (Exception var4) {
            throw new IllegalStateException(var4);
        }
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

    public static byte[] hexToByteArray(String inHex) {
        int hexlen = inHex.length();
        byte[] result;
        if (hexlen % 2 == 1) {
            //奇数
            hexlen++;
            result = new byte[(hexlen / 2)];
            inHex = "0" + inHex;
        } else {
            //偶数
            result = new byte[(hexlen / 2)];
        }
        int j = 0;
        for (int i = 0; i < hexlen; i += 2) {
            result[j] = hexToByte(inHex.substring(i, i + 2));
            j++;
        }
        return result;
    }

    public static byte hexToByte(String inHex) {
        return (byte) Integer.parseInt(inHex, 16);
    }
}
