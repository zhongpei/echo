#!/usr/bin/env bash
now_dir=`pwd`
cd `dirname $0`

history_pid=`ps -ef | grep echo-meta-server | grep -v "grep" | awk '{print $2}'`

echo history_pid:${history_pid}
if [ -n "${history_pid}" ] ;then
    echo kill pid ${history_pid}
    kill -9 ${history_pid}
fi

echo refresh code
#git clean -dfx
git pull

mvn -Pprod -Pint -Dmaven.test.skip=true install

cd echo-meta-server

echo "assemble jar"
mvn -Pprod -Pint  -Dmaven.test.skip=true package

cd ..
echo "run project"
nohup java -jar echo-meta-server/target/echo-meta-server-0.0.1.jar >/dev/null 2>&1  &
echo "script success..."
cd ${now_dir}

sleep 2

now_pid=`ps -ef | grep echo-meta-server | grep -v "grep" | awk '{print $2}'`

echo project_running_on_pid:${now_pid}