FROM registry.cn-hangzhou.aliyuncs.com/acs/maven AS build-env
RUN mkdir -p /root/.m2 \
    && mkdir /root/.m2/repository
# Copy maven settings, containing repository configurations
COPY settings.xml /root/.m2
ENV MY_HOME=/usr/src/app
RUN mkdir -p $MY_HOME
WORKDIR $MY_HOME
# ADD pom.xml $MY_HOME

# # get all the downloads out of the way
# RUN ["/usr/local/bin/mvn-entrypoint.sh","mvn","verify","clean","--fail-never"]
# add source
ADD . $MY_HOME
RUN cd $MY_HOME/echo-proxy-lib && mvn install
RUN cd $MY_HOME/echo-common && mvn install

RUN ./create-dist.sh
