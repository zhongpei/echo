package com.virjar.echo.meta.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.virjar.echo.meta.server.entity.AuthWhiteIp;
import com.virjar.echo.meta.server.mapper.AuthWhiteIpMapper;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 出口ip白名单 服务实现类
 * </p>
 *
 * @author virjar
 * @since 2021-01-07
 */
@Service
public class AuthWhiteIpService extends ServiceImpl<AuthWhiteIpMapper, AuthWhiteIp> implements IService<AuthWhiteIp> {

}
