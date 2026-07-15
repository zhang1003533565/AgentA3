package com.example.appbackend.service.impl;

import com.example.appbackend.dto.LearningPathDTO;
import com.example.appbackend.service.LearningWorkflowStateStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
    void authoritativeReadNeverReturnsTheProcessFallback() throws Exception {
        RedisLearningWorkflowStateStore.RedisClient redis = mock(
                RedisLearningWorkflowStateStore.RedisClient.class);
        doThrow(new IOException("redis unavailable"))
                .when(redis).set(any(), any(), any(Duration.class));
        when(redis.get(eq("learning:workflow:wf-authoritative")))
                .thenThrow(new IOException("redis unavailable"));
        RedisLearningWorkflowStateStore store = new RedisLearningWorkflowStateStore(
                redis, new ObjectMapper().findAndRegisterModules());
        store.save(state("wf-authoritative", 42L));

        assertThat(store.find("wf-authoritative")).isPresent();
        assertThatThrownBy(() -> store.findAuthoritatively("wf-authoritative"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("authoritative state unavailable");
    }

    @Test
    void successfulRedisWritesDoNotCreateAnUnboundedShadowCopy() throws Exception {
        RedisLearningWorkflowStateStore.RedisClient redis = mock(
                RedisLearningWorkflowStateStore.RedisClient.class);
        RedisLearningWorkflowStateStore store = new RedisLearningWorkflowStateStore(
                redis, new ObjectMapper().findAndRegisterModules());

        store.save(state("wf-redis-only", 42L));
        when(redis.get(any())).thenThrow(new IOException("later outage"));

        assertThat(store.find("wf-redis-only")).isEmpty();
        assertThat(store.fallbackSize()).isZero();
    }

    @Test
    void fallbackCacheHasTtlCapacityAndBatchEvictionWithAControllableClock() throws Exception {
        RedisLearningWorkflowStateStore.RedisClient redis = mock(
                RedisLearningWorkflowStateStore.RedisClient.class);
        doThrow(new IOException("redis unavailable"))
                .when(redis).set(any(), any(), any(Duration.class));
        when(redis.get(any())).thenThrow(new IOException("redis unavailable"));
        MutableClock clock = new MutableClock(Instant.parse("2026-07-15T00:00:00Z"));
        RedisLearningWorkflowStateStore store = new RedisLearningWorkflowStateStore(
                redis, new ObjectMapper().findAndRegisterModules(), clock, 3);

        for (int index = 0; index < 8; index++) {
            store.save(state("wf-" + index, 42L));
            clock.advance(Duration.ofSeconds(1));
        }

        assertThat(store.fallbackSize()).isEqualTo(3);
        assertThat(store.find("wf-0")).isEmpty();
        assertThat(store.find("wf-7")).isPresent();

        clock.advance(Duration.ofHours(25));
        assertThat(store.find("wf-7")).isEmpty();
        assertThat(store.fallbackSize()).isZero();
    }

    @Test
    void retryClaimsUseAtomicRedisSetNxAndTokenCheckedRelease() throws Exception {
        RedisLearningWorkflowStateStore.RedisClient redis = mock(
                RedisLearningWorkflowStateStore.RedisClient.class);
        when(redis.setIfAbsent(any(), any(), any(Duration.class)))
                .thenReturn(true, false);
        RedisLearningWorkflowStateStore store = new RedisLearningWorkflowStateStore(
                redis, new ObjectMapper().findAndRegisterModules());

        Optional<String> first = store.claimRetry("wf-claim", "code_lab");
        Optional<String> second = store.claimRetry("wf-claim", "practice_set");

        assertThat(first).isPresent();
        assertThat(second).isEmpty();
        store.releaseRetryClaim("wf-claim", "code_lab", first.orElseThrow());
        verify(redis, times(2)).setIfAbsent(
                eq("learning:workflow:retry:wf-claim"), any(), eq(Duration.ofMinutes(10)));
        verify(redis).compareAndDelete(
                "learning:workflow:retry:wf-claim", first.orElseThrow());
    }

    @Test
    void redisOutageFailsClosedForRetryClaimsAcrossServiceInstances() throws Exception {
        RedisLearningWorkflowStateStore.RedisClient redis = mock(
                RedisLearningWorkflowStateStore.RedisClient.class);
        when(redis.setIfAbsent(any(), any(), any(Duration.class)))
                .thenThrow(new IOException("redis unavailable"));
        RedisLearningWorkflowStateStore firstInstance = new RedisLearningWorkflowStateStore(
                redis, new ObjectMapper().findAndRegisterModules());
        RedisLearningWorkflowStateStore secondInstance = new RedisLearningWorkflowStateStore(
                redis, new ObjectMapper().findAndRegisterModules());

        assertThat(firstInstance.claimRetry("wf-concurrent", "code_lab")).isEmpty();
        assertThat(secondInstance.claimRetry("wf-concurrent", "practice_set")).isEmpty();
    }

    @Test
    void retryStateWritesRenewOrReleaseOnlyInsideTheTokenFencedOperation()
            throws Exception {
        RedisLearningWorkflowStateStore.RedisClient redis = mock(
                RedisLearningWorkflowStateStore.RedisClient.class);
        when(redis.get("learning:workflow:retry:wf-fenced"))
                .thenReturn("claim-token");
        when(redis.compareAndExpire(
                "learning:workflow:retry:wf-fenced",
                "claim-token",
                Duration.ofMinutes(10)))
                .thenReturn(true);
        when(redis.writeStateIfClaimOwner(
                eq("learning:workflow:wf-fenced"),
                any(),
                eq(Duration.ofHours(24)),
                eq("learning:workflow:retry:wf-fenced"),
                eq("claim-token"),
                eq(Duration.ofMinutes(10)),
                eq(false)))
                .thenReturn(true);
        when(redis.writeStateIfClaimOwner(
                eq("learning:workflow:wf-fenced"),
                any(),
                eq(Duration.ofHours(24)),
                eq("learning:workflow:retry:wf-fenced"),
                eq("claim-token"),
                eq(Duration.ofMinutes(10)),
                eq(true)))
                .thenReturn(true);
        RedisLearningWorkflowStateStore store = new RedisLearningWorkflowStateStore(
                redis, new ObjectMapper().findAndRegisterModules());
        LearningWorkflowStateStore.WorkflowState state = state("wf-fenced", 42L);

        assertThat(store.isRetryClaimOwner(
                "wf-fenced", "code_lab", "claim-token")).isTrue();
        assertThat(store.renewRetryClaim(
                "wf-fenced", "code_lab", "claim-token")).isTrue();
        assertThat(store.saveRetryState(
                state, "code_lab", "claim-token")).isTrue();
        assertThat(store.completeRetryState(
                state, "code_lab", "claim-token")).isTrue();

        verify(redis, never()).compareAndDelete(any(), any());
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

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
