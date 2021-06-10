package com.virjar.echo.meta.server.controller;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.virjar.echo.meta.server.entity.CommonRes;
import com.virjar.echo.meta.server.entity.ProxyResource;
import com.virjar.echo.meta.server.entity.UserInfo;
import com.virjar.echo.meta.server.intercept.LoginRequired;
import com.virjar.echo.meta.server.mapper.ProxyResourceMapper;
import com.virjar.echo.meta.server.service.ProxyResourceService;
import com.virjar.echo.meta.server.utils.AppContext;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 代理ip资源 前端控制器
 * </p>
 *
 * @author virjar
 * @since 2021-01-07
 */
@RestController
@RequestMapping("/echo-api/proxy-resource")
public class ProxyResourceController {

    @Resource
    private ProxyResourceMapper proxyResourceMapper;

    @Resource
    private ProxyResourceService proxyResourceService;

    @LoginRequired(apiToken = true)
    @GetMapping("/getProxy")
    @ResponseBody
    public CommonRes<IPage<ProxyResource>> getProxy(int page, int pageSize) {
        if (pageSize > 50) {
            pageSize = 50;
        }
        QueryWrapper<ProxyResource> queryWrapper = new QueryWrapper<>();
        UserInfo user = AppContext.getUser();
        if (!user.getIsAdmin()) {
            queryWrapper.eq(ProxyResource.BIND_ACCOUNT, user.getUserName());
        }
        return CommonRes.success(proxyResourceMapper.selectPage(
                new Page<ProxyResource>(page, pageSize), queryWrapper
        ));
    }

    @ApiOperation("查询一个终端出口的映射ip，主要给移动端使用")
    @LoginRequired(apiToken = true)
    @GetMapping("/clientMappingProxy")
    @ResponseBody
    public CommonRes<List<ProxyResource>> clientMappingProxy(String clientId) {
        UserInfo user = AppContext.getUser();
        if (StringUtils.isBlank(clientId)) {
            return CommonRes.failed("need param:{clientId}");
        }
        if (!clientId.contains("|@--@--@|")) {
            clientId = user.getUserName() + "|@--@--@|" + clientId;
        }

        return CommonRes.success(proxyResourceMapper.selectList(
                new QueryWrapper<ProxyResource>()
                        .eq(ProxyResource.CLIENT_ID, clientId)
        ));
    }

    @LoginRequired(apiToken = true)
    @GetMapping("/query")
    @ResponseBody
    public Object query(boolean random, Integer size, boolean detail) {
        if (size == null) {
            size = 0;
        }
        if (size < 1) {
            size = 1;
        } else if (size > 100) {
            size = 100;
        }

        return proxyResourceService.queryFetch(random, size, detail);
    }


}
