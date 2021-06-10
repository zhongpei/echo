package com.virjar.echo.meta.server.controller;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.virjar.echo.meta.server.entity.AuthWhiteIp;
import com.virjar.echo.meta.server.entity.CommonRes;
import com.virjar.echo.meta.server.entity.UserInfo;
import com.virjar.echo.meta.server.intercept.LoginRequired;
import com.virjar.echo.meta.server.mapper.AuthWhiteIpMapper;
import com.virjar.echo.meta.server.utils.AppContext;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 出口ip白名单 前端控制器
 * </p>
 *
 * @author virjar
 * @since 2021-01-07
 */
@RestController
@RequestMapping("/echo-api/auth-white-ip")
public class AuthWhiteIpController {

    @Resource
    private AuthWhiteIpMapper authWhiteIpMapper;

    @LoginRequired
    @ApiOperation(value = "添加白名单")
    @GetMapping("/addWhiteIp")
    public CommonRes<AuthWhiteIp> addWhiteIp(String ip) {
        UserInfo user = AppContext.getUser();

        List<AuthWhiteIp> authWhiteIps = authWhiteIpMapper.selectList(new QueryWrapper<AuthWhiteIp>()
                .eq(AuthWhiteIp.BIND_ACCOUNT, user.getUserName()));
        if (authWhiteIps.size() > user.getAuthWhiteIpCount()) {
            //超过了配置限度
            return CommonRes.failed("config quota limited");
        }

        for (AuthWhiteIp authWhiteIp : authWhiteIps) {
            if (authWhiteIp.getWhiteIp().equals(ip)) {
                //已经存在
                return CommonRes.success(authWhiteIp);
            }
        }

        AuthWhiteIp insert = new AuthWhiteIp();
        insert.setBindAccount(user.getUserName());
        insert.setWhiteIp(ip);
        authWhiteIpMapper.insert(insert);
        return CommonRes.success(insert);
    }

    @LoginRequired
    @ApiOperation(value = "列出当前账户的白名单出口ip")
    @GetMapping("/listAuthWhiteIp")
    public CommonRes<List<AuthWhiteIp>> listAuthWhiteIp() {
        UserInfo user = AppContext.getUser();
        return CommonRes.success(authWhiteIpMapper.selectList(new QueryWrapper<AuthWhiteIp>()
                .eq(AuthWhiteIp.BIND_ACCOUNT, user.getUserName())));
    }

    @LoginRequired(forAdmin = true)
    @ApiOperation(value = "列出所有账户的白名单出口ip，管理员使用")
    @GetMapping("/listAllAuthWhiteIp")
    public CommonRes<IPage<AuthWhiteIp>> listAllAuthWhiteIp(
            int page, int pageSize) {
        if (pageSize > 50) {
            pageSize = 50;
        }


        return CommonRes.success(authWhiteIpMapper.selectPage(
                new Page<AuthWhiteIp>(page, pageSize), new QueryWrapper<>()
        ));
    }

    @LoginRequired
    @ApiOperation(value = "删除某个出口ip配置")
    @GetMapping("/deleteAuthWhiteIp")
    public CommonRes<String> deleteAuthWhiteIp(Long id) {
        UserInfo user = AppContext.getUser();
        AuthWhiteIp authWhiteIp = authWhiteIpMapper.selectById(id);
        if (authWhiteIp == null) {
            return CommonRes.failed("record not found");
        }
        if (!authWhiteIp.getBindAccount().equals(user.getUserName())
                && !user.getIsAdmin()
        ) {
            return CommonRes.failed("permission deny");
        }

        authWhiteIpMapper.deleteById(id);
        return CommonRes.success("ok");
    }

}
