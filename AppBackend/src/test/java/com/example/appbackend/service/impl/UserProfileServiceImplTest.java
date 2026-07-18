package com.example.appbackend.service.impl;

import com.example.appbackend.entity.UserProfileDimension;
import com.example.appbackend.repository.UserProfileDimensionRepository;
import com.example.appbackend.repository.UserProfileEvidenceRepository;
import com.example.appbackend.service.SystemConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserProfileServiceImplTest {

    private UserProfileDimensionRepository dimensionRepository;
    private UserProfileEvidenceRepository evidenceRepository;
    private PythonAiProxyService pythonAiProxyService;
    private SystemConfigService systemConfigService;
    private UserProfileServiceImpl service;

    @BeforeEach
    void setUp() {
        dimensionRepository = mock(UserProfileDimensionRepository.class);
        evidenceRepository = mock(UserProfileEvidenceRepository.class);
        pythonAiProxyService = mock(PythonAiProxyService.class);
        systemConfigService = mock(SystemConfigService.class);

        when(dimensionRepository.findByUserIdAndDimensionKey(eq(42L), anyString()))
                .thenReturn(Optional.empty());
        when(dimensionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(evidenceRepository.countByUserId(42L)).thenReturn(0L);
        when(evidenceRepository.countByUserIdAndStatus(42L, "candidate")).thenReturn(0L);
        when(evidenceRepository.findByUserIdAndDimensionKeyAndCreateTimeAfter(
                eq(42L), eq("resource_preference"), any())).thenReturn(List.of());

        service = new UserProfileServiceImpl(
                dimensionRepository,
                evidenceRepository,
                pythonAiProxyService,
                systemConfigService,
                new ObjectMapper().findAndRegisterModules(),
                Runnable::run
        );
    }

    @Test
    void leaderLocalContextBuildsCompatibleFallbackWithoutRemoteSummary() {
        Map<String, Object> context = service.buildLeaderProfileContext(42L);

        assertThat(context)
                .containsKeys(
                        "overallScore",
                        "confidenceLevel",
                        "dataStatus",
                        "profileTags",
                        "aiSummary",
                        "strengthSummary",
                        "weaknessSummary",
                        "improvementSuggestions",
                        "summaryEngine",
                        "resourcePreference",
                        "outputPreferenceHints",
                        "dimensions",
                        "leaderUsageRules",
                        "updateContract"
                )
                .containsEntry("summaryEngine", "local_profile_summary_v1");
        assertThat(context.get("aiSummary")).asString().isNotBlank();
        assertThat((List<?>) context.get("dimensions")).hasSize(7);
        verifyNoInteractions(pythonAiProxyService, systemConfigService);
    }

    @Test
    void authorizationAwareContextStillSupportsProfileSummaryAgent() {
        when(systemConfigService.getValue(
                "ai.agent-bindings.profile_summary_agent.model", ""))
                .thenReturn("ai.service.text.profile-fast");
        when(pythonAiProxyService.queryRag(any(), eq("Bearer test-token"))).thenReturn(Map.of(
                "answer", """
                        {
                          "aiSummary": "AI-refined summary",
                          "strengthSummary": "AI-refined strength",
                          "weaknessSummary": "AI-refined weakness"
                        }
                        """
        ));

        Map<String, Object> context = service.buildLeaderProfileContext(42L, "Bearer test-token");

        assertThat(context)
                .containsEntry("summaryEngine", "profile_summary_agent")
                .containsEntry("aiSummary", "AI-refined summary")
                .containsEntry("strengthSummary", "AI-refined strength")
                .containsEntry("weaknessSummary", "AI-refined weakness");
        Map<String, Object> cachedContext = service.buildLeaderProfileContext(42L);
        assertThat(cachedContext)
                .containsEntry("summaryEngine", "profile_summary_agent")
                .containsEntry("aiSummary", "AI-refined summary");

        when(evidenceRepository.countByUserId(42L)).thenReturn(1L);
        Map<String, Object> changedProfileContext = service.buildLeaderProfileContext(42L);
        assertThat(changedProfileContext).containsEntry("summaryEngine", "local_profile_summary_v1");

        // A fingerprint mismatch removes the obsolete insight instead of letting it reappear.
        when(evidenceRepository.countByUserId(42L)).thenReturn(0L);
        Map<String, Object> revertedProfileContext = service.buildLeaderProfileContext(42L);
        assertThat(revertedProfileContext).containsEntry("summaryEngine", "local_profile_summary_v1");
        verify(pythonAiProxyService, times(1)).queryRag(any(), eq("Bearer test-token"));
    }

    @Test
    void asyncRefreshQueuesOnceDoesNotRetainAuthorizationInTheCachedInsightAndSkipsFreshCache() {
        List<Runnable> pending = new java.util.ArrayList<>();
        Executor capturingExecutor = pending::add;
        service = new UserProfileServiceImpl(
                dimensionRepository,
                evidenceRepository,
                pythonAiProxyService,
                systemConfigService,
                new ObjectMapper().findAndRegisterModules(),
                capturingExecutor
        );
        when(systemConfigService.getValue(
                "ai.agent-bindings.profile_summary_agent.model", ""))
                .thenReturn("ai.service.text.profile-fast");
        when(pythonAiProxyService.queryRag(any(), eq("Bearer test-token"))).thenReturn(Map.of(
                "answer", """
                        {
                          "aiSummary": "Async AI summary",
                          "strengthSummary": "Async AI strength",
                          "weaknessSummary": "Async AI weakness"
                        }
                        """
        ));

        service.refreshLeaderProfileContextAsync(42L, "Bearer test-token");
        service.refreshLeaderProfileContextAsync(42L, "Bearer test-token");

        assertThat(pending).hasSize(1);
        verifyNoInteractions(pythonAiProxyService, systemConfigService);

        pending.removeFirst().run();
        Map<String, Object> cachedContext = service.buildLeaderProfileContext(42L);
        assertThat(cachedContext)
                .containsEntry("summaryEngine", "profile_summary_agent")
                .containsEntry("aiSummary", "Async AI summary");
        assertThat(cachedContext.toString()).doesNotContain("test-token", "Bearer");

        service.refreshLeaderProfileContextAsync(42L, "Bearer test-token");
        assertThat(pending).hasSize(1);
        pending.removeFirst().run();
        verify(pythonAiProxyService, times(1)).queryRag(any(), eq("Bearer test-token"));

        service.refreshLeaderProfileContextAsync(42L, "test-token-without-bearer");
        assertThat(pending).isEmpty();
    }
}
