package com.virjar.echo.client.installer;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
class CmdExecutor {
    static String runCommand(String cmd) throws IOException, InterruptedException {
        return runCommand(cmd, 2);
    }

    static String runCommand(String cmd, int timeout) throws IOException, InterruptedException {
        log.info("execute cmd: {}", cmd);

        // 直接执行没有环境变量，这里把环境变量导入一下，否则有一些自己安装的服务无法成功执行
        Map<String, String> newEnvironment = new HashMap<>(System.getenv());
        int i = 0;
        String[] environment = new String[newEnvironment.size()];
        for (Map.Entry<String, String> entry : newEnvironment.entrySet()) {
            environment[i] = entry.getKey() + "=" + entry.getValue();
            i++;
        }

        try {
            Process process = Runtime.getRuntime().exec(cmd, environment);
            StreamReadTask streamReadTaskInfo = new StreamReadTask(process.getInputStream());
            StreamReadTask streamReadTaskError = new StreamReadTask(process.getErrorStream());
            streamReadTaskInfo.start();
            streamReadTaskError.start();

            boolean success = process.waitFor(timeout, TimeUnit.MINUTES);
            if (!success) {
                throw new IllegalStateException("cmd : {" + cmd + "} execute timeout");
            }
            // 休眠1s，因为读取线程可能还没有把数据读完整
            Thread.sleep(1000);

            String out = streamReadTaskInfo.finalOut().trim() + streamReadTaskError.finalOut().trim();
            log.info("cmd execute result:\n{}", out);
            return out;
        } catch (Throwable throwable) {
            log.error("cmd execute error", throwable);
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            throwable.printStackTrace(new PrintWriter(new OutputStreamWriter(byteArrayOutputStream)));
            return byteArrayOutputStream.toString("utf8");
        }
    }

    private static class StreamReadTask extends Thread {
        private final InputStream inputStream;
        private final StringBuilder output = new StringBuilder();


        StreamReadTask(InputStream inputStream) {
            this.inputStream = inputStream;
        }

        // 命令执行和线程切换，可能存在延时，所以这里依靠加锁来同步状态
        // 由于切换非常快，2s时间就够了
        public String finalOut() {
//            boolean lockSuccess = false;
//            try {
//                lockSuccess = lock.tryLock(5, TimeUnit.SECONDS);
//            } catch (InterruptedException interruptedException) {
//                log.error("interruptedException ", interruptedException);
//            } finally {
//                if (lockSuccess) {
//                    lock.unlock();
//                }
//            }
            return output.toString();
        }

        @Override
        public void run() {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            try {

                while ((line = bufferedReader.readLine()) != null) {
                    log.info(line);
                    output.append(line);
                    output.append("\n");
                }
            } catch (IOException e) {
                output.append(e)
                        .append(":")
                        .append(e.getMessage());
            } finally {
                IOUtils.closeQuietly(inputStream);
            }
        }
    }
}
