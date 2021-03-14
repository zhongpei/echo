package com.virjar.echo.server.common;

import lombok.Data;

import java.util.Objects;

@Data
public class NatUpstreamMeta {
    private String listenIp;
    private String clientId;
    private int port;
    private String clientAccount;
    private boolean shared;
    private String clientOutIp;


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NatUpstreamMeta that = (NatUpstreamMeta) o;
        return Objects.equals(clientId, that.clientId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clientId);
    }

}
