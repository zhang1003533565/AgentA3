package com.example.appbackend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .openapi("3.0.1")
                .info(new Info()
                        .title("Smart Campus API")
                        .version("1.0")
                        .description("智慧校园系统后端API文档")
                        .contact(new Contact()
                                .name("Smart Campus Team")
                                .email("support@smartcampus.com")));
    }
}
