package com.virjar.echo.meta.server.controller;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.virjar.echo.meta.server.entity.CommonRes;
import com.virjar.echo.meta.server.service.EventBusService;
import com.virjar.echo.server.common.eventbus.ComponentEvent;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * 专门用于接受其他组件的消息，消息使用http推送过来，方便调试
 */
@RestController
@RequestMapping("/echoNatApi")
@Slf4j
public class EventBusController {

    @Resource
    private EventBusService eventBusService;

    @ApiOperation("接受其他slave组件推送的消息，主要包括：设备上线/下线、代理上线/下线")
    @PostMapping("/pushEvent")
    @ResponseBody
    public CommonRes<String> pushOnlineClient(@RequestBody String body, HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        log.info("pushOnlineClient from ip: {} body:{}", remoteAddr, body);
        JSONObject jsonObject = JSONObject.parseObject(body);

        JSONArray events = jsonObject.getJSONArray("events");

        for (int i = 0; i < events.size(); i++) {
            ComponentEvent componentEvent = events.getJSONObject(i).toJavaObject(ComponentEvent.class);
            eventBusService.receiveEvent(remoteAddr, componentEvent);
        }
        return CommonRes.success("ok");
    }
}
