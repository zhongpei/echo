package com.virjar.echo.nat.protocol;

public class PacketCommon {
    /// 编码相关
    static final int TYPE_SIZE = 1;

    static final int SERIAL_NUMBER_SIZE = 8;

    static final int EXTRA_LENGTH_SIZE = 1;

    static final int TRACE_ID_LENGTH_SIZE = 1;

    static final String magicString = "echo.001";

    // magic is the hexData of 'magicString'
    static final long magic = 0x6563686F2E303031L;

    //单个报文，最大1M，超过1M需要分段
    public static final int MAX_FRAME_LENGTH = 5 * 1024;

    //心跳相关

    public static int READ_IDLE_TIME = 30;

    public static int WRITE_IDLE_TIME = 5;


    /// 消息类型相关
    public static String getReadableType(byte type) {
        switch (type) {
            case C_TYPE_REGISTER:
                return "C_TYPE_REGISTER";
            case TYPE_HEARTBEAT:
                return "TYPE_HEARTBEAT";
            case TYPE_CONNECT:
                return "TYPE_CONNECT";
            case TYPE_DISCONNECT:
                return "TYPE_DISCONNECT";
            case TYPE_TRANSFER:
                return "TYPE_TRANSFER";
            case C_TYPE_CONTROL:
                return "C_TYPE_CONTROL";
            case TYPE_CONNECT_READY:
                return "TYPE_CONNECT_READY";
            case TYPE_DESTROY:
                return "TYPE_DESTROY";
            default:
                return "UNKNOWN:" + type;
        }
    }

    /**
     * 客户端连接服务器，将会带有客户端id
     */
    public static final byte C_TYPE_REGISTER = 0x01;


    /**
     * 心跳消息
     */
    public static final byte TYPE_HEARTBEAT = 0x02;

    /**
     * 代理请求连接建立，需要在 http proxy server建立channel。该消息仅发生在nat server-> nat client
     */
    public static final byte TYPE_CONNECT = 0x03;


    /**
     * 代理端主动关闭连接，将会关闭对应channel。该消息仅发生在 nat client -> nat server
     */
    public static final byte TYPE_DISCONNECT = 0x04;

    /**
     * 数据传输，消息需要带流水号
     */
    public static final byte TYPE_TRANSFER = 0x05;

    /**
     * 控制命令，目前没有使用。用来执行一些在手机本地执行的命令。如控制飞行模式切换，实现重播等
     */
    public static final byte C_TYPE_CONTROL = 0x06;


    /**
     * 新的链接建立成功后，会给服务器回一个信息。预示着从客户端到真正的server之间的tcp链接真正建立完成
     */
    public static final byte TYPE_CONNECT_READY = 0x07;

    // 销毁隧道
    public static final byte TYPE_DESTROY = 0x08;
}
