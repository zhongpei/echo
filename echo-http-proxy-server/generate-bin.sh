#!/usr/bin/env bash
source /etc/profile
now_dir=`pwd`
cd `dirname $0`

shell_dir=`pwd`
cd ..
cd echo-lib/
mvn -Pint -Dmaven.test.skip=true clean install

cd ..
cd echo-common/
mvn -Pint -Dmaven.test.skip=true clean install


cd ${shell_dir}

mvn  -Pint -Pprod -Dmaven.test.skip=true clean package appassembler:assemble

publish_version=`cat ./pom.xml | grep '<echo-http-proxy-pom-version>' | awk -F "version>" '{print $2}' | awk -F "</echo-http-proxy" '{print $1}'| sed 's/^\s*//;s/\s*$//' `

cd target/dist-echo-http-proxy-${publish_version}/

chmod +x ./bin/EchoHttpServer.sh
echo ${publish_version} > conf/echo_http_proxy_version.txt

zip -r  echo-http-proxy-${publish_version}.zip ./*
mv echo-http-proxy-${publish_version}.zip ../
cp conf/echo_http_proxy_version.txt ../