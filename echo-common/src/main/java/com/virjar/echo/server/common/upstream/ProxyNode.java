package com.virjar.echo.server.common.upstream;

import com.alibaba.fastjson.JSONObject;
import com.virjar.echo.server.common.NatUpstreamMeta;
import io.netty.channel.Channel;
import lombok.Data;

/**
 * 描述一个代理链接隧道,
 * 代理端口-> 代理serverChannel对象->upstream host:port -> 客户端id
 */
@Data
public class ProxyNode {
    private NatUpstreamMeta natUpstreamMeta;
    private String clientId;
    private Integer proxyPort;
    private Channel proxyServerChannel;
    private Channel natMappingHealthChannel;

    private long createTimestamp;

    public JSONObject toVo() {
        JSONObject additionInfoJson = new JSONObject();
        additionInfoJson.put("clientId", getClientId());
        additionInfoJson.put("proxyPort", getProxyPort());
        additionInfoJson.put("createTimestamp", getCreateTimestamp());
        return additionInfoJson;
    }
}
