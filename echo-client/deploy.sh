#!/usr/bin/env bash

source /etc/profile
now_dir=`pwd`
cd `dirname $0`
shell_dir=`pwd`


function do_deploy(){

    remote_server=$1
    echo 'compile echo client  module'
    ./generate-bin.sh

    ssh root@${remote_server} "[[ ! -d /opt/echo/ ]] && mkdir /opt/echo/"

    ssh root@${remote_server} "[[ ! -d /opt/echo/dist-echo-client-1.0/ ]] && mkdir /opt/echo/dist-echo-client-1.0/"

    echo 'push echo client binary'
    scp -r  target/dist-echo-client-1.0/* root@${remote_server}:/opt/echo/dist-echo-client-1.0/

    echo "install bash script for server:${remote_server}"
    ssh root@${remote_server} "source /etc/profile && /opt/echo/dist-echo-client-1.0/bin/EchoClient.sh -i"

    echo "install finished, your echo client service will auto start in next system reboot"
}


if [[ $# -ge 1 ]] ;then
   do_deploy $1
else
   echo "please input remote server address"
fi









