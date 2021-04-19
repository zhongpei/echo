FROM openjdk:8-alpine
WORKDIR /app
COPY ./target .
EXPOSE 32000-33000
EXPOSE 5699
EXPOSE 5698
ENV API_ENTRY="http://echonew.virjar.com/"
ENV MAPPING_SERVER_URL="http://127.0.0.1:4826/echoNatApi/connectionList"
ENV MAPPING_SERVER_FORCE_HOST="127.0.0.1"
ENV AUTH_CONFIG_URL="http://127.0.0.1:4826/echoNatApi/syncAuthConfig"
ENV SERVER_ID="echo-http-proxy-001"
ENTRYPOINT [ "/app/bin/EchoHttpServer.sh",\
    "--mapping-server-url","$MAPPING_SERVER_URL", \
    # "--mapping-server-force-host","$MAPPING_SERVER_FORCE_HOST",\
    "--auth-config-url", "$AUTH_CONFIG_URL",\
    "--api-entry", "$API_ENTRY"]