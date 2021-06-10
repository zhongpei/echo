package com.virjar.echo.meta.server.controller;


import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.virjar.echo.meta.server.entity.CommonRes;
import com.virjar.echo.meta.server.entity.NatMappingServer;
import com.virjar.echo.meta.server.intercept.LoginRequired;
import com.virjar.echo.meta.server.mapper.NatMappingServerMapper;
import com.virjar.echo.meta.server.service.MetaResourceService;
import com.virjar.echo.meta.server.utils.AppContext;
import com.virjar.echo.meta.server.utils.IPUtils;
import com.virjar.echo.nat.bootstrap.IOUtils;
import com.virjar.echo.server.common.SimpleHttpInvoker;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;

/**
 * <p>
 * mapping server,提供客户端网络mapping到同一台服务器的功能 前端控制器
 * </p>
 *
 * @author virjar
 * @since 2021-01-07
 */
@RestController
@Slf4j
@RequestMapping("/echo-api/nat-mapping-server")
public class NatMappingServerController {

    @Resource
    private NatMappingServerMapper natMappingServerMapper;

    @Resource
    private MetaResourceService metaResourceService;

    @GetMapping("/setNatMappingServerStatus")
    @LoginRequired(forAdmin = true)
    @ResponseBody
    public CommonRes<String> setNatMappingServerStatus(Long id, boolean enabled) {
        NatMappingServer downstreamServer = natMappingServerMapper.selectById(id);
        if (downstreamServer == null) {
            return CommonRes.failed("record not found!!");
        }
        downstreamServer.setEnabled(enabled);
        natMappingServerMapper.updateById(downstreamServer);
        return CommonRes.success("ok");
    }

    @GetMapping("/listNatMappingServer")
    @LoginRequired(forAdmin = true)
    @ResponseBody
    public CommonRes<IPage<NatMappingServer>> listNatMappingServer(
            int page, int pageSize
    ) {
        if (pageSize > 50) {
            pageSize = 50;
        }
        return CommonRes.success(natMappingServerMapper.selectPage(
                new Page<>(page, pageSize), new QueryWrapper<>()
        ));
    }


    @GetMapping("/addNatMappingServer")
    @LoginRequired(forAdmin = true)
    @ResponseBody
    public CommonRes<NatMappingServer> addNatMappingServer(String apiUrl) {
        apiUrl = apiUrl.trim();
        log.info("add NatMapping server url:{} ", apiUrl);
        try {
            new URL(apiUrl);
        } catch (MalformedURLException e) {
            return CommonRes.failed("illegal url: " + e.getMessage());
        }

        NatMappingServer one = natMappingServerMapper.selectOne(
                new QueryWrapper<NatMappingServer>().eq(NatMappingServer.API_BASE_URL, apiUrl)
                        .last("limit 1")
        );

        if (one == null) {
            one = new NatMappingServer();
            one.setApiBaseUrl(apiUrl.trim());
        }


        if (one.getId() == null) {
            natMappingServerMapper.insert(one);
            metaResourceService.heartBeatSchedule();
        } else {
            natMappingServerMapper.updateById(one);
        }

        return CommonRes.success(one);
    }

    @GetMapping("/allocateNatMappingServer")
    @LoginRequired(apiToken = true)
    @ResponseBody
    public String allocateNatMappingServer(String clientId) {
        List<NatMappingServer> natMappingServers = metaResourceService.allocateNatMappingServerForClient(
                AppContext.getUser().getUserName() + clientId
        );
        log.info("allocate result: {}", JSONObject.toJSONString(natMappingServers));
        if (natMappingServers.isEmpty()) {
            return "";
        }
        StringBuilder ret = new StringBuilder();

        for (NatMappingServer natMappingServer : natMappingServers) {
            try {
                String host = new URL(natMappingServer.getApiBaseUrl()).getHost();
                ret.append(host).append(":").append(natMappingServer.getNatPort())
                        .append("\n");
            } catch (MalformedURLException e) {
                log.error("error url", e);
            }
        }
        return ret.toString();
    }

    @GetMapping("/sendCommandToClient")
    @LoginRequired(apiToken = true)
    @ResponseBody
    public CommonRes<?> sendCommandToClient(String clientId, String action, String additionParam) {
        List<NatMappingServer> natMappingServers = metaResourceService.allocateNatMappingServerForClient(
                AppContext.getUser().getUserName() + clientId
        );
        if (natMappingServers.isEmpty()) {
            return CommonRes.failed("no nat server online");
        }
        String failedMessage = "failed";
        for (NatMappingServer natMappingServer : natMappingServers) {
            String url = IPUtils.genURL(natMappingServer.getApiBaseUrl(), "/echoNatApi/sendCmd");

            //clientId  action additionParam
            url += "?clientId=" + URLEncoder.encode(clientId) + "&action="
                    + URLEncoder.encode(action) + "&addition=" + URLEncoder.encode(StringUtils.trimToEmpty(additionParam));
            try {
                log.info("sendCommandToClient invoke with url: {}", url);
                String s = SimpleHttpInvoker.get(url);
                log.info("response: {}", s);
                if (StringUtils.isBlank(s)) {
                    continue;
                }
                JSONObject jsonObject = JSONObject.parseObject(s);
                Integer status = jsonObject.getInteger("status");
                if (status != null && status == 0) {
                    return CommonRes.success(jsonObject.get("data"));
                }
                failedMessage = jsonObject.getString("msg");

            } catch (JSONException e) {
                //ignore
            }
        }
        return CommonRes.failed(failedMessage);
    }
}
