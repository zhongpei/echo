package com.virjar.echo.server.common;

public enum DownstreamServerType {
    http, // http/socks代理服务，用来组件代理服务器集群使用
    penetration;// 内网穿透服务，实现类似花生壳、frp等端口映射服务

    public static DownstreamServerType getByName(String name) {
        for (DownstreamServerType downstreamServerType : values()) {
            if (name.equals(downstreamServerType.name())) {
                return downstreamServerType;
            }
        }
        return http;
    }
}
