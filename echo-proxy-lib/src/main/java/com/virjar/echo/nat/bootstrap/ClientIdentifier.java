package com.virjar.echo.nat.bootstrap;

import android.Manifest;
import android.app.ActivityThread;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;
import android.telephony.TelephonyManager;
import com.virjar.echo.nat.log.EchoLogger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Random;
import java.util.UUID;

public class ClientIdentifier {
    private static final String clientIdFileName = "echo_client_id.txt";
    private static final String UN_RESOLVE = "un_resolve_";

    public static boolean isAndroid = isAndroidEnv();
    private static String clientIdInMemory;

    public static void setupId(String id) {
        if (id == null || id.isEmpty()) {
            return;
        }
        clientIdInMemory = id;
        File file = resolveIdCacheFile();
        IOUtils.writeFile(file, clientIdInMemory);
    }

    public static String id() {
        if (clientIdInMemory != null) {
            return clientIdInMemory;
        }
        // from cache file
        File file = resolveIdCacheFile();
        EchoLogger.getLogger().info("clientIdFile: " + file.getAbsolutePath());
        if (file.exists()) {
            try {
                String s = IOUtils.toString(new FileInputStream(file));
                if (s != null && !s.isEmpty() && !s.startsWith(UN_RESOLVE)) {
                    clientIdInMemory = s;
                    return clientIdInMemory;
                }
            } catch (IOException e) {
                EchoLogger.getLogger().error("can not read id file: " + file.getAbsolutePath(), e);
            }
        }

        clientIdInMemory = generateClientId() + "_" + new Random().nextInt(10000);
        IOUtils.writeFile(file, clientIdInMemory);
        return clientIdInMemory;
    }


    private static String generateClientId() {
        if (isAndroid) {
            String s = generateClientIdForAndroid();
            if (s != null && !s.isEmpty()) {
                return s;
            }
        }

        String mac = generateClientIdForNormalJVM();
        if (TextUtil.isNotEmpty(mac)) {
            return mac;
        }
        return UN_RESOLVE + UUID.randomUUID().toString();
    }

    private static String generateClientIdForNormalJVM() {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                if (networkInterface.isVirtual()) {
                    continue;
                }
                if (networkInterface.isLoopback()) {
                    continue;
                }

                byte[] hardwareAddress = networkInterface.getHardwareAddress();
                if (hardwareAddress == null) {
                    continue;
                }
                return parseByte(hardwareAddress[0]) + ":" +
                        parseByte(hardwareAddress[1]) + ":" +
                        parseByte(hardwareAddress[2]) + ":" +
                        parseByte(hardwareAddress[3]) + ":" +
                        parseByte(hardwareAddress[4]) + ":" +
                        parseByte(hardwareAddress[5]);
            }
            return null;
        } catch (SocketException e) {
            return null;
        }
    }


    private static String parseByte(byte b) {
        int intValue;
        if (b >= 0) {
            intValue = b;
        } else {
            intValue = 256 + b;
        }
        return Integer.toHexString(intValue);
    }

    private static String generateClientIdForAndroid() {
        Application application = ActivityThread.currentApplication();
        if (application.checkPermission(Manifest.permission.READ_PHONE_STATE, Process.myPid(), Process.myUid())
                == PackageManager.PERMISSION_GRANTED
                ) {
            TelephonyManager telephonyManager = (TelephonyManager) application.getSystemService(Context.TELEPHONY_SERVICE);
            String imei;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                imei = telephonyManager.getImei();
            } else {
                imei = telephonyManager.getDeviceId();
            }
            if (TextUtil.isNotEmpty(imei)) {
                return imei;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                String serial = Build.getSerial();
                if (TextUtil.isNotEmpty(serial)) {
                    return serial;
                }
            }
        } else {
            EchoLogger.getLogger().warn("need permission :" + Manifest.permission.READ_PHONE_STATE);
        }

        String serial = Build.SERIAL;
        if ("unknown".equalsIgnoreCase(serial)) {
            return "";
        }
        return serial;
    }

    private static File resolveIdCacheFile() {
        if (isAndroid) {
            return resolveAndroidCacheIdFile();
        }
        return resolveJvmEnvCacheIdFile();
    }

    private static File resolveJvmEnvCacheIdFile() {
        String userHome = System.getProperty("user.home");
        File base;
        if (userHome != null && !userHome.trim().isEmpty()) {
            base = new File(userHome);
        } else {
            base = new File(".");
        }
        return new File(base, clientIdFileName);
    }

    private static File resolveAndroidCacheIdFile() {
        Application application = ActivityThread.currentApplication();
        return new File(application.getFilesDir(), clientIdFileName);
    }

    private static boolean isAndroidEnv() {
        try {
            Class.forName("android.util.Log");
            return true;
        } catch (Throwable throwable) {
            //ignore
        }
        return false;
    }
}


