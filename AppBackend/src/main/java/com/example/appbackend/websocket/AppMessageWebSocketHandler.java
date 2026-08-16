package com.example.appbackend.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AppMessageWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final Map<Long, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();

    public AppMessageWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = userId(session);
        if (userId != null) {
            userSessions.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        remove(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        remove(session);
        try {
            session.close(CloseStatus.SERVER_ERROR);
        } catch (Exception ignored) {
        }
    }

    public void sendToUser(Long userId, Set<String> scopes) {
        if (userId == null || scopes == null || scopes.isEmpty()) {
            return;
        }
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        send(sessions, scopes);
    }

    public void sendToAll(Set<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return;
        }
        userSessions.values().forEach(sessions -> send(sessions, scopes));
    }

    private void send(Set<WebSocketSession> sessions, Set<String> scopes) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "MESSAGE_STATE_CHANGED");
        payload.put("scopes", new ArrayList<>(scopes));
        final String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception ignored) {
            return;
        }
        sessions.removeIf(session -> !send(session, json));
    }

    private boolean send(WebSocketSession session, String json) {
        if (session == null || !session.isOpen()) {
            return false;
        }
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void remove(WebSocketSession session) {
        Long userId = userId(session);
        if (userId == null) {
            return;
        }
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                userSessions.remove(userId, sessions);
            }
        }
    }

    private Long userId(WebSocketSession session) {
        Object value = session.getAttributes().get("userId");
        return value instanceof Long id ? id : null;
    }
}
