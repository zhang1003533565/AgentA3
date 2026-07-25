package com.example.appbackend.service;

import com.example.appbackend.websocket.AppMessageWebSocketHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class MessageRealtimeNotifierTest {

    @AfterEach
    void cleanupTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void mergesScopesAndOnlySendsAfterCommit() {
        AppMessageWebSocketHandler handler = mock(AppMessageWebSocketHandler.class);
        MessageRealtimeNotifier notifier = new MessageRealtimeNotifier(handler);
        TransactionSynchronizationManager.initSynchronization();

        notifier.notifyUser(7L, "chat", "sessions");
        notifier.notifyUser(7L, "app", "chat");

        verifyNoInteractions(handler);
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(TransactionSynchronization::afterCommit);
        synchronizations.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));

        verify(handler).sendToUser(7L, Set.of("chat", "sessions", "app"));
    }
}
