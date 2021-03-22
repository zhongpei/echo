FROM openjdk:8-alpine
WORKDIR /app
COPY ./target/dist-echo-nat-1.1 .
EXPOSE 32000-33000
EXPOSE 5699
EXPOSE 5698
ENV API_ENTRY="http://echonew.virjar.com/"
ENV SERVER_ID="echo-nat-001"
ENTRYPOINT [ "/app/bin/EchoNatServer.sh","--api-entry","$API_ENTRY"]