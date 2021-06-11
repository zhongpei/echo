package com.virjar.echo.nat.cmd;

import com.virjar.echo.nat.client.EchoClient;
import com.virjar.echo.nat.log.EchoLogger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class ShellCmdHandler implements CmdHandler {
    @Override
    public String action() {
        return CmdHandler.ACTION_SHELL;
    }

    @Override
    public void handle(String param, CmdResponse cmdResponse) {
        new Thread("") {
            @Override
            public void run() {
                try {
                    EchoLogger.getLogger().info("execute shell:" + param);
                    Process process = Runtime.getRuntime().exec(param);

                    StringBuffer out = new StringBuffer();
                    StringBuffer error = new StringBuffer();

                    autoFillOutput(process.getInputStream(), out);
                    autoFillOutput(process.getErrorStream(), error);
                    process.waitFor();

                    cmdResponse.success(out + "\n\n" + error);
                } catch (Exception e) {
                    e.printStackTrace();
                    EchoLogger.getLogger().error("error when execute cmd", e);
                }
            }
        }.start();

    }

    @Override
    public void handle(String param, CmdResponse cmdResponse, EchoClient echoClient) {
        this.handle(param, cmdResponse);
    }

    private static void autoFillOutput(InputStream inputStream, StringBuffer stringBuffer) {
        new Thread() {
            @Override
            public void run() {
                try {
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                    String line;
                    while ((line = bufferedReader.readLine()) != null) {
                        stringBuffer.append(line).append("\n");
                    }
                    bufferedReader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }.start();
    }
}
