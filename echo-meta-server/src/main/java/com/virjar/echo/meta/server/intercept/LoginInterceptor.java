package com.virjar.echo.meta.server.intercept;

import com.alibaba.fastjson.JSONObject;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.virjar.echo.meta.server.entity.CommonRes;
import com.virjar.echo.meta.server.entity.UserInfo;
import com.virjar.echo.meta.server.service.UserInfoService;
import com.virjar.echo.meta.server.utils.AppContext;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.util.List;


@Component
public class LoginInterceptor implements HandlerInterceptor {
    @Resource
    private UserInfoService userInfoService;

    private static final byte[] needLoginResponse = JSONObject.toJSONString(CommonRes.failed("请登录后访问")).getBytes(Charsets.UTF_8);
    private static final byte[] loginExpire = JSONObject.toJSONString(CommonRes.failed("请重新登录")).getBytes(Charsets.UTF_8);
    private static final byte[] onlyForAdminResponse = JSONObject.toJSONString(CommonRes.failed("非管理员")).getBytes(Charsets.UTF_8);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        Method method = ((HandlerMethod) handler).getMethod();
        LoginRequired loginRequired = method.getAnnotation(LoginRequired.class);

        //不需要登陆
        if (loginRequired == null) {
            return true;
        }

        List<String> tokenList = Lists.newArrayList();

        // header 不区分大小写
        String operatorToken = request.getHeader("Token");
        if (StringUtils.isNotBlank(operatorToken)) {
            tokenList.add(operatorToken);
        }
        operatorToken = request.getParameter("Token");
        if (StringUtils.isNotBlank(operatorToken)) {
            tokenList.add(operatorToken);
        }
        operatorToken = request.getParameter("token");
        if (StringUtils.isNotBlank(operatorToken)) {
            tokenList.add(operatorToken);
        }

        if (tokenList.isEmpty()) {
            response.addHeader("content-type", "application/json; charset=utf-8");
            response.getOutputStream().write(needLoginResponse);
            return false;
        }

        CommonRes<UserInfo> result = userInfoService.checkLogin(tokenList);
        if (!result.isOk()) {
            //如果这个接口允许api token访问，那么直接允许，并且使用对应的token所在账户身份
            if (loginRequired.apiToken()) {
                result = userInfoService.checkAPIToken(tokenList);
            }
            if (!result.isOk()) {
                response.addHeader("content-type", "application/json; charset=utf-8");
                response.getOutputStream().write(loginExpire);
                return false;
            }
        }
        if (loginRequired.forAdmin()) {
            if (!BooleanUtils.isTrue(result.getData().getIsAdmin())) {
                response.addHeader("content-type", "application/json; charset=utf-8");
                response.getOutputStream().write(onlyForAdminResponse);
                return false;
            }
        }
        AppContext.setUser(result.getData());
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        AppContext.removeUser();
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {

    }

}
