package com.example.appbackend.service.impl;

import com.example.appbackend.dto.AssistantResourceInteractionRequest;
import com.example.appbackend.dto.AssistantResourceInteractionResponse;
import com.example.appbackend.dto.LearningPathDTO;
import com.example.appbackend.dto.UserProfileDTO;
import com.example.appbackend.entity.AiLeaderMessage;
import com.example.appbackend.entity.AiLeaderSession;
import com.example.appbackend.entity.AiLeaderResourceInteraction;
import com.example.appbackend.entity.UserProfileEvidence;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.AiLeaderMessageRepository;
import com.example.appbackend.repository.AiLeaderResourceInteractionRepository;
import com.example.appbackend.repository.AiLeaderSessionRepository;
import com.example.appbackend.repository.UserProfileDimensionRepository;
import com.example.appbackend.repository.UserProfileEvidenceRepository;
import com.example.appbackend.service.SystemConfigService;
import com.example.appbackend.service.LearningPathService;
import com.example.appbackend.service.UserProfileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssistantResourceInteractionServiceTest {

    private AiLeaderSessionRepository sessionRepository;
    private AiLeaderMessageRepository messageRepository;
    private AiLeaderResourceInteractionRepository interactionRepository;
    private UserProfileService userProfileService;
    private LearningPathService learningPathService;
    private AssistantResourceInteractionService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(AiLeaderSessionRepository.class);
        messageRepository = mock(AiLeaderMessageRepository.class);
        interactionRepository = mock(AiLeaderResourceInteractionRepository.class);
        userProfileService = mock(UserProfileService.class);
        learningPathService = mock(LearningPathService.class);
        objectMapper = new ObjectMapper();
        service = new AssistantResourceInteractionService(
                sessionRepository,
                messageRepository,
                interactionRepository,
                userProfileService,
                learningPathService,
                objectMapper
        );

        AiLeaderSession session = new AiLeaderSession();
        session.setId(9L);
        session.setUserId(42L);
        session.setSessionId("session-1");
        when(sessionRepository.findForUpdateByUserIdAndSessionId(42L, "session-1"))
                .thenReturn(Optional.of(session));

        AiLeaderMessage message = new AiLeaderMessage();
        message.setId(101L);
        message.setLeaderSessionId(9L);
        message.setRole(AiLeaderMessage.ROLE_ASSISTANT);
        message.setResourcesJson("""
                [{
                  "schemaVersion":"assistant-resource-v1",
                  "id":"res_doc",
                  "kind":"document",
                  "deliveryType":"document",
                  "title":"高数复习讲义"
                }]
                """);
        message.setEvidenceChainJson("""
                {"schemaVersion":"assistant-evidence-v1","chainId":"chain_abc123"}
                """);
        when(messageRepository.findById(101L)).thenReturn(Optional.of(message));
        when(interactionRepository.existsById(any())).thenReturn(false);
        when(interactionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UserProfileDTO.EvidenceResponse profileResponse = new UserProfileDTO.EvidenceResponse();
        profileResponse.setStatus("candidate");
        when(userProfileService.addEvidence(any(), any())).thenReturn(profileResponse);
    }

    @Test
    void recordsOnlyServerDerivedResourceEvidence() {
        AssistantResourceInteractionResponse response = service.record(
                42L, "session-1", 101L, "res_doc", request("download"));

        ArgumentCaptor<UserProfileDTO.EvidenceRequest> evidenceCaptor =
                ArgumentCaptor.forClass(UserProfileDTO.EvidenceRequest.class);
        verify(userProfileService).addEvidence(org.mockito.ArgumentMatchers.eq(42L), evidenceCaptor.capture());
        UserProfileDTO.EvidenceRequest evidence = evidenceCaptor.getValue();

        assertThat(response.getStatus()).isEqualTo("recorded");
        assertThat(response.isDuplicate()).isFalse();
        assertThat(response.getSourceId()).matches("ari_[0-9a-f]{64}");
        assertThat(evidence.getDimensionKey()).isEqualTo("resource_preference");
        assertThat(evidence.getSourceType()).isEqualTo("assistant_resource");
        assertThat(evidence.getSourceId()).isEqualTo(response.getSourceId());
        assertThat(evidence.getAction()).isEqualTo("download");
        assertThat(evidence.getObjectType()).isEqualTo("assistant_resource");
        assertThat(evidence.getObjectId()).isEqualTo("res_doc");
        assertThat(evidence.getObjectName()).isEqualTo("高数复习讲义");
        assertThat(evidence.getEvidence()).contains("高数复习讲义", "下载");
        assertThat(evidence.getDirection()).isEqualTo("increase");
        assertThat(evidence.getSuggestedDelta()).isEqualTo(2);
        assertThat(evidence.getMetadata()).containsEntry("sessionId", "session-1")
                .containsEntry("messageId", 101L)
                .containsEntry("resourceKind", "document")
                .containsEntry("deliveryType", "document")
                .containsEntry("chainId", "chain_abc123");

        ArgumentCaptor<AiLeaderResourceInteraction> receiptCaptor =
                ArgumentCaptor.forClass(AiLeaderResourceInteraction.class);
        verify(interactionRepository).save(receiptCaptor.capture());
        assertThat(receiptCaptor.getValue().getId()).isEqualTo(response.getSourceId());
        assertThat(receiptCaptor.getValue())
                .extracting(AiLeaderResourceInteraction::getUserId,
                        AiLeaderResourceInteraction::getLeaderSessionId,
                        AiLeaderResourceInteraction::getMessageId,
                        AiLeaderResourceInteraction::getResourceId,
                        AiLeaderResourceInteraction::getAction)
                .containsExactly(42L, 9L, 101L, "res_doc", "download");
        verify(sessionRepository).findForUpdateByUserIdAndSessionId(42L, "session-1");
    }

    @Test
    void duplicateTupleDoesNotCreateASecondEvidenceRecord() {
        when(interactionRepository.existsById(any())).thenReturn(true);

        AssistantResourceInteractionResponse response = service.record(
                42L, "session-1", 101L, "res_doc", request("preview"));

        assertThat(response.getStatus()).isEqualTo("duplicate");
        assertThat(response.isDuplicate()).isTrue();
        verify(interactionRepository, never()).save(any());
        verify(userProfileService, never()).addEvidence(any(), any());
    }

    @Test
    void completeUsesOnlyStoredMetadataToResolveAnOwnedActivePythonPathItem() {
        AiLeaderMessage learningResource = message(109L, """
                [{
                  "schemaVersion":"assistant-resource-v1",
                  "id":"res_python_lab",
                  "kind":"code_example",
                  "deliveryType":"bundle",
                  "title":"Python 列表切片实验",
                  "metadata":{
                    "learningPathId":88,
                    "learningPathItemKey":"review-lists"
                  }
                }]
                """);
        when(messageRepository.findById(109L)).thenReturn(Optional.of(learningResource));
        LearningPathDTO.PathView path = activePath(88L, "review-lists", 901L);
        when(learningPathService.getActivePath(42L, "python")).thenReturn(path);

        AssistantResourceInteractionResponse response = service.record(
                42L, "session-1", 109L, "res_python_lab", request("complete"));

        assertThat(response.getStatus()).isEqualTo("recorded");
        ArgumentCaptor<LearningPathDTO.InteractionRequest> requestCaptor =
                ArgumentCaptor.forClass(LearningPathDTO.InteractionRequest.class);
        verify(learningPathService).recordResourceInteraction(
                org.mockito.ArgumentMatchers.eq(42L),
                org.mockito.ArgumentMatchers.eq(901L),
                requestCaptor.capture());
        assertThat(requestCaptor.getValue().getAction()).isEqualTo("complete");

        ArgumentCaptor<UserProfileDTO.EvidenceRequest> evidenceCaptor =
                ArgumentCaptor.forClass(UserProfileDTO.EvidenceRequest.class);
        verify(userProfileService).addEvidence(org.mockito.ArgumentMatchers.eq(42L), evidenceCaptor.capture());
        assertThat(evidenceCaptor.getValue().getAction()).isEqualTo("complete");
        assertThat(evidenceCaptor.getValue().getEvidence()).contains("完成");
    }

    @Test
    void completeRejectsMissingOrForeignStoredPathIdentityBeforeWritingAReceipt() {
        BusinessException missingMetadata = assertThrows(BusinessException.class,
                () -> service.record(42L, "session-1", 101L, "res_doc", request("complete")));
        assertThat(missingMetadata.getCode()).isEqualTo(409);

        AiLeaderMessage foreignPathResource = message(110L, """
                [{
                  "schemaVersion":"assistant-resource-v1",
                  "id":"res_python_foreign",
                  "kind":"document",
                  "deliveryType":"document",
                  "title":"外部路径资源",
                  "metadata":{
                    "learningPathId":999,
                    "learningPathItemKey":"review-lists"
                  }
                }]
                """);
        when(messageRepository.findById(110L)).thenReturn(Optional.of(foreignPathResource));
        when(learningPathService.getActivePath(42L, "python"))
                .thenReturn(activePath(88L, "review-lists", 901L));

        BusinessException foreignPath = assertThrows(BusinessException.class,
                () -> service.record(
                        42L, "session-1", 110L, "res_python_foreign", request("complete")));

        assertThat(foreignPath.getCode()).isEqualTo(404);
        verify(interactionRepository, never()).save(any());
        verify(learningPathService, never()).recordResourceInteraction(any(), any(), any());
        verify(userProfileService, never()).addEvidence(any(), any());
    }

    @Test
    void duplicateCompleteDoesNotResolveOrCompleteThePathAgain() {
        AiLeaderMessage learningResource = message(111L, """
                [{
                  "schemaVersion":"assistant-resource-v1",
                  "id":"res_python_done",
                  "kind":"document",
                  "deliveryType":"document",
                  "title":"Python 条件语句讲义",
                  "metadata":{
                    "learningPathId":88,
                    "learningPathItemKey":"review-conditions"
                  }
                }]
                """);
        when(messageRepository.findById(111L)).thenReturn(Optional.of(learningResource));
        when(interactionRepository.existsById(any())).thenReturn(true);

        AssistantResourceInteractionResponse response = service.record(
                42L, "session-1", 111L, "res_python_done", request("complete"));

        assertThat(response.isDuplicate()).isTrue();
        verify(learningPathService, never()).getActivePath(any(), any());
        verify(learningPathService, never()).recordResourceInteraction(any(), any(), any());
        verify(interactionRepository, never()).save(any());
        verify(userProfileService, never()).addEvidence(any(), any());
    }

    @Test
    void rejectsForeignMessageMissingResourceMalformedStorageAndUnknownAction() {
        BusinessException foreignSessionError = assertThrows(BusinessException.class,
                () -> service.record(42L, "foreign-session", 101L, "res_doc", request("open")));
        assertThat(foreignSessionError.getCode()).isEqualTo(404);

        AiLeaderMessage foreign = new AiLeaderMessage();
        foreign.setId(102L);
        foreign.setLeaderSessionId(99L);
        foreign.setRole(AiLeaderMessage.ROLE_ASSISTANT);
        foreign.setResourcesJson("[]");
        when(messageRepository.findById(102L)).thenReturn(Optional.of(foreign));

        BusinessException foreignError = assertThrows(BusinessException.class,
                () -> service.record(42L, "session-1", 102L, "res_doc", request("open")));
        assertThat(foreignError.getCode()).isEqualTo(404);

        BusinessException missingError = assertThrows(BusinessException.class,
                () -> service.record(42L, "session-1", 101L, "missing", request("open")));
        assertThat(missingError.getCode()).isEqualTo(404);

        AiLeaderMessage malformed = new AiLeaderMessage();
        malformed.setId(103L);
        malformed.setLeaderSessionId(9L);
        malformed.setRole(AiLeaderMessage.ROLE_ASSISTANT);
        malformed.setResourcesJson("{broken");
        when(messageRepository.findById(103L)).thenReturn(Optional.of(malformed));
        BusinessException malformedError = assertThrows(BusinessException.class,
                () -> service.record(42L, "session-1", 103L, "res_doc", request("open")));
        assertThat(malformedError.getCode()).isEqualTo(409);

        BusinessException actionError = assertThrows(BusinessException.class,
                () -> service.record(42L, "session-1", 101L, "res_doc", request("delete")));
        assertThat(actionError.getCode()).isEqualTo(400);
        verify(userProfileService, never()).addEvidence(any(), any());
    }

    @Test
    void rejectsNonCanonicalKindDeliveryAndControlCharactersInTitles() {
        when(messageRepository.findById(104L)).thenReturn(Optional.of(message(104L, """
                [{
                  "schemaVersion":"assistant-resource-v1",
                  "id":"res_unknown",
                  "kind":"admin_panel",
                  "deliveryType":"document",
                  "title":"越权资源"
                }]
                """)));
        BusinessException kindError = assertThrows(BusinessException.class,
                () -> service.record(42L, "session-1", 104L, "res_unknown", request("open")));
        assertThat(kindError.getCode()).isEqualTo(409);

        when(messageRepository.findById(105L)).thenReturn(Optional.of(message(105L, """
                [{
                  "schemaVersion":"assistant-resource-v1",
                  "id":"res_delivery",
                  "kind":"document",
                  "deliveryType":"internal_redirect",
                  "title":"错误交付类型"
                }]
                """)));
        BusinessException deliveryError = assertThrows(BusinessException.class,
                () -> service.record(42L, "session-1", 105L, "res_delivery", request("open")));
        assertThat(deliveryError.getCode()).isEqualTo(409);

        when(messageRepository.findById(106L)).thenReturn(Optional.of(message(106L, """
                [{
                  "schemaVersion":"assistant-resource-v1",
                  "id":"res_title",
                  "kind":"document",
                  "deliveryType":"document",
                  "title":"第一行\n第二行"
                }]
                """)));
        BusinessException titleError = assertThrows(BusinessException.class,
                () -> service.record(42L, "session-1", 106L, "res_title", request("open")));
        assertThat(titleError.getCode()).isEqualTo(409);
        verify(userProfileService, never()).addEvidence(any(), any());
    }

    @Test
    void canonicalIdentityMatchesThePersistedContractAndSupportsLongTitles() throws Exception {
        BusinessException dottedId = assertThrows(BusinessException.class,
                () -> service.record(42L, "session-1", 101L, "res.bad", request("open")));
        assertThat(dottedId.getCode()).isEqualTo(400);

        when(messageRepository.findById(107L)).thenReturn(Optional.of(message(107L,
                objectMapper.writeValueAsString(List.of(Map.of(
                        "schemaVersion", "assistant-resource-v1",
                        "id", 123,
                        "kind", "document",
                        "deliveryType", "document",
                        "title", "数值 ID 不是规范资源"
                ))))));
        BusinessException numericId = assertThrows(BusinessException.class,
                () -> service.record(42L, "session-1", 107L, "123", request("open")));
        assertThat(numericId.getCode()).isEqualTo(404);

        String longTitle = "资".repeat(240);
        when(messageRepository.findById(108L)).thenReturn(Optional.of(message(108L,
                objectMapper.writeValueAsString(List.of(Map.of(
                        "schemaVersion", "assistant-resource-v1",
                        "id", "res_long",
                        "kind", "document",
                        "deliveryType", "document",
                        "title", longTitle
                ))))));
        AssistantResourceInteractionResponse response = service.record(
                42L, "session-1", 108L, "res_long", request("open"));

        assertThat(response.getStatus()).isEqualTo("recorded");
        ArgumentCaptor<UserProfileDTO.EvidenceRequest> evidenceCaptor =
                ArgumentCaptor.forClass(UserProfileDTO.EvidenceRequest.class);
        verify(userProfileService).addEvidence(org.mockito.ArgumentMatchers.eq(42L), evidenceCaptor.capture());
        assertThat(evidenceCaptor.getValue().getObjectName()).hasSize(240);
    }

    @Test
    void dismissCreatesADecreasingPreferenceSignal() {
        service.record(42L, "session-1", 101L, "res_doc", request("dismiss"));

        ArgumentCaptor<UserProfileDTO.EvidenceRequest> captor =
                ArgumentCaptor.forClass(UserProfileDTO.EvidenceRequest.class);
        verify(userProfileService).addEvidence(org.mockito.ArgumentMatchers.eq(42L), captor.capture());
        assertThat(captor.getValue().getDirection()).isEqualTo("decrease");
        assertThat(captor.getValue().getSuggestedDelta()).isEqualTo(-1);
    }

    @Test
    void assistantResourcesUseTheExistingClickResourceReliabilityTier() throws Exception {
        UserProfileServiceImpl profileService = new UserProfileServiceImpl(
                mock(UserProfileDimensionRepository.class),
                mock(UserProfileEvidenceRepository.class),
                mock(PythonAiProxyService.class),
                mock(SystemConfigService.class),
                new ObjectMapper()
        );
        var method = UserProfileServiceImpl.class
                .getDeclaredMethod("sourceReliabilityScore", String.class);
        method.setAccessible(true);

        assertThat((Double) method.invoke(profileService, "assistant_resource")).isEqualTo(0.55);
    }

    @Test
    void dismissedDocumentsDoNotBecomePositiveOutputPreferences() throws Exception {
        UserProfileEvidenceRepository evidenceRepository = mock(UserProfileEvidenceRepository.class);
        UserProfileEvidence dismissed = new UserProfileEvidence();
        dismissed.setDimensionKey("resource_preference");
        dismissed.setSourceType("assistant_resource");
        dismissed.setEvidence("用户忽略了助手推送的文档资源");
        dismissed.setObjectName("高数复习讲义");
        dismissed.setMetadataJson("{\"deliveryType\":\"document\"}");
        dismissed.setDirection("decrease");
        dismissed.setSuggestedDelta(-1);
        dismissed.setConfidence(0.8);
        when(evidenceRepository.findByUserIdAndDimensionKeyAndCreateTimeAfter(
                org.mockito.ArgumentMatchers.eq(42L),
                org.mockito.ArgumentMatchers.eq("resource_preference"),
                any())).thenReturn(List.of(dismissed));

        UserProfileServiceImpl profileService = new UserProfileServiceImpl(
                mock(UserProfileDimensionRepository.class),
                evidenceRepository,
                mock(PythonAiProxyService.class),
                mock(SystemConfigService.class),
                new ObjectMapper()
        );
        var hintsMethod = UserProfileServiceImpl.class
                .getDeclaredMethod("buildOutputPreferenceHints", Long.class);
        hintsMethod.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> hints = (Map<String, Object>) hintsMethod.invoke(profileService, 42L);

        assertThat(hints.get("preferredFormat")).isEqualTo("");

        var labelMethod = UserProfileServiceImpl.class.getDeclaredMethod("sourceLabel", String.class);
        labelMethod.setAccessible(true);
        assertThat(labelMethod.invoke(profileService, "assistant_resource")).isEqualTo("助手资源互动");
    }

    private AssistantResourceInteractionRequest request(String action) {
        AssistantResourceInteractionRequest request = new AssistantResourceInteractionRequest();
        request.setAction(action);
        return request;
    }

    private AiLeaderMessage message(Long id, String resourcesJson) {
        AiLeaderMessage message = new AiLeaderMessage();
        message.setId(id);
        message.setLeaderSessionId(9L);
        message.setRole(AiLeaderMessage.ROLE_ASSISTANT);
        message.setResourcesJson(resourcesJson);
        return message;
    }

    private LearningPathDTO.PathView activePath(Long pathId, String itemKey, Long itemId) {
        LearningPathDTO.PathItemView item = new LearningPathDTO.PathItemView();
        item.setId(itemId);
        item.setPathId(pathId);
        item.setItemKey(itemKey);
        item.setKnowledgePoint("python.lists.slicing");
        item.setStatus("ready");

        LearningPathDTO.PathView path = new LearningPathDTO.PathView();
        path.setId(pathId);
        path.setUserId(42L);
        path.setCourseKey("python");
        path.setStatus("active");
        path.setItems(List.of(item));
        return path;
    }
}
