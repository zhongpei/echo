#!/usr/bin/env bash
source /etc/profile
now_dir=`pwd`
cd `dirname $0`

shell_dir=`pwd`
cd ..
cd echo-proxy-lib/
mvn -Pint -Dmaven.test.skip=true clean install

cd ..
cd echo-common/
mvn -Pint -Dmaven.test.skip=true clean install



cd ${shell_dir}

mvn -Pint -Pprod -Dmaven.test.skip=true clean package appassembler:assemble

publish_version=`cat ./pom.xml | grep '<echo-nat-server-pom-version>' | awk -F "version>" '{print $2}' | awk -F "</echo-nat-server" '{print $1}'| sed 's/^\s*//;s/\s*$//' `

cd target/dist-echo-nat-${publish_version}/

chmod +x ./bin/EchoNatServer.sh
echo ${publish_version} > conf/echo_nat_version.txt

zip -r  dist-echo-nat-${publish_version}.zip ./*
mv dist-echo-nat-${publish_version}.zip ../
cp conf/echo_nat_version.txt ../