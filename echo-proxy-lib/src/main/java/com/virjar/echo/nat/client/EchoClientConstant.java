package com.virjar.echo.nat.client;

import io.netty.util.AttributeKey;

public class EchoClientConstant {

    public static AttributeKey<Long> SERIAL_NUM = AttributeKey.newInstance("connection_serial");

    public static final String ECHO_ENCODER = "echo-encoder";
    public static final String ECHO_DECODER = "echo-decoder";
    public static final String ECHO_IDLE = "echo-idle";
    public static final String ECHO_NAT = "echo-nat";
    public static AttributeKey<String> ECHO_TRACE_ID = AttributeKey.newInstance("echo_trace_id");


}
