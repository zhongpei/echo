package com.virjar.echo.meta.server.controller;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.virjar.echo.meta.server.entity.CommonRes;
import com.virjar.echo.meta.server.entity.UserInfo;
import com.virjar.echo.meta.server.intercept.LoginRequired;
import com.virjar.echo.meta.server.service.UserInfoService;
import com.virjar.echo.meta.server.utils.AppContext;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Date;
import java.util.UUID;

/**
 * <p>
 * 用户信息 前端控制器
 * </p>
 *
 * @author virjar
 * @since 2021-01-07
 */
@RestController
@RequestMapping("/echo-api/user-info")
public class UserInfoController {
    @Resource
    private UserInfoService userInfoService;


    @ApiOperation(value = "登陆")
    @RequestMapping(value = "/login", method = RequestMethod.POST)
    public CommonRes<UserInfo> login(String userName, String password) {
        return userInfoService.login(userName, password);
    }

    @ApiOperation(value = "登陆")
    @RequestMapping(value = "/getLogin", method = RequestMethod.GET)
    public CommonRes<UserInfo> getLogin(String userName, String password) {
        return userInfoService.login(userName, password);
    }


    @ApiOperation(value = "注册")
    @RequestMapping(value = "/register", method = RequestMethod.POST)
    public CommonRes<UserInfo> register(String userName, String password) {
        return userInfoService.register(userName, password);
    }

    @LoginRequired
    @ApiOperation(value = "当前用户信息")
    @RequestMapping(value = "/userInfo", method = RequestMethod.GET)
    public CommonRes<UserInfo> userInfo() {
        return CommonRes.success(AppContext.getUser());
    }

    @LoginRequired
    @ApiOperation(value = "刷新当前用户token")
    @GetMapping(value = "/refreshToken")
    public CommonRes<String> refreshToken() {
        String newToken = userInfoService.refreshToken(AppContext.getUser().getLoginToken());
        if (newToken == null) {
            return CommonRes.failed(CommonRes.statusLoginExpire, "请重新登陆");
        }
        return CommonRes.success(newToken);
    }


    @LoginRequired
    @ApiOperation(value = "设置当前用户的鉴权账户数据")
    @GetMapping("/setupAuthAccount")
    public CommonRes<UserInfo> setupAuthAccount(String authAccount, String authPassword) {
        UserInfo one = userInfoService.getOne(new QueryWrapper<UserInfo>()
                .eq(UserInfo.AUTH_ACCOUNT, authAccount)
        );
        UserInfo mUser = AppContext.getUser();
        if (one != null && !one.getId().equals(mUser.getId())) {
            return CommonRes.failed("the  authAccount: " + authAccount + " already exist");
        }

        mUser.setAuthAccount(authAccount);
        mUser.setAuthPwd(authPassword);

        userInfoService.updateById(mUser);
        return CommonRes.success(mUser);
    }


    @LoginRequired
    @ApiOperation(value = "重新生产api访问的token")
    @GetMapping("/regenerateAPIToken")
    public CommonRes<UserInfo> regenerateAPIToken() {
        UserInfo mUser = AppContext.getUser();
        mUser.setApiToken(UUID.randomUUID().toString());
        userInfoService.updateById(mUser);
        return CommonRes.success(mUser);
    }


    @ApiOperation(value = "(管理员专用)管理员穿越到普通用户，获取普通用户token")
    @LoginRequired(forAdmin = true)
    @GetMapping("/travelToUser")
    public CommonRes<UserInfo> travelToUser(Long id) {
        UserInfo toUser = userInfoService.getById(id);
        if (toUser == null) {
            return CommonRes.failed("user not exist");
        }
        toUser.setLoginToken(userInfoService.genLoginToken(toUser, new Date()));
        return CommonRes.success(toUser);
    }

    @ApiOperation(value = "(管理员专用)用户列表")
    @LoginRequired(forAdmin = true)
    @GetMapping("/listUser")
    public CommonRes<IPage<UserInfo>> listUser(int page, int pageSize) {
//        if (pageSize > 50) {
//            pageSize = 50;
//        }
        if (page < 1) {
            page = 1;
        }
        return CommonRes.success(userInfoService.page(new Page<>(page, pageSize)));
    }

    @ApiOperation(value = "(管理员专用)创建用户")
    @LoginRequired(forAdmin = true)
    @GetMapping("/createUser")
    public CommonRes<UserInfo> createUser(String userName, String password) {
        return userInfoService.register(userName, password);
    }

    @ApiOperation(value = "(管理员专用)设置各项quota")
    @ApiParam(name = "qpsQuota", value = "单台服务器的qps，qps可以为小数")
    @LoginRequired(forAdmin = true)
    @GetMapping("/setupQuota")
    public CommonRes<UserInfo> setupQuota(Long userId,
                                          Integer registerQuota,
                                          Integer authWhiteIpCount,
                                          Integer userQuota,
                                          Double qpsQuota
    ) {
        UserInfo userInfo = userInfoService.getById(userId);
        if (userId == null) {
            return CommonRes.failed("user not exist");
        }

        if (registerQuota != null) {
            userInfo.setRegisterQuota(registerQuota);
        }
        if (authWhiteIpCount != null) {
            userInfo.setAuthWhiteIpCount(authWhiteIpCount);
        }

        if (userQuota != null) {
            userInfo.setUserQuota(userQuota);
        }
        if (qpsQuota != null) {
            userInfo.setQpsQuota(qpsQuota);
        }
        userInfoService.updateById(userInfo);
        return CommonRes.success(userInfo);
    }
}
