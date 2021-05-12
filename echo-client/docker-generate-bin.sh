#!/usr/bin/env bash
source /etc/profile
now_dir=`pwd`
cd `dirname $0`

shell_dir=`pwd`
cd ..
cd echo-common/
mvn -Pint -Dmaven.test.skip=true clean install

cd ${shell_dir}

mvn -Pint -Pdocker clean package appassembler:assemble

## 现在计算版本号，解析发布带版本号的路径
publish_version=`cat ./pom.xml | grep '<echo-client-pom-version>' | awk -F "version>" '{print $2}' | awk -F "</echo-client" '{print $1}'| sed 's/^\s*//;s/\s*$//' `

cd target/dist-echo-client-${publish_version}/

chmod +x ./bin/EchoClient.sh
echo ${publish_version} > conf/echo_client_version.txt

zip -r  echo-client-${publish_version}.zip ./*
mv echo-client-${publish_version}.zip ../
cp conf/echo_client_version.txt ../