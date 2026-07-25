package com.example.appbackend.service;

import com.example.appbackend.websocket.AppMessageWebSocketHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Service
public class MessageRealtimeNotifier {

    private static final long BROADCAST_USER_ID = -1L;
    private final Object transactionResourceKey = new Object();
    private final AppMessageWebSocketHandler socketHandler;

    public MessageRealtimeNotifier(AppMessageWebSocketHandler socketHandler) {
        this.socketHandler = socketHandler;
    }

    public void notifyUser(Long userId, String... scopes) {
        if (userId == null) {
            return;
        }
        enqueue(userId, scopes);
    }

    public void notifyAll(String... scopes) {
        enqueue(BROADCAST_USER_ID, scopes);
    }

    @SuppressWarnings("unchecked")
    private void enqueue(Long userId, String... scopes) {
        Set<String> normalized = normalize(scopes);
        if (normalized.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send(userId, normalized);
            return;
        }

        Map<Long, Set<String>> pending;
        if (TransactionSynchronizationManager.hasResource(transactionResourceKey)) {
            pending = (Map<Long, Set<String>>) TransactionSynchronizationManager.getResource(transactionResourceKey);
        } else {
            pending = new LinkedHashMap<>();
            TransactionSynchronizationManager.bindResource(transactionResourceKey, pending);
            Map<Long, Set<String>> captured = pending;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    captured.forEach(MessageRealtimeNotifier.this::send);
                }

                @Override
                public void afterCompletion(int status) {
                    if (TransactionSynchronizationManager.hasResource(transactionResourceKey)) {
                        TransactionSynchronizationManager.unbindResource(transactionResourceKey);
                    }
                }
            });
        }
        pending.computeIfAbsent(userId, ignored -> new LinkedHashSet<>()).addAll(normalized);
    }

    private Set<String> normalize(String... scopes) {
        Set<String> result = new LinkedHashSet<>();
        if (scopes != null) {
            for (String scope : scopes) {
                if (scope != null && !scope.isBlank()) {
                    result.add(scope.trim());
                }
            }
        }
        return result;
    }

    private void send(Long userId, Set<String> scopes) {
        if (BROADCAST_USER_ID == userId) {
            socketHandler.sendToAll(scopes);
        } else {
            socketHandler.sendToUser(userId, scopes);
        }
    }
}
