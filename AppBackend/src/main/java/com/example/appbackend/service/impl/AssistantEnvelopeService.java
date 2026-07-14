package com.example.appbackend.service.impl;

import com.example.appbackend.dto.AiLeaderMessageItem;
import com.example.appbackend.dto.AssistantEvidenceChainDTO;
import com.example.appbackend.dto.AssistantEvidenceGeneration;
import com.example.appbackend.dto.AssistantEvidenceIntegrity;
import com.example.appbackend.dto.AssistantEvidenceSource;
import com.example.appbackend.dto.AssistantEvidenceStep;
import com.example.appbackend.dto.AssistantResourceAction;
import com.example.appbackend.dto.AssistantResourceDTO;
import com.example.appbackend.dto.AssistantResourceIntegrity;
import com.example.appbackend.dto.AssistantResourceLink;
import com.example.appbackend.dto.LlmChatResponse;
import com.example.appbackend.entity.AiLeaderGeneratedExport;
import com.example.appbackend.entity.AiLeaderMessage;
import com.example.appbackend.entity.AiLeaderSession;
import com.example.appbackend.repository.AiLeaderGeneratedExportRepository;
import com.example.appbackend.repository.AiLeaderMessageRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriUtils;
import org.springframework.beans.factory.annotation.Value;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

@Service
public class AssistantEnvelopeService {

    private static final Logger log = LoggerFactory.getLogger(AssistantEnvelopeService.class);
    private static final int MAX_MATCHED_RESULTS = 20;
    private static final int MAX_SOURCES = 20;
    private static final int MAX_RESOURCES = 40;
    private static final int MAX_EXCERPT_CHARS = 800;
    private static final int MAX_ENVELOPE_BYTES = 256 * 1024;
    private static final int MAX_RAW_ENVELOPE_BYTES = 2 * 1024 * 1024;
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9:_-]{1,160}");
    private static final Pattern SHA256 = Pattern.compile("(?:sha256:)?[0-9a-f]{64}");
    private static final Pattern EVIDENCE_DIGEST = Pattern.compile("sha256:[0-9a-f]{64}");
    private static final Pattern STORAGE_KEY = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.[a-z0-9]{1,16}");
    private static final Set<String> RESOURCE_KINDS = Set.of(
            "explanation", "mind_map", "diagram", "exercise", "code_example", "extended_reading",
            "image", "video", "audio", "document", "presentation", "spreadsheet", "bundle",
            "course", "activity", "meeting", "dining", "facility", "secondhand");
    private static final Set<String> DELIVERY_TYPES = Set.of(
            "content", "image", "video", "audio", "document", "presentation", "spreadsheet",
            "bundle", "business_card");
    private static final Set<String> BUSINESS_KINDS = Set.of(
            "course", "activity", "meeting", "dining", "facility", "secondhand");
    private static final Set<String> FILE_DELIVERIES = Set.of(
            "image", "video", "audio", "document", "presentation", "spreadsheet", "bundle");
    private static final Set<String> CONTENT_KINDS = Set.of(
            "explanation", "mind_map", "diagram", "exercise", "code_example", "extended_reading");
    private static final Map<String, Set<String>> KIND_DELIVERIES = Map.ofEntries(
            Map.entry("explanation", Set.of("content", "document", "presentation")),
            Map.entry("mind_map", Set.of("content", "image", "document")),
            Map.entry("diagram", Set.of("content", "image", "document")),
            Map.entry("exercise", Set.of("content", "document", "spreadsheet")),
            Map.entry("code_example", Set.of("content", "document", "bundle")),
            Map.entry("extended_reading", Set.of("content", "document")),
            Map.entry("image", Set.of("image")),
            Map.entry("video", Set.of("video")),
            Map.entry("audio", Set.of("audio")),
            Map.entry("document", Set.of("document")),
            Map.entry("presentation", Set.of("presentation")),
            Map.entry("spreadsheet", Set.of("spreadsheet")),
            Map.entry("bundle", Set.of("bundle")),
            Map.entry("course", Set.of("business_card")),
            Map.entry("activity", Set.of("business_card")),
            Map.entry("meeting", Set.of("business_card")),
            Map.entry("dining", Set.of("business_card")),
            Map.entry("facility", Set.of("business_card")),
            Map.entry("secondhand", Set.of("business_card")));
    private static final Set<String> BUSINESS_NUMBER_FIELDS = Set.of(
            "weekday", "startSection", "endSection", "rating", "price", "longitude", "latitude");
    private static final Set<String> AVAILABILITY_STATES = Set.of(
            "active", "expired", "unavailable", "legacy_unavailable");
    private static final Set<String> GROUNDING_STATUSES = Set.of("grounded", "context_only", "model_only");
    private static final Set<String> ACTION_TYPES = Set.of("open_resource", "download", "preview", "follow_up");
    private static final Set<String> EVIDENCE_STATES = Set.of(
            "available", "legacy_missing", "malformed", "integrity_failed", "generation_failed");
    private static final Set<String> SAFE_METADATA_KEYS = Set.of(
            "source", "sourceId", "sourceType", "sourceVersion", "retrievedAt", "title", "type", "kind",
            "time", "location", "route", "status", "legacy", "serverGenerated");
    private static final Set<String> SAFE_STEP_DETAIL_KEYS = Set.of(
            "agentName", "targetAgent", "toolName", "toolDisplayName", "routeReason", "intent",
            "resultCount", "documentCount", "strategy", "summarizedByModel");
    private static final Set<String> FORBIDDEN_KEYS = Set.of(
            "userid", "sellerid", "phone", "contact", "memberlist", "participants", "transcript", "token",
            "raw", "authorization", "apikey", "capability", "profile");
    private static final Map<String, Set<String>> BUSINESS_FIELDS = Map.of(
            "course", Set.of("businessId", "courseName", "teacherName", "weekday", "startSection", "endSection", "classroom", "weekText"),
            "activity", Set.of("businessId", "title", "category", "startTime", "endTime", "location", "status"),
            "meeting", Set.of("businessId", "title", "startTime", "endTime", "location", "status"),
            "dining", Set.of("businessId", "name", "category", "location", "openingHours", "rating", "priceRange", "imageUrl"),
            "facility", Set.of("businessId", "name", "category", "location", "openingHours", "status", "longitude", "latitude"),
            "secondhand", Set.of("businessId", "title", "category", "price", "condition", "status", "createdAt", "imageUrl")
    );

    private final AiLeaderMessageRepository messageRepository;
    private final AiLeaderGeneratedExportRepository exportRepository;
    private final ObjectMapper objectMapper;
    private final Set<String> allowedPublicHosts;

    public AssistantEnvelopeService(AiLeaderMessageRepository messageRepository,
                                    AiLeaderGeneratedExportRepository exportRepository,
                                    ObjectMapper objectMapper,
                                    @Value("${ai.assistant.public-resource-hosts:}") String allowedPublicHosts) {
        this.messageRepository = messageRepository;
        this.exportRepository = exportRepository;
        this.objectMapper = objectMapper;
        this.allowedPublicHosts = parseAllowedHosts(allowedPublicHosts);
    }

    public record PreparedEnvelope(List<Map<String, Object>> internalAttachments) {
    }

    public PreparedEnvelope prepareLiveResponse(LlmChatResponse response,
                                                Map<String, Object> rawResult,
                                                String expectedQuery) {
        response.setOutputMeta(sanitizeGenericMap(response.getOutputMeta(), 0));
        response.setRetrievalMeta(sanitizeGenericMap(response.getRetrievalMeta(), 0));
        response.setTrace(sanitizeTrace(response.getTrace()));
        Object matched = rawResult.containsKey("documents") ? rawResult.get("documents") : rawResult.get("matchedResults");
        response.setMatchedResults(sanitizeMatchedResults(matched));
        List<Map<String, Object>> internalAttachments = mapList(rawResult.get("attachments"));
        response.setAttachments(sanitizeAttachments(internalAttachments));
        Object rawResources = rawResult.get("resources");
        List<AssistantResourceDTO> resources = sanitizeResources(rawResources, false);
        boolean resourcesTruncated = mapList(rawResources).size() > MAX_RESOURCES;
        AssistantEvidenceChainDTO chain = validateEvidence(
                rawResult.get("evidenceChain"), resources, "generation_failed",
                response.getAnswer(), expectedQuery, resourcesTruncated);
        if (!"available".equals(chain.getEvidenceState())) {
            downgradeUntrustedGrounding(resources);
            chain = sealFailureChain(chain, resources, response, expectedQuery);
        }
        fitEnvelope(resources, chain);
        response.setResources(resources);
        response.setEvidenceChain(chain);
        return new PreparedEnvelope(copyMapList(internalAttachments));
    }

    @Transactional
    public AiLeaderMessage persistAssistantMessage(Long userId,
                                                   AiLeaderSession session,
                                                   LlmChatResponse response,
                                                   List<Map<String, Object>> internalAttachments,
                                                   AiLeaderMessage existing) {
        AiLeaderMessage message = existing == null ? new AiLeaderMessage() : existing;
        if (existing == null) {
            message.setLeaderSessionId(session.getId());
            message.setRole(AiLeaderMessage.ROLE_ASSISTANT);
            fillBaseFields(message, response);
            clearPublicEnvelope(message);
            message = messageRepository.save(message);
        }
        if (message.getId() == null) {
            throw new IllegalStateException("assistant message id was not generated");
        }
        response.setMessageId(message.getId());
        bindGeneratedExports(userId, session, message, response, internalAttachments);
        fillMessage(message, response);
        return messageRepository.save(message);
    }

    public void restoreEnvelope(AiLeaderMessage message,
                                AiLeaderMessageItem item,
                                String expectedQuery) {
        FieldRead<List<Map<String, Object>>> matched = readMatchedResults(message);
        FieldRead<List<AssistantResourceDTO>> resourceRead = readResources(message);
        FieldRead<List<Map<String, Object>>> attachments = readAttachments(message);
        List<AssistantResourceDTO> resources = resourceRead.value();
        AssistantEvidenceChainDTO evidenceChain = readEvidence(message, resources, expectedQuery);
        if (matched.malformed() || resourceRead.malformed() || attachments.malformed()) {
            evidenceChain = stateChain("malformed", false);
        }
        if (!"available".equals(evidenceChain.getEvidenceState())) {
            downgradeUntrustedGrounding(resources);
        }
        item.setMatchedResults(matched.value());
        item.setResources(resources);
        item.setEvidenceChain(evidenceChain);
        item.setAttachments(attachments.value());
    }

    public void overwriteSsePayload(Object eventPayload, LlmChatResponse response) {
        if (response == null || !(eventPayload instanceof Map<?, ?> source)) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> target = (Map<String, Object>) source;
        target.put("messageId", response.getMessageId());
        target.put("sessionId", response.getSessionId());
        target.put("matchedResults", response.getMatchedResults());
        target.put("attachments", response.getAttachments());
        target.put("resources", response.getResources());
        target.put("evidenceChain", response.getEvidenceChain());
    }

    private void bindGeneratedExports(Long userId,
                                      AiLeaderSession session,
                                      AiLeaderMessage message,
                                      LlmChatResponse response,
                                      List<Map<String, Object>> internalAttachments) {
        List<AssistantResourceDTO> resources = response.getResources() == null
                ? new ArrayList<>() : new ArrayList<>(response.getResources());
        Map<String, List<AssistantResourceDTO>> resourcesByStorage = new HashMap<>();
        for (AssistantResourceDTO resource : resources) {
            resource.setMessageId(message.getId());
            if (StringUtils.hasText(resource.getStorageKey())) {
                resourcesByStorage.computeIfAbsent(resource.getStorageKey(), ignored -> new ArrayList<>()).add(resource);
            }
        }
        Map<String, String> downloadUrls = new HashMap<>();
        Set<String> processedStorageKeys = new HashSet<>();
        for (Map<String, Object> attachment : internalAttachments == null ? List.<Map<String, Object>>of() : internalAttachments) {
            if (!Boolean.TRUE.equals(attachment.get("serverGenerated"))) {
                continue;
            }
            String storageKey = text(attachment.get("storageKey"), 300);
            if (!processedStorageKeys.add(storageKey)) {
                continue;
            }
            List<AssistantResourceDTO> matchingResources = resourcesByStorage.getOrDefault(storageKey, List.of());
            Optional<GeneratedManifest> parsed = parseManifest(attachment);
            if (matchingResources.size() != 1 || parsed.isEmpty()) {
                continue;
            }
            AssistantResourceDTO resource = matchingResources.getFirst();
            GeneratedManifest manifest = parsed.get();
            if (!resourceMatchesManifest(resource, manifest)) {
                continue;
            }
            Optional<AiLeaderGeneratedExport> existing = exportRepository.findByMessageIdAndStorageKey(message.getId(), storageKey);
            if (existing.isEmpty()) {
                AiLeaderGeneratedExport entity = new AiLeaderGeneratedExport();
                entity.setUserId(userId);
                entity.setLeaderSessionId(session.getId());
                entity.setMessageId(message.getId());
                entity.setResourceId(resource.getId());
                entity.setStorageKey(storageKey);
                entity.setFileName(manifest.fileName());
                entity.setMimeType(manifest.mimeType());
                entity.setSize(manifest.size());
                entity.setSha256(manifest.sha256());
                entity.setPythonCapability(manifest.capability());
                entity.setCreatedAt(manifest.createdAt());
                entity.setExpiresAt(manifest.expiresAt());
                entity.setStatus(AiLeaderGeneratedExport.STATUS_ACTIVE);
                exportRepository.save(entity);
            } else if (!existingManifestMatches(
                    existing.get(), userId, session.getId(), message.getId(), resource.getId(), storageKey, manifest)) {
                continue;
            }
            String downloadUrl = downloadUrl(session.getSessionId(), message.getId(), storageKey);
            downloadUrls.put(storageKey, downloadUrl);
            resource.setUrl(downloadUrl);
            resource.setPreviewUrl("");
            resource.setAvailability("active");
            resource.setMetadata(safeMetadata(resource.getMetadata()));
        }
        for (AssistantResourceDTO resource : resources) {
            if (StringUtils.hasText(resource.getStorageKey()) && !downloadUrls.containsKey(resource.getStorageKey())) {
                resource.setUrl("");
                resource.setPreviewUrl("");
                resource.setAvailability("unavailable");
                resource.setActions(resource.getActions() == null ? List.of() : resource.getActions().stream()
                        .filter(action -> !Set.of("download", "preview").contains(action.getType()))
                        .toList());
            }
        }
        response.setResources(resources);

        List<Map<String, Object>> attachments = sanitizeAttachments(internalAttachments);
        for (Map<String, Object> attachment : attachments) {
            String storageKey = text(attachment.get("storageKey"), 300);
            if (downloadUrls.containsKey(storageKey)) {
                attachment.put("url", downloadUrls.get(storageKey));
                attachment.put("status", "active");
            } else if (Boolean.TRUE.equals(attachment.get("serverGenerated"))) {
                attachment.remove("url");
                attachment.remove("previewUrl");
                attachment.put("status", "unavailable");
            }
        }
        response.setAttachments(attachments);
    }

    private Optional<GeneratedManifest> parseManifest(Map<String, Object> attachment) {
        Object fileNameValue = first(attachment, "fileName", "name", "title");
        if (!stringFields(attachment, Set.of(
                "storageKey", "internalCapability", "sha256", "fileName", "name", "title", "mimeType",
                "createdAt", "expiresAt", "url", "previewUrl", "type", "ext", "sourceType", "sourceId"))
                || !(attachment.get("storageKey") instanceof String)
                || !(attachment.get("internalCapability") instanceof String)
                || !(attachment.get("sha256") instanceof String)
                || !(attachment.get("size") instanceof Number)
                || !(fileNameValue instanceof String)
                || !(attachment.get("mimeType") instanceof String)
                || !(attachment.get("createdAt") instanceof String)
                || !(attachment.get("expiresAt") instanceof String)) {
            return Optional.empty();
        }
        String storageKey = text(attachment.get("storageKey"), 300);
        String capability = text(attachment.get("internalCapability"), 200);
        String digest = normalizeSha256(attachment.get("sha256"));
        Long size = nonNegativeLong(attachment.get("size"));
        String fileName = text(fileNameValue, 240);
        String mimeType = text(attachment.get("mimeType"), 160);
        Instant createdAt = strictUtcInstant(attachment.get("createdAt"));
        Instant expiresAt = strictUtcInstant(attachment.get("expiresAt"));
        if (!STORAGE_KEY.matcher(storageKey).matches()
                || !StringUtils.hasText(capability)
                || !SHA256.matcher(digest).matches()
                || size == null
                || !StringUtils.hasText(fileName)
                || !StringUtils.hasText(mimeType)
                || createdAt == null
                || expiresAt == null
                || !expiresAt.isAfter(createdAt)) {
            return Optional.empty();
        }
        return Optional.of(new GeneratedManifest(
                fileName, mimeType, size, digest.replace("sha256:", ""), capability, createdAt, expiresAt));
    }

    private record GeneratedManifest(String fileName,
                                     String mimeType,
                                     Long size,
                                     String sha256,
                                     String capability,
                                     Instant createdAt,
                                     Instant expiresAt) {
    }

    private boolean resourceMatchesManifest(AssistantResourceDTO resource, GeneratedManifest manifest) {
        AssistantResourceIntegrity integrity = resource.getIntegrity();
        return integrity != null
                && manifest.mimeType().equals(resource.getMimeType())
                && manifest.size().equals(integrity.getSize())
                && manifest.sha256().equals(integrity.getDigest())
                && manifest.expiresAt().equals(instant(resource.getExpiresAt()))
                && manifest.createdAt().equals(instant(resource.getCreatedAt()));
    }

    private boolean existingManifestMatches(AiLeaderGeneratedExport existing,
                                            Long userId,
                                            Long sessionId,
                                            Long messageId,
                                            String resourceId,
                                            String storageKey,
                                            GeneratedManifest manifest) {
        return userId.equals(existing.getUserId())
                && sessionId.equals(existing.getLeaderSessionId())
                && messageId.equals(existing.getMessageId())
                && resourceId.equals(existing.getResourceId())
                && storageKey.equals(existing.getStorageKey())
                && manifest.fileName().equals(existing.getFileName())
                && manifest.mimeType().equals(existing.getMimeType())
                && manifest.size().equals(existing.getSize())
                && manifest.sha256().equals(existing.getSha256())
                && manifest.capability().equals(existing.getPythonCapability())
                && manifest.createdAt().equals(existing.getCreatedAt())
                && manifest.expiresAt().equals(existing.getExpiresAt())
                && AiLeaderGeneratedExport.STATUS_ACTIVE.equals(existing.getStatus());
    }

    private void fillBaseFields(AiLeaderMessage message, LlmChatResponse response) {
        message.setContent(response == null || response.getAnswer() == null ? "" : response.getAnswer());
        message.setAnswerType(response == null ? "text" : response.getAnswerType());
        message.setOutputType(response == null ? "text" : response.getOutputType());
        message.setAgentName(response == null ? "leader_agent" : defaultText(response.getAgentName(), "leader_agent"));
        message.setSearchKeyword(response == null ? "" : response.getSearchKeyword());
    }

    private void fillMessage(AiLeaderMessage message, LlmChatResponse response) {
        fillBaseFields(message, response);
        message.setOutputTypesJson(writeJson(message.getId(), "outputTypes", safeList(response.getOutputTypes()), "[]"));
        message.setOutputMetaJson(writeJson(message.getId(), "outputMeta", sanitizeGenericMap(response.getOutputMeta(), 0), "{}"));
        message.setRetrievalMetaJson(writeJson(message.getId(), "retrievalMeta", sanitizeGenericMap(response.getRetrievalMeta(), 0), "{}"));
        message.setTraceJson(writeJson(message.getId(), "trace", sanitizeTrace(response.getTrace()), "[]"));
        message.setAttachmentsJson(writeJson(message.getId(), "attachments", sanitizeAttachments(response.getAttachments()), "[]"));
        message.setMatchedResultsJson(writeJson(message.getId(), "matchedResults", sanitizeMatchedResults(response.getMatchedResults()), "[]"));
        message.setResourcesJson(writeJson(message.getId(), "resources", response.getResources() == null ? List.of() : response.getResources(), "[]"));
        message.setEvidenceChainJson(writeJson(message.getId(), "evidenceChain",
                response.getEvidenceChain() == null ? stateChain("generation_failed", false) : response.getEvidenceChain(), "{}"));
    }

    private void clearPublicEnvelope(AiLeaderMessage message) {
        message.setOutputTypesJson("[]");
        message.setOutputMetaJson("{}");
        message.setRetrievalMetaJson("{}");
        message.setTraceJson("[]");
        message.setAttachmentsJson("[]");
        message.setMatchedResultsJson("[]");
        message.setResourcesJson("[]");
        message.setEvidenceChainJson("{}");
    }

    private record FieldRead<T>(T value, boolean malformed) {
    }

    private FieldRead<List<Map<String, Object>>> readMatchedResults(AiLeaderMessage message) {
        JsonNode node = readJson(message.getId(), "matchedResults", message.getMatchedResultsJson());
        if (!StringUtils.hasText(message.getMatchedResultsJson())) {
            return new FieldRead<>(List.of(), false);
        }
        if (node == null || !node.isArray()) {
            logMalformedField(message.getId(), "matchedResults", "rootType");
            return new FieldRead<>(List.of(), true);
        }
        List<Map<String, Object>> raw = mapList(objectMapper.convertValue(node, Object.class));
        List<Map<String, Object>> sanitized = sanitizeMatchedResults(raw);
        boolean malformed = raw.size() != node.size() || sanitized.size() != Math.min(raw.size(), MAX_MATCHED_RESULTS);
        if (malformed) {
            logMalformedField(message.getId(), "matchedResults", "typedValidation");
        }
        return new FieldRead<>(sanitized, malformed);
    }

    private FieldRead<List<AssistantResourceDTO>> readResources(AiLeaderMessage message) {
        JsonNode node = readJson(message.getId(), "resources", message.getResourcesJson());
        if (!StringUtils.hasText(message.getResourcesJson())) {
            return new FieldRead<>(List.of(), false);
        }
        if (node == null || !node.isArray()) {
            logMalformedField(message.getId(), "resources", "rootType");
            return new FieldRead<>(List.of(), true);
        }
        List<Map<String, Object>> raw = mapList(objectMapper.convertValue(node, Object.class));
        List<AssistantResourceDTO> resources = sanitizeResources(raw, true);
        boolean malformed = raw.size() != node.size() || resources.size() != Math.min(raw.size(), MAX_RESOURCES);
        if (malformed) {
            logMalformedField(message.getId(), "resources", "typedValidation");
        }
        resources.forEach(resource -> resource.setMessageId(message.getId()));
        return new FieldRead<>(resources, malformed);
    }

    private AssistantEvidenceChainDTO readEvidence(AiLeaderMessage message,
                                                   List<AssistantResourceDTO> resources,
                                                   String expectedQuery) {
        if (!StringUtils.hasText(message.getEvidenceChainJson()) || "{}".equals(message.getEvidenceChainJson().trim())) {
            return stateChain("legacy_missing", false);
        }
        JsonNode node = readJson(message.getId(), "evidenceChain", message.getEvidenceChainJson());
        if (node == null || !node.isObject()) {
            return stateChain("malformed", false);
        }
        return validateEvidence(node, resources, "integrity_failed",
                message.getContent(), expectedQuery, false);
    }

    private FieldRead<List<Map<String, Object>>> readAttachments(AiLeaderMessage message) {
        JsonNode node = readJson(message.getId(), "attachments", message.getAttachmentsJson());
        if (!StringUtils.hasText(message.getAttachmentsJson())) {
            return new FieldRead<>(List.of(), false);
        }
        if (node == null || !node.isArray()) {
            logMalformedField(message.getId(), "attachments", "rootType");
            return new FieldRead<>(List.of(), true);
        }
        List<Map<String, Object>> raw = mapList(objectMapper.convertValue(node, Object.class));
        List<Map<String, Object>> sanitized = sanitizeAttachments(raw);
        boolean malformed = raw.size() != node.size() || sanitized.size() != raw.size();
        if (malformed) {
            logMalformedField(message.getId(), "attachments", "typedValidation");
        }
        return new FieldRead<>(sanitized, malformed);
    }

    private JsonNode readJson(Long messageId, String field, String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception error) {
            log.warn("assistant message json malformed messageId={} field={} errorType={}",
                    messageId, field, error.getClass().getSimpleName());
            return null;
        }
    }

    private void logMalformedField(Long messageId, String field, String errorType) {
        log.warn("assistant message json malformed messageId={} field={} errorType={}",
                messageId, field, errorType);
    }

    private String writeJson(Long messageId, String field, Object value, String fallback) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception error) {
            log.warn("assistant message json write failed messageId={} field={} errorType={}",
                    messageId, field, error.getClass().getSimpleName());
            return fallback;
        }
    }

    private List<Map<String, Object>> sanitizeMatchedResults(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> item : mapList(value)) {
            if (result.size() >= MAX_MATCHED_RESULTS) {
                break;
            }
            Map<String, Object> safe = new LinkedHashMap<>();
            putText(safe, "id", item.get("id"), 200);
            putText(safe, "content", first(item, "content", "excerpt", "text"), MAX_EXCERPT_CHARS);
            putText(safe, "source", item.get("source"), 300);
            putScalar(safe, "score", item.get("score"));
            Map<String, Object> metadata = safeMetadata(mapValue(item.get("metadata")));
            if (!metadata.isEmpty()) {
                safe.put("metadata", metadata);
            }
            if (!safe.isEmpty()) {
                result.add(safe);
            }
        }
        return result;
    }

    private List<AssistantResourceDTO> sanitizeResources(Object value, boolean persisted) {
        List<AssistantResourceDTO> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (Map<String, Object> raw : mapList(value)) {
            if (result.size() >= MAX_RESOURCES) {
                break;
            }
            Optional<AssistantResourceDTO> parsed = sanitizeResource(raw, persisted);
            if (parsed.isPresent() && ids.add(parsed.get().getId())) {
                result.add(parsed.get());
            }
        }
        return result;
    }

    private Optional<AssistantResourceDTO> sanitizeResource(Map<String, Object> raw, boolean persisted) {
        if (!stringFields(raw, Set.of(
                "schemaVersion", "id", "kind", "deliveryType", "groundingStatus", "title", "summary",
                "mimeType", "storageKey", "url", "previewUrl", "sourceType", "sourceId", "authScope",
                "createdAt", "expiresAt", "availability"))) {
            return Optional.empty();
        }
        String schemaVersion = text(raw.get("schemaVersion"), 64);
        String id = text(raw.get("id"), 80);
        String kind = text(raw.get("kind"), 40);
        String deliveryType = text(raw.get("deliveryType"), 40);
        String groundingStatus = text(raw.get("groundingStatus"), 40);
        if (!"assistant-resource-v1".equals(schemaVersion)
                || !IDENTIFIER.matcher(id).matches()
                || !RESOURCE_KINDS.contains(kind)
                || !DELIVERY_TYPES.contains(deliveryType)
                || !KIND_DELIVERIES.getOrDefault(kind, Set.of()).contains(deliveryType)
                || !GROUNDING_STATUSES.contains(groundingStatus)) {
            return Optional.empty();
        }
        String storageKey = text(raw.get("storageKey"), 300);
        if (StringUtils.hasText(storageKey) && !STORAGE_KEY.matcher(storageKey).matches()) {
            return Optional.empty();
        }
        if (!(raw.get("payload") instanceof Map<?, ?>)
                || raw.get("metadata") != null && !(raw.get("metadata") instanceof Map<?, ?>)
                || raw.get("integrity") != null && !(raw.get("integrity") instanceof Map<?, ?>)
                || raw.get("messageId") != null && !(raw.get("messageId") instanceof Number)) {
            return Optional.empty();
        }
        Optional<Map<String, Object>> payload = sanitizePayload(kind, deliveryType, mapValue(raw.get("payload")));
        if (payload.isEmpty()) {
            return Optional.empty();
        }
        if (!(raw.get("evidenceIds") instanceof List<?> evidenceValues)
                || evidenceValues.stream().anyMatch(item -> !(item instanceof String))) {
            return Optional.empty();
        }
        List<String> evidenceIds = stringList(raw.get("evidenceIds"), 100, 160);
        if (new HashSet<>(evidenceIds).size() != evidenceIds.size()) {
            return Optional.empty();
        }
        if ("grounded".equals(groundingStatus) != !evidenceIds.isEmpty()) {
            return Optional.empty();
        }
        List<AssistantResourceAction> actions = sanitizeActions(raw.get("actions"));
        if (raw.get("actions") != null
                && (!(raw.get("actions") instanceof List<?> actionValues)
                || actions.size() != Math.min(actionValues.size(), 3))) {
            return Optional.empty();
        }
        AssistantResourceIntegrity integrity = resourceIntegrity(raw.get("integrity"));
        if (raw.get("integrity") != null && integrity == null
                || StringUtils.hasText(storageKey) && !validFileIntegrity(payload.get(), integrity)) {
            return Optional.empty();
        }
        if (!(raw.get("createdAt") instanceof String createdAt) || !utcTimestamp(createdAt)) {
            return Optional.empty();
        }
        String expiresAt = null;
        if (raw.get("expiresAt") != null) {
            if (!(raw.get("expiresAt") instanceof String expiresValue) || !utcTimestamp(expiresValue)) {
                return Optional.empty();
            }
            expiresAt = expiresValue;
        }
        if (expiresAt != null && !instant(expiresAt).isAfter(instant(createdAt))) {
            return Optional.empty();
        }
        String authScope = text(raw.get("authScope"), 40);
        if (!Set.of("session_owner", "request_user", "public").contains(authScope)) {
            return Optional.empty();
        }
        String rawUrl = text(raw.get("url"), 1_000);
        String rawPreviewUrl = text(raw.get("previewUrl"), 1_000);
        String url = safeUrl(rawUrl);
        String previewUrl = safeUrl(rawPreviewUrl);
        if (!validResourceUrls(persisted, authScope, storageKey, rawUrl, rawPreviewUrl, url, previewUrl, actions)) {
            return Optional.empty();
        }
        String availability = text(raw.get("availability"), 40);
        if (StringUtils.hasText(availability) && !AVAILABILITY_STATES.contains(availability)) {
            return Optional.empty();
        }
        AssistantResourceDTO resource = new AssistantResourceDTO();
        resource.setSchemaVersion(schemaVersion);
        resource.setId(id);
        resource.setMessageId(null);
        resource.setKind(kind);
        resource.setDeliveryType(deliveryType);
        resource.setGroundingStatus(groundingStatus);
        resource.setTitle(text(raw.get("title"), 240));
        resource.setSummary(text(raw.get("summary"), 400));
        resource.setMimeType(text(raw.get("mimeType"), 160));
        resource.setStorageKey(storageKey);
        resource.setUrl(url);
        resource.setPreviewUrl(previewUrl);
        resource.setSourceType(text(raw.get("sourceType"), 80));
        resource.setSourceId(text(raw.get("sourceId"), 256));
        resource.setEvidenceIds(evidenceIds);
        resource.setActions(actions);
        resource.setAuthScope(authScope);
        resource.setCreatedAt(createdAt);
        resource.setExpiresAt(expiresAt);
        resource.setIntegrity(integrity);
        resource.setPayload(payload.get());
        resource.setMetadata(safeMetadata(mapValue(raw.get("metadata"))));
        resource.setAvailability(StringUtils.hasText(availability) ? availability : null);
        if (isLegacyInternalUrl(raw.get("url")) || isLegacyInternalUrl(raw.get("previewUrl"))) {
            markLegacyUnavailable(resource);
        }
        return Optional.of(resource);
    }

    private Optional<Map<String, Object>> sanitizePayload(String kind,
                                                          String deliveryType,
                                                          Map<String, Object> raw) {
        if (!stringFields(raw, Set.of("type", "content", "language", "format", "digest"))) {
            return Optional.empty();
        }
        String type = text(raw.get("type"), 20);
        Map<String, Object> safe = new LinkedHashMap<>();
        safe.put("type", type);
        if ("content".equals(type) && "content".equals(deliveryType) && CONTENT_KINDS.contains(kind)) {
            putText(safe, "content", raw.get("content"), 12_000);
            putText(safe, "language", raw.get("language"), 40);
            return StringUtils.hasText(String.valueOf(safe.getOrDefault("content", "")))
                    ? Optional.of(safe) : Optional.empty();
        }
        if ("file".equals(type) && FILE_DELIVERIES.contains(deliveryType) && !BUSINESS_KINDS.contains(kind)) {
            putText(safe, "format", raw.get("format"), 40);
            if (raw.get("size") != null && !(raw.get("size") instanceof Number)) {
                return Optional.empty();
            }
            Long size = raw.get("size") == null ? null : nonNegativeLong(raw.get("size"));
            if (raw.get("size") != null && size == null) {
                return Optional.empty();
            }
            if (size != null) {
                safe.put("size", size);
            }
            String digest = normalizeSha256(raw.get("digest"));
            if (raw.get("digest") != null
                    && (!(raw.get("digest") instanceof String) || !EVIDENCE_DIGEST.matcher(digest).matches())) {
                return Optional.empty();
            }
            if (StringUtils.hasText(digest)) {
                safe.put("digest", digest.startsWith("sha256:") ? digest : "sha256:" + digest);
            }
            return StringUtils.hasText(String.valueOf(safe.getOrDefault("format", "")))
                    ? Optional.of(safe) : Optional.empty();
        }
        if ("business".equals(type) && "business_card".equals(deliveryType) && BUSINESS_FIELDS.containsKey(kind)) {
            for (String key : BUSINESS_FIELDS.get(kind)) {
                Object item = raw.get(key);
                if (BUSINESS_NUMBER_FIELDS.contains(key)) {
                    if (item != null && (!(item instanceof Number number) || !finite(number))) {
                        return Optional.empty();
                    }
                    if (item != null) {
                        safe.put(key, item);
                    }
                } else if ("imageUrl".equals(key)) {
                    if (item != null && !(item instanceof String)) {
                        return Optional.empty();
                    }
                    String url = safeUrl(item);
                    if (StringUtils.hasText(url)) {
                        safe.put(key, url);
                    }
                } else {
                    if (item != null && !(item instanceof String)) {
                        return Optional.empty();
                    }
                    putText(safe, key, item, 300);
                }
            }
            return StringUtils.hasText(String.valueOf(safe.getOrDefault("businessId", "")))
                    ? Optional.of(safe) : Optional.empty();
        }
        return Optional.empty();
    }

    private List<AssistantResourceAction> sanitizeActions(Object value) {
        List<AssistantResourceAction> result = new ArrayList<>();
        for (Map<String, Object> raw : mapList(value)) {
            if (result.size() >= 3) {
                break;
            }
            if (!stringFields(raw, Set.of("type", "label", "target"))
                    || !(raw.get("requiresAuth") instanceof Boolean)) {
                continue;
            }
            String type = text(raw.get("type"), 40);
            if (!ACTION_TYPES.contains(type) || !"resource".equals(text(raw.get("target"), 40))) {
                continue;
            }
            AssistantResourceAction action = new AssistantResourceAction();
            action.setType(type);
            action.setLabel(text(raw.get("label"), 80));
            action.setTarget("resource");
            action.setRequiresAuth(Boolean.TRUE.equals(raw.get("requiresAuth")));
            result.add(action);
        }
        return result;
    }

    private AssistantResourceIntegrity resourceIntegrity(Object value) {
        Map<String, Object> raw = mapValue(value);
        if (raw.isEmpty()) {
            return null;
        }
        String digest = normalizeSha256(raw.get("digest"));
        Long size = nonNegativeLong(raw.get("size"));
        if (!"SHA-256".equals(raw.get("algorithm")) || !SHA256.matcher(digest).matches() || size == null) {
            return null;
        }
        AssistantResourceIntegrity integrity = new AssistantResourceIntegrity();
        integrity.setAlgorithm("SHA-256");
        integrity.setDigest(digest.replace("sha256:", ""));
        integrity.setSize(size);
        return integrity;
    }

    private boolean validFileIntegrity(Map<String, Object> payload, AssistantResourceIntegrity integrity) {
        if (integrity == null || payload == null || !"file".equals(payload.get("type"))) {
            return false;
        }
        Long payloadSize = nonNegativeLong(payload.get("size"));
        String payloadDigest = normalizeSha256(payload.get("digest")).replace("sha256:", "");
        return payloadSize != null
                && payloadSize.equals(integrity.getSize())
                && payloadDigest.equals(integrity.getDigest());
    }

    private AssistantEvidenceChainDTO validateEvidence(Object value,
                                                        List<AssistantResourceDTO> resources,
                                                        String invalidState,
                                                        String expectedAnswer,
                                                        String expectedQuery,
                                                        boolean resourcesTruncated) {
        JsonNode node = value instanceof JsonNode jsonNode ? jsonNode : objectMapper.valueToTree(value);
        if (node == null || !node.isObject()) {
            return stateChain(invalidState, false);
        }
        try {
            if (objectMapper.writeValueAsBytes(node).length > MAX_RAW_ENVELOPE_BYTES) {
                return stateChain(invalidState, true);
            }
            JsonNode integrityNode = node.path("integrity");
            String expectedDigest = integrityNode.path("digest").asText("");
            if (!"assistant-evidence-v1".equals(node.path("schemaVersion").asText())
                    || !"SHA-256".equals(integrityNode.path("algorithm").asText())
                    || !"canonical-json-without-integrity".equals(integrityNode.path("scope").asText())
                    || integrityNode.path("signed").asBoolean(true)
                    || !EVIDENCE_DIGEST.matcher(expectedDigest).matches()
                    || !expectedDigest.equals(canonicalEvidenceDigest(node))) {
                return stateChain("integrity_failed", false);
            }
            if (!validEvidenceNodeTypes(node)) {
                return stateChain(invalidState, false);
            }
            AssistantEvidenceChainDTO chain = objectMapper.treeToValue(node, AssistantEvidenceChainDTO.class);
            if (!validDigest(chain.getQueryDigest())
                    || !validDigest(chain.getAnswerDigest())
                    || !utcTimestamp(chain.getGeneratedAt())) {
                return stateChain("integrity_failed", false);
            }
            if (!GROUNDING_STATUSES.contains(chain.getStatus())
                    || !EVIDENCE_STATES.contains(chain.getEvidenceState())
                    || !IDENTIFIER.matcher(defaultText(chain.getChainId(), "")).matches()
                    || !IDENTIFIER.matcher(defaultText(chain.getRequestId(), "")).matches()
                    || chain.getGeneration() == null
                    || !validGeneration(chain)
                    || chain.getSources() == null
                    || chain.getSteps() == null
                    || chain.getResourceLinks() == null
                    || !validSources(chain.getSources())
                    || !validSteps(chain.getSteps())
                    || !validResourceLinks(
                    resources, chain.getResourceLinks(), chain.getSources(), resourcesTruncated)) {
                return stateChain(invalidState, false);
            }
            if (!chain.getAnswerDigest().equals(sha256Text(expectedAnswer))) {
                return stateChain("integrity_failed", false);
            }
            if (expectedQuery != null && !chain.getQueryDigest().equals(sha256Text(expectedQuery))) {
                return stateChain("integrity_failed", false);
            }
            if (!"available".equals(chain.getEvidenceState())) {
                return "model_only".equals(chain.getStatus())
                        && chain.getSources().isEmpty()
                        && chain.getSteps().isEmpty()
                        ? chain : stateChain(invalidState, false);
            }
            boolean hasSources = !chain.getSources().isEmpty();
            if ("grounded".equals(chain.getStatus()) != hasSources) {
                return stateChain(invalidState, false);
            }
            boolean truncated = resourcesTruncated || chain.getSources().size() > MAX_SOURCES
                    || chain.getSteps().size() > 20
                    || chain.getSources().stream().anyMatch(source -> source.getExcerpt() != null
                    && source.getExcerpt().length() > MAX_EXCERPT_CHARS);
            List<AssistantEvidenceSource> sources = new ArrayList<>();
            for (AssistantEvidenceSource source : chain.getSources().stream().limit(MAX_SOURCES).toList()) {
                if (source.getExcerpt() != null && source.getExcerpt().length() > MAX_EXCERPT_CHARS) {
                    source.setExcerpt(source.getExcerpt().substring(0, MAX_EXCERPT_CHARS));
                }
                sources.add(source);
            }
            chain.setSources(sources);
            chain.setSteps(new ArrayList<>(chain.getSteps().stream().limit(20).toList()));
            retainEvidenceLinks(resources, sources);
            rebuildResourceLinks(chain, resources);
            chain.setTruncated(chain.isTruncated() || truncated);
            refreshEvidenceIntegrity(chain);
            return chain;
        } catch (Exception error) {
            return stateChain(invalidState, false);
        }
    }

    private boolean validSources(List<AssistantEvidenceSource> sources) {
        if (sources.size() > 200) {
            return false;
        }
        Set<String> ids = new HashSet<>();
        for (AssistantEvidenceSource source : sources) {
            if (source == null
                    || !IDENTIFIER.matcher(defaultText(source.getEvidenceId(), "")).matches()
                    || !ids.add(source.getEvidenceId())
                    || !StringUtils.hasText(source.getSourceType())
                    || !StringUtils.hasText(source.getSourceId())
                    || !StringUtils.hasText(source.getTitle())
                    || !StringUtils.hasText(source.getAccessScope())
                    || !boundedText(source.getSourceType(), 80)
                    || !boundedText(source.getSourceId(), 256)
                    || !boundedText(source.getTitle(), 240)
                    || !boundedText(source.getExcerpt(), 12_000)
                    || !validDigest(source.getContentDigest())
                    || !utcTimestamp(source.getRetrievedAt())
                    || !boundedText(source.getSourceVersion(), 128)
                    || !boundedText(source.getAccessScope(), 80)
                    || !safeMetadata(source.getMetadata()).equals(source.getMetadata() == null ? Map.of() : source.getMetadata())) {
                return false;
            }
        }
        return true;
    }

    private boolean validGeneration(AssistantEvidenceChainDTO chain) {
        return StringUtils.hasText(chain.getGeneration().getAgent())
                && StringUtils.hasText(chain.getGeneration().getAnswerType())
                && boundedText(chain.getGeneration().getAgent(), 64)
                && boundedText(chain.getGeneration().getModel(), 128)
                && boundedText(chain.getGeneration().getAnswerType(), 64);
    }

    private boolean validEvidenceNodeTypes(JsonNode node) {
        if (!textFields(node, Set.of(
                "schemaVersion", "chainId", "requestId", "status", "generatedAt", "evidenceState",
                "queryDigest", "answerDigest"))
                || !node.path("sources").isArray()
                || !node.path("steps").isArray()
                || !node.path("resourceLinks").isArray()
                || !node.path("generation").isObject()
                || !node.path("integrity").isObject()
                || node.has("truncated") && !node.path("truncated").isBoolean()) {
            return false;
        }
        JsonNode integrity = node.path("integrity");
        if (!textFields(integrity, Set.of("algorithm", "digest", "scope"))
                || !integrity.path("signed").isBoolean()) {
            return false;
        }
        JsonNode generation = node.path("generation");
        if (!textFields(generation, Set.of("agent", "model", "answerType"))
                || !generation.path("profileContextUsed").isBoolean()) {
            return false;
        }
        for (JsonNode source : node.path("sources")) {
            if (!source.isObject()
                    || !textFields(source, Set.of(
                    "evidenceId", "sourceType", "sourceId", "title", "excerpt", "retrievedAt",
                    "contentDigest", "accessScope"))
                    || source.has("sourceVersion") && !source.path("sourceVersion").isTextual()
                    || source.has("metadata") && !safeObjectNode(source.path("metadata"))) {
                return false;
            }
        }
        for (JsonNode step : node.path("steps")) {
            if (!step.isObject() || !textFields(step, Set.of("stage"))
                    || !step.path("detail").isObject() || !safeObjectNode(step.path("detail"))) {
                return false;
            }
        }
        for (JsonNode link : node.path("resourceLinks")) {
            if (!link.isObject() || !textFields(link, Set.of("resourceId"))
                    || !link.path("evidenceIds").isArray()) {
                return false;
            }
            for (JsonNode evidenceId : link.path("evidenceIds")) {
                if (!evidenceId.isTextual()) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean textFields(JsonNode node, Set<String> fields) {
        if (node == null || !node.isObject()) {
            return false;
        }
        return fields.stream().allMatch(field -> node.has(field) && node.path(field).isTextual());
    }

    private boolean safeObjectNode(JsonNode node) {
        if (!node.isObject()) {
            return false;
        }
        var fields = node.properties().iterator();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            JsonNode value = entry.getValue();
            if (!(value.isTextual() || value.isBoolean() || value.isIntegralNumber()
                    || value.isFloatingPointNumber() && Double.isFinite(value.asDouble()))) {
                return false;
            }
        }
        return true;
    }

    private boolean validSteps(List<AssistantEvidenceStep> steps) {
        if (steps.size() > 100) {
            return false;
        }
        for (AssistantEvidenceStep step : steps) {
            if (step == null || !IDENTIFIER.matcher(defaultText(step.getStage(), "")).matches()) {
                return false;
            }
            Map<String, Object> detail = step.getDetail() == null ? Map.of() : step.getDetail();
            if (detail.size() > SAFE_STEP_DETAIL_KEYS.size()) {
                return false;
            }
            for (Map.Entry<String, Object> entry : detail.entrySet()) {
                if (!SAFE_STEP_DETAIL_KEYS.contains(entry.getKey()) || forbidden(entry.getKey())) {
                    return false;
                }
                Object item = entry.getValue();
                if (!(item instanceof String || item instanceof Number || item instanceof Boolean)) {
                    return false;
                }
                if (item instanceof String text
                        && (text.length() > 300 || isLegacyInternalUrl(text) || forbiddenText(text))) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean validResourceLinks(List<AssistantResourceDTO> resources,
                                       List<AssistantResourceLink> links,
                                       List<AssistantEvidenceSource> sources,
                                       boolean resourcesTruncated) {
        Set<String> sourceIds = sources.stream().map(AssistantEvidenceSource::getEvidenceId).collect(java.util.stream.Collectors.toSet());
        Map<String, Set<String>> resourceSide = new TreeMap<>();
        for (AssistantResourceDTO resource : resources) {
            resourceSide.put(resource.getId(), new LinkedHashSet<>(resource.getEvidenceIds() == null ? List.of() : resource.getEvidenceIds()));
        }
        Map<String, Set<String>> chainSide = new TreeMap<>();
        for (AssistantResourceLink link : links) {
            List<String> evidenceIds = link == null || link.getEvidenceIds() == null ? List.of() : link.getEvidenceIds();
            if (link == null || !IDENTIFIER.matcher(defaultText(link.getResourceId(), "")).matches()
                    || new HashSet<>(evidenceIds).size() != evidenceIds.size()
                    || !sourceIds.containsAll(evidenceIds)
                    || chainSide.putIfAbsent(link.getResourceId(),
                    new LinkedHashSet<>(evidenceIds)) != null) {
                return false;
            }
        }
        if (!resourcesTruncated) {
            return resourceSide.equals(chainSide);
        }
        return resourceSide.entrySet().stream()
                .allMatch(entry -> entry.getValue().equals(chainSide.get(entry.getKey())));
    }

    private void retainEvidenceLinks(List<AssistantResourceDTO> resources,
                                     List<AssistantEvidenceSource> sources) {
        Set<String> retained = sources.stream().map(AssistantEvidenceSource::getEvidenceId)
                .collect(java.util.stream.Collectors.toSet());
        for (AssistantResourceDTO resource : resources) {
            List<String> ids = resource.getEvidenceIds() == null ? List.of() : resource.getEvidenceIds();
            resource.setEvidenceIds(ids.stream().filter(retained::contains).distinct().toList());
            if ("grounded".equals(resource.getGroundingStatus()) && resource.getEvidenceIds().isEmpty()) {
                resource.setGroundingStatus("model_only");
            }
        }
    }

    private void rebuildResourceLinks(AssistantEvidenceChainDTO chain,
                                      List<AssistantResourceDTO> resources) {
        List<AssistantResourceLink> links = new ArrayList<>();
        for (AssistantResourceDTO resource : resources) {
            AssistantResourceLink link = new AssistantResourceLink();
            link.setResourceId(resource.getId());
            link.setEvidenceIds(resource.getEvidenceIds() == null ? List.of() : resource.getEvidenceIds());
            links.add(link);
        }
        chain.setResourceLinks(links);
    }

    private String canonicalEvidenceDigest(JsonNode source) throws Exception {
        ObjectNode unsigned = ((ObjectNode) source.deepCopy());
        unsigned.remove("integrity");
        JsonNode canonical = canonicalNode(unsigned);
        byte[] bytes = objectMapper.writeValueAsString(canonical).getBytes(StandardCharsets.UTF_8);
        return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private void refreshEvidenceIntegrity(AssistantEvidenceChainDTO chain) throws Exception {
        if (chain.getIntegrity() == null) {
            chain.setIntegrity(new AssistantEvidenceIntegrity());
        }
        chain.getIntegrity().setAlgorithm("SHA-256");
        chain.getIntegrity().setScope("canonical-json-without-integrity");
        chain.getIntegrity().setSigned(false);
        chain.getIntegrity().setDigest(canonicalEvidenceDigest(objectMapper.valueToTree(chain)));
    }

    private JsonNode canonicalNode(JsonNode value) {
        if (value.isObject()) {
            ObjectNode sorted = objectMapper.createObjectNode();
            List<String> names = new ArrayList<>();
            value.fieldNames().forEachRemaining(names::add);
            names.sort(Comparator.naturalOrder());
            for (String name : names) {
                sorted.set(name, canonicalNode(value.get(name)));
            }
            return sorted;
        }
        if (value.isArray()) {
            ArrayNode array = objectMapper.createArrayNode();
            value.forEach(item -> array.add(canonicalNode(item)));
            return array;
        }
        return value.deepCopy();
    }

    private AssistantEvidenceChainDTO stateChain(String state, boolean truncated) {
        AssistantEvidenceChainDTO chain = new AssistantEvidenceChainDTO();
        chain.setSchemaVersion("assistant-evidence-v1");
        chain.setStatus("model_only");
        chain.setEvidenceState(EVIDENCE_STATES.contains(state) ? state : "malformed");
        chain.setSources(List.of());
        chain.setSteps(List.of());
        chain.setResourceLinks(List.of());
        chain.setTruncated(truncated);
        AssistantEvidenceIntegrity integrity = new AssistantEvidenceIntegrity();
        integrity.setAlgorithm("SHA-256");
        integrity.setScope("canonical-json-without-integrity");
        integrity.setSigned(false);
        chain.setIntegrity(integrity);
        return chain;
    }

    private AssistantEvidenceChainDTO sealFailureChain(AssistantEvidenceChainDTO chain,
                                                       List<AssistantResourceDTO> resources,
                                                       LlmChatResponse response,
                                                       String expectedQuery) {
        try {
            String answer = response == null ? "" : defaultText(response.getAnswer(), "");
            String query = defaultText(expectedQuery, "");
            String seed = sha256Text(query + "\n" + answer).substring("sha256:".length(), "sha256:".length() + 24);
            chain.setSchemaVersion("assistant-evidence-v1");
            chain.setChainId("chain_failure_" + seed);
            chain.setRequestId("req_failure_" + seed);
            chain.setStatus("model_only");
            chain.setGeneratedAt(Instant.now().toString());
            if (!EVIDENCE_STATES.contains(chain.getEvidenceState()) || "available".equals(chain.getEvidenceState())) {
                chain.setEvidenceState("generation_failed");
            }
            chain.setQueryDigest(sha256Text(query));
            chain.setAnswerDigest(sha256Text(answer));
            chain.setSources(List.of());
            chain.setSteps(List.of());
            rebuildResourceLinks(chain, resources);
            AssistantEvidenceGeneration generation = new AssistantEvidenceGeneration();
            generation.setAgent(response == null ? "leader_agent" : defaultText(response.getAgentName(), "leader_agent"));
            generation.setModel(response == null ? "" : defaultText(response.getModel(), ""));
            generation.setAnswerType(response == null ? "text" : defaultText(response.getAnswerType(), "text"));
            generation.setProfileContextUsed(false);
            chain.setGeneration(generation);
            refreshEvidenceIntegrity(chain);
            return chain;
        } catch (Exception error) {
            log.warn("assistant failure evidence sealing failed errorType={}", error.getClass().getSimpleName());
            return stateChain("generation_failed", chain != null && chain.isTruncated());
        }
    }

    private List<Map<String, Object>> sanitizeAttachments(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> allowed = Set.of(
                "name", "fileName", "title", "type", "ext", "mimeType", "toolName", "formatLabel",
                "source", "sourceType", "sourceId", "storageKey", "serverGenerated", "sha256", "size",
                "createdAt", "expiresAt", "url", "previewUrl", "status");
        Set<String> textFields = new HashSet<>(allowed);
        textFields.remove("serverGenerated");
        textFields.remove("size");
        for (Map<String, Object> raw : mapList(value)) {
            if (!stringFields(raw, textFields)
                    || raw.get("serverGenerated") != null && !(raw.get("serverGenerated") instanceof Boolean)
                    || raw.get("size") != null && !(raw.get("size") instanceof Number)
                    || raw.get("size") instanceof Number && nonNegativeLong(raw.get("size")) == null
                    || raw.get("createdAt") instanceof String createdAt && !utcTimestamp(createdAt)
                    || raw.get("expiresAt") instanceof String expiresAt && !utcTimestamp(expiresAt)
                    || invalidAttachmentUrl(raw.get("url"), raw.get("storageKey"))
                    || invalidAttachmentUrl(raw.get("previewUrl"), raw.get("storageKey"))) {
                continue;
            }
            Map<String, Object> safe = new LinkedHashMap<>();
            for (String key : allowed) {
                Object item = raw.get(key);
                if (item == null) {
                    continue;
                }
                if (Set.of("size").contains(key)) {
                    Long number = nonNegativeLong(item);
                    if (number != null) {
                        safe.put(key, number);
                    }
                } else if ("serverGenerated".equals(key)) {
                    safe.put(key, Boolean.TRUE.equals(item));
                } else if (Set.of("url", "previewUrl").contains(key)) {
                    String url = safeUrl(item);
                    if (validAttachmentUrl(url, text(raw.get("storageKey"), 300))) {
                        safe.put(key, url);
                    }
                } else {
                    putText(safe, key, item, Set.of("name", "fileName", "title").contains(key) ? 240 : 300);
                }
            }
            if (isLegacyInternalUrl(raw.get("url")) || isLegacyInternalUrl(raw.get("previewUrl"))) {
                safe.remove("url");
                safe.remove("previewUrl");
                safe.put("status", "legacy_unavailable");
            }
            if (!safe.isEmpty()) {
                result.add(safe);
            }
        }
        return result;
    }

    private boolean invalidAttachmentUrl(Object value, Object storageKeyValue) {
        String raw = text(value, 1_000);
        if (!StringUtils.hasText(raw) || isLegacyInternalUrl(raw)) {
            return false;
        }
        String safe = safeUrl(raw);
        return !validAttachmentUrl(safe, text(storageKeyValue, 300));
    }

    private boolean validAttachmentUrl(String url, String storageKey) {
        if (!StringUtils.hasText(url)) {
            return false;
        }
        if (url.startsWith("/api/")) {
            return StringUtils.hasText(storageKey) && boundExportUrl(url, storageKey);
        }
        return url.startsWith("/uploads/") || url.startsWith("https://");
    }

    private List<Map<String, Object>> sanitizeTrace(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> raw : mapList(value)) {
            if (result.size() >= 20) {
                break;
            }
            Map<String, Object> safe = new LinkedHashMap<>();
            for (String key : Set.of("stage", "agentName", "targetAgent", "toolName", "toolDisplayName", "routeReason", "intent", "strategy")) {
                putText(safe, key, raw.get(key), 300);
            }
            if (!safe.isEmpty()) {
                result.add(safe);
            }
        }
        return result;
    }

    private Map<String, Object> safeMetadata(Map<String, Object> raw) {
        Map<String, Object> safe = new LinkedHashMap<>();
        if (raw == null) {
            return safe;
        }
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            if (!SAFE_METADATA_KEYS.contains(entry.getKey()) || forbidden(entry.getKey())) {
                continue;
            }
            Object value = entry.getValue();
            if (value instanceof Number || value instanceof Boolean) {
                safe.put(entry.getKey(), value);
            } else if (value instanceof String) {
                putText(safe, entry.getKey(), value, 300);
            }
        }
        return safe;
    }

    private List<String> safeList(List<String> raw) {
        return raw == null ? List.of() : raw.stream().filter(StringUtils::hasText).map(item -> text(item, 80)).limit(20).toList();
    }

    private Map<String, Object> sanitizeGenericMap(Map<String, Object> raw, int depth) {
        if (raw == null || raw.isEmpty() || depth > 3) {
            return Map.of();
        }
        Map<String, Object> safe = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            if (safe.size() >= 50 || forbidden(entry.getKey())) {
                continue;
            }
            Object value = sanitizeGenericValue(entry.getKey(), entry.getValue(), depth + 1);
            if (value != null) {
                safe.put(entry.getKey(), value);
            }
        }
        return safe;
    }

    private Object sanitizeGenericValue(String key, Object value, int depth) {
        if (value == null || depth > 3) {
            return null;
        }
        if (value instanceof Map<?, ?>) {
            return sanitizeGenericMap(mapValue(value), depth);
        }
        if (value instanceof List<?> list) {
            List<Object> safe = new ArrayList<>();
            for (Object item : list) {
                if (safe.size() >= 20) {
                    break;
                }
                Object sanitized = sanitizeGenericValue(key, item, depth + 1);
                if (sanitized != null) {
                    safe.add(sanitized);
                }
            }
            return safe;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value;
        }
        String text = text(value, 1_000);
        String normalizedKey = key == null ? "" : key.toLowerCase(Locale.ROOT);
        if ((normalizedKey.contains("url") || normalizedKey.contains("endpoint"))
                && (text.startsWith("/") || text.startsWith("http"))
                && !StringUtils.hasText(safeUrl(text))) {
            return null;
        }
        return StringUtils.hasText(text) ? text : null;
    }

    private void markLegacyUnavailable(AssistantResourceDTO resource) {
        resource.setUrl("");
        resource.setPreviewUrl("");
        resource.setAvailability("legacy_unavailable");
        resource.setActions(List.of());
        Map<String, Object> metadata = new LinkedHashMap<>(resource.getMetadata() == null ? Map.of() : resource.getMetadata());
        metadata.put("status", "legacy_unavailable");
        resource.setMetadata(metadata);
    }

    private void downgradeUntrustedGrounding(List<AssistantResourceDTO> resources) {
        for (AssistantResourceDTO resource : resources) {
            resource.setGroundingStatus("model_only");
            resource.setEvidenceIds(List.of());
        }
    }

    private String downloadUrl(String sessionId, Long messageId, String storageKey) {
        return "/api/ai/leader/sessions/"
                + UriUtils.encodePathSegment(sessionId, StandardCharsets.UTF_8)
                + "/messages/" + messageId
                + "/exports/" + storageKey;
    }

    private int envelopeBytes(List<AssistantResourceDTO> resources, AssistantEvidenceChainDTO chain) {
        try {
            return objectMapper.writeValueAsBytes(Map.of(
                    "resources", resources == null ? List.of() : resources,
                    "evidenceChain", chain == null ? stateChain("generation_failed", false) : chain)).length;
        } catch (Exception error) {
            return Integer.MAX_VALUE;
        }
    }

    private void fitEnvelope(List<AssistantResourceDTO> resources, AssistantEvidenceChainDTO chain) {
        if (envelopeBytes(resources, chain) <= MAX_ENVELOPE_BYTES) {
            return;
        }
        chain.setTruncated(true);
        for (AssistantResourceDTO resource : resources) {
            resource.setSummary(truncateText(resource.getSummary(), 160));
            resource.setMetadata(Map.of());
            Map<String, Object> payload = resource.getPayload();
            if (payload != null && "content".equals(payload.get("type")) && payload.get("content") instanceof String content) {
                Map<String, Object> trimmed = new LinkedHashMap<>(payload);
                trimmed.put("content", truncateText(content, 2_000));
                resource.setPayload(trimmed);
            }
        }
        if (chain.getSources() != null) {
            for (AssistantEvidenceSource source : chain.getSources()) {
                source.setExcerpt(truncateText(source.getExcerpt(), 320));
            }
        }
        refreshChainAfterTrimming(chain, resources);
        while (envelopeBytes(resources, chain) > MAX_ENVELOPE_BYTES && resources.size() > 1) {
            resources.removeLast();
            refreshChainAfterTrimming(chain, resources);
        }
        if (envelopeBytes(resources, chain) > MAX_ENVELOPE_BYTES && !resources.isEmpty()) {
            AssistantResourceDTO first = resources.getFirst();
            Map<String, Object> payload = first.getPayload();
            if (payload != null && payload.get("content") instanceof String content) {
                Map<String, Object> trimmed = new LinkedHashMap<>(payload);
                trimmed.put("content", truncateText(content, 500));
                first.setPayload(trimmed);
            }
            first.setSummary(truncateText(first.getSummary(), 80));
            refreshChainAfterTrimming(chain, resources);
        }
    }

    private void refreshChainAfterTrimming(AssistantEvidenceChainDTO chain,
                                           List<AssistantResourceDTO> resources) {
        if (!"available".equals(chain.getEvidenceState())) {
            return;
        }
        try {
            rebuildResourceLinks(chain, resources);
            refreshEvidenceIntegrity(chain);
        } catch (Exception error) {
            chain.setEvidenceState("generation_failed");
            chain.setSources(List.of());
            chain.setResourceLinks(List.of());
        }
    }

    private boolean isLegacyInternalUrl(Object value) {
        String url = text(value, 1_000);
        if (!StringUtils.hasText(url)) {
            return false;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.contains("localhost")
                || lower.contains("127.0.0.1")
                || lower.contains("/generated/")
                || lower.matches("https?://10\\..*")
                || lower.matches("https?://192\\.168\\..*")
                || lower.matches("https?://172\\.(1[6-9]|2[0-9]|3[01])\\..*");
    }

    private String safeUrl(Object value) {
        String url = text(value, 1_000);
        if (!StringUtils.hasText(url)) {
            return "";
        }
        if (url.startsWith("/") && !url.startsWith("//")) {
            try {
                URI relative = URI.create(url);
                if ((relative.getPath().startsWith("/api/") || relative.getPath().startsWith("/uploads/"))
                        && relative.getRawFragment() == null
                        && !secretQuery(relative.getRawQuery())
                        && !url.contains("..") && !url.contains("\\")
                        && !url.toLowerCase(Locale.ROOT).contains("%2e")
                        && !url.toLowerCase(Locale.ROOT).contains("%5c")) {
                    return url;
                }
            } catch (Exception ignored) {
                return "";
            }
            return "";
        }
        try {
            URI uri = URI.create(url);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getRawUserInfo() != null
                    || uri.getPort() != -1 && uri.getPort() != 443
                    || uri.getRawFragment() != null
                    || !host.contains(".")
                    || host.endsWith(".")
                    || host.equals("localhost")
                    || host.endsWith(".local")
                    || host.endsWith(".internal")
                    || host.startsWith("127.")
                    || host.startsWith("10.")
                    || host.startsWith("192.168.")
                    || host.startsWith("169.254.")
                    || host.equals("0.0.0.0")
                    || host.equals("::1")
                    || host.startsWith("fe80:")
                    || host.startsWith("fc")
                    || host.startsWith("fd")
                    || private172(host)
                    || !allowedPublicHost(host)
                    || secretQuery(uri.getRawQuery())) {
                return "";
            }
            return url;
        } catch (Exception ignored) {
            return "";
        }
    }

    private boolean validResourceUrls(boolean persisted,
                                      String authScope,
                                      String storageKey,
                                      String rawUrl,
                                      String rawPreviewUrl,
                                      String url,
                                      String previewUrl,
                                      List<AssistantResourceAction> actions) {
        boolean hasRawUrl = StringUtils.hasText(rawUrl) || StringUtils.hasText(rawPreviewUrl);
        if (persisted && (isLegacyInternalUrl(rawUrl) || isLegacyInternalUrl(rawPreviewUrl))) {
            return true;
        }
        if (!hasRawUrl) {
            return true;
        }
        if (!StringUtils.hasText(url) && StringUtils.hasText(rawUrl)
                || !StringUtils.hasText(previewUrl) && StringUtils.hasText(rawPreviewUrl)) {
            return false;
        }
        if ("public".equals(authScope)) {
            if (actions.stream().anyMatch(AssistantResourceAction::isRequiresAuth)) {
                return false;
            }
            return publicResourceUrl(url) && publicResourceUrl(previewUrl);
        }
        if (!persisted || !StringUtils.hasText(storageKey)) {
            return false;
        }
        return boundExportUrl(url, storageKey) && !StringUtils.hasText(previewUrl);
    }

    private boolean publicResourceUrl(String value) {
        return !StringUtils.hasText(value)
                || value.startsWith("/uploads/")
                || value.startsWith("https://");
    }

    private boolean boundExportUrl(String value, String storageKey) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return value.matches("/api/ai/leader/sessions/[^/?#]+/messages/[0-9]+/exports/"
                + Pattern.quote(storageKey));
    }

    private boolean allowedPublicHost(String host) {
        return allowedPublicHosts.stream().anyMatch(allowed -> host.equals(allowed) || host.endsWith("." + allowed));
    }

    private boolean secretQuery(String rawQuery) {
        if (!StringUtils.hasText(rawQuery)) {
            return false;
        }
        try {
            String decoded = java.net.URLDecoder.decode(rawQuery, StandardCharsets.UTF_8);
            for (String parameter : decoded.split("[&;]")) {
                String key = parameter.contains("=") ? parameter.substring(0, parameter.indexOf('=')) : parameter;
                String normalized = key.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
                if (normalized.contains("token")
                        || normalized.contains("capability")
                        || normalized.contains("authorization")
                        || normalized.contains("apikey")
                        || normalized.contains("secret")) {
                    return true;
                }
            }
            return false;
        } catch (Exception ignored) {
            return true;
        }
    }

    private boolean private172(String host) {
        if (!host.startsWith("172.")) {
            return false;
        }
        String[] parts = host.split("\\.");
        if (parts.length < 2) {
            return true;
        }
        try {
            int second = Integer.parseInt(parts[1]);
            return second >= 16 && second <= 31;
        } catch (NumberFormatException ignored) {
            return true;
        }
    }

    private Set<String> parseAllowedHosts(String value) {
        if (!StringUtils.hasText(value)) {
            return Set.of();
        }
        Set<String> hosts = new LinkedHashSet<>();
        for (String item : value.split(",")) {
            String host = item.trim().toLowerCase(Locale.ROOT);
            if (host.matches("[a-z0-9.-]+") && host.contains(".") && !host.startsWith(".") && !host.endsWith(".")) {
                hosts.add(host);
            }
        }
        return Set.copyOf(hosts);
    }

    private String normalizeSha256(Object value) {
        String digest = text(value, 80).toLowerCase(Locale.ROOT);
        return digest;
    }

    private boolean validDigest(String digest) {
        return StringUtils.hasText(digest) && EVIDENCE_DIGEST.matcher(digest).matches();
    }

    private String sha256Text(String value) throws Exception {
        byte[] bytes = String.valueOf(value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private boolean utcTimestamp(String value) {
        return StringUtils.hasText(value) && value.endsWith("Z") && instant(value) != null;
    }

    private Instant strictUtcInstant(Object value) {
        return value instanceof String timestamp && utcTimestamp(timestamp) ? instant(timestamp) : null;
    }

    private boolean finite(Number value) {
        if (value instanceof Double doubleValue) {
            return Double.isFinite(doubleValue);
        }
        if (value instanceof Float floatValue) {
            return Float.isFinite(floatValue);
        }
        return true;
    }

    private boolean boundedText(String value, int maxLength) {
        return value == null || value.length() <= maxLength;
    }

    private boolean forbiddenText(String value) {
        String normalized = value == null ? "" : value.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        return FORBIDDEN_KEYS.stream().anyMatch(normalized::contains);
    }

    private String truncateText(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private Instant instant(Object value) {
        try {
            return Instant.parse(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private Long nonNegativeLong(Object value) {
        if (!(value instanceof Number number) || !finite(number)) {
            return null;
        }
        try {
            long result = new java.math.BigDecimal(number.toString()).longValueExact();
            return result >= 0 ? result : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean forbidden(String key) {
        String normalized = key == null ? "" : key.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        return FORBIDDEN_KEYS.stream().anyMatch(normalized::contains);
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String text(Object value, int max) {
        if (value == null) {
            return "";
        }
        if (value instanceof Map<?, ?> || value instanceof Iterable<?> || value.getClass().isArray()) {
            return "";
        }
        String result = String.valueOf(value).trim();
        return result.length() <= max ? result : result.substring(0, max);
    }

    private void putText(Map<String, Object> target, String key, Object value, int max) {
        String result = text(value, max);
        if (StringUtils.hasText(result)) {
            target.put(key, result);
        }
    }

    private void putScalar(Map<String, Object> target, String key, Object value) {
        if (value instanceof Number || value instanceof Boolean) {
            target.put(key, value);
        } else {
            putText(target, key, value, 300);
        }
    }

    private Object first(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                return value;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().filter(Map.class::isInstance).map(this::mapValue).toList();
    }

    private List<Map<String, Object>> copyMapList(List<Map<String, Object>> value) {
        if (value == null) {
            return List.of();
        }
        return value.stream().map(LinkedHashMap::new).map(item -> (Map<String, Object>) item).toList();
    }

    private List<String> stringList(Object value, int maxItems, int maxLength) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(item -> text(item, maxLength)).filter(StringUtils::hasText).limit(maxItems).toList();
    }

    private boolean stringFields(Map<String, Object> value, Set<String> fields) {
        for (String field : fields) {
            Object item = value.get(field);
            if (item != null && !(item instanceof String)) {
                return false;
            }
        }
        return true;
    }
}
