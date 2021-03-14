package com.virjar.echo.client.installer;

import com.virjar.echo.client.ClasspathResourceUtil;
import com.virjar.echo.client.JVMBootStrap;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.ArrayList;
import java.util.List;

class InstallerWindows {
    private static String classpath;
    private static String javaHome;

    private static final String echoServiceName = "echo_client";

    // 以下为shell脚本而外附加的参数
    private static final List<String> additionJvmArguments = new ArrayList<>();
    private static final List<String> skipSyncJVMArguments = new ArrayList<String>() {
        {
            add("-Dapp.pid=");
            add("-Dapp.repo=");
            add("-Dbasedir=");
        }
    };


    static void doInstall4Windows(File installDir, File logsDir) throws Exception {
//        //https://blog.csdn.net/happydecai/article/details/96477852
        boolean is64 = "64".equalsIgnoreCase(System.getProperty("sun.arch.data.model"));

        File javaServiceBindFile = releaseJavaServiceBin(installDir, is64);

        // 需要提取的参数
        // 1. java_home: java的可执行文件位置
        // 2. classPath
        // 3. jvm参数，如 -Xms64M -Xmx512M
        extractJVMInstallEnv(installDir);

        // 从java_home里面抽取 jvm.dll
        String jvmDllPath = findJvmDllPath();
        if (StringUtils.isBlank(jvmDllPath)) {
            throw new IllegalStateException("can not findJvmDllPath from java_home:" + javaHome);
        }


        //卸载历史的echo服务
        CmdExecutor.runCommand(javaServiceBindFile.getAbsolutePath() + " -uninstall " + echoServiceName);

        // 安装服务
        String command = buildJavaServiceCommand(javaServiceBindFile, jvmDllPath, installDir, logsDir);
        CmdExecutor.runCommand(command);
    }


    static String buildJavaServiceCommand(File javaServiceBindFile, String jvmDllPath, File installDir,
                                          File logsDir) {
        StringBuilder registerCmd = new StringBuilder(javaServiceBindFile.getAbsolutePath());
        registerCmd.append(" -install ").append(echoServiceName).append(" ");
        registerCmd.append("\"").append(jvmDllPath).append("\"");

        // classpath
        registerCmd.append(" -Djava.class.path=")
                .append(classpath);

        // EXTRA_JVM_ARGUMENTS
        for (String arg : additionJvmArguments) {
            // -Xms256m -Xmx256m -XX:NewSize=192m
            registerCmd.append(" ").append(arg);
        }

        // mainClass
        registerCmd.append(" -start ");
        String mainClass = JVMBootStrap.class.getName();
        registerCmd.append(mainClass);

        // 标准输入输出的日志文件
        registerCmd.append(" -out ");
        registerCmd
                // .append("\"")
                .append(new File(logsDir, "out.log").getAbsolutePath())
        // .append("\"")
        ;

        registerCmd.append(" -err ");
        registerCmd
                //.append("\"")
                .append(new File(logsDir, "err.log").getAbsolutePath())
        //.append("\"")
        ;

        // 运行的根目录
        registerCmd.append(" -current ");
        registerCmd
                //.append("\"")
                .append(installDir.getAbsolutePath())
        //.append("\"")
        ;

        registerCmd.append(" -auto");
        registerCmd.append(" -description \"echo client service: https://git.virjar.com/echo/echo\"");
        return registerCmd.toString();
    }

    static String findJvmDllPath() {
        File root = new File(javaHome);
        File testJvmFile = new File(root, "bin/server/jvm.dll");
        if (testJvmFile.exists() && testJvmFile.isFile()) {
            return testJvmFile.getAbsolutePath();
        }

        testJvmFile = new File(root, "jre/bin/server/jvm.dll");
        if (testJvmFile.exists() && testJvmFile.isFile()) {
            return testJvmFile.getAbsolutePath();
        }

        testJvmFile = new File(root.getParent(), "bin/server/jvm.dll");
        if (testJvmFile.exists() && testJvmFile.isFile()) {
            return testJvmFile.getAbsolutePath();
        }
        testJvmFile = new File(root.getParent(), "jre/bin/server/jvm.dll");
        if (testJvmFile.exists() && testJvmFile.isFile()) {
            return testJvmFile.getAbsolutePath();
        }
        return null;
    }

    static void extractJVMInstallEnv(File installDir) {
        RuntimeMXBean mxb = ManagementFactory.getRuntimeMXBean();
        //classpath = mxb.getClassPath();
        // classpath需要手动扫描，不能使用当前进程的
        StringBuilder classpathSb = new StringBuilder();
        classpathSb.append(new File(installDir, "conf").getAbsolutePath())
                .append(";");
        File jarDir = new File(installDir, "lib");
        File[] allJarLib = jarDir.listFiles();
        if (allJarLib != null) {
            for (File file : allJarLib) {
                classpathSb.append(file.getAbsolutePath()).append(";");
            }
        }
        classpathSb.setLength(classpathSb.length() - 1);
        classpath = classpathSb.toString();

        // "java.home": "/Library/Java/JavaVirtualMachines/jdk1.8.0_144.jdk/Contents/Home/jre",
        javaHome = System.getProperty("java.home");

        List<String> inputArguments = mxb.getInputArguments();
        for (String arg : inputArguments) {
            boolean needAdd = true;
            for (String skip : skipSyncJVMArguments) {
                if (arg.startsWith(skip)) {
                    needAdd = false;
                    break;
                }
            }
            if (needAdd) {
                additionJvmArguments.add(arg);
            }
        }

        //-Dapp.repo -Dbasedir 需要被重新覆盖到install dir下面
        additionJvmArguments.add(
                "-Dapp.repo=" + new File(installDir, "lib").getAbsolutePath()
        );
        additionJvmArguments.add(
                "-Dbasedir=" + installDir.getAbsolutePath()
        );
    }

    static File releaseJavaServiceBin(File installDir, boolean is64) throws IOException {
        String javaServiceFileName = is64 ? "JavaService64_2_0_10.exe" : "JavaService32_2_0_10.exe";

        File javaServerFile = new File(installDir, "bin/" + javaServiceFileName);
        try (InputStream resourceAsStream = ClasspathResourceUtil.getResourceAsStream("windows/" + javaServiceFileName)) {
            try (FileOutputStream fileOutputStream = new FileOutputStream(javaServerFile)) {
                IOUtils.copy(resourceAsStream, fileOutputStream);
            }
        }
        return javaServerFile;
    }
}
