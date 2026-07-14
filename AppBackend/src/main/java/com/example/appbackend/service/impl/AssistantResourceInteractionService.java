package com.example.appbackend.service.impl;

import com.example.appbackend.dto.AssistantResourceInteractionRequest;
import com.example.appbackend.dto.AssistantResourceInteractionResponse;
import com.example.appbackend.dto.UserProfileDTO;
import com.example.appbackend.entity.AiLeaderMessage;
import com.example.appbackend.entity.AiLeaderResourceInteraction;
import com.example.appbackend.entity.AiLeaderSession;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.AiLeaderMessageRepository;
import com.example.appbackend.repository.AiLeaderResourceInteractionRepository;
import com.example.appbackend.repository.AiLeaderSessionRepository;
import com.example.appbackend.service.UserProfileService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class AssistantResourceInteractionService {

    private static final Set<String> ACTIONS = Set.of(
            "view", "open", "download", "preview", "follow_up", "dismiss");
    private static final Pattern CHAIN_ID = Pattern.compile(
            "[A-Za-z0-9][A-Za-z0-9._:-]{0,159}");
    private static final Map<String, String> ACTION_LABELS = Map.of(
            "view", "查看",
            "open", "打开",
            "download", "下载",
            "preview", "预览",
            "follow_up", "继续提问",
            "dismiss", "忽略"
    );

    private final AiLeaderSessionRepository sessionRepository;
    private final AiLeaderMessageRepository messageRepository;
    private final AiLeaderResourceInteractionRepository interactionRepository;
    private final UserProfileService userProfileService;
    private final ObjectMapper objectMapper;

    public AssistantResourceInteractionService(AiLeaderSessionRepository sessionRepository,
                                               AiLeaderMessageRepository messageRepository,
                                               AiLeaderResourceInteractionRepository interactionRepository,
                                               UserProfileService userProfileService,
                                               ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.interactionRepository = interactionRepository;
        this.userProfileService = userProfileService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AssistantResourceInteractionResponse record(Long userId,
                                                       String sessionId,
                                                       Long messageId,
                                                       String resourceId,
                                                       AssistantResourceInteractionRequest request) {
        String action = request == null ? "" : request.getAction();
        if (!ACTIONS.contains(action)) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "资源互动动作不受支持");
        }
        if (!StringUtils.hasText(sessionId)
                || messageId == null
                || !StringUtils.hasText(resourceId)
                || !AssistantResourceContract.isValidResourceId(resourceId)) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "资源互动参数无效");
        }

        AiLeaderSession session = sessionRepository.findForUpdateByUserIdAndSessionId(userId, sessionId)
                .orElseThrow(() -> notFound());
        AiLeaderMessage message = messageRepository.findById(messageId)
                .filter(item -> session.getId().equals(item.getLeaderSessionId()))
                .filter(item -> AiLeaderMessage.ROLE_ASSISTANT.equals(item.getRole()))
                .orElseThrow(() -> notFound());
        ResourceSnapshot resource = findResource(message, resourceId);
        String sourceId = interactionSourceId(userId, session.getId(), messageId, resourceId, action);
        if (interactionRepository.existsById(sourceId)) {
            return new AssistantResourceInteractionResponse("duplicate", true, sourceId, null);
        }

        AiLeaderResourceInteraction receipt = new AiLeaderResourceInteraction();
        receipt.setId(sourceId);
        receipt.setUserId(userId);
        receipt.setLeaderSessionId(session.getId());
        receipt.setMessageId(messageId);
        receipt.setResourceId(resourceId);
        receipt.setAction(action);
        receipt.setCreateTime(LocalDateTime.now());
        interactionRepository.save(receipt);

        UserProfileDTO.EvidenceRequest evidence = buildEvidence(
                sessionId, message, resource, sourceId, action);
        UserProfileDTO.EvidenceResponse profileEvidence = userProfileService.addEvidence(userId, evidence);
        return new AssistantResourceInteractionResponse("recorded", false, sourceId, profileEvidence);
    }

    private UserProfileDTO.EvidenceRequest buildEvidence(String sessionId,
                                                         AiLeaderMessage message,
                                                         ResourceSnapshot resource,
                                                         String sourceId,
                                                         String action) {
        boolean dismissed = "dismiss".equals(action);
        UserProfileDTO.EvidenceRequest evidence = new UserProfileDTO.EvidenceRequest();
        evidence.setDimensionKey("resource_preference");
        evidence.setSourceType("assistant_resource");
        evidence.setSourceId(sourceId);
        evidence.setAction(action);
        evidence.setObjectType("assistant_resource");
        evidence.setObjectId(resource.id());
        evidence.setObjectName(resource.title());
        evidence.setResult("assistant_resource_interaction");
        evidence.setEvidence("用户对助手资源「" + resource.title() + "」执行了" + ACTION_LABELS.get(action));
        evidence.setDirection(dismissed ? "decrease" : "increase");
        evidence.setSuggestedDelta(dismissed ? -1 : Set.of("download", "follow_up").contains(action) ? 2 : 1);
        evidence.setOccurredAt(LocalDateTime.now());
        evidence.setEvidenceTags(List.of(resource.kind(), resource.deliveryType(), action));
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("submitter", "AssistantResourceInteractionService");
        metadata.put("sessionId", sessionId);
        metadata.put("messageId", message.getId());
        metadata.put("resourceKind", resource.kind());
        metadata.put("deliveryType", resource.deliveryType());
        metadata.put("chainId", readChainId(message));
        evidence.setMetadata(metadata);
        return evidence;
    }

    private ResourceSnapshot findResource(AiLeaderMessage message, String resourceId) {
        try {
            JsonNode root = objectMapper.readTree(message.getResourcesJson());
            if (root == null || !root.isArray()) {
                throw malformedResources();
            }
            ResourceSnapshot found = null;
            for (JsonNode item : root) {
                JsonNode storedId = item.isObject() ? item.get("id") : null;
                if (storedId == null || !storedId.isTextual() || !resourceId.equals(storedId.textValue())) {
                    continue;
                }
                if (found != null) {
                    throw malformedResources();
                }
                if (!"assistant-resource-v1".equals(item.path("schemaVersion").asText())
                        || !item.path("kind").isTextual()
                        || !item.path("deliveryType").isTextual()
                        || !item.path("title").isTextual()) {
                    throw malformedResources();
                }
                String schemaVersion = bounded(item.path("schemaVersion").asText(), 64);
                String kind = bounded(item.path("kind").asText(), 40);
                String deliveryType = bounded(item.path("deliveryType").asText(), 40);
                String title = bounded(item.path("title").asText(), AssistantResourceContract.MAX_TITLE_LENGTH);
                if (!AssistantResourceContract.isValidCore(
                        schemaVersion, resourceId, kind, deliveryType, title)) {
                    throw malformedResources();
                }
                found = new ResourceSnapshot(resourceId, kind, deliveryType, title);
            }
            if (found == null) {
                throw notFound();
            }
            return found;
        } catch (BusinessException error) {
            throw error;
        } catch (Exception error) {
            throw malformedResources();
        }
    }

    private String readChainId(AiLeaderMessage message) {
        try {
            JsonNode chain = objectMapper.readTree(message.getEvidenceChainJson());
            if (chain != null
                    && chain.isObject()
                    && "assistant-evidence-v1".equals(chain.path("schemaVersion").asText())
                    && chain.path("chainId").isTextual()) {
                String chainId = chain.path("chainId").asText();
                return CHAIN_ID.matcher(chainId).matches() ? chainId : "";
            }
        } catch (Exception ignored) {
            // Resource ownership is authoritative even when an old evidence snapshot has no chain id.
        }
        return "";
    }

    private String interactionSourceId(Long userId,
                                       Long leaderSessionId,
                                       Long messageId,
                                       String resourceId,
                                       String action) {
        String tuple = userId + "\n" + leaderSessionId + "\n" + messageId + "\n" + resourceId + "\n" + action;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(tuple.getBytes(StandardCharsets.UTF_8));
            return "ari_" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    private String bounded(String value, int maxLength) {
        String result = value == null ? "" : value.trim();
        return result.length() <= maxLength ? result : "";
    }

    private BusinessException notFound() {
        return new BusinessException(Result.NOT_FOUND_CODE, "助手资源不存在");
    }

    private BusinessException malformedResources() {
        return new BusinessException(409, "助手资源记录不可用");
    }

    private record ResourceSnapshot(String id, String kind, String deliveryType, String title) {
    }
}
