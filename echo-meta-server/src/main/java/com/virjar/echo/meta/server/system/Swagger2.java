package com.virjar.echo.meta.server.system;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.ParameterBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.schema.ModelRef;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Parameter;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by virjar on 2018/8/4.<br>
 * swagger集成
 */
@Configuration
public class Swagger2 {


    @Bean
    public Docket createRestApi() {
        return new Docket(DocumentationType.SWAGGER_2)
                .apiInfo(apiInfo())
                .select()
                .apis(RequestHandlerSelectors.basePackage("com.virjar.echo.meta.server.controller"))
                .paths(PathSelectors.any())
                .build()
                .globalOperationParameters(parameter());
    }

    private List<Parameter> parameter() {
        List<Parameter> params = new ArrayList<>();
        params.add(new ParameterBuilder().name("Token")
                .description("请求令牌，访问登陆接口获得(登陆注册接口不需要这个参数): /echo-api/user-info/login")
                .modelRef(new ModelRef("string"))
                .parameterType("query")
                .required(false).build());
        return params;
    }
    private ApiInfo apiInfo() {
        return new ApiInfoBuilder()
                .title("echo系统")
                .description("echo代理系统")
                .termsOfServiceUrl("https://git.virjar.com/echo/echo-core")
                .version("1.0")
                .build();
    }
}
