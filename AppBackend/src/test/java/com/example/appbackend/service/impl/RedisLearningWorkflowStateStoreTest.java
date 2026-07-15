package com.example.appbackend.service.impl;

import com.example.appbackend.dto.LearningPathDTO;
import com.example.appbackend.service.LearningWorkflowStateStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisLearningWorkflowStateStoreTest {

    @Test
    void storesCanonicalJsonForTwentyFourHours() throws Exception {
        RedisLearningWorkflowStateStore.RedisClient redis = mock(
                RedisLearningWorkflowStateStore.RedisClient.class);
        AtomicReference<String> serialized = new AtomicReference<>();
        AtomicReference<Duration> ttl = new AtomicReference<>();
        doAnswer(invocation -> {
            serialized.set(invocation.getArgument(1));
            ttl.set(invocation.getArgument(2));
            return null;
        }).when(redis).set(any(), any(), any(Duration.class));

        RedisLearningWorkflowStateStore store = new RedisLearningWorkflowStateStore(
                redis, new ObjectMapper().findAndRegisterModules());
        LearningWorkflowStateStore.WorkflowState state = state("wf-canonical", 42L);
        state.setContext(Map.of("z", 1, "a", 2));

        store.save(state);

        assertThat(serialized.get()).contains("\"a\":2").contains("\"z\":1");
        assertThat(serialized.get().indexOf("\"a\":2"))
                .isLessThan(serialized.get().indexOf("\"z\":1"));
        assertThat(ttl.get()).isEqualTo(Duration.ofHours(24));
    }

    @Test
    void redisFailureFallsBackToProcessMemoryAndRecoversOnNextRead() throws Exception {
        RedisLearningWorkflowStateStore.RedisClient redis = mock(
                RedisLearningWorkflowStateStore.RedisClient.class);
        when(redis.get(any())).thenThrow(new IOException("redis unavailable"));
        doThrow(new IOException("redis unavailable"))
                .when(redis).set(any(), any(), any(Duration.class));
        RedisLearningWorkflowStateStore store = new RedisLearningWorkflowStateStore(
                redis, new ObjectMapper().findAndRegisterModules());
        LearningWorkflowStateStore.WorkflowState state = state("wf-fallback", 42L);

        store.save(state);
        Optional<LearningWorkflowStateStore.WorkflowState> restored = store.find("wf-fallback");

        assertThat(restored).isPresent();
        assertThat(restored.orElseThrow().getOwnerUserId()).isEqualTo(42L);
        assertThat(restored.orElseThrow().getView().getStatus()).isEqualTo("accepted");
    }

    @Test
    void validRedisSnapshotRepopulatesFallbackCache() throws Exception {
        RedisLearningWorkflowStateStore.RedisClient redis = mock(
                RedisLearningWorkflowStateStore.RedisClient.class);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        LearningWorkflowStateStore.WorkflowState state = state("wf-redis", 7L);
        when(redis.get(eq("learning:workflow:wf-redis")))
                .thenReturn(mapper.writeValueAsString(state));
        RedisLearningWorkflowStateStore store = new RedisLearningWorkflowStateStore(redis, mapper);

        assertThat(store.find("wf-redis")).isPresent();

        when(redis.get(eq("learning:workflow:wf-redis")))
                .thenThrow(new IOException("later outage"));
        assertThat(store.find("wf-redis")).isPresent();
    }

    @Test
    void respCodecUsesUtf8ByteLengthsAndRejectsRedisErrors() throws Exception {
        byte[] command = RedisLearningWorkflowStateStore.RespRedisClient.encodeCommand(
                List.of("SET", "learning:workflow:wf-1", "学习", "EX", "86400"));
        String encoded = new String(command, StandardCharsets.UTF_8);

        assertThat(encoded).contains("$6\r\n学习\r\n");
        Object response = RedisLearningWorkflowStateStore.RespRedisClient.readResponse(
                new ByteArrayInputStream("$6\r\n学习\r\n".getBytes(StandardCharsets.UTF_8)));
        assertThat(response).isEqualTo("学习");
        org.junit.jupiter.api.Assertions.assertThrows(IOException.class,
                () -> RedisLearningWorkflowStateStore.RespRedisClient.readResponse(
                        new ByteArrayInputStream("-ERR denied\r\n"
                                .getBytes(StandardCharsets.US_ASCII))));
    }

    private LearningWorkflowStateStore.WorkflowState state(String workflowId, Long userId) {
        LearningPathDTO.WorkflowView view = new LearningPathDTO.WorkflowView();
        view.setWorkflowId(workflowId);
        view.setCourseKey("python");
        view.setStatus("accepted");
        view.setStage("accepted");
        view.setProgress(0);
        view.setResources(Map.of());
        view.setErrors(Map.of());
        LearningWorkflowStateStore.WorkflowState state = new LearningWorkflowStateStore.WorkflowState();
        state.setWorkflowId(workflowId);
        state.setOwnerUserId(userId);
        state.setView(view);
        return state;
    }
}
