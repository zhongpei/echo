package com.virjar.echo.meta.server.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONPath;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.virjar.echo.meta.server.entity.*;
import com.virjar.echo.meta.server.mapper.ClientInfoMapper;
import com.virjar.echo.meta.server.mapper.DownstreamServerMapper;
import com.virjar.echo.meta.server.mapper.ProxyResourceMapper;
import com.virjar.echo.meta.server.mapper.UserInfoMapper;
import com.virjar.echo.meta.server.utils.AppContext;
import com.virjar.echo.meta.server.utils.CommonUtils;
import com.virjar.echo.meta.server.utils.IPUtils;
import com.virjar.echo.server.common.ConstantHashUtil;
import com.virjar.echo.server.common.SimpleHttpInvoker;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * <p>
 * 代理ip资源 服务实现类
 * </p>
 *
 * @author virjar
 * @since 2021-01-07
 */
@Service
@Slf4j
public class ProxyResourceService extends ServiceImpl<ProxyResourceMapper, ProxyResource> implements IService<ProxyResource> {

    @Resource
    private ProxyResourceMapper proxyResourceMapper;

    /**
     * 一个共享模式下，一个ip产生4个ip
     */
    private static final int SHARED_PROXY_RESOURCE_RATE = 4;
    @Resource
    private UserInfoMapper userInfoMapper;

    @Resource
    private DownstreamServerMapper downstreamServerMapper;

    @Resource
    private ClientInfoMapper clientInfoMapper;

    private List<ProxyResource> queryFetchWithSharedMode(boolean random, Integer targetSize) {

        UserInfo user = AppContext.getUser();
        // shared模式，首先不是管理员，所以个人资产随机抽选
        // shared空间随机抽选
        int userProxySize = proxyResourceMapper.selectCount(new QueryWrapper<ProxyResource>().eq(ProxyResource.BIND_ACCOUNT, user.getUserName()).eq(ProxyResource.ONLINE, true));
        if (userProxySize <= 0) {
            return Collections.emptyList();
        }
        int maxProxySize = userProxySize * SHARED_PROXY_RESOURCE_RATE;

        int ownerTaskSize, sharedTaskSize;
        if (targetSize >= maxProxySize) {
            // 如果当前的提取数量大于最大容量，此时优先抽取完成个人资源
            ownerTaskSize = userProxySize;
            sharedTaskSize = maxProxySize - userProxySize;
        } else {
            // 如果容量小于最大容量，那么此时安比例抽取个人空间和共享空间
            ownerTaskSize = targetSize / SHARED_PROXY_RESOURCE_RATE;
            sharedTaskSize = targetSize * (SHARED_PROXY_RESOURCE_RATE - 1) / SHARED_PROXY_RESOURCE_RATE;
        }

        if (ownerTaskSize + sharedTaskSize < targetSize) {
            //可能存在小数误差，所以这里加1
            ownerTaskSize += 1;
        }

        List<ProxyResource> ret = Lists.newArrayList();

        // fetch owner resource
        QueryWrapper<ProxyResource> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(ProxyResource.BIND_ACCOUNT, user.getUserName());
        queryWrapper.eq(ProxyResource.ONLINE, true);
        if (random) {
            int start = userProxySize <= ownerTaskSize ? 0 :
                    ThreadLocalRandom.current().nextInt(userProxySize - ownerTaskSize);
            queryWrapper.last("limit " + start + ", " + ownerTaskSize);
        } else {
            queryWrapper.last("limit " + ownerTaskSize);
        }

        ret.addAll(proxyResourceMapper.selectList(queryWrapper));

        if (sharedTaskSize <= 0) {
            return ret;
        }

        // fetch shared resource
        queryWrapper = new QueryWrapper<>();
        queryWrapper
                .ne(ProxyResource.BIND_ACCOUNT, user.getUserName())
                .eq(ProxyResource.ONLINE, true)
                .eq(ProxyResource.SHARED, true);


        Integer totalSharedSize = proxyResourceMapper.selectCount(queryWrapper);
        if (totalSharedSize <= 0) {
            //共享空间没有资源，放弃抽取
            return ret;
        }

        if (totalSharedSize <= sharedTaskSize) {
            // 当前系统可用共享ip资源，不足以cover共享抽取目标数量
            ret.addAll(proxyResourceMapper.selectList(queryWrapper));
            return ret;
        }

        int spaceStart = Math.abs(((int) ConstantHashUtil.murHash(user.getUserName()))) % totalSharedSize;
        if (totalSharedSize - spaceStart < maxProxySize) {
            spaceStart = totalSharedSize - maxProxySize;
        }

        int start = random ?
                spaceStart + ThreadLocalRandom.current().nextInt(maxProxySize - sharedTaskSize)
                : spaceStart;
        if (start < 0) {
            // todo why start less than zero
            start = 0;
        }

        queryWrapper.last("limit " + start + ", " + sharedTaskSize);
        ret.addAll(proxyResourceMapper.selectList(queryWrapper));
        return ret;
    }

    private List<ProxyResource> queryFetchWithNormalMode(boolean random, Integer size) {
        QueryWrapper<ProxyResource> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(ProxyResource.ONLINE, true);
        UserInfo user = AppContext.getUser();
        if (!user.getIsAdmin()) {
            // 独享账户只访问自己的资源，同时其他账户也无法访问到自己
            queryWrapper.eq(ProxyResource.BIND_ACCOUNT, user.getUserName());
        }


        if (random) {
            Integer totalSize = proxyResourceMapper.selectCount(queryWrapper);
            int start = totalSize <= size ? 0 :
                    ThreadLocalRandom.current().nextInt(totalSize - size);
            queryWrapper.last("limit " + start + ", " + size);
        } else {
            queryWrapper.last("limit " + size);
        }
        return proxyResourceMapper.selectList(queryWrapper);
    }

    public Object queryFetch(boolean random, Integer size, boolean detail) {
        UserInfo user = AppContext.getUser();
        List<ProxyResource> proxyResources;
        if (user.getIsAdmin() || !user.getShared()) {
            proxyResources = queryFetchWithNormalMode(random, size);
        } else {
            proxyResources = queryFetchWithSharedMode(random, size);
        }
        if (detail) {
            return CommonRes.success(proxyResources);
        }

        // return ip:port list string
        return StringUtils.join(
                proxyResources.stream()
                        .map(proxyResource -> proxyResource.getProxyIp() + ":" + proxyResource.getProxyPort())
                        .collect(Collectors.toList())
                , "\n");
    }

    void syncProxyResource() {
        List<DownstreamServer> downstreamServers = downstreamServerMapper.selectList(
                new QueryWrapper<DownstreamServer>().eq(DownstreamServer.ENABLED, true)
                        .isNotNull(DownstreamServer.SERVER_ID)
                        .gt(DownstreamServer.ALIVE_TIME, DateTime.now().minusMinutes(10).toDate())
        );

        Set<String> onlineServer = downstreamServers.stream().map(DownstreamServer::getServerId).collect(Collectors.toSet());
        //删除数据库中被移除的服务器
        Set<String> existServers = proxyResourceMapper.proxyDownstreamServices();

        HashSet<String> needOfflineServer = Sets.newHashSet(existServers);
        needOfflineServer.removeAll(onlineServer);


        // offline sever
        for (String server : needOfflineServer) {
            log.info("offline server :{} remove all proxy resource for this server", server);
            while (true) {
                List<Object> ids = proxyResourceMapper.selectObjs(new QueryWrapper<ProxyResource>()
                        .select(ProxyResource.ID)
                        .eq(ProxyResource.DOWNSTREAM_SERVER_ID, server)
                        .last("limit 1024")
                );
                if (ids.isEmpty()) {
                    break;
                }
                log.info("offline proxy size:{}", ids.size());
                proxyResourceMapper.deleteBatchIds(ids.stream().map(
                        o -> (Serializable) o
                ).collect(Collectors.toList()));

            }
        }

        // now sync data from all downstream server
        for (DownstreamServer downstreamServer : downstreamServers) {
            try {
                syncProxyResourceFromDownstreamServer(downstreamServer);
            } catch (Exception e) {
                log.warn("sync downstream server failed", e);
            }
        }

    }

    private void syncProxyResourceFromDownstreamServer(DownstreamServer downstreamServer) {
        String downloadResourceUrl = IPUtils.genURL(downstreamServer.getApiBaseUrl(), "/echoNatApi/proxyList");

        log.info("download proxy resource for server:{} from url:{}", downstreamServer.getServerId(), downloadResourceUrl);
        String loadResponse = SimpleHttpInvoker.get(downloadResourceUrl);
        log.info("download response:{}", loadResponse);
        if (StringUtils.isBlank(loadResponse)) {
            return;
        }

        //{"proxies":[{"proxyPort":29970,"clientId":"clientId-virjar-test","createTimestamp":1610166905733}],"status":0}
        JSONArray proxies = (JSONArray) JSONPath.compile("$.proxies").eval(JSONObject.parseObject(loadResponse));
        if (proxies == null || proxies.isEmpty()) {
            log.warn("response empty");
            return;
        }
        String host = IPUtils.extractHostFromUrl(downloadResourceUrl);

        Set<Long> existedIds = Sets.newHashSet();
        for (int i = 0; i < proxies.size(); i++) {
            ProxyResource proxyResource = flushProxyResource(proxies.getJSONObject(i), downstreamServer.getServerId(), host);
            if (proxyResource != null) {
                existedIds.add(proxyResource.getId());
            }
        }

        // remove offline proxy
        List<Long> needRemoveIds = proxyResourceMapper.selectObjs(
                new QueryWrapper<ProxyResource>()
                        .select(ProxyResource.ID)
                        .eq(ProxyResource.DOWNSTREAM_SERVER_ID, downstreamServer.getServerId())
                        .notIn(ProxyResource.ID, existedIds)
        ).stream().filter(Objects::nonNull).map(o -> (Long) o).collect(Collectors.toList());

        if (!needRemoveIds.isEmpty()) {
            proxyResourceMapper.deleteBatchIds(needRemoveIds);
        }

    }


    ProxyResource flushProxyResource(JSONObject proxyVo, String downstreamServerId, String defaultServerHost) {
        String clientId = proxyVo.getString("clientId");
        String account = CommonUtils.extractAccountFromClientId(clientId);

        ProxyResource proxyResource = proxyResourceMapper.selectOne(
                new QueryWrapper<ProxyResource>()
                        .eq(ProxyResource.CLIENT_ID, clientId)
                        .eq(ProxyResource.DOWNSTREAM_SERVER_ID, downstreamServerId)
        );
        if (proxyResource == null) {
            proxyResource = new ProxyResource();
            proxyResource.setClientId(clientId);
            proxyResource.setDownstreamServerId(downstreamServerId);
        }

        if (IPUtils.isPrivateIp(defaultServerHost)) {
            // do not use privateIp
            DownstreamServer downstreamServer = downstreamServerMapper.selectOne(new QueryWrapper<DownstreamServer>().eq(DownstreamServer.SERVER_ID, downstreamServerId));
            if (downstreamServer != null) {
                defaultServerHost = IPUtils.extractHostFromUrl(downstreamServer.getApiBaseUrl());
            }
        }

        //{"proxyPort":29970,"clientId":"clientId-virjar-test","createTimestamp":1610166905733}
        proxyResource.setLastAliveTime(new Date());
        proxyResource.setProxyIp(defaultServerHost);
        proxyResource.setBindAccount(account);
        proxyResource.setProxyPort(proxyVo.getIntValue("proxyPort"));

        expandProxyAddition(proxyResource);

        //TODO 下面字段需要补齐
        proxyResource.setProxyType(proxyVo.getIntValue("proxyType"));
        proxyResource.setOnline(true);

        if (proxyResource.getId() != null) {
            proxyResourceMapper.updateById(proxyResource);
        } else {
            proxyResourceMapper.insert(proxyResource);
            proxyResource = proxyResourceMapper.selectOne(
                    new QueryWrapper<ProxyResource>()
                            .eq(ProxyResource.CLIENT_ID, clientId)
                            .eq(ProxyResource.DOWNSTREAM_SERVER_ID, downstreamServerId));
        }
        return proxyResource;
    }

    private void expandProxyAddition(ProxyResource proxyResource) {
        if (StringUtils.isNotBlank(proxyResource.getBindAccount())) {
            // 代理ip是否是共享模式
            UserInfo userInfo = userInfoMapper.selectOne(
                    new QueryWrapper<UserInfo>()
                            .eq(UserInfo.USER_NAME, proxyResource.getBindAccount())
            );
            proxyResource.setShared(userInfo.getShared());


            //代理的出口ip
            ClientInfo clientInfo = clientInfoMapper.selectOne(new QueryWrapper<ClientInfo>()
                    .eq(ClientInfo.CLIENT_ID, proxyResource.getClientId())
                    .eq(ClientInfo.BIND_ACCOUNT, proxyResource.getBindAccount())
                    .last("limit 1")
            );

            if (clientInfo != null) {
                proxyResource.setOutIp(clientInfo.getClientOutIp());
            }
        }


    }

    void offlineProxyResource(JSONObject proxyVo, String downstreamServerId) {
        String clientId = proxyVo.getString("clientId");

        ProxyResource proxyResource = proxyResourceMapper.selectOne(
                new QueryWrapper<ProxyResource>()
                        .eq(ProxyResource.CLIENT_ID, clientId)
                        .eq(ProxyResource.DOWNSTREAM_SERVER_ID, downstreamServerId)
        );
        if (proxyResource == null) {
            return;
        }
        proxyResource.setOnline(false);
        proxyResourceMapper.updateById(proxyResource);
    }
}
