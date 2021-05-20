package com.virjar.echo.nat.cmd;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import com.virjar.echo.nat.client.EchoClient;
import com.virjar.echo.nat.log.EchoLogger;

/**
 * only used for Android platform
 */
public class AndroidReDialHandler implements CmdHandler {
    private final Context context;

    public AndroidReDialHandler(Context context) {
        this.context = context;
    }

    @Override
    public String action() {
        return CmdHandler.ACTION_ANDROID_REDIAL;
    }

    @Override
    public void handle(String param, CmdResponse cmdResponse) {
        cmdResponse.success("accept task");
        setAirplaneModeStatus(true);
        new Thread("close-airplane") {
            @Override
            public void run() {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException interruptedException) {
                    EchoLogger.getLogger().warn("sleep interrupted");
                }
                setAirplaneModeStatus(false);
            }
        }.start();
    }

    @Override
    public void handle(String param, CmdResponse cmdResponse, EchoClient echoClient) {
        this.handle(param, cmdResponse);
    }

    private void setAirplaneModeStatus(boolean open) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN) {
            Settings.System.putInt(context.getContentResolver(), Settings.System.AIRPLANE_MODE_ON, open ? 1 : 0);
        } else {
            Settings.Global.putInt(context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, open ? 1 : 0);
        }
        Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        intent.putExtra("state", open);
        context.sendBroadcast(intent);

        //TODO with su shell
    }
}
