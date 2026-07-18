package com.example.appbackend.config;

import com.example.appbackend.websocket.AppMessageHandshakeInterceptor;
import com.example.appbackend.websocket.AppMessageWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class AppMessageWebSocketConfig implements WebSocketConfigurer {

    private final AppMessageWebSocketHandler handler;
    private final AppMessageHandshakeInterceptor handshakeInterceptor;

    public AppMessageWebSocketConfig(AppMessageWebSocketHandler handler,
                                     AppMessageHandshakeInterceptor handshakeInterceptor) {
        this.handler = handler;
        this.handshakeInterceptor = handshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/api/realtime/messages")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOrigins("*");
    }
}
