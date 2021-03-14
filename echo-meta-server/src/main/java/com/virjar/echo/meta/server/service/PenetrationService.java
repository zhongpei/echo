package com.virjar.echo.meta.server.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.google.common.collect.Lists;
import com.virjar.echo.meta.server.entity.*;
import com.virjar.echo.meta.server.mapper.ClientInfoMapper;
import com.virjar.echo.meta.server.mapper.PenetrationMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 端口映射配置 服务实现类
 * </p>
 *
 * @author virjar
 * @since 2021-03-08
 */
@Service
public class PenetrationService extends ServiceImpl<PenetrationMapper, Penetration> implements IService<Penetration> {

    @Resource
    private ClientInfoMapper clientInfoMapper;

    public CommonRes<Penetration> add(UserInfo userInfo, Long clientId,
                                      String remoteIp, Integer remotePort,
                                      Boolean rebind, String comment) {
        ClientInfo clientInfo = clientInfoMapper.selectById(clientId);
        if (clientInfo == null) {
            return CommonRes.failed("client not exist");
        }

        if (!userInfo.getIsAdmin() &&
                !userInfo.getUserName().equals(clientInfo.getBindAccount())) {
            return CommonRes.failed("no permission to use this client,account not match");
        }


        Penetration one = getOne(new QueryWrapper<Penetration>()
                .eq(Penetration.CLIENT_ID, clientInfo.getClientId())
                .eq(Penetration.REMOTE_IP, remoteIp)
                .eq(Penetration.REMOTE_PORT, remotePort)
        );
        if (one == null) {
            one = new Penetration();
            one.setClientId(clientInfo.getClientId());
            one.setRemoteIp(remoteIp);
            one.setRemotePort(remotePort);
        }
        one.setBindAccount(userInfo.getUserName());
        one.setRebind(rebind);
        one.setPenetrationComment(comment);
        saveOrUpdate(one);
        return CommonRes.success(one);
    }


    public CommonRes<List<Penetration>> allocatePenetrationMappingTask(DownstreamServer downstreamServer, int size) {
        List<Penetration> list = list(new QueryWrapper<Penetration>().eq(Penetration.ONLINE, false).last("limit " + size));
        List<Penetration> ret = Lists.newLinkedList();
        for (Penetration penetration : list) {
            boolean update = update(new UpdateWrapper<Penetration>()
                    .eq(Penetration.ID, penetration.getId())
                    .eq(Penetration.ONLINE, false)
                    .set(Penetration.ONLINE, true)
                    .set(Penetration.PENETRATION_SERVER_ID, downstreamServer.getServerId())
            );
            if (update) {
                ret.add(penetration);
            }
        }
        return CommonRes.success(ret);
    }
}
