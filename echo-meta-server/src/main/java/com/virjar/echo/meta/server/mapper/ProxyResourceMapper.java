package com.virjar.echo.meta.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.virjar.echo.meta.server.entity.ProxyResource;

import java.util.Set;

/**
 * <p>
 * 代理ip资源 Mapper 接口
 * </p>
 *
 * @author virjar
 * @since 2021-01-07
 */
public interface ProxyResourceMapper extends BaseMapper<ProxyResource> {

    Set<String> proxyDownstreamServices();
}
