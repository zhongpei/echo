package com.virjar.echo.nat.server;

import com.alibaba.fastjson.JSONObject;
import com.virjar.echo.server.common.ClasspathResourceUtil;
import org.apache.commons.cli.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;


public class EchoNatServerStarter {

    private static final String configPropertiesFileName = "config.properties";
    private static final String CMD_PORT = "cmd-port";
    private static final String NAT_PORT = "nat-port";
    private static final String MAPPING_SPACE = "mapping-space";
    private static final String LOCAL_HOST = "local-host";
    private static final String API_ENTRY = "api-entry";

    public static final String DEFAULT_NAT_PORT = "5698";
    public static final String DEFAULT_CMD_PORT = "5699";
    public static final String DEFAULT_MAPPING_SPACE = "20000-25000";
    public static final String DEFAULT_LOCAL_HOST = "127.0.0.1";
    private static final String DEFAULT_API_ENTRY = "http://echonew.virjar.com/";

    private static final String SERVER_ID = "server-id";

    public static void main(String[] args) throws Exception {
        Properties properties = loadFromConfigProperties();
        if (overWriteWithCommandLine(properties, args)) {
            // just print help message
            return;
        }

        fillInDefault(properties);

        System.out.println("EchoServer startup with config: " + JSONObject.toJSONString(properties));
        startEchoNatService(properties);
    }

    private static void startEchoNatService(Properties properties) {
        EchoNatServer.builder()
                .cmdHttpPort(NumberUtils.toInt(properties.getProperty(CMD_PORT)))
                .localHost(properties.getProperty(LOCAL_HOST))
                .natPort(NumberUtils.toInt(properties.getProperty(NAT_PORT)))
                .mappingSpace(properties.getProperty(MAPPING_SPACE))
                .serverId(properties.getProperty(SERVER_ID))
                .apiEntry(properties.getProperty(API_ENTRY))
                .build().startUp();
    }

    private static void fillInDefault(Properties properties) {
        fillInDefaultItem(properties, CMD_PORT, DEFAULT_CMD_PORT);
        fillInDefaultItem(properties, NAT_PORT, DEFAULT_NAT_PORT);
        fillInDefaultItem(properties, MAPPING_SPACE, DEFAULT_MAPPING_SPACE);
        fillInDefaultItem(properties, LOCAL_HOST, DEFAULT_LOCAL_HOST);
        fillInDefaultItem(properties, API_ENTRY, DEFAULT_API_ENTRY);
    }

    private static void fillInDefaultItem(Properties properties, String key, String defaultValue) {
        if (properties.containsKey(key)) {
            String property = properties.getProperty(key);
            if (!StringUtils.isBlank(property)) {
                properties.setProperty(key, property.trim());
                return;
            }
        }
        properties.setProperty(key, defaultValue.trim());
    }

    private static boolean overWriteWithCommandLine(Properties properties, String[] args) throws ParseException {
        Options options = new Options();
        options.addOption(new Option("", CMD_PORT, true, "the command http server port"));
        options.addOption(new Option("", SERVER_ID, true, "the server id"));
        options.addOption(new Option("", NAT_PORT, true, "echo nat server port, connected with echo client with echoNatProtocol"));
        options.addOption(new Option("", MAPPING_SPACE, true, "the mapping space is local port space used to bind client channel ," +
                "the proxy protocol implement(http/https/socks5/udpProxy) will connect this port and forward users network data"));
        options.addOption(new Option("", LOCAL_HOST, true, "to decide which network interface to listen when bind client channel"));
        options.addOption(new Option("", API_ENTRY, true, "the meta server api entry,to push some event to meta server"));
        options.addOption(new Option("h", "help", false, "show help message"));

        DefaultParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args, false);
        if (cmd.hasOption('h')) {
            HelpFormatter hf = new HelpFormatter();
            hf.setWidth(110);
            hf.printHelp("EchoNatServer", options);
            return true;
        }

        syncCmdItem(properties, cmd, CMD_PORT);
        syncCmdItem(properties, cmd, NAT_PORT);
        syncCmdItem(properties, cmd, MAPPING_SPACE);
        syncCmdItem(properties, cmd, LOCAL_HOST);
        syncCmdItem(properties, cmd, API_ENTRY);

        return false;
    }

    private static void syncCmdItem(Properties properties, CommandLine cmd, String key) {
        if (cmd.hasOption(key)) {
            properties.put(key, cmd.getOptionValue(key));
        }
    }

    private static Properties loadFromConfigProperties() throws IOException {
        Properties properties = new Properties();
        try (InputStream inputStream = ClasspathResourceUtil.getResourceAsStream("/" + configPropertiesFileName)) {
            if (inputStream == null) {
                return properties;
            }
            properties.load(inputStream);
        }
        return properties;
    }
}
