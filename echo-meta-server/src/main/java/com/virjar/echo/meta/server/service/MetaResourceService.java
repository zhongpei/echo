package com.virjar.echo.meta.server.service;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONPath;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.virjar.echo.meta.server.entity.ClientInfo;
import com.virjar.echo.meta.server.entity.DownstreamServer;
import com.virjar.echo.meta.server.entity.NatMappingServer;
import com.virjar.echo.meta.server.mapper.ClientInfoMapper;
import com.virjar.echo.meta.server.mapper.DownstreamServerMapper;
import com.virjar.echo.meta.server.mapper.NatMappingServerMapper;
import com.virjar.echo.meta.server.utils.IPUtils;
import com.virjar.echo.server.common.ConstantHashUtil;
import com.virjar.echo.server.common.DownstreamServerType;
import com.virjar.echo.server.common.NatUpstreamMeta;
import com.virjar.echo.server.common.SimpleHttpInvoker;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 同步和管理上下游资源
 */
@Slf4j
@Service
public class MetaResourceService {

    @Resource
    private DownstreamServerMapper downstreamServerMapper;

    @Resource
    private NatMappingServerMapper natMappingServerMapper;


    @Resource
    private ClientInfoMapper clientInfoMapper;


    private final Executor upstreamLoadExecutor = Executors.newSingleThreadExecutor();

    private final Executor downstreamLoadExecutor = Executors.newSingleThreadExecutor();

    private final Executor heartBeatExecutor = Executors.newSingleThreadExecutor();

    @Resource
    private ProxyResourceService proxyResourceService;


    @Resource
    private ClientInfoService clientInfoService;

    /**
     * 连接upstream server，下载当前已经成功连接到服务器的终端信息
     */
    @Scheduled(fixedRate = 30000, initialDelay = 10000)
    private void loadUpstreamSchedule() {
        upstreamLoadExecutor.execute(() -> clientInfoService.loadUpstreamClientInfo());
    }

    /**
     * 连接下游服务器资源,同步代理ip等外放服务资源
     */
    @Scheduled(fixedRate = 30000)
    public void loadDownStreamSchedule() {
        downstreamLoadExecutor.execute(() -> proxyResourceService.syncProxyResource());
    }

    @PostConstruct
    @Scheduled(fixedRate = 3 * 60 * 1000)
    public void heartBeatSchedule() {
        heartBeatExecutor.execute(this::doServerHeartbeat);
    }

    private static JSONObject heartBeatAndGetServerId(String heartbeatUrl) {
        String response = SimpleHttpInvoker.get(heartbeatUrl);
        log.info("response :{}", response);
        if (StringUtils.isBlank(response)) {
            //heartbeat error
            return null;
        }
        JSONObject jsonObject = JSONObject.parseObject(response);
        String serverId = (String) JSONPath.compile("$.serverId").eval(jsonObject);
        if (StringUtils.isBlank(serverId)) {
            log.error("can not get server id  from api :{}", heartbeatUrl);
            return null;
        }
        return jsonObject;
    }

    private void doServerHeartbeat() {
        // 保持和各个服务器的心跳，同步各服务的状态
        List<NatMappingServer> natMappingServers = natMappingServerMapper.selectList(new QueryWrapper<NatMappingServer>().eq(NatMappingServer.ENABLED, true));
        Map<String, NatMappingServer> withServerId = Maps.newHashMap();
        for (NatMappingServer natMappingServer : natMappingServers) {
            String heartbeatUrl = IPUtils.genURL(natMappingServer.getApiBaseUrl(), "/echoNatApi/serverId");
            log.info("heartbeat nat mapping server with url:{} for server:{}", heartbeatUrl, natMappingServer.getServerId());
            JSONObject serverIdResponse = heartBeatAndGetServerId(heartbeatUrl);
            if (serverIdResponse == null) {
                continue;
            }
            String serverId = serverIdResponse.getString("serverId");
            NatMappingServer existedNatMappingServer = withServerId.get(serverId);
            if (existedNatMappingServer != null) {
                natMappingServer.setEnabled(false);
                natMappingServerMapper.updateById(natMappingServer);
                log.error("duplicate serverId define with: {} and :{}", existedNatMappingServer.getApiBaseUrl(), natMappingServer.getApiBaseUrl());
                continue;
            }
            Integer natPort = serverIdResponse.getInteger("natPort");
            if (natPort != null && natPort > 10) {
                natMappingServer.setNatPort(natPort);
            } else {
                natMappingServer.setNatPort(5698);
            }
            natMappingServer.setAliveTime(new Date());
            natMappingServer.setServerId(serverId);
            natMappingServerMapper.updateById(natMappingServer);
            withServerId.put(serverId, natMappingServer);
        }


        List<DownstreamServer> downstreamServers = downstreamServerMapper.selectList(new QueryWrapper<DownstreamServer>().eq(NatMappingServer.ENABLED, true));
        Map<String, DownstreamServer> duplicateDownstreamServers = Maps.newHashMap();
        for (DownstreamServer downstreamServer : downstreamServers) {
            String heartbeatUrl = IPUtils.genURL(downstreamServer.getApiBaseUrl(), "/echoNatApi/serverId");
            log.info("heartbeat downstream server with url:{} for server:{}", heartbeatUrl, downstreamServer.getServerId());
            JSONObject serverIdResponse = heartBeatAndGetServerId(heartbeatUrl);
            if (serverIdResponse == null) {
                continue;
            }
            String serverId = serverIdResponse.getString("serverId");
            DownstreamServer existedDownstreamServer = duplicateDownstreamServers.get(serverId);
            if (existedDownstreamServer != null) {
                downstreamServer.setEnabled(false);
                downstreamServerMapper.updateById(downstreamServer);
                log.error("duplicate serverId define with: {} and :{}", existedDownstreamServer.getApiBaseUrl(), downstreamServer.getApiBaseUrl());
                continue;
            }

            String serverType = serverIdResponse.getString("serverType");
            serverType = DownstreamServerType.getByName(serverType).name();
            downstreamServer.setServerType(serverType);

            downstreamServer.setAliveTime(new Date());
            downstreamServer.setServerId(serverId);
            downstreamServerMapper.updateById(downstreamServer);
            duplicateDownstreamServers.put(serverId, downstreamServer);
        }
    }


    public List<NatMappingServer> allocateNatMappingServerForClient(String clientId) {
        log.info("allocate NatMapping server for client: {}", clientId);
        List<NatMappingServer> natMappingServers = natMappingServerMapper.selectList(
                new QueryWrapper<NatMappingServer>()
                        .eq(NatMappingServer.ENABLED, true)
                        .gt(DownstreamServer.ALIVE_TIME, DateTime.now().minusMinutes(5).toDate())
        );


        if (natMappingServers.size() <= 2) {
            //小于2台，两台服务器都返回
            return natMappingServers;
        }

        Map<Long, NatMappingServer> serverIdMap = natMappingServers.stream().collect(
                Collectors.toMap(
                        natMappingServer -> ConstantHashUtil.murHash(natMappingServer.getServerId()), o -> o)
        );
        TreeMap<Long, NatMappingServer> sortedServerMap = Maps.newTreeMap();
        sortedServerMap.putAll(serverIdMap);

        Set<Long> fetchedServerId = Sets.newHashSet();
        for (int i = 0; i < 5; i++) {
            fetchedServerId.add(fetchConstantKey(clientId + i, sortedServerMap));
            if (fetchedServerId.size() >= 2) {
                break;
            }
        }

        List<NatMappingServer> ret = Lists.newArrayListWithExpectedSize(fetchedServerId.size());
        for (Long id : fetchedServerId) {
            ret.add(serverIdMap.get(id));
        }

        return ret;
    }


    private <V> Long fetchConstantKey(String inputKey, TreeMap<Long, V> sortedMap) {
        SortedMap<Long, V> tailMap = sortedMap.tailMap(ConstantHashUtil.murHash(inputKey));

        if (tailMap.isEmpty()) {
            return sortedMap.firstKey();
        }
        return tailMap.firstKey();
    }

    public List<NatUpstreamMeta> allocateUpstreamMetaForServer(String downstreamServerId) {
        log.info("allocateUpstreamMetaForServer:{}", downstreamServerId);

        //上游服务器
        List<NatMappingServer> natMappingServers = natMappingServerMapper.selectList(new QueryWrapper<NatMappingServer>().eq(NatMappingServer.ENABLED, true)
                .isNotNull(DownstreamServer.SERVER_ID)
                .gt(DownstreamServer.ALIVE_TIME, DateTime.now().minusMinutes(10)
                        .toDate())
        );

        if (natMappingServers.isEmpty()) {
            return Collections.emptyList();
        }

        List<DownstreamServer> downstreamServers = downstreamServerMapper.selectList(
                new QueryWrapper<DownstreamServer>()
                        .eq(DownstreamServer.ENABLED, true)
                        .eq(DownstreamServer.SERVER_TYPE, DownstreamServerType.http.name())
                        .gt(DownstreamServer.ALIVE_TIME, DateTime.now().minusMinutes(5).toDate()
                        )
        );

        //下游服务器
        Map<String, DownstreamServer> downStreamMap = downstreamServers.stream().collect(Collectors.toMap(DownstreamServer::getServerId, o -> o));

        int uSize = natMappingServers.size();
        int dSize = downStreamMap.size();
        // int virtualSpace = uSize * dSize;

        //第几台下游服务器
        int indexOfDownstream = downstreamServerId.hashCode() % downstreamServers.size();
        DownstreamServer hintedDownstreamServer = downStreamMap.get(downstreamServerId);
        if (hintedDownstreamServer != null) {
            int tempIndex = downstreamServers.indexOf(hintedDownstreamServer);
            if (tempIndex >= 0) {
                indexOfDownstream = tempIndex;
            }
        } else {
            log.warn("the downstream server name:{} not register in meta server", downstreamServerId);
        }

        int startOfVirtualSpace = indexOfDownstream * uSize;
        int endOfVirtualSpace = (indexOfDownstream + 1) * uSize;

        int startOfUServer = startOfVirtualSpace / dSize;
        double startRatioOfUServer = (startOfVirtualSpace % dSize) * 1.0 / dSize;
        int endOfUServer = endOfVirtualSpace / dSize;
        if (endOfUServer == uSize) {
            endOfUServer = uSize - 1;
        }
        double endRatioOfUServer = (endOfVirtualSpace % dSize) * 1.0 / dSize;


        //所有准备分发的服务器
        ArrayList<String> sortedUpstreamServerList = Lists.newArrayList(Sets.newTreeSet(natMappingServers.parallelStream().map(NatMappingServer::getServerId).collect(Collectors.toSet())));
        //最终返回的容器
        List<NatUpstreamMeta> ret = Lists.newArrayList();

        for (int server = startOfUServer; server <= endOfUServer; server++) {
            String fetchServer = sortedUpstreamServerList.get(server);
            // 30s 之内的时间上报到metaServer
            List<ClientInfo> clientInfos = clientInfoMapper.selectList(
                    new QueryWrapper<ClientInfo>().eq(ClientInfo.SERVER_ID, fetchServer)
                            .eq(ClientInfo.ONLINE, true)
                            .gt(ClientInfo.LAST_ALIVE_TIME, DateTime.now().minusSeconds(30)
                                    .toDate())
            );

            ArrayList<NatUpstreamMeta> sortedUpStreamResource = transformClientInfoToNatUpStream(clientInfos);
            if (server != startOfUServer && server != endOfUServer) {
                // downstream server数量小于upstream的时候，一个downstream会对应多个upstream server，
                ret.addAll(sortedUpStreamResource);
                continue;
            }

            sortedUpStreamResource.sort(Comparator.comparing(NatUpstreamMeta::getClientId));
            if (server == startOfUServer) {
                int startIndex = (int) (sortedUpStreamResource.size() * startRatioOfUServer);
                ret.addAll(sortedUpStreamResource.subList(startIndex, sortedUpStreamResource.size()));
            } else {
                int endIndex = (int) (sortedUpStreamResource.size() * endRatioOfUServer);
                ret.addAll(sortedUpStreamResource.subList(0, endIndex));
            }
        }
        return ret;
    }

    private ArrayList<NatUpstreamMeta> transformClientInfoToNatUpStream(List<ClientInfo> clientInfos) {
        return clientInfos.stream().map(clientInfo -> {
            NatUpstreamMeta natUpstreamMeta = new NatUpstreamMeta();
            natUpstreamMeta.setShared(clientInfo.getShared());
            natUpstreamMeta.setClientAccount(clientInfo.getBindAccount());
            natUpstreamMeta.setClientId(clientInfo.getClientId());
            natUpstreamMeta.setListenIp(clientInfo.getListenIp());
            natUpstreamMeta.setPort(clientInfo.getListenPort());
            natUpstreamMeta.setClientOutIp(clientInfo.getClientOutIp());
            return natUpstreamMeta;
        }).collect(Collectors.toCollection(Lists::newArrayList));
    }
}
