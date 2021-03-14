package com.virjar.echo.server.common;

import io.netty.channel.Channel;
import io.netty.channel.DefaultChannelPromise;
import io.netty.util.AttributeKey;

public class CommonConstant {
    public static AttributeKey<Channel> NEXT_CHANNEL = AttributeKey.newInstance("next_channel");
    public static final AttributeKey<DefaultChannelPromise> connectReadyPromiseKey =
            AttributeKey.newInstance("connect-ready-promise");
}
