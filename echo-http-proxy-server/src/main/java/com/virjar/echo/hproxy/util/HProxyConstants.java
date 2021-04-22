package com.virjar.echo.hproxy.util;

import com.virjar.echo.server.common.upstream.ProxyNode;
import io.netty.channel.DefaultChannelPromise;
import io.netty.util.AttributeKey;

public class HProxyConstants {
    public static AttributeKey<ProxyNode> BIND_PROXY_NODE = AttributeKey.newInstance("bind_proxy_node");
    public static final AttributeKey<DefaultChannelPromise> connectReadyPromiseKey =
            AttributeKey.newInstance("connect-ready-promise");
    public static AttributeKey<String> ECHO_TRACE_ID = AttributeKey.newInstance("echo_trace_id");
}
