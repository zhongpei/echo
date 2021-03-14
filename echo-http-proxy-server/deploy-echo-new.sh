#!/usr/bin/env bash
source /etc/profile
now_dir=`pwd`
cd `dirname $0`
shell_dir=`pwd`

echo 'compile http proxy module'
./generate-bin.sh

echo 'push http binary'
nat_server_version=`cat target/echo_http_proxy_version.txt`
scp -r  target/dist-echo-http-proxy-${nat_server_version}/* root@echonew.virjar.com:/opt/echo/dist-echo-http-proxy-1.0/

echo 'shutdown echonew http proxy server'
remote_pid=`ssh root@echonew.virjar.com "ps -ef | grep 'dist-echo-http-proxy' | grep -v 'grep' " | awk '{print $2}'`

echo remote_pid:${remote_pid}
if [ -n "${remote_pid}" ] ;then
    echo kill pid ${remote_pid}
    ssh root@echonew.virjar.com "kill -9 ${remote_pid}"
fi

echo "start echonew http proxy server"
ssh root@echonew.virjar.com "nohup /opt/echo/dist-echo-http-proxy-1.0/bin/EchoHttpServer.sh >/dev/null 2>&1 &"

sleep 2

remote_pid=`ssh root@echonew.virjar.com "ps -ef | grep 'dist-echo-http-proxy' | grep -v 'grep' " | awk '{print $2}'`

echo "remote pid:${remote_pid}"





