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
 * 用户信息
 * </p>
 *
 * @author virjar
 * @since 2021-01-07
 */
@Data
@EqualsAndHashCode(callSuper = false)
@ApiModel(value = "UserInfo对象", description = "用户信息")
public class UserInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "自增主建")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "用户名")
    private String userName;

    @ApiModelProperty(value = "密码")
    private String passwd;

    @ApiModelProperty(value = "最后登陆时间")
    private Date lastActive;

    @ApiModelProperty(value = "用户使用ip数量")
    private Integer userQuota;

    @ApiModelProperty(value = "用户可注册的客户端数量")
    private Integer registerQuota;

    @ApiModelProperty(value = "用户是否是管理员")
    private Boolean admin;

    @ApiModelProperty(value = "授权专用账户")
    private String authAccount;

    @ApiModelProperty(value = "授权专用密码")
    private String authPwd;

    @ApiModelProperty(value = "出口配置最大配置数量")
    private Integer authWhiteIpCount;

    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    @ApiModelProperty(value = "用户登陆的token，客户端和web页面访问的凭证")
    private String loginToken;

    @ApiModelProperty(value = "转为api访问使用的token，存在数据库中，不发生改变")
    private String apiToken;

    @ApiModelProperty(value = "单台server qps限制")
    private Double qpsQuota;

    @ApiModelProperty(value = "是否共享")
    private Boolean shared;

    public static final String ID = "id";

    public static final String USER_NAME = "user_name";

    public static final String PASSWD = "passwd";

    public static final String LAST_ACTIVE = "last_active";

    public static final String USER_QUOTA = "user_quota";

    public static final String REGISTER_QUOTA = "register_quota";

    public static final String ADMIN = "admin";

    public static final String AUTH_ACCOUNT = "auth_account";

    public static final String AUTH_PWD = "auth_pwd";

    public static final String AUTH_WHITE_IP_COUNT = "auth_white_ip_count";

    public static final String CREATE_TIME = "create_time";

    public static final String API_TOKEN = "api_token";

    public static final String QPS_QUOTA = "qps_quota";

    public static final String SHARED = "shared";

    public boolean getIsAdmin() {
        return admin;
    }
}
