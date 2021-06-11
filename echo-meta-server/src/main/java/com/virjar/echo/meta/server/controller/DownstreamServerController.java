package com.virjar.echo.meta.server.controller;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.virjar.echo.meta.server.entity.CommonRes;
import com.virjar.echo.meta.server.entity.DownstreamServer;
import com.virjar.echo.meta.server.intercept.LoginRequired;
import com.virjar.echo.meta.server.mapper.DownstreamServerMapper;
import com.virjar.echo.meta.server.service.MetaResourceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * <p>
 * 下游服务，比如http代理协议实现 前端控制器
 * </p>
 *
 * @author virjar
 * @since 2021-01-07
 */
@RestController
@RequestMapping("/echo-api/downstream-server")
@Slf4j
public class DownstreamServerController {

    @Resource
    private DownstreamServerMapper downstreamServerMapper;

    @Resource
    private MetaResourceService metaResourceService;

    @GetMapping("/setResourceStatus")
    @LoginRequired(forAdmin = true)
    @ResponseBody
    public CommonRes<String> setResourceStatus(Long id, boolean enabled) {
        DownstreamServer downstreamServer = downstreamServerMapper.selectById(id);
        if (downstreamServer == null) {
            return CommonRes.failed("record not found!!");
        }
        downstreamServer.setEnabled(enabled);
        downstreamServerMapper.updateById(downstreamServer);
        return CommonRes.success("ok");
    }

    @GetMapping("/listDownstreamServer")
    @LoginRequired(forAdmin = true)
    @ResponseBody
    public CommonRes<IPage<DownstreamServer>> listDownstreamServer(
            int page, int pageSize
    ) {
        if (pageSize > 50) {
            pageSize = 50;
        }
        return CommonRes.success(downstreamServerMapper.selectPage(
                new Page<>(page, pageSize), new QueryWrapper<>()
        ));
    }


    @GetMapping("/addDownstreamServer")
    @LoginRequired(forAdmin = true)
    @ResponseBody
    public CommonRes<DownstreamServer> addDownstreamServer(String apiUrl) {
        log.info("add Downstream server url:{}", apiUrl);
        try {
            new URL(apiUrl);
        } catch (MalformedURLException e) {
            return CommonRes.failed("illegal url: " + e.getMessage());
        }

        DownstreamServer one = downstreamServerMapper.selectOne(
                new QueryWrapper<DownstreamServer>().eq(DownstreamServer.API_BASE_URL, apiUrl)
                        .last("limit 1")
        );
        if (one == null) {
            one = new DownstreamServer();
            one.setApiBaseUrl(apiUrl.trim());
        }

        if (one.getId() == null) {
            downstreamServerMapper.insert(one);
            metaResourceService.heartBeatSchedule();
        } else {
            downstreamServerMapper.updateById(one);
        }
        return CommonRes.success(one);
    }
}
