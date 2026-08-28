package com.example.appbackend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    /**
     * 共享 WebClient：智能体目录等 JSON 可能超过默认 256KB 缓冲上限。
     */
    @Bean
    public WebClient.Builder webClientBuilder(
            @Value("${ai.python.max-in-memory-bytes:52428800}") int maxInMemoryBytes) {
        return WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(maxInMemoryBytes));
    }
}
