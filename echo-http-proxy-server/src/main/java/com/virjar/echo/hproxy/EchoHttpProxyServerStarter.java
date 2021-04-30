package com.virjar.echo.hproxy;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import com.google.common.primitives.Booleans;
import com.virjar.echo.server.common.ClasspathResourceUtil;
import org.apache.commons.cli.*;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.Properties;


public class EchoHttpProxyServerStarter {

    private static final String configPropertiesFileName = "config.properties";
    private static final String CMD_PORT = "cmd-port";
    private static final String MAPPING_SPACE = "mapping-space";
    private static final String META_URLS = "mapping-server-url";
    private static final String SERVER_ID = "server-id";
    private static final String AUTH_CONFIG_URL = "auth-config-url";
    private static final String DEBUG = "debug";
    private static final String API_ENTRY = "api-entry";


    public static final String DEFAULT_CMD_PORT = "5710";
    public static final String DEFAULT_MAPPING_SPACE = "20000-25000";
    public static final String DEFAULT_DEBUG = "false";
    private static final String DEFAULT_API_ENTRY = "http://echonew.virjar.com/";

    public static void main(String[] args) throws Exception {
        Properties properties = loadFromConfigProperties();
        if (overWriteWithCommandLine(properties, args)) {
            // just print help message
            return;
        }
        fillInDefault(properties);
        System.out.println("EchoServer startup with config: " + JSONObject.toJSONString(properties));
        startEchoHttpService(properties);
    }

    private static void startEchoHttpService(Properties properties) {
        EchoHttpProxyServer.builder()
                .cmdHttpPort(NumberUtils.toInt(properties.getProperty(CMD_PORT)))
                .mappingSpace(properties.getProperty(MAPPING_SPACE))
                .mappingServerUrls(properties.getProperty(META_URLS))
                .serverId(properties.getProperty(SERVER_ID))
                .authInfoLoadUrl(properties.getProperty(AUTH_CONFIG_URL))
                .debugMode(BooleanUtils.isTrue(Boolean.valueOf(properties.getProperty(DEBUG))))
                .apiEntry(properties.getProperty(API_ENTRY))
                .build().startUp();
    }

    private static void fillInDefault(Properties properties) {
        fillInDefaultItem(properties, CMD_PORT, DEFAULT_CMD_PORT);
        fillInDefaultItem(properties, MAPPING_SPACE, DEFAULT_MAPPING_SPACE);
        fillInDefaultItem(properties, DEBUG, DEFAULT_DEBUG);
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
        options.addOption(new Option("", AUTH_CONFIG_URL, true, ""));
        options.addOption(new Option("", SERVER_ID, true, ""));
        options.addOption(new Option("", MAPPING_SPACE, true, "the mapping space is local port space used to provide http proxy service"));
        options.addOption(new Option("", META_URLS, true, "meta server urls ,we download connection upstream info from meta server"));
        options.addOption(new Option("", API_ENTRY, true, "the meta server api entry,to push some event to meta server"));
        options.addOption(new Option("", DEBUG, true, "running on debug mode"));
        options.addOption(new Option("h", "help", false, "show help message"));

        DefaultParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args, false);
        if (cmd.hasOption('h')) {
            HelpFormatter hf = new HelpFormatter();
            hf.setWidth(110);
            hf.printHelp("EchoHttpServer", options);
            return true;
        }

        Lists.newArrayList(CMD_PORT, META_URLS, API_ENTRY, MAPPING_SPACE, DEBUG, AUTH_CONFIG_URL, SERVER_ID)
                .forEach(key -> syncCmdItem(properties, cmd, key));
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
