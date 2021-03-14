package com.virjar.echo.server.common.eventbus;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import com.virjar.echo.server.common.SimpleHttpInvoker;
import com.virjar.echo.server.common.hserver.EchoHttpCommandServer;
import com.virjar.echo.server.common.hserver.HttpActionHandler;
import com.virjar.echo.server.common.hserver.NanoUtil;
import fi.iki.elonen.NanoHTTPD;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
public class EventBusManager {
    private final LinkedBlockingQueue<ComponentEvent> pushTaskList = new LinkedBlockingQueue<>();

    private final String serverId;
    private String apiEntry;
    private final boolean canWork;

    public EventBusManager(String serverId, String apiEntry) {
        this.serverId = serverId;
        this.apiEntry = apiEntry;
        if (StringUtils.isNotBlank(apiEntry) && apiEntry.trim().endsWith("/")) {
            this.apiEntry = apiEntry.trim();
            this.apiEntry = apiEntry.substring(0, apiEntry.length() - 1);
        }
        canWork = StringUtils.isNotBlank(apiEntry);
    }

    public void pushEvent(ComponentEvent event) {
        if (canWork) {
            pushTaskList.offer(event);
        }
    }

    private void doPushTask() throws InterruptedException {
        List<ComponentEvent> pendingTask = Lists.newLinkedList();
        ComponentEvent event;
        while ((event = pushTaskList.poll()) != null) {
            event.setFromServerId(serverId);
            pendingTask.add(event);
        }
        if (pendingTask.isEmpty()) {
            // blocking to wait  push task
            ComponentEvent take = pushTaskList.take();
            pushTaskList.offer(take);
            // just run next task loop
            return;
        }
        JSONObject pushBody = new JSONObject();
        JSONArray jsonArray = new JSONArray(pendingTask.size());
        pushBody.put("events", jsonArray);
        for (ComponentEvent it : pendingTask) {
            jsonArray.add(JSONObject.toJSONString(it));
        }
        log.info("push event to meta server  request: {}", pushBody);
        String response = SimpleHttpInvoker.post(apiEntry + "/echoNatApi/pushEvent", pushBody);
        log.info("push event to meta server response:{}", response);
    }

    private final Thread eventPushThread = new Thread("toMetaServerEventPush") {

        @Override
        public void run() {
            while (Thread.currentThread().isAlive()) {
                try {
                    doPushTask();
                } catch (Throwable throwable) {
                    log.error("push online device error", throwable);
                }
            }
        }
    };

    public EventBusManager startup() {
        if (!canWork) {
            return this;
        }
        if (eventPushThread.isAlive()) {
            return this;
        }
        eventPushThread.setDaemon(true);
        eventPushThread.start();
        return this;
    }

    private class HServerHandler implements HttpActionHandler {

        @Override
        public JSONObject handle(NanoHTTPD.IHTTPSession httpSession) {
            if (eventHandlers.isEmpty()) {
                return NanoUtil.success("no handler");
            }
            try (InputStream inputStream = httpSession.getInputStream()) {
                String jsonContent = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
                ComponentEvent componentEvent = JSONObject.parseObject(jsonContent).toJavaObject(ComponentEvent.class);
                for (EventHandler eventHandler : eventHandlers) {
                    eventHandler.handleEvent(componentEvent);
                }
            } catch (Exception e) {
                log.error("error", e);
                return NanoUtil.failed(-1, "handle event bus error:" + e.getMessage());
            }

            return NanoUtil.success("ok");
        }
    }

    public EventBusManager enableEventReceiveService(EchoHttpCommandServer echoHttpCommandServer) {
        echoHttpCommandServer.registerHandler("/broadcastEvent", new HServerHandler());
        return this;
    }

    private final List<EventHandler> eventHandlers = Lists.newCopyOnWriteArrayList();

    public EventBusManager registerEventHandler(EventHandler eventHandler) {
        eventHandlers.add(eventHandler);
        return this;
    }
}
