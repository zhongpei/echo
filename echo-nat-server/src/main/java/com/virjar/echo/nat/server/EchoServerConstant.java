package com.virjar.echo.nat.server;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class EchoServerConstant {
    public static AttributeKey<String> NAT_CHANNEL_CLIENT_KEY = AttributeKey.newInstance("nat_channel_client_id");
    public static AttributeKey<Long> NAT_CHANNEL_SERIAL = AttributeKey.newInstance("nat_channel_serial");


    public static AttributeKey<Map<Long, Channel>> MAPPING_CLIENT_CHANNELS = AttributeKey.newInstance("mapping_client_channels");
    public static AttributeKey<Set<Channel>> HEART_BEAT_CHANNEL = AttributeKey.newInstance("mapping_client_heartbeat_channel");
    public static AttributeKey<AtomicLong> SEQ = AttributeKey.newInstance("SEQ");

    public static AttributeKey<EchoTuningExtra> ECHO_TURNING_EXTRA = AttributeKey.newInstance("echo_tuning_extra");
    public static AttributeKey<String> ECHO_TRACE_ID = AttributeKey.newInstance("echo_trace_id");
}
