FROM openjdk:8-alpine
WORKDIR /app
COPY ./target/dist-echo-http-proxy-1.1 .
COPY ./src/main/resources-env/dev/log4j.xml /app/conf/
EXPOSE 33001-34000
EXPOSE 5710
ENV API_ENTRY="http://echonew.virjar.com/"
ENV MAPPING_SERVER_URL="http://127.0.0.1:4826/echoNatApi/connectionList"
ENV MAPPING_SERVER_FORCE_HOST="127.0.0.1"
ENV AUTH_CONFIG_URL="http://127.0.0.1:4826/echoNatApi/syncAuthConfig"
ENV SERVER_ID="echo-http-proxy-001"
ENV MAPPING_SPACE="33001-34000"
ENTRYPOINT "/app/bin/EchoHttpServer.sh" \
    "--mapping-server-url" "${MAPPING_SERVER_URL}"  \
    "--auth-config-url" "${AUTH_CONFIG_URL}" \
    "--api-entry" "${API_ENTRY}" \
    "--mapping-space" "${MAPPING_SPACE}"
