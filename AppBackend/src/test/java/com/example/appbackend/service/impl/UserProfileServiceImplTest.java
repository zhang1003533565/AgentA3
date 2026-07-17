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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
                new ObjectMapper().findAndRegisterModules()
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
        verify(pythonAiProxyService).queryRag(any(), eq("Bearer test-token"));
    }
}
