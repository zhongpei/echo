package com.virjar.echo.meta.server.controller;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.virjar.echo.meta.server.entity.AuthWhiteIp;
import com.virjar.echo.meta.server.entity.CommonRes;
import com.virjar.echo.meta.server.entity.UserInfo;
import com.virjar.echo.meta.server.mapper.AuthWhiteIpMapper;
import com.virjar.echo.meta.server.mapper.UserInfoMapper;
import com.virjar.echo.meta.server.service.MetaResourceService;
import com.virjar.echo.server.common.NatUpstreamMeta;
import com.virjar.echo.server.common.auth.AuthenticateAccountInfo;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * echo内部系统使用的接口
 * TODO 这些接口应该配置内部鉴权token，否则源码泄露后有可能被外部任意得到配置
 */
@RestController
@RequestMapping("/echoNatApi")
@Slf4j
public class EchoInnerResourceController {


    @Resource
    private MetaResourceService metaResourceService;

    @Resource
    private UserInfoMapper userInfoMapper;

    @Resource
    private AuthWhiteIpMapper authWhiteIpMapper;


    @ApiOperation("分布式集群环境下，downstream使用这个接口替换echoNatMapping接口，并通过本接口挑选服务器实现负载均衡")
    @GetMapping("/connectionList")
    @ResponseBody
    public Map<String, Object> connectionList(@RequestParam(required = false) String serverId) {
        if (StringUtils.isBlank(serverId)) {
            serverId = "default";
        }

        //{"clients":[{"listenIp":"127.0.0.1","clientId":"clientId-virjar","port":24577},{"listenIp":"127.0.0.1","clientId":"clientId-virjar-test","port":24576}]}
        List<NatUpstreamMeta> natUpstreamMetas = metaResourceService.allocateUpstreamMetaForServer(serverId);
        Map<String, Object> ret = Maps.newHashMap();
        ret.put("clients", natUpstreamMetas);
        log.info("connectionList allocate result: {}", JSONObject.toJSONString(ret));
        return ret;
    }


    @ApiOperation("下载鉴配置,给各个proxy节点执行这些鉴权策略使用，本接口应该五分钟级别同步。而不应该高频访问，参考代理云配置规则")
    @GetMapping("/syncAuthConfig")
    @ResponseBody
    public CommonRes<List<AuthenticateAccountInfo>> syncAuthConfig() {

        List<UserInfo> userInfos = userInfoMapper.selectList(null);
        if (userInfos.isEmpty()) {
            return CommonRes.success(Collections.emptyList());
        }

        List<AuthenticateAccountInfo> ret = Lists.newArrayListWithExpectedSize(userInfos.size());
        for (UserInfo userInfo : userInfos) {
            AuthenticateAccountInfo accountConfigBean = new AuthenticateAccountInfo();

            accountConfigBean.setAccount(userInfo.getUserName());
            accountConfigBean.setAuthAccount(userInfo.getAuthAccount());
            accountConfigBean.setAuthPwd(userInfo.getAuthPwd());
            accountConfigBean.setAdmin(userInfo.getIsAdmin());
            accountConfigBean.setMaxQps(userInfo.getQpsQuota());
            accountConfigBean.setShared(userInfo.getShared());
            if (userInfo.getAuthWhiteIpCount() > 0) {
                List<AuthWhiteIp> authWhiteIps = authWhiteIpMapper.selectList(new QueryWrapper<AuthWhiteIp>().eq(AuthWhiteIp.BIND_ACCOUNT, userInfo.getUserName()).last("limit " + userInfo.getAuthWhiteIpCount()));
                accountConfigBean.setOutWhiteIp(authWhiteIps.stream().map(AuthWhiteIp::getWhiteIp).collect(Collectors.toList()));
            }
            if (isValidAuthConfig(accountConfigBean)) {
                // 如果没有配置鉴权数据，那么不skip配置
                ret.add(accountConfigBean);
            }
        }
        return CommonRes.success(ret);
    }

    private boolean isValidAuthConfig(AuthenticateAccountInfo accountConfigBean) {
        if (!accountConfigBean.getOutWhiteIp().isEmpty()) {
            return true;
        }
        if (StringUtils.isNotBlank(accountConfigBean.getAuthAccount())
                && StringUtils.isNotBlank(accountConfigBean.getAuthPwd())
        ) {
            return true;
        }
        if (accountConfigBean.isAdmin()) {
            return true;
        }
        return false;
    }

    //TODO 个节点上报监控数据，包括流量，并发数量等
}
