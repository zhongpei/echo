#!/usr/bin/env bash
now_dir=`pwd`
cd `dirname $0`

shell_dir=`pwd`
cd ..

mvn -Pprod -Pint -Dmaven.test.skip=true install

cd ${shell_dir}

echo "assemble jar"
mvn -Pprod -Pint  -Dmaven.test.skip=true package

cd ${now_dir}