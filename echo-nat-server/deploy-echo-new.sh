#!/usr/bin/env bash
source /etc/profile
now_dir=`pwd`
cd `dirname $0`
shell_dir=`pwd`

echo 'compile echo nat  module'
./generate-bin.sh

echo 'push echo nat binary'
nat_server_version=`cat target/echo_nat_version.txt`
scp -r  target/dist-echo-nat-${nat_server_version}/* root@echonew.virjar.com:/opt/echo/dist-echo-nat-1.0/

echo 'shutdown echonew echo nat server'
remote_pid=`ssh root@echonew.virjar.com "ps -ef | grep 'dist-echo-nat-1.0' | grep -v 'grep' " | awk '{print $2}'`

echo remote_pid:${remote_pid}
if [ -n "${remote_pid}" ] ;then
    echo kill pid ${remote_pid}
    ssh root@echonew.virjar.com "kill -9 ${remote_pid}"
fi

echo "start echonew echo nat server"
ssh root@echonew.virjar.com "nohup /opt/echo/dist-echo-nat-1.0/bin/EchoNatServer.sh >/dev/null 2>&1 &"

sleep 2

remote_pid=`ssh root@echonew.virjar.com "ps -ef | grep 'dist-echo-nat' | grep -v 'grep' " | awk '{print $2}'`

echo "remote pid:${remote_pid}"





