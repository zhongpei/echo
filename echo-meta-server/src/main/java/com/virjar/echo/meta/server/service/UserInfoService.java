package com.virjar.echo.meta.server.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.virjar.echo.meta.server.entity.CommonRes;
import com.virjar.echo.meta.server.entity.UserInfo;
import com.virjar.echo.meta.server.mapper.UserInfoMapper;
import com.virjar.echo.meta.server.utils.AppContext;
import com.virjar.echo.meta.server.utils.Md5Util;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * <p>
 * 用户信息 服务实现类
 * </p>
 *
 * @author virjar
 * @since 2021-01-07
 */
@Service
@Slf4j
public class UserInfoService extends ServiceImpl<UserInfoMapper, UserInfo> implements IService<UserInfo> {
    @Resource
    private UserInfoMapper userMapper;

    private static final String salt = "echo2021&^!@(";

    public CommonRes<UserInfo> login(String account, String password) {
        UserInfo userInfo = userMapper.selectOne(
                new QueryWrapper<UserInfo>().eq(UserInfo.USER_NAME, account)
                        .last("limit 1")
        );
        if (userInfo == null) {
            return CommonRes.failed("请检查用户名或密码");
        }
        if (!StringUtils.equals(userInfo.getPasswd(), password)) {
            return CommonRes.failed("请检查用户名或密码");
        }

        userInfo.setLoginToken(genLoginToken(userInfo, new Date()));
        userMapper.updateById(userInfo);
        return CommonRes.success(userInfo);
    }

    public String refreshToken(String oldToken) {
        UserInfo userInfo = getUserInfoFromToken(oldToken);
        if (userInfo == null) {
            //token不合法
            return null;
        }
        if (isRightToken(oldToken, userInfo)) {
            return genLoginToken(userInfo, new Date());
        }

        return null;
    }

    private boolean isRightToken(String token, UserInfo userInfo) {
        for (int i = 0; i < 3; i++) {
            // 每个token三分钟有效期，算法检测历史9分钟内的token，超过9分钟没有执行刷新操作，token失效
            String historyToken = genLoginToken(userInfo, DateTime.now().minusMinutes(i * 3).toDate());
            if (historyToken.equals(token)) {
                return true;
            }
        }
        return false;
    }


    public String genLoginToken(UserInfo userInfo, Date date) {
        DateTime dateTime = new DateTime(date.getTime());
        byte[] bytes = md5(userInfo.getUserName() + "|"
                + userInfo.getPasswd() + "|" + salt + "|"
                + (dateTime.getMinuteOfDay() / 3) + "|" + dateTime.getDayOfYear()
        );
        //
        byte[] userIdData = longToByte(userInfo.getId());
        byte[] finalData = new byte[bytes.length + userIdData.length];

        for (int i = 0; i < 8; i++) {
            finalData[i * 2] = userIdData[i];
            finalData[i * 2 + 1] = bytes[i];
        }

        if (bytes.length - 8 >= 0) {
            System.arraycopy(bytes, 8, finalData, 16, bytes.length - 8);
        }

//        for (int i = 0; i < bytes.length - 8; i++) {
//            finalData[16 + i] = bytes[8 + i];
//        }
        return Md5Util.toHexString(finalData);
    }

    public static long byteToLong(byte[] b) {
        long s = 0;
        long s0 = b[0] & 0xff;// 最低位
        long s1 = b[1] & 0xff;
        long s2 = b[2] & 0xff;
        long s3 = b[3] & 0xff;
        long s4 = b[4] & 0xff;// 最低位
        long s5 = b[5] & 0xff;
        long s6 = b[6] & 0xff;
        long s7 = b[7] & 0xff;

        // s0不变
        s1 <<= 8;
        s2 <<= 16;
        s3 <<= 24;
        s4 <<= 8 * 4;
        s5 <<= 8 * 5;
        s6 <<= 8 * 6;
        s7 <<= 8 * 7;
        s = s0 | s1 | s2 | s3 | s4 | s5 | s6 | s7;
        return s;
    }

    public static byte[] longToByte(long number) {
        long temp = number;
        byte[] b = new byte[8];
        for (int i = 0; i < b.length; i++) {
            b[i] = new Long(temp & 0xff).byteValue();
            // 将最低位保存在最低位
            temp = temp >> 8;
            // 向右移8位
        }
        return b;
    }

    public static byte[] md5(String inputString) {
        try {
            byte[] buffer = inputString.getBytes(StandardCharsets.UTF_8);
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            md5.update(buffer, 0, buffer.length);
            return md5.digest();
        } catch (Exception var4) {
            throw new IllegalStateException(var4);
        }
    }


    public CommonRes<UserInfo> register(String account, String password) {
        if (StringUtils.isAnyBlank(account, password)) {
            return CommonRes.failed("用户或者密码不能为空");
        }
        UserInfo userInfo = userMapper.selectOne(
                new QueryWrapper<UserInfo>().eq(UserInfo.USER_NAME, account)
                        .last("limit 1")
        );

        if (userInfo != null) {
            return CommonRes.failed("用户已存在");
        }

        // 默认是0  管理员在后台配置quota后放开
        userInfo = new UserInfo();
        userInfo.setAdmin(false);
        userInfo.setUserName(account);
        userInfo.setPasswd(password);
        //
        userInfo.setRegisterQuota(2);
        userInfo.setAuthWhiteIpCount(5);
        userInfo.setApiToken(UUID.randomUUID().toString());

        int result = userMapper.insert(userInfo);
        if (result == 1) {
            userInfo.setLoginToken(genLoginToken(userInfo, new Date()));
            userMapper.updateById(userInfo);
            return CommonRes.success(userInfo);
        }
        return CommonRes.failed("注册失败，请稍后再试");
    }

    public CommonRes<UserInfo> checkAPIToken(List<String> tokenList) {
        QueryWrapper<UserInfo> queryWrapper = new QueryWrapper<>();
        if (tokenList.size() == 1) {
            queryWrapper.eq(UserInfo.API_TOKEN, tokenList.get(0));
        } else {
            queryWrapper.in(UserInfo.API_TOKEN, tokenList);
        }

        UserInfo userInfo = userMapper.selectOne(queryWrapper.last("limit 1"));
        if (userInfo != null) {
            return CommonRes.success(userInfo);
        }
        return CommonRes.failed("请登录");
    }


    public CommonRes<UserInfo> checkLogin(List<String> tokenList) {
        for (String candidateToken : tokenList) {
            CommonRes<UserInfo> res = checkLogin(candidateToken);
            if (res.isOk()) {
                return res;
            }
        }
        return CommonRes.failed("请登录");
    }

    public CommonRes<UserInfo> checkLogin(String token) {
        UserInfo userInfo = getUserInfoFromToken(token);
        if (userInfo == null) {
            return CommonRes.failed(CommonRes.statusNeedLogin, "token错误");
        }
        if (!isRightToken(token, userInfo)) {
            return CommonRes.failed("请登录");
        }
        userInfo.setLoginToken(genLoginToken(userInfo, new Date()));
        userMapper.updateById(userInfo);
        return CommonRes.success(userInfo);
    }

    private UserInfo getUserInfoFromToken(String token) {
        // check token format
        if (StringUtils.isBlank(token)) {
            return null;
        }
        if ((token.length() & 0x01) != 0) {
            //token长度必须是偶数
            return null;
        }
        if (token.length() < 16) {
            return null;
        }
        for (char ch : token.toCharArray()) {
            // [0-9] [a-f]
            if (ch >= '0' && ch <= '9') {
                continue;
            }
            if (ch >= 'a' && ch <= 'f') {
                continue;
            }
            log.warn("broken token: {} reason:none dex character", token);
            return null;
        }

        byte[] bytes = Md5Util.hexToByteArray(token);
        byte[] longByteArray = new byte[8];
        // byte[] md5BeginByteArray = new byte[8];
        for (int i = 0; i < 8; i++) {
            longByteArray[i] = bytes[i * 2];
            //  md5BeginByteArray[i] = bytes[i * 2 + 1];
        }
        long userId = byteToLong(longByteArray);
        return userMapper.selectById(userId);
//        UserInfo userInfo = userMapper.selectById(userId);
//        if (userInfo == null) {
//            return null;
//        }
    }

}
