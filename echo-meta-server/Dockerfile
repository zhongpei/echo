FROM openjdk:8-alpine
WORKDIR /app
COPY ./target .
CMD ["java", "-jar", "/app/echo-meta-server-0.0.1.jar","--server.port","8080"]