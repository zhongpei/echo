package com.virjar.echo.meta.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;

/**
 * <p>
 * 端口映射配置
 * </p>
 *
 * @author virjar
 * @since 2021-03-08
 */
@Data
@EqualsAndHashCode(callSuper = false)
@ApiModel(value = "Penetration对象", description = "端口映射配置")
public class Penetration implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "自增主建")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "终端ID")
    private String clientId;

    @ApiModelProperty(value = "对应的账户")
    private String bindAccount;

    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    @ApiModelProperty(value = "映射远端ip地址，受限网络目标ip地址")
    private String remoteIp;

    @ApiModelProperty(value = "映射远端端口，受限网络目标port")
    private Integer remotePort;

    @ApiModelProperty(value = "端口映射的服务器id")
    private String penetrationServerId;

    @ApiModelProperty(value = "监听PenetrationServer的ip地址")
    private String penetrationListenIp;

    @ApiModelProperty(value = "监听PenetrationServer的端口号")
    private Integer penetrationListenPort;

    @ApiModelProperty(value = "备注信息")
    private String penetrationComment;

    @ApiModelProperty(value = "是否在线,默认为false，配置成功之后设置为true")
    private Boolean online;

    @ApiModelProperty(value = "最后存活时间时间")
    private Date lastAliveTime;

    @ApiModelProperty(value = "自动重绑，默认不重绑")
    private Boolean rebind;


    public static final String ID = "id";

    public static final String CLIENT_ID = "client_id";

    public static final String BIND_ACCOUNT = "bind_account";

    public static final String CREATE_TIME = "create_time";

    public static final String REMOTE_IP = "remote_ip";

    public static final String REMOTE_PORT = "remote_port";

    public static final String PENETRATION_SERVER_ID = "penetration_server_id";

    public static final String PENETRATION_LISTEN_IP = "penetration_listen_ip";

    public static final String PENETRATION_LISTEN_PORT = "penetration_listen_port";

    public static final String PENETRATION_COMMENT = "penetration_comment";

    public static final String ONLINE = "online";

    public static final String LAST_ALIVE_TIME = "last_alive_time";

    public static final String REBIND = "rebind";

}
