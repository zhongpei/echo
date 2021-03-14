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
 * 下游服务，比如http代理协议实现
 * </p>
 *
 * @author virjar
 * @since 2021-01-07
 */
@Data
@EqualsAndHashCode(callSuper = false)
@ApiModel(value="DownstreamServer对象", description="下游服务，比如http代理协议实现")
public class DownstreamServer implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "自增主建")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "服务器id，内部业务使用这个作为业务id")
    private String serverId;

    @ApiModelProperty(value = "API接口地址")
    private String apiBaseUrl;

    @ApiModelProperty(value = "是否启用")
    private Boolean enabled;

    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    @ApiModelProperty(value = "最后存活时间，用来做心跳")
    private Date aliveTime;

    @ApiModelProperty(value = "服务类型->http: http(socks)代理服务器，penetration:内网穿透服务")
    private String serverType;

    public static final String ID = "id";

    public static final String SERVER_ID = "server_id";

    public static final String API_BASE_URL = "api_base_url";

    public static final String ENABLED = "enabled";

    public static final String CREATE_TIME = "create_time";

    public static final String ALIVE_TIME = "alive_time";

    public static final String SERVER_TYPE="server_type";
}
