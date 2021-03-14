package com.virjar.echo.meta.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * <p>
 * 出口ip白名单
 * </p>
 *
 * @author virjar
 * @since 2021-01-07
 */
@Data
@EqualsAndHashCode(callSuper = false)
@ApiModel(value = "AuthWhiteIp对象", description = "出口ip白名单")
public class AuthWhiteIp implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "自增主建")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "出口ip")
    private String whiteIp;

    @ApiModelProperty(value = "对应的账户")
    private String bindAccount;

    @ApiModelProperty(value = "创建时间")
    private Date createTime;


    public static final String ID = "id";

    public static final String WHITE_IP = "white_ip";

    public static final String BIND_ACCOUNT = "bind_account";

    public static final String CREATE_TIME = "create_time";

}
