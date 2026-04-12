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

/**
 * 腾讯地图 WebClient 配置：TLS 握手超時 / 連接超時 / 回應超時。
 *
 * Reactor Netty HttpClient 默认 TLS 握手超时仅 10s，在弱网或高延迟环境下容易超时。
 * 配置方式：通过 SslProvider.builder() → sslContext() → handshakeTimeout() → build()
 *           再注入 HttpClient.secure(SslProvider)。
 */
@Configuration
public class TencentMapConfig {

    /** TCP 连接超时（毫秒），默认 20s */
    private final int connectTimeoutMs = 20000;

    /** 整次请求（含 TLS + 等待 body）超时（毫秒），默认 45s */
    private final int responseTimeoutMs = 45000;

    /** TLS 握手超时（毫秒），默认 30s（比 Reactor Netty 默认 10s 更宽松） */
    private final int handshakeTimeoutMs = 30000;

    @Bean
    public WebClient tencentMapWebClient() {
        SslProvider sslProvider;
        try {
            sslProvider = SslProvider.builder()
                    .sslContext(SslContextBuilder.forClient().build())
                    .handshakeTimeout(Duration.ofMillis(handshakeTimeoutMs))
                    .build();
        } catch (SSLException e) {
            throw new IllegalStateException("初始化腾讯地图 HTTPS 客户端失败", e);
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
