package com.virjar.echo.meta.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;

import java.time.LocalDateTime;
import java.io.Serializable;
import java.util.Date;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <p>
 * 代理ip资源
 * </p>
 *
 * @author virjar
 * @since 2021-01-07
 */
@Data
@EqualsAndHashCode(callSuper = false)
@ApiModel(value = "ProxyResource对象", description = "代理ip资源")
public class ProxyResource implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "自增主建")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "代理ip")
    private String proxyIp;

    @ApiModelProperty(value = "代理端口")
    private Integer proxyPort;

    @ApiModelProperty(value = "代理类型，如http/https/sockts/udp")
    private Integer proxyType;

    @ApiModelProperty(value = "对应的客户端设备id")
    private String clientId;

    @ApiModelProperty(value = "对应出口服务器")
    private String downstreamServerId;

    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    @ApiModelProperty(value = "最后存活时间,服务器同步的最后时间")
    private Date lastAliveTime;

    @ApiModelProperty(value = "对应的账户")
    private String bindAccount;

    @ApiModelProperty(value = "是否共享")
    private Boolean shared;

    @ApiModelProperty(value = "出口ip")
    private String outIp;

    @ApiModelProperty("是否在线")
    private Boolean online;

    public static final String ID = "id";

    public static final String PROXY_IP = "proxy_ip";

    public static final String PROXY_PORT = "proxy_port";

    public static final String PROXY_TYPE = "proxy_type";

    public static final String CLIENT_ID = "client_id";

    public static final String CREATE_TIME = "create_time";

    public static final String DOWNSTREAM_SERVER_ID = "downstream_server_id";

    public static final String LAST_ALIVE_TIME = "last_alive_time";

    public static final String BIND_ACCOUNT = "bind_account";

    public static final String SHARED = "shared";

    public static final String OUT_IP = "client_out_ip";

    public static final String ONLINE = "online";
}
