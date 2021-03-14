package com.virjar.echo.client;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONPath;
import com.virjar.echo.client.installer.JvmClientInstaller;
import com.virjar.echo.nat.bootstrap.ClientIdentifier;
import com.virjar.echo.nat.bootstrap.EchoBootstrap;
import com.virjar.echo.nat.bootstrap.SimpleHttpInvoker;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.*;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.Properties;

@Slf4j
public class JVMBootStrap {
    public static final String configPropertiesFileName = "config.properties";
    private static final String ECHO_ACCOUNT = "echo-account";
    private static final String ECHO_PASSWORD = "echo-password";

    private static final String CLIENT_ID = "client-id";
    private static final String ECHO_API_ENTRY = "api-entry";

    private static final String DEFAULT_API_ENTRY = "http://echonew.virjar.com/";


    public static void main(String[] args) throws Exception {
        Properties properties = loadFromConfigProperties();
        if (overWriteWithCommandLine(properties, args)) {
            // just print help message
            return;
        }

        fillInDefault(properties);

        startupEchoClient(properties);
    }

    private static void startupEchoClient(Properties properties) {

        //http://echonew.virjar.com/
        String apiEntry = properties.getProperty(ECHO_API_ENTRY);

        String account = properties.getProperty(ECHO_ACCOUNT);
        String loginUrl = apiEntry + "echo-api/user-info/getLogin?userName="
                + URLEncoder.encode(account)
                + "&password="
                + URLEncoder.encode(properties.getProperty(ECHO_PASSWORD));

        log.info("login request: {}", loginUrl);
        String loginResponse = SimpleHttpInvoker.get(loginUrl);
        log.info("login response: {}", loginResponse);
        if (StringUtils.isBlank(loginResponse)) {
            log.error("login failed!! network error");
            return;
        }

        JSONObject jsonObject = JSONObject.parseObject(loginResponse);
        String apiToken = (String) JSONPath.compile("$.data.apiToken").eval(jsonObject);
        if (StringUtils.isBlank(apiToken)) {
            log.error("login failed!! ");
            return;
        }

        // overwrite clientId
        String clientId = properties.getProperty(CLIENT_ID);
        if (StringUtils.isNotBlank(clientId)) {
            ClientIdentifier.setupId(clientId);
        }

        EchoBootstrap.startup(apiEntry, account, apiToken, true);
    }

    private static void fillInDefault(Properties properties) {
        if (properties.containsKey(JVMBootStrap.ECHO_API_ENTRY)) {
            String property = properties.getProperty(JVMBootStrap.ECHO_API_ENTRY);
            if (!StringUtils.isBlank(property)) {
                properties.setProperty(JVMBootStrap.ECHO_API_ENTRY, property.trim());
                return;
            }
        }
        properties.setProperty(JVMBootStrap.ECHO_API_ENTRY, JVMBootStrap.DEFAULT_API_ENTRY.trim());
    }

    private static boolean overWriteWithCommandLine(Properties properties, String[] args) throws ParseException {
        Options options = new Options();
        options.addOption(new Option("", ECHO_API_ENTRY, true, "api entry"));
        options.addOption(new Option("", ECHO_ACCOUNT, true, "the web admin login user"));
        options.addOption(new Option("", ECHO_PASSWORD, true, "the web admin login password"));
        options.addOption(new Option("", CLIENT_ID, true, "the client id"));

        options.addOption(new Option("i", "install", false, "auto install script into system"));
        options.addOption(new Option("h", "help", false, "show help message"));

        DefaultParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args, false);
        if (cmd.hasOption('h')) {
            HelpFormatter hf = new HelpFormatter();
            hf.setWidth(110);
            hf.printHelp("EchoNatServer", options);
            return true;
        }

        if (cmd.hasOption('i')) {
            try {
                JvmClientInstaller.doInstall();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return true;
        }

        syncCmdItem(properties, cmd, ECHO_API_ENTRY);
        syncCmdItem(properties, cmd, ECHO_ACCOUNT);
        syncCmdItem(properties, cmd, ECHO_PASSWORD);
        syncCmdItem(properties, cmd, CLIENT_ID);

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
