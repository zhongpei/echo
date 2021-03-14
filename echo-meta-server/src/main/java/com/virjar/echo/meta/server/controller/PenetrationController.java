package com.virjar.echo.meta.server.controller;


import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.virjar.echo.meta.server.entity.CommonRes;
import com.virjar.echo.meta.server.entity.DownstreamServer;
import com.virjar.echo.meta.server.entity.Penetration;
import com.virjar.echo.meta.server.intercept.LoginRequired;
import com.virjar.echo.meta.server.mapper.DownstreamServerMapper;
import com.virjar.echo.meta.server.mapper.PenetrationMapper;
import com.virjar.echo.meta.server.service.EventBusService;
import com.virjar.echo.meta.server.service.PenetrationService;
import com.virjar.echo.meta.server.utils.AppContext;
import com.virjar.echo.server.common.DownstreamServerType;
import com.virjar.echo.server.common.eventbus.ComponentEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 端口映射配置 前端控制器
 * </p>
 *
 * @author virjar
 * @since 2021-03-08
 */
@Slf4j
@RestController
@RequestMapping("/echo-api/penetration")
public class PenetrationController {

    @Resource
    private PenetrationService penetrationService;

    @Resource
    private PenetrationMapper penetrationMapper;

    @Resource
    private DownstreamServerMapper downstreamServerMapper;

    @Resource
    private EventBusService eventBusService;

    @GetMapping("/allocatePenetrationMappingTask")
    @ResponseBody
    public CommonRes<List<Penetration>> allocatePenetrationMappingTask(String penetrationServerId, int size) {
        DownstreamServer downstreamServer = downstreamServerMapper.selectOne(new QueryWrapper<DownstreamServer>()
                .eq(DownstreamServer.SERVER_ID, penetrationServerId)
                .last("limit 1")
        );
        if (downstreamServer == null) {
            return CommonRes.failed("the server :" + penetrationServerId + " not exist");
        }
        if (!StringUtils.equals(downstreamServer.getServerType(),
                DownstreamServerType.penetration.name())) {
            return CommonRes.failed("request server not penetration server");
        }

        // 一个服务器一次最多拉取32个配置
        if (size < 0) {
            size = 1;
        }
        if (size > 32) {
            size = 32;
        }
        return penetrationService.allocatePenetrationMappingTask(downstreamServer, size);
    }

    @GetMapping("/rebind")
    @LoginRequired(apiToken = true)
    @ResponseBody
    public CommonRes<Penetration> rebind(Long id) {
        if (id == null || id < 0) {
            return CommonRes.failed("need penetration Id param");
        }
        Penetration penetration = penetrationMapper.selectById(id);
        if (penetration == null) {
            return CommonRes.failed("record not exist");
        }
        if (!StringUtils.equals(penetration.getBindAccount(),
                AppContext.getUser().getUserName())) {
            return CommonRes.failed("no permission");
        }

        if (!penetration.getOnline()) {
            //本来就还没有绑定关系
            return CommonRes.success(penetration);
        }

        penetration.setOnline(false);
        penetrationMapper.updateById(penetration);

        eventBusService.broadcast(ComponentEvent.createPenetrationUnbindEvent(
                (JSONObject) JSONObject.toJSON(penetration)
        ));

        return CommonRes.success(penetration);
    }


    @GetMapping("/addPenetration")
    @LoginRequired(apiToken = true)
    @ResponseBody
    public CommonRes<Penetration> addPenetration(
            Long clientId, String remoteIp, Integer remotePort,
            Boolean rebind, String comment
    ) {
        if (clientId == null) {
            return CommonRes.failed("need param:{clientId}");
        }

        if (StringUtils.isBlank(remoteIp) || remotePort == null) {
            return CommonRes.failed("need param:{remoteIp:remotePort}");
        }

        if (rebind == null) {
            rebind = false;
        }
        if (StringUtils.isBlank(comment)) {
            comment = "";
        }

        return penetrationService.add(AppContext.getUser(), clientId, remoteIp, remotePort, rebind, comment);
    }
}
