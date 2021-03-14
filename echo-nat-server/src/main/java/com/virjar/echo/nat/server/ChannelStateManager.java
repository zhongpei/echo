package com.virjar.echo.nat.server;

import com.virjar.echo.server.common.CommonConstant;
import com.virjar.echo.server.common.NettyUtils;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import io.netty.util.internal.ConcurrentSet;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class ChannelStateManager {
    public enum CHANNEL_TYPE {
        CHANNEL_TYPE_UNKNOWN,
        CHANNEL_TYPE_NAT,
        CHANNEL_TYPE_MAPPING_SERVER,
        CHANNEL_TYPE_MAPPING_CHILD
    }

    public static AttributeKey<CHANNEL_TYPE> CHANNEL_TYPE_ATTR = AttributeKey.newInstance("channel_type");

    /**
     * 首先，所有channel被注册的时候，调用这个方法设置channel类型
     */
    public static void setup(Channel channel, CHANNEL_TYPE channelType) {
        CHANNEL_TYPE nowType = channel.attr(CHANNEL_TYPE_ATTR).get();
        if (nowType == null) {
            if (!channel.attr(CHANNEL_TYPE_ATTR).compareAndSet(null, channelType)) {
                throw new IllegalStateException("can not setup channel type target:" + channel + " now:" + channel.attr(CHANNEL_TYPE_ATTR).get());
            }
            return;
        }

        if (nowType != channelType) {
            throw new IllegalStateException("can not setup channel type target:" + channel + " now:" + nowType + "  targetType:" + channelType);
        }
    }

    /**
     * 第二：当客户端连接到服务器之后，需要绑定设备id
     */
    public static void setupClientId(Channel natChannel, String clientId) {
        Channel bindNatChannel = getBindNatChannel(natChannel);
        if (bindNatChannel == null) {
            log.warn("can not get BindNatChannel from:{}", natChannel);
            throw new IllegalStateException("can not get BindNatChannel");
        }
        natChannel.attr(EchoServerConstant.NAT_CHANNEL_CLIENT_KEY).set(clientId);

    }


    /**
     * 第三：当代理隧道建立时，在这里维护各channel关系，初始化各资源数据<br>
     * 请注意，这个方法调用完成，代表代理资源正式被启用。<br>
     * 请注意，这里的代理，是echo提供的代理协议。不是常规网络代理协议
     */
    public static void onEchoProxyEstablish(EchoTuningExtra echoTuningExtra) {
        Channel natChannel = echoTuningExtra.getEchoNatChannel(); // 远端手机客户端连接产生的channel
        Channel mappingServerChannel = echoTuningExtra.getMappingServerChannel(); // 连接本地分配的端口生成的channel

        NettyUtils.makePair(natChannel, mappingServerChannel);
        NettyUtils.loveOther(natChannel, mappingServerChannel);


        mappingServerChannel.attr(EchoServerConstant.MAPPING_CLIENT_CHANNELS).set(new ConcurrentHashMap<>());
        mappingServerChannel.attr(EchoServerConstant.HEART_BEAT_CHANNEL).set(new ConcurrentSet<>());
        mappingServerChannel.attr(EchoServerConstant.SEQ).set(new AtomicLong(0));

        natChannel.attr(EchoServerConstant.ECHO_TURNING_EXTRA).set(echoTuningExtra);
        mappingServerChannel.attr(EchoServerConstant.ECHO_TURNING_EXTRA).set(echoTuningExtra);
    }


    public static CHANNEL_TYPE channelType(Channel channel) {
        CHANNEL_TYPE nowType = channel.attr(CHANNEL_TYPE_ATTR).get();
        if (nowType == null) {
            return CHANNEL_TYPE.CHANNEL_TYPE_UNKNOWN;
        }
        return nowType;
    }

    public static Channel getBindMappingServerChannel(Channel channel) {
        CHANNEL_TYPE channelType = channelType(channel);
        switch (channelType) {
            case CHANNEL_TYPE_MAPPING_SERVER:
                return channel;
            case CHANNEL_TYPE_NAT:
                return getBindMappingServerChannel(channel.attr(CommonConstant.NEXT_CHANNEL).get());
            case CHANNEL_TYPE_MAPPING_CHILD:
                return getBindMappingServerChannel(channel.parent());
            case CHANNEL_TYPE_UNKNOWN:
            default:
                return null;

        }
    }

    /**
     * 给定任意一个channel对象，获得NatChannel对象
     *
     * @param channel 任意类型的channel
     * @return NatChannel对象，长链接端控制channel对象
     */
    public static Channel getBindNatChannel(Channel channel) {
        if (channel == null) {
            return null;
        }
        CHANNEL_TYPE channelType = channelType(channel);
        switch (channelType) {
            case CHANNEL_TYPE_NAT:
                return channel;
            case CHANNEL_TYPE_MAPPING_SERVER:
                return getBindNatChannel(channel.attr(CommonConstant.NEXT_CHANNEL).get());
            case CHANNEL_TYPE_MAPPING_CHILD:
                return getBindNatChannel(channel.parent().attr(CommonConstant.NEXT_CHANNEL).get());
            case CHANNEL_TYPE_UNKNOWN:
            default:
                return null;

        }
    }

    /**
     * 根据一个请求流水号，查询一个代理端链接对象(用户端请求，一般是真实用户的某个tcp请求).<br>
     * echo proxy协议模型:<br>
     * echo-user   ->    echo-local-mapping-server   ->                        echo-nat                          ->        echo-client              ->    real-server<br>
     * 多个业务tcp      echo mapping协议监听的server端口     长链接隧道，业务tcp通过流水号在逻辑层合并为实际的一个channel传输     客户端，通过流水号散开为多个tcp链接       真实的服务端tcp链接<br>
     *
     * @param channel 任意类型的channel
     * @param seq     请求流水号。echo会在握手阶段为每个业务tcp请求创建一个唯一的流水id
     * @return 该流水号对应的业务tcp链接对象
     */
    public static Channel getMappingChildChannel(Channel channel, Long seq) {
        Channel bindMappingServerChannel = getBindMappingServerChannel(channel);
        if (bindMappingServerChannel == null) {
            return null;
        }
        return bindMappingServerChannel.attr(EchoServerConstant.MAPPING_CLIENT_CHANNELS).get()
                .get(seq);
    }

    public static String getClientId(Channel channel) {
        Channel bindNatChannel = getBindNatChannel(channel);
        if (bindNatChannel == null) {
            return null;
        }
        return bindNatChannel.attr(EchoServerConstant.NAT_CHANNEL_CLIENT_KEY).get();
    }

    public static Long userConnectionEstablish(Channel mappingClientChannel) {
        CHANNEL_TYPE channelType = channelType(mappingClientChannel);
        if (channelType != CHANNEL_TYPE.CHANNEL_TYPE_MAPPING_CHILD) {
            throw new IllegalStateException("error state,can not create seq with channelType:" + channelType);
        }
        Channel bindMappingServerChannel = getBindMappingServerChannel(mappingClientChannel);

        if (bindMappingServerChannel == null) {
            throw new IllegalStateException("error state,no mapping server channel bound");
        }
        Long seq = bindMappingServerChannel.attr(EchoServerConstant.SEQ).get().incrementAndGet();
        mappingClientChannel.attr(EchoServerConstant.NAT_CHANNEL_SERIAL).set(seq);
        bindMappingServerChannel.attr(EchoServerConstant.MAPPING_CLIENT_CHANNELS)
                .get().put(seq, mappingClientChannel);
        return seq;
    }

    public static void userConnectionDisconnect(Channel mappingClientChannel) {
        CHANNEL_TYPE channelType = channelType(mappingClientChannel);
        if (channelType != CHANNEL_TYPE.CHANNEL_TYPE_MAPPING_CHILD) {
            throw new IllegalStateException("error state,can not handle disconnect method" +
                    " with channelType:" + channelType);
        }
        Channel bindMappingServerChannel = getBindMappingServerChannel(mappingClientChannel);

        if (bindMappingServerChannel == null) {
            throw new IllegalStateException("error state,no mapping server channel bound");
        }
        Long seq = mappingClientChannel.attr(EchoServerConstant.NAT_CHANNEL_SERIAL).get();
        if (seq != null) {
            bindMappingServerChannel.attr(EchoServerConstant.MAPPING_CLIENT_CHANNELS)
                    .get().remove(seq);
        }
        mappingClientChannel.close();

    }

    public static Collection<Channel> connectedDownStreams(Channel channel) {
        Channel bindMappingServerChannel = getBindMappingServerChannel(channel);
        if (bindMappingServerChannel == null) {
            return Collections.emptyList();
        }
        Map<Long, Channel> childChannelMaps = bindMappingServerChannel.attr(EchoServerConstant.MAPPING_CLIENT_CHANNELS).get();
        if (childChannelMaps == null) {
            return Collections.emptyList();
        }
        return childChannelMaps.values();
    }

    public static Collection<Channel> heartBeatChildChannel(Channel channel) {
        Channel bindMappingServerChannel = getBindMappingServerChannel(channel);
        if (bindMappingServerChannel == null) {
            return Collections.emptyList();
        }
        Set<Channel> channels = bindMappingServerChannel.attr(EchoServerConstant.HEART_BEAT_CHANNEL).get();
        if (channels == null) {
            return Collections.emptyList();
        }
        return channels;
    }
}
