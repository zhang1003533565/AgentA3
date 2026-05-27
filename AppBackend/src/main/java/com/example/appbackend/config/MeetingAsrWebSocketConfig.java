package com.example.appbackend.config;

import com.example.appbackend.websocket.MeetingAsrHandshakeInterceptor;
import com.example.appbackend.websocket.MeetingAsrWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class MeetingAsrWebSocketConfig implements WebSocketConfigurer {

    private final MeetingAsrWebSocketHandler meetingAsrWebSocketHandler;
    private final MeetingAsrHandshakeInterceptor meetingAsrHandshakeInterceptor;

    public MeetingAsrWebSocketConfig(MeetingAsrWebSocketHandler meetingAsrWebSocketHandler,
                                     MeetingAsrHandshakeInterceptor meetingAsrHandshakeInterceptor) {
        this.meetingAsrWebSocketHandler = meetingAsrWebSocketHandler;
        this.meetingAsrHandshakeInterceptor = meetingAsrHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(meetingAsrWebSocketHandler, "/api/meetings/{sessionId}/asr/stream")
                .addInterceptors(meetingAsrHandshakeInterceptor)
                .setAllowedOrigins("*");
    }
}
