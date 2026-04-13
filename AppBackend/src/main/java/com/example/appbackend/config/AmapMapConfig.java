package com.example.appbackend.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContextBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.tcp.SslProvider;

import javax.net.ssl.SSLException;
import java.time.Duration;

@Configuration
public class AmapMapConfig {

    private final int connectTimeoutMs = 20000;
    private final int responseTimeoutMs = 45000;
    private final int handshakeTimeoutMs = 30000;

    @Bean
    public WebClient amapWebClient() {
        SslProvider sslProvider;
        try {
            sslProvider = SslProvider.builder()
                    .sslContext(SslContextBuilder.forClient().build())
                    .handshakeTimeout(Duration.ofMillis(handshakeTimeoutMs))
                    .build();
        } catch (SSLException e) {
            throw new IllegalStateException("初始化高德地图 HTTPS 客户端失败", e);
        }

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .responseTimeout(Duration.ofMillis(responseTimeoutMs))
                .secure(sslProvider);

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
