package com.virjar.echo.client.installer;

import com.virjar.echo.client.ClasspathResourceUtil;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

class InstallerMac {
    /**
     * mac os 默认安装
     */
    static void doInstall4Mac(File installDir, File logsDir) throws Exception {
        // 引导echo命令行启动的脚本，他处理mac兼容性问题，并被mac os的提供引导
        File startupShFile = setupStartupScript(installDir);

        // plist 文件，是mac os的引导标准
        setupPList(logsDir, startupShFile);
    }


    private static void setupPList(File logsDir, File startupShFile) throws IOException, InterruptedException {
        File plistStd = new File(logsDir, "echo-out.log");
        File plistErr = new File(logsDir, "echo-err.log");

        String plistFileContent = IOUtils.toString(ClasspathResourceUtil.getResourceAsStream("mac/boot_echo.plist"), StandardCharsets.UTF_8);


        plistFileContent =
                plistFileContent.replaceAll("##echo_startup_shell_path##",
                        startupShFile.getAbsolutePath());

        plistFileContent = plistFileContent.replaceAll("##echo_log_stdout##",
                plistStd.getAbsolutePath()
        );
        plistFileContent = plistFileContent.replaceAll("##echo_log_stderr##",
                plistErr.getAbsolutePath()
        );

        File plistSaveFile = new File(FileUtils.getUserDirectory(),
                "Library/LaunchAgents/com.virjar.echo-client.plist");

        FileUtils.writeStringToFile(plistSaveFile, plistFileContent, StandardCharsets.UTF_8);

        //launchctl load ~/Library/LaunchAgents/com.virjar.echo-client.plist
        String launchctlLoadCommand =
                "launchctl load " + plistSaveFile.getAbsolutePath();
        CmdExecutor.runCommand(launchctlLoadCommand);
    }

    private static File setupStartupScript(File installDir) throws IOException {
        // 引导Echo本身脚本的一个脚本
        File startupShFile = new File(installDir, "bin/startup.sh");

        try (InputStream resourceAsStream = ClasspathResourceUtil.getResourceAsStream("mac/startup.sh")) {
            try (FileOutputStream fileOutputStream = new FileOutputStream(startupShFile)) {
                IOUtils.copy(resourceAsStream, fileOutputStream);
            }
        }


        if (startupShFile.canExecute()) {
            startupShFile.setExecutable(true);
        }
        return startupShFile;
    }
}
