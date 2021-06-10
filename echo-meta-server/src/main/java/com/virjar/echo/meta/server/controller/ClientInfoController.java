package com.virjar.echo.meta.server.controller;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.virjar.echo.meta.server.entity.ClientInfo;
import com.virjar.echo.meta.server.entity.CommonRes;
import com.virjar.echo.meta.server.entity.UserInfo;
import com.virjar.echo.meta.server.intercept.LoginRequired;
import com.virjar.echo.meta.server.mapper.ClientInfoMapper;
import com.virjar.echo.meta.server.utils.AppContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * <p>
 * 接入到平台的终端 前端控制器
 * </p>
 *
 * @author virjar
 * @since 2021-01-07
 */
@RestController
@RequestMapping("/echo-api/client-info")
public class ClientInfoController {

    @Resource
    private ClientInfoMapper clientInfoMapper;

    @LoginRequired(apiToken = true)
    @GetMapping("/listClientInfo")
    @ResponseBody
    public CommonRes<IPage<ClientInfo>> listClientInfo(int page, int pageSize) {
        if (pageSize > 50) {
            pageSize = 50;
        }
        QueryWrapper<ClientInfo> queryWrapper = new QueryWrapper<>();
        UserInfo user = AppContext.getUser();
        if (!user.getIsAdmin()) {
            queryWrapper.eq(ClientInfo.BIND_ACCOUNT, user.getUserName());
        }

        return CommonRes.success(clientInfoMapper.selectPage(
                new Page<ClientInfo>(page, pageSize), queryWrapper
        ));
    }

}
