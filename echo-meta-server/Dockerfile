FROM openjdk:8-alpine
WORKDIR /app
COPY ./target .
EXPOSE 4826
CMD ["java", "-jar", "/app/echo-meta-server-0.0.1.jar"]