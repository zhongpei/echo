package com.virjar.echo.meta.server;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@SpringBootApplication
@MapperScan("com.virjar.echo.meta.server.mapper")
@EnableSwagger2
@EnableScheduling
public class EchoMataApplication {
    public static void main(String[] args) {
        SpringApplication.run(EchoMataApplication.class, args);
    }
}
