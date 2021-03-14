#!/bin/bash
# https://www.jianshu.com/p/4945a63b60a4
function shutdown()
{
# 关机用的脚本放这里
  exit 0
}

function startup()
{

  # 开机用的脚本放这里
  now_dir=`pwd`
  cd `dirname $0`

  # 先加载一下环境变量，难道太早了java环境变量还没有起来？？
  source /etc/profile
  chmod +x ./EchoClient.sh
  nohup ./EchoClient.sh  >/dev/null 2>&1 &
}

trap shutdown SIGTERM
trap shutdown SIGKILL

startup;