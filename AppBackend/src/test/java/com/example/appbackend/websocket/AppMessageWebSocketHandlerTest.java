package com.example.appbackend.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppMessageWebSocketHandlerTest {

    @Test
    void sendsOnlyToTheAffectedUsersOpenSessions() throws Exception {
        AppMessageWebSocketHandler handler = new AppMessageWebSocketHandler(new ObjectMapper());
        WebSocketSession target = session(7L);
        WebSocketSession unrelated = session(8L);
        handler.afterConnectionEstablished(target);
        handler.afterConnectionEstablished(unrelated);

        handler.sendToUser(7L, Set.of("chat", "app"));

        verify(target).sendMessage(argThat(message -> message instanceof TextMessage text
                && text.getPayload().contains("MESSAGE_STATE_CHANGED")
                && text.getPayload().contains("chat")
                && text.getPayload().contains("app")));
        verify(unrelated, never()).sendMessage(org.mockito.ArgumentMatchers.any());
    }

    private WebSocketSession session(Long userId) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getAttributes()).thenReturn(Map.of("userId", userId));
        when(session.isOpen()).thenReturn(true);
        return session;
    }
}
