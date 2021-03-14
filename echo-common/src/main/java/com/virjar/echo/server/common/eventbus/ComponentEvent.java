package com.virjar.echo.server.common.eventbus;

import com.alibaba.fastjson.JSONObject;
import lombok.Data;

@Data
public class ComponentEvent {
    private String eventType;
    private JSONObject data;
    private String fromServerId;

    /**
     * 广播消息会被直接推送给其他所有节点
     */
    private boolean broadcast;

    public static final String TYPE_NAT_CLIENT_ONLINE = "nat_client_online";

    public static final String TYPE_NAT_CLIENT_OFFLINE = "nat_client_offline";

    public static final String TYPE_HTTP_PROXY_ONLINE = "h_proxy_online";

    public static final String TYPE_HTTP_PROXY_OFFLINE = "h_proxy_offline";

    public static final String TYPE_PENETRATION_UNBIND = "penetration_unbind";

    public static ComponentEvent createNatClientOnlineEvent(JSONObject msg) {
        ComponentEvent componentEvent = new ComponentEvent();
        componentEvent.eventType = TYPE_NAT_CLIENT_ONLINE;
        componentEvent.data = msg;
        componentEvent.broadcast = true;
        return componentEvent;
    }

    public static ComponentEvent createNatClientOfflineEvent(JSONObject msg) {
        ComponentEvent componentEvent = new ComponentEvent();
        componentEvent.eventType = TYPE_NAT_CLIENT_OFFLINE;
        componentEvent.data = msg;
        return componentEvent;
    }

    public static ComponentEvent createHProxyOnlineEvent(JSONObject msg) {
        ComponentEvent componentEvent = new ComponentEvent();
        componentEvent.eventType = TYPE_HTTP_PROXY_ONLINE;
        componentEvent.data = msg;
        return componentEvent;
    }

    public static ComponentEvent createHProxyOfflineEvent(JSONObject msg) {
        ComponentEvent componentEvent = new ComponentEvent();
        componentEvent.eventType = TYPE_HTTP_PROXY_OFFLINE;
        componentEvent.data = msg;
        return componentEvent;
    }

    public static ComponentEvent createPenetrationUnbindEvent(JSONObject msg) {
        ComponentEvent componentEvent = new ComponentEvent();
        componentEvent.eventType = TYPE_PENETRATION_UNBIND;
        componentEvent.data = msg;
        return componentEvent;
    }

}
