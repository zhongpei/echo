CREATE TABLE `auth_white_ip` (
  `id` bigint(11) NOT NULL AUTO_INCREMENT COMMENT '自增主建',
  `white_ip` varchar(50) DEFAULT NULL COMMENT '出口ip',
  `bind_account` varchar(64) NOT NULL COMMENT '对应的账户',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB  DEFAULT CHARSET=utf8mb4 COMMENT='出口ip白名单';

CREATE TABLE `client_info` (
  `id` bigint(11) NOT NULL AUTO_INCREMENT COMMENT '自增主建',
  `client_id` varchar(64) DEFAULT NULL COMMENT '终端ID',
  `bind_account` varchar(64) NOT NULL COMMENT '对应的账户',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `client_out_ip` varchar(20) DEFAULT NULL COMMENT '客户端出口ip',
  `last_alive_time` datetime DEFAULT NULL COMMENT '最后存活时间时间',
  `listen_ip` varchar(20) DEFAULT NULL COMMENT '监听NatServer的ip地址',
  `listen_port` int(5) DEFAULT NULL COMMENT '监听NatServer的端口号',
  `shared` tinyint(1) DEFAULT '0' COMMENT '改资源是否是共享资源',
  `server_id` varchar(50) DEFAULT NULL COMMENT '监听的服务器id',
  `online` tinyint(1) DEFAULT '1' COMMENT '是否在线',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB  DEFAULT CHARSET=utf8mb4 COMMENT='接入到平台的终端';

CREATE TABLE `downstream_server` (
  `id` bigint(11) NOT NULL AUTO_INCREMENT COMMENT '自增主建',
  `server_id` varchar(64) DEFAULT NULL COMMENT '服务器id，内部业务使用这个作为业务id',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `alive_time` datetime DEFAULT NULL COMMENT '最后存活时间，用来做心跳',
  `api_base_url` varchar(128) NOT NULL COMMENT 'API接口地址',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB  DEFAULT CHARSET=utf8mb4 COMMENT='下游服务，比如http代理协议实现';

CREATE TABLE `nat_mapping_server` (
  `id` bigint(11) NOT NULL AUTO_INCREMENT COMMENT '自增主建',
  `server_id` varchar(64) DEFAULT NULL COMMENT '服务器id，内部业务使用这个作为业务id',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `alive_time` datetime DEFAULT NULL COMMENT '最后存活时间，用来做心跳',
  `api_base_url` varchar(128) NOT NULL COMMENT 'API接口地址',
  `nat_port` int(5) NOT NULL DEFAULT '5698' COMMENT 'nat接入端口，下发给终端使用',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB  DEFAULT CHARSET=utf8mb4 COMMENT='mapping server,提供客户端网络mapping到同一台服务器的功能';

CREATE TABLE `proxy_resource` (
  `id` bigint(11) NOT NULL AUTO_INCREMENT COMMENT '自增主建',
  `proxy_ip` varchar(64) DEFAULT NULL COMMENT '代理ip',
  `proxy_port` int(11) NOT NULL DEFAULT '0' COMMENT '代理端口',
  `proxy_type` tinyint(4) NOT NULL DEFAULT '0' COMMENT '代理类型，如http/https/socks/udp',
  `client_id` varchar(64) NOT NULL COMMENT '对应的客户端设备id',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `downstream_server_id` varchar(64) DEFAULT NULL COMMENT '对应出口服务器',
  `last_alive_time` datetime DEFAULT NULL COMMENT '最后存活时间,服务器同步的最后时间',
  `bind_account` varchar(64) NOT NULL COMMENT '对应的账户',
  `shared` tinyint(1) DEFAULT '1' COMMENT '是否共享',
  `out_ip` varchar(20) DEFAULT NULL COMMENT '出口ip',
  `online` tinyint(1) DEFAULT '1' COMMENT '是否在线',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB  DEFAULT CHARSET=utf8mb4 COMMENT='代理ip资源';

CREATE TABLE `user_info` (
  `id` bigint(11) NOT NULL AUTO_INCREMENT COMMENT '自增主建',
  `user_name` varchar(64) DEFAULT NULL COMMENT '用户名',
  `passwd` varchar(64) DEFAULT NULL COMMENT '密码',
  `last_active` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后登陆时间',
  `user_quota` int(11) NOT NULL DEFAULT '0' COMMENT '用户使用ip数量',
  `register_quota` int(11) NOT NULL DEFAULT '1' COMMENT '用户可注册的客户端数量',
  `admin` tinyint(1) NOT NULL DEFAULT '0' COMMENT '用户是否是管理员',
  `auth_account` varchar(64) DEFAULT NULL COMMENT '授权专用账户',
  `auth_pwd` varchar(64) DEFAULT NULL COMMENT '授权专用账户',
  `auth_white_ip_count` smallint(6) NOT NULL DEFAULT '5' COMMENT '出口配置最大配置数量',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `login_token` varchar(64) DEFAULT NULL COMMENT '登录token',
  `api_token` varchar(64) DEFAULT '' COMMENT 'api 访问token',
  `qps_quota` double NOT NULL DEFAULT '1' COMMENT '单台server qps限制',
  `shared` tinyint(1) DEFAULT '1' COMMENT '是否共享',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB  DEFAULT CHARSET=utf8mb4 COMMENT='用户信息';