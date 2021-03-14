package com.virjar.echo.client.installer;

import com.virjar.echo.client.ClasspathResourceUtil;
import com.virjar.echo.client.JVMBootStrap;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;
import org.dom4j.tree.DefaultElement;

import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
public class JvmClientInstaller {
    public static void doInstall() throws Exception {
        ClassLoader classLoader = ClasspathResourceUtil.class.getClassLoader();
        if (!(classLoader instanceof URLClassLoader)) {
            System.err.println("error install script environment");
            return;
        }
        URLClassLoader urlClassLoader = (URLClassLoader) classLoader;

        File configPropertiesFile = ClasspathResourceUtil.getResourceFileFromURLClassLoader(
                "/" + JVMBootStrap.configPropertiesFileName, urlClassLoader
        );
        if (configPropertiesFile == null) {
            log.info("error install script environment，can not find file path: " + JVMBootStrap.configPropertiesFileName);
            return;
        }

        // resolve scriptRootDir
        File scriptRootDir = configPropertiesFile.getParentFile().getParentFile();

        String osName = System.getProperty("os.name").toLowerCase();

        boolean isWindows = osName.startsWith("Windows".toLowerCase());

        // resolve install directory
        File installDir = null;
        if (isWindows) {
            // for windows ,some none unicode characters in file path
            // use C:\\ or D:\\ as install root_dir to avoid fucking unpredictable case
            installDir = chooseInstallDisk4Windows("D:\\");
            if (installDir == null) {
                installDir = chooseInstallDisk4Windows("E:\\");
            }
            if (installDir == null) {
                installDir = chooseInstallDisk4Windows("C:\\");
            }
        }
        if (installDir == null) {
            File userHome = FileUtils.getUserDirectory();
            installDir = new File(userHome, "echo-proxy-client");
        }
        copyResourceToInstallDir(scriptRootDir, installDir);

        File logsDir = new File(installDir, "logs");
        FileUtils.forceMkdir(logsDir);
        editLogbackRule(installDir, logsDir);


        if (osName.startsWith("Mac OS".toLowerCase())) {
            InstallerMac.doInstall4Mac(installDir, logsDir);
            setExecutePermissionForUnix(installDir);
        } else if (osName.startsWith("Windows".toLowerCase())) {
            InstallerWindows.doInstall4Windows(installDir, logsDir);
        } else {
            setExecutePermissionForUnix(installDir);
            InstallerLinux.doInstall4Linux(installDir, logsDir);
            setExecutePermissionForUnix(installDir);
        }

        log.info("your echo client install successful in path: {}", installDir.getAbsolutePath());
    }

    private static File chooseInstallDisk4Windows(String diskName) {
        File candidateInstallRootDir = new File(diskName);
        if (!candidateInstallRootDir.exists()) {
            return null;
        }
        File installDir = new File(candidateInstallRootDir, "echo-proxy-client-install");
        if (!installDir.exists()) {
            if (installDir.mkdirs()) {
                return installDir;
            }
            return null;
        }
        if (installDir.isFile()) {
            return null;
        }
        try {
            FileUtils.cleanDirectory(installDir);
            return installDir;
        } catch (IOException e) {
            return null;
        }
    }


    private static void copyResourceToInstallDir(File scriptRootDir, File installDir) throws IOException {
        FileUtils.forceMkdir(installDir);
        log.info("copy directory from :" + scriptRootDir.getAbsolutePath() + " to:" + installDir.getAbsolutePath());
        FileUtils.copyDirectory(scriptRootDir, installDir);
    }

    private static void setExecutePermissionForUnix(File installDir) {
        // make sure bin script has execute permission
        File binDir = new File(installDir, "bin");
        File[] files = binDir.listFiles((dir, name) -> name.endsWith(".sh"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (!file.canExecute()) {
                if (!file.setExecutable(true)) {
                    log.warn("can not set execute permission for sh file:{}", file);
                }
            }
        }
    }

    private static void editLogbackRule(File installDir, File logsDir) throws DocumentException, IOException {

        SAXReader saxReader = new SAXReader();
        saxReader.setEncoding("UTF8");
        URL logbackURL = InstallerMac.class.getClassLoader().getResource("logback.xml");
        if (logbackURL == null) {
            throw new IllegalStateException("can not read resource: logback.xml");
        }

        Document document = saxReader.read(logbackURL);

        Element rootElement = document.getRootElement();

        List<Element> propertyList = rootElement.elements("property");

        for (Element element : propertyList) {
            if (!(element instanceof DefaultElement)) {
                continue;
            }
            DefaultElement propertyElement = (DefaultElement) element;

            Attribute attribute = propertyElement.attribute("name");
            if (attribute == null) {
                continue;
            }


            String attributeValue = attribute.getValue();
            if (!"logDir".equals(attributeValue)) {
                continue;
            }

            String logConfig4Logback = logsDir.getAbsolutePath();
            if (File.separatorChar != '/') {
                // windows上面，也需要转化成java形式的文件描述，否则无法被logback正确识别
                logConfig4Logback = logConfig4Logback.replace(File.separatorChar, '/');
            }

            propertyElement.attribute("value").setValue(logConfig4Logback);
            File logbackSaveFile = new File(installDir, "conf/logback.xml");

            OutputFormat outputFormat = new OutputFormat();
            outputFormat.setEncoding("UTF8");
            FileOutputStream fileOutputStream = new FileOutputStream(logbackSaveFile);

            XMLWriter writer = new XMLWriter(
                    new OutputStreamWriter(fileOutputStream, StandardCharsets.UTF_8)
                    , outputFormat);
            writer.write(document);
            writer.close();
            return;

        }

    }

}
