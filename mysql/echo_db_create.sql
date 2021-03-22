-- MySQL dump 10.13  Distrib 5.7.22, for macos10.13 (x86_64)
--
-- Host: rm-2zen9an8k64mtbbehfo.mysql.rds.aliyuncs.com    Database: echo
-- ------------------------------------------------------
-- Server version	5.7.28-log

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
SET @MYSQLDUMP_TEMP_LOG_BIN = @@SESSION.SQL_LOG_BIN;
SET @@SESSION.SQL_LOG_BIN= 0;

--
-- GTID state at the beginning of the backup 
--

SET @@GLOBAL.GTID_PURGED='ea29798a-34af-11eb-91a8-00163e2ca7d2:1-9839572';


--
-- Table structure for table `auth_white_ip`
--
CREATE DATABASE  `echo` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

use echo;

DROP TABLE IF EXISTS `auth_white_ip`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `auth_white_ip` (
  `id` bigint(11) NOT NULL AUTO_INCREMENT COMMENT '自增主建',
  `white_ip` varchar(50) DEFAULT NULL COMMENT '出口ip',
  `bind_account` varchar(64) NOT NULL COMMENT '对应的账户',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=49 DEFAULT CHARSET=utf8mb4 COMMENT='出口ip白名单';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `client_info`
--

DROP TABLE IF EXISTS `client_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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
) ENGINE=InnoDB AUTO_INCREMENT=116 DEFAULT CHARSET=utf8mb4 COMMENT='接入到平台的终端';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `downstream_server`
--

DROP TABLE IF EXISTS `downstream_server`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `downstream_server` (
  `id` bigint(11) NOT NULL AUTO_INCREMENT COMMENT '自增主建',
  `server_id` varchar(64) DEFAULT NULL COMMENT '服务器id，内部业务使用这个作为业务id',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `alive_time` datetime DEFAULT NULL COMMENT '最后存活时间，用来做心跳',
  `api_base_url` varchar(128) NOT NULL COMMENT 'API接口地址',
  `server_type` varchar(10) NOT NULL DEFAULT 'http' COMMENT '服务类型-> 0:http(socks)代理服务器，1:内网穿透服务(penetration)',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COMMENT='下游服务，比如http代理协议实现';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `nat_mapping_server`
--

DROP TABLE IF EXISTS `nat_mapping_server`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `nat_mapping_server` (
  `id` bigint(11) NOT NULL AUTO_INCREMENT COMMENT '自增主建',
  `server_id` varchar(64) DEFAULT NULL COMMENT '服务器id，内部业务使用这个作为业务id',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `alive_time` datetime DEFAULT NULL COMMENT '最后存活时间，用来做心跳',
  `api_base_url` varchar(128) NOT NULL COMMENT 'API接口地址',
  `nat_port` int(5) NOT NULL DEFAULT '5698' COMMENT 'nat接入端口，下发给终端使用',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COMMENT='mapping server,提供客户端网络mapping到同一台服务器的功能';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `penetration`
--

DROP TABLE IF EXISTS `penetration`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `penetration` (
  `id` bigint(11) NOT NULL AUTO_INCREMENT COMMENT '自增主建',
  `client_id` varchar(64) DEFAULT NULL COMMENT '终端ID',
  `bind_account` varchar(64) NOT NULL COMMENT '对应的账户',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `remote_ip` varchar(20) DEFAULT NULL COMMENT '映射远端ip地址，受限网络目标ip地址',
  `remote_port` int(5) DEFAULT NULL COMMENT '映射远端端口，受限网络目标port',
  `penetration_server_id` varchar(50) DEFAULT NULL COMMENT '端口映射的服务器id',
  `penetration_listen_ip` varchar(20) DEFAULT NULL COMMENT '监听PenetrationServer的ip地址',
  `penetration_listen_port` int(5) DEFAULT NULL COMMENT '监听PenetrationServer的端口号',
  `penetration_comment` varchar(128) DEFAULT NULL COMMENT '备注信息',
  `online` tinyint(1) DEFAULT '0' COMMENT '是否在线,默认为false，配置成功之后设置为true',
  `last_alive_time` datetime DEFAULT NULL COMMENT '最后存活时间时间',
  `rebind` tinyint(1) DEFAULT '0' COMMENT '自动重绑，默认不重绑',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='端口映射配置';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `proxy_resource`
--

DROP TABLE IF EXISTS `proxy_resource`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `proxy_resource` (
  `id` bigint(11) NOT NULL AUTO_INCREMENT COMMENT '自增主建',
  `proxy_ip` varchar(64) DEFAULT NULL COMMENT '代理ip',
  `proxy_port` int(11) NOT NULL DEFAULT '0' COMMENT '代理端口',
  `proxy_type` tinyint(4) NOT NULL DEFAULT '0' COMMENT '代理类型，如http/https/sockts/udp',
  `client_id` varchar(64) NOT NULL COMMENT '对应的客户端设备id',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `downstream_server_id` varchar(64) DEFAULT NULL COMMENT '对应出口服务器',
  `last_alive_time` datetime DEFAULT NULL COMMENT '最后存活时间,服务器同步的最后时间',
  `bind_account` varchar(64) NOT NULL COMMENT '对应的账户',
  `shared` tinyint(1) DEFAULT '1' COMMENT '是否共享',
  `out_ip` varchar(20) DEFAULT NULL COMMENT '出口ip',
  `online` tinyint(1) DEFAULT '1' COMMENT '是否在线',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5687 DEFAULT CHARSET=utf8mb4 COMMENT='代理ip资源';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_info`
--

DROP TABLE IF EXISTS `user_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `user_info` (
  `id` bigint(11) NOT NULL AUTO_INCREMENT COMMENT '自增主建',
  `user_name` varchar(64) DEFAULT NULL COMMENT '用户名',
  `passwd` varchar(64) DEFAULT NULL COMMENT '密码',
  `last_active` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后登陆时间',
  `user_quota` int(11) NOT NULL DEFAULT '0' COMMENT '用户使用ip数量',
  `register_quota` int(11) NOT NULL DEFAULT '1' COMMENT '用户可注册的客户端数量',
  `admin` tinyint(1) NOT NULL DEFAULT '0' COMMENT '用户是否是管理员',
  `auth_account` varchar(64) DEFAULT NULL COMMENT '授权专用账户\n',
  `auth_pwd` varchar(64) DEFAULT NULL COMMENT '授权专用账户',
  `auth_white_ip_count` smallint(6) NOT NULL DEFAULT '5' COMMENT '出口配置最大配置数量',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `login_token` varchar(64) DEFAULT NULL COMMENT '登录token',
  `api_token` varchar(64) DEFAULT '' COMMENT 'api 访问token',
  `qps_quota` double NOT NULL DEFAULT '1' COMMENT '单台server qps限制',
  `shared` tinyint(1) DEFAULT '1' COMMENT '是否共享',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=204 DEFAULT CHARSET=utf8mb4 COMMENT='用户信息';
/*!40101 SET character_set_client = @saved_cs_client */;
SET @@SESSION.SQL_LOG_BIN = @MYSQLDUMP_TEMP_LOG_BIN;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2021-03-21 20:32:51
