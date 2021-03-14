package com.virjar.echo.meta.server.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Sets;
import com.virjar.echo.meta.server.entity.ClientInfo;
import com.virjar.echo.meta.server.entity.DownstreamServer;
import com.virjar.echo.meta.server.entity.NatMappingServer;
import com.virjar.echo.meta.server.entity.UserInfo;
import com.virjar.echo.meta.server.mapper.ClientInfoMapper;
import com.virjar.echo.meta.server.mapper.NatMappingServerMapper;
import com.virjar.echo.meta.server.mapper.UserInfoMapper;
import com.virjar.echo.meta.server.utils.CommonUtils;
import com.virjar.echo.meta.server.utils.IPUtils;
import com.virjar.echo.server.common.NatUpstreamMeta;
import com.virjar.echo.server.common.SimpleHttpInvoker;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * <p>
 * 接入到平台的终端 服务实现类
 * </p>
 *
 * @author virjar
 * @since 2021-01-07
 */
@Service
@Slf4j
public class ClientInfoService extends ServiceImpl<ClientInfoMapper, ClientInfo> implements IService<ClientInfo> {

    @Resource
    private NatMappingServerMapper natMappingServerMapper;

    @Resource
    private UserInfoMapper userInfoMapper;

    @Resource
    private ClientInfoMapper clientInfoMapper;

    void loadUpstreamClientInfo() {
        List<NatMappingServer> natMappingServers = natMappingServerMapper.selectList(new QueryWrapper<NatMappingServer>().eq(NatMappingServer.ENABLED, true)
                .isNotNull(DownstreamServer.SERVER_ID)
                .gt(DownstreamServer.ALIVE_TIME, DateTime.now().minusMinutes(10)
                        .toDate())
        );

        for (NatMappingServer natMappingServer : natMappingServers) {
            try {
                Set<NatUpstreamMeta> upstreamMetaMap = loadConnectionMap(natMappingServer);

                //保存链接记录到数据库，用户限制用户链接quota
                insertClientInfo(upstreamMetaMap, natMappingServer.getServerId());
            } catch (Exception e) {
                log.error("load upstream meta failed for server:{} api:{}", natMappingServer.getServerId(), natMappingServer.getApiBaseUrl());
            }
        }

    }


    private void fillNatUpStreamMetaInfoClientInfo(NatUpstreamMeta natUpstreamMeta, ClientInfo clientInfo) {
        clientInfo.setClientOutIp(natUpstreamMeta.getClientOutIp());
        clientInfo.setClientId(natUpstreamMeta.getClientId());
        clientInfo.setListenIp(natUpstreamMeta.getListenIp());
        clientInfo.setListenPort(natUpstreamMeta.getPort());
        clientInfo.setShared(natUpstreamMeta.isShared());
        UserInfo userInfo = userInfoMapper.selectOne(
                new QueryWrapper<UserInfo>()
                        .eq(UserInfo.USER_NAME, natUpstreamMeta.getClientAccount())
                        .last("limit 1")
        );
        if (userInfo != null) {
            natUpstreamMeta.setShared(BooleanUtils.isTrue(userInfo.getShared()));
        }
    }

    private void insertClientInfo(Set<NatUpstreamMeta> natUpstreamMetas, String serverId) {
        for (NatUpstreamMeta natUpstreamMeta : natUpstreamMetas) {
            String clientId = natUpstreamMeta.getClientId();
            String account = CommonUtils.extractAccountFromClientId(clientId);

            ClientInfo one = clientInfoMapper.selectOne(
                    new QueryWrapper<ClientInfo>()
                            .eq(ClientInfo.CLIENT_ID, clientId)
                            .eq(ClientInfo.BIND_ACCOUNT, account)
                            .eq(ClientInfo.SERVER_ID, serverId)
                            .last("limit 1")
            );
            if (one != null) {
                fillNatUpStreamMetaInfoClientInfo(natUpstreamMeta, one);
                one.setLastAliveTime(new Date());
                one.setOnline(true);
                clientInfoMapper.updateById(one);
                continue;
            }
            ClientInfo clientInfo = new ClientInfo();
            clientInfo.setBindAccount(account);
            clientInfo.setClientId(clientId);
            clientInfo.setServerId(serverId);
            clientInfo.setLastAliveTime(new Date());
            clientInfo.setOnline(true);
            fillNatUpStreamMetaInfoClientInfo(natUpstreamMeta, clientInfo);
            clientInfoMapper.insert(clientInfo);
        }
    }

    private Set<NatUpstreamMeta> loadConnectionMap(NatMappingServer natMappingServer) {
        // load resource

        String upstreamResourceUrl = IPUtils.genURL(natMappingServer.getApiBaseUrl(), "/echoNatApi/connectionList");

        log.info("get NatMapping Node resource from url:{} with server:{}",
                upstreamResourceUrl, natMappingServer.getServerId()
        );

        String jsonContent = SimpleHttpInvoker.get(upstreamResourceUrl);
        log.info("node resource: {}", jsonContent);
        if (StringUtils.isBlank(jsonContent)) {
            log.warn("download nat mapping info failed: {}", upstreamResourceUrl);
            return Collections.emptySet();
        }
        // the target host maybe localhost,
        // if echo server running on different computers,we can not access service from local ip,
        // so wo detect ip from url and rewrite it from mapping server response
        String defaultHost = IPUtils.extractHostFromUrl(upstreamResourceUrl);
        //{"clients":[{"listenIp":"127.0.0.1","clientId":"clientId-virjar","port":24577},{"listenIp":"127.0.0.1","clientId":"clientId-virjar-test","port":24576}]}
        JSONObject jsonObject;
        try {
            jsonObject = JSONObject.parseObject(jsonContent);
        } catch (JSONException e) {
            log.warn("download nat mapping info failed: {} response: {}", upstreamResourceUrl, jsonContent);
            return Collections.emptySet();
        }
        if (jsonObject == null) {
            log.warn("download nat mapping info failed: {} response: {}", upstreamResourceUrl, jsonContent);
            return Collections.emptySet();
        }
        return parseUpStreamBody(defaultHost, jsonObject);

    }

    private Set<NatUpstreamMeta> parseUpStreamBody(String defaultHost, JSONObject jsonObject) {
        JSONArray clients = jsonObject.getJSONArray("clients");
        ArrayListMultimap<String, NatUpstreamMeta> natMappingInfo = ArrayListMultimap.create();
        for (int i = 0; i < clients.size(); i++) {
            JSONObject clientJsonNode = clients.getJSONObject(i);
            NatUpstreamMeta natUpstreamMeta = clientJsonNode.toJavaObject(NatUpstreamMeta.class);
            if (IPUtils.isLocalHost(natUpstreamMeta.getListenIp())) {
                natUpstreamMeta.setListenIp(defaultHost);
            }
            natMappingInfo.put(natUpstreamMeta.getClientId(), natUpstreamMeta);
        }

        //for every connect info array, choose one randomly
        Set<NatUpstreamMeta> ret = Sets.newHashSet();
        for (String clientId : natMappingInfo.keys()) {
            List<NatUpstreamMeta> natUpstreamMetas = natMappingInfo.get(clientId);
            if (natUpstreamMetas.isEmpty()) {
                continue;
            }
            if (natMappingInfo.size() == 1) {
                ret.add(natUpstreamMetas.iterator().next());
                continue;
            }
            ret.add(natUpstreamMetas.get(ThreadLocalRandom.current().nextInt(
                    natUpstreamMetas.size()
            )));
        }
        return ret;
    }

    void onOneNatUpstreamResourceOnline(String serverId, NatUpstreamMeta natUpstreamMeta) {
        if (!validNatMappingServer(serverId)) {
            log.warn("receive none existed NatMapping server upStream,ignore");
            return;
        }

        insertClientInfo(Sets.newHashSet(natUpstreamMeta), serverId);
    }

    void onOneNatUpstreamResourceOffline(String serverId, NatUpstreamMeta natUpstreamMeta) {
        String clientId = natUpstreamMeta.getClientId();
        String account = CommonUtils.extractAccountFromClientId(clientId);

        ClientInfo one = clientInfoMapper.selectOne(
                new QueryWrapper<ClientInfo>()
                        .eq(ClientInfo.CLIENT_ID, clientId)
                        .eq(ClientInfo.SERVER_ID, serverId)
                        .eq(ClientInfo.BIND_ACCOUNT, account)
                        .last("limit 1")
        );
        if (one == null) {
            return;
        }
        one.setOnline(false);
        clientInfoMapper.updateById(one);
    }

    private boolean validNatMappingServer(String serverId) {
        if (StringUtils.isBlank(serverId)) {
            return false;
        }
        Integer exist = natMappingServerMapper.selectCount(new QueryWrapper<NatMappingServer>().eq(NatMappingServer.SERVER_ID, serverId));
        return exist > 0;
    }

}
