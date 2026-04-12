package com.example.appbackend.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Getter
@Configuration
public class DeepSeekConfig {

    @Value("${deepseek.base-url}")
    private String baseUrl;

    @Value("${deepseek.api-key}")
    private String apiKey;

    @Value("${deepseek.model}")
    private String model;
}
