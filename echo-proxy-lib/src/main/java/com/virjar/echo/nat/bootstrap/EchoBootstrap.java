package com.virjar.echo.nat.bootstrap;

import android.app.ActivityThread;
import android.content.Context;
import com.virjar.echo.nat.client.EchoClient;
import com.virjar.echo.nat.cmd.AndroidReDialHandler;
import com.virjar.echo.nat.cmd.CmdHandler;
import com.virjar.echo.nat.cmd.ShellCmdHandler;
import com.virjar.echo.nat.log.EchoLogger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * EchoBootstrap是EchoClient的一个封装，主要实现更加自动化的参数配置<br>
 * 1. 不是直接链接EchoNatServer，而是通过ApiEndpoint接入，通过web服务动态请求服务列表
 * 2. EchoBootstrap对接Echo分布式环境的MetaServer，EchoClient对接的是EchoNatServer
 * 3. 会自动完成客户端ID计算/缓存，客户端插件安装。完成android环境和JVm环境的自动适配
 * 4. 分布式环境下，他会实现服务器资源双连接会话，以此实现高可用
 * 5. 如果是在Android环境下运行，那么自动配置了ip重播插件(手机飞行模式切换)
 * <br>
 * 请注意，如果是Android环境，需要开启如下权限：
 * <ul>
 * <li>android.permission.INTERNET          :访问网络</li>
 * <li>android.permission.READ_PHONE_STATE  :读取设备ID(需要用户手动授权)</li>
 * <li>android.permission.WRITE_SETTINGS    :开关飞行模式，进行拨号操作(需要用户手动授权)</li>
 * </ul>
 */
public class EchoBootstrap {

    private static final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 启动echo客户端服务，
     *
     * @param apiEntryPoint 接入点，为一个http URL，目前官方demo部署在 http://echonew.virjar.com/
     * @param account       后台登录账号，echo系统通过账号进行服务访问鉴权(你只能访问自己账号下的代理服务资源)
     * @param apiToken      APIToken是一组echo后台服务访问的鉴权凭证，通过他可以访问Echo后台的部分非管理功能(拉取代理列表、拉取服务器列表等)
     *                      APIToken可以写入到公司配置中心或者写入到代码中，他隶属于某个echo后台账户，且不会改变(如果泄露可以在web后台重新生成)
     */
    public static void startup(String apiEntryPoint, String account, String apiToken, boolean sync) {
        if (running.compareAndSet(false, true)) {
            startupInternal(apiEntryPoint, account, apiToken, sync);
        }
    }

    public static void startup(String apiEntryPoint, String account, String apiToken) {
        startup(apiEntryPoint, account, apiToken, false);
    }


    private static void startupInternal(String apiEntryPoint, String account, String apiToken, boolean sync) {
        // apiEntryPoint = http://echonew.virjar.com/
        SimpleHttpInvoker.setupEchoParam(apiEntryPoint, apiToken);

        //服务器链接地址配置
        List<String> echoNatServerConfig = getEchoNatServerConfig();

        // 启动长链接服务
        startupEcho(echoNatServerConfig, account, sync);
    }


    @SuppressWarnings("all")
    private static Map<String, EchoClient> runningEchoClient = new ConcurrentHashMap<>();

    private static void startupEcho(List<String> echoNatServerConfig, String account, boolean sync) {
        for (String config : echoNatServerConfig) {
            EchoLogger.getLogger().info("startup echo client for server: " + config);
            String[] ipAndPort = config.split(":");
            EchoClient echoClient = new EchoClient(ipAndPort[0], Integer.parseInt(ipAndPort[1]), ClientIdentifier.id());
            echoClient.setAdminAccount(account);
            addCmdHandler(echoClient);

            echoClient.startUp();

            runningEchoClient.put(config, echoClient);
        }

        if (sync) {
            syncClient();
        }
    }

    @SuppressWarnings("all")
    private static void syncClient() {
        while (true) {
            for (EchoClient echoClient : runningEchoClient.values()) {
                echoClient.sync();
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException interruptedException) {
                EchoLogger.getLogger().error("wait failed", interruptedException);
            }
        }
    }

    public static void registerCmdHandler(CmdHandler cmdHandler) {
        for (EchoClient echoClient : runningEchoClient.values()) {
            echoClient.registerCmdHandler(cmdHandler);
        }
    }

    private static void addCmdHandler(EchoClient echoClient) {
        echoClient.registerCmdHandler(new ShellCmdHandler());
        if (ClientIdentifier.isAndroid) {
            echoClient.registerCmdHandler(new AndroidReDialHandler(getContext()));
        }
    }


    @SuppressWarnings("all")
    private static Context getContext() {
        Object application = ActivityThread.currentApplication();
        // 这里比较特殊，在android环境下，application继承自Content，可以直接向上转型
        // 但是在jvm环境下，由于Android的class是compile only的，
        // application转型为Context的过程会在class初始化的时候进行检查，然后会导致NoClassDefoundError: Exception in thread "main" java.lang.NoClassDefFoundError: android/content/Context
        // 所以我们这里手动先转型为Object，然后再手动向下转型，避免字节码检查错误
        return (Context) application;
    }

    private static List<String> getEchoNatServerConfig() {
        // we
        String clientId = ClientIdentifier.id();

        Map<String, String> allocateNatMappingServerParam = new HashMap<>();
        allocateNatMappingServerParam.put("clientId", clientId);

        //first allocate nat server resource from entry point
        String natMappingServer = SimpleHttpInvoker.echoAccess("/echo-api/nat-mapping-server/allocateNatMappingServer", allocateNatMappingServerParam);
        // startup echo tuning channel with all nat mapping node
        // ip1:port
        // ip2:port

        if (TextUtil.isEmpty(natMappingServer)) {
            EchoLogger.getLogger().error("query nat mapping server failed");
            return Collections.emptyList();
        }

        String[] serverList = natMappingServer.split("\n");
        List<String> trimServerList = new ArrayList<>();
        for (String serverConfig : serverList) {
            if (TextUtil.isEmpty(serverConfig)) {
                continue;
            }
            if (!serverConfig.contains(":")) {
                continue;
            }
            trimServerList.add(serverConfig.trim());
        }

        EchoLogger.getLogger().info("echo nat server config size: " + trimServerList.size());
        return trimServerList;
    }
}
