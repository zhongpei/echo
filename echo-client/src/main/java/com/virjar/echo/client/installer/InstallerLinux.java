package com.virjar.echo.client.installer;

import com.virjar.echo.client.ClasspathResourceUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
class InstallerLinux {
    static void doInstall4Linux(File installDir, File logsDir) throws Exception {
        // 首先，Linux环境下的安装，必须运行在root模式，否则无法开机自启动
        String uid = CmdExecutor.runCommand("id -u");
        if (!StringUtils.equals(uid.trim(), "0")) {
            throw new IllegalStateException("echo can only be installed as root user,now: " + uid);
        }

        // https://www.cnblogs.com/cq90/p/9538048.html
        // linux配置依赖 supervisor
        makeSureSupervisor();


        // 配置echo的自启动环境
        configEchoSupervisorTask(installDir);

        // 配置supervisord服务开机自启动
        startOnBoot();


//        String supervisordStatus = CmdExecutor.runCommand("ps -ef | grep supervisord | grep -v \"grep\"").trim();
//        if (StringUtils.isBlank(supervisordStatus)) {
//            // 还没有运行，直接加载
//            CmdExecutor.runCommand("supervisord -c /etc/supervisord.conf");
//            return;
//        }


        String supervisorctlStatus = CmdExecutor.runCommand("supervisorctl status");
        if (supervisorctlStatus.contains("echo-client")) {
            CmdExecutor.runCommand("supervisorctl restart echo-client");
        } else {
            CmdExecutor.runCommand("supervisord -c /etc/supervisord.conf");
        }
    }

    private static void startOnBoot() throws IOException, InterruptedException {
        File file = new File("/etc/init.d/supervisord");
        if (file.exists()) {
            return;
        }

        //https://www.cnblogs.com/cq90/p/9538048.html
        String supervisordContent = IOUtils.toString(ClasspathResourceUtil.getResourceAsStream("linux/supervisord.sh"), StandardCharsets.UTF_8);

        String supervisordPath = CmdExecutor.runCommand("which supervisord");

        supervisordContent = supervisordContent.replace("${exec_prefix_stub}", supervisordPath);

        FileUtils.writeStringToFile(file, supervisordContent, StandardCharsets.UTF_8);

        CmdExecutor.runCommand("chmod -R 755 /etc/init.d/supervisord");
        CmdExecutor.runCommand("chkconfig --add supervisord");
        CmdExecutor.runCommand("chkconfig  supervisord on");

    }


    private static void configEchoSupervisorTask(File installDir) throws IOException {
        File superVisorConfigDir = new File("/etc/supervisord.d");
        FileUtils.forceMkdir(superVisorConfigDir);

        List<String> echoConfigLines = new ArrayList<>();
        echoConfigLines.add("[program:echo-client]");
        echoConfigLines.add("command = " + new File(installDir, "bin/EchoClient.sh").getAbsolutePath());
        echoConfigLines.add("autostart = true");

        FileUtils.writeLines(new File(superVisorConfigDir, "echo.ini"), echoConfigLines);

    }


    private static void makeSureSupervisor() throws IOException, InterruptedException {

        log.info("test if supervisorctl installed successful");
        String supervisorctlHelpMessage = CmdExecutor.runCommand("supervisorctl -h");
        if (StringUtils.contains(supervisorctlHelpMessage, "control applications run by supervisord from the cmd line")) {
            log.info("supervisorctl has been installed");
            editEtcSupervisorConf();
            return;
        }

        // 考虑网络问题，pip安装限定5分钟内执行完即可
        String content = CmdExecutor.runCommand("pip install supervisor", 5);
        if (StringUtils.contains(content, "Python 2.7 will reach the end of its life on January 1st, 2020. Please upgrade your Python as Python 2.7")) {
            CmdExecutor.runCommand("pip3 install supervisor", 5);
        }

        supervisorctlHelpMessage = CmdExecutor.runCommand("supervisorctl -h");
        if (!StringUtils.contains(supervisorctlHelpMessage, "control applications run by supervisord from the cmd line")) {
            throw new IllegalStateException("supervisor install failed");
        }
        String sampleSupervisorConfig = CmdExecutor.runCommand("echo_supervisord_conf");

        if (!sampleSupervisorConfig.contains("Sample supervisor config file")) {
            throw new IllegalArgumentException("failed to generate Sample supervisor config");
        }
        FileUtils.writeStringToFile(new File("/etc/supervisord.conf"), sampleSupervisorConfig, StandardCharsets.UTF_8);
        editEtcSupervisorConf();
    }


    /**
     * 确保配置了 files = supervisord.d/*.ini
     */
    private static void editEtcSupervisorConf() throws IOException {
        File globalConfigFile = new File("/etc/supervisord.conf");
        if (!globalConfigFile.exists() || !globalConfigFile.canRead()) {
            throw new IllegalStateException("can not read :" + globalConfigFile.getAbsolutePath());
        }
        List<String> strings = FileUtils.readLines(globalConfigFile, StandardCharsets.UTF_8);

        int includeIndex = -1;
        for (int i = 0; i < strings.size(); i++) {
            if (strings.get(i).trim().equalsIgnoreCase("[include]")) {
                includeIndex = i;
                if (i + 1 < strings.size()) {
                    if (strings.get(i + 1).trim().equalsIgnoreCase("files = supervisord.d/*.ini")) {
                        return;
                    }
                }
            } else if (strings.get(i).trim().startsWith("[")) {
                includeIndex = -1;
            }
        }

        if (includeIndex < 0) {
            strings.add("[include]");
            strings.add("files = supervisord.d/*.ini");
        } else {
            strings.add(includeIndex, "files = supervisord.d/*.ini");
        }

        FileUtils.writeLines(globalConfigFile, strings);
    }

}
