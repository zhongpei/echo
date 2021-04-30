# Docker 部署说明

## docker-compose.yaml
- mysql 初始化
- echo-meta-server
- echo-admin-ui

## 独立启动其他容器

```sh
docker run --rm --name echo-nat-server -e API_ENTRY=http://192.168.50.104:4826 -p 5699:5699 -p 5698:5698 -p 20000-21000:20000-21000 registry.cn-beijing.aliyuncs.com/virjar/echo-nat-server:20210430 

docker run --rm --name echo-http-proxy-server -e API_ENTRY=http://192.168.50.104:4826 \
  -e MAPPING_SERVER_URL=http://192.168.50.104:4826/echoNatApi/connectionList \
  -e AUTH_CONFIG_URL=http://192.168.50.104:4826/echoNatApi/syncAuthConfig \
  -e MAPPING_SPACE="13001-14000" \
  -p 5710:5710 -p 13001-14000:13001-14000 \
  registry.cn-beijing.aliyuncs.com/virjar/echo-http-proxy-server:20210430

docker run --rm -e API_ENTRY=http://192.168.50.104:4826 -e CLIENT_ID=client-001 -e ECHO_ACCOUNT=admin -e ECHO_PASSWORD=admin registry.cn-beijing.aliyuncs.com/virjar/echo-client:20210430

./EchoNatServer.sh --api-entry http://192.168.50.104:4826 --server-id nat1 --mapping-space 10000-10010

./EchoHttpServer.sh --mapping-server-url http://192.168.50.104:4826/echoNatApi/connectionList --auth-config-url http://192.168.50.104:4826/echoNatApi/syncAuthConfig --api-entry http://192.168.50.104:4826/ --mapping-space 10010-10020

./EchoClient.sh --api-entry http://192.168.50.104:4826/ --echo-account admin --echo-password admin 
```