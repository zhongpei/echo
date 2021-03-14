package com.virjar.echo.nat.protocol;

import lombok.Data;

@Data
public class EchoPacket {
    /**
     * 消息类型
     */
    private byte type;

    /**
     * 消息流水号
     */
    private long serialNumber;

    /**
     * 额外参数，其含义在不同的消息类型下，有不同的作用
     */
    private String extra;

    /**
     * 数据溯源字段
     */
    private String traceID;

    /**
     * 消息传输数据
     */
    private byte[] data;
}
