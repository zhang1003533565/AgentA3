package com.example.appbackend.service.impl;

import com.example.appbackend.dto.AiLeaderMessageItem;
import com.example.appbackend.dto.AssistantEvidenceChainDTO;
import com.example.appbackend.dto.AssistantEvidenceIntegrity;
import com.example.appbackend.dto.AssistantEvidenceSource;
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
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9:_-]{1,160}");
    private static final Pattern SHA256 = Pattern.compile("(?:sha256:)?[0-9a-f]{64}");
    private static final Pattern STORAGE_KEY = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.[a-z0-9]{1,16}");
    private static final Set<String> RESOURCE_KINDS = Set.of(
            "explanation", "mind_map", "diagram", "exercise", "code_example", "extended_reading",
            "image", "video", "audio", "document", "presentation", "spreadsheet", "bundle",
            "course", "activity", "meeting", "dining", "facility", "secondhand");
    private static final Set<String> DELIVERY_TYPES = Set.of(
            "content", "image", "video", "audio", "document", "presentation", "spreadsheet",
            "bundle", "business_card");
    private static final Set<String> GROUNDING_STATUSES = Set.of("grounded", "context_only", "model_only");
    private static final Set<String> ACTION_TYPES = Set.of("open_resource", "download", "preview", "follow_up");
    private static final Set<String> EVIDENCE_STATES = Set.of(
            "available", "legacy_missing", "malformed", "integrity_failed", "generation_failed");
    private static final Set<String> SAFE_METADATA_KEYS = Set.of(
            "source", "sourceId", "sourceType", "sourceVersion", "retrievedAt", "title", "type", "kind",
            "time", "location", "route", "status", "legacy", "serverGenerated");
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

    public AssistantEnvelopeService(AiLeaderMessageRepository messageRepository,
                                    AiLeaderGeneratedExportRepository exportRepository,
                                    ObjectMapper objectMapper) {
        this.messageRepository = messageRepository;
        this.exportRepository = exportRepository;
        this.objectMapper = objectMapper;
    }

    public record PreparedEnvelope(List<Map<String, Object>> internalAttachments) {
    }

    public PreparedEnvelope prepareLiveResponse(LlmChatResponse response, Map<String, Object> rawResult) {
        response.setOutputMeta(sanitizeGenericMap(response.getOutputMeta(), 0));
        response.setRetrievalMeta(sanitizeGenericMap(response.getRetrievalMeta(), 0));
        response.setTrace(sanitizeTrace(response.getTrace()));
        Object matched = rawResult.containsKey("documents") ? rawResult.get("documents") : rawResult.get("matchedResults");
        response.setMatchedResults(sanitizeMatchedResults(matched));
        List<Map<String, Object>> internalAttachments = mapList(rawResult.get("attachments"));
        response.setAttachments(sanitizeAttachments(internalAttachments));
        List<AssistantResourceDTO> resources = sanitizeResources(rawResult.get("resources"));
        AssistantEvidenceChainDTO chain = validateEvidence(rawResult.get("evidenceChain"), resources, "generation_failed");
        if (!"available".equals(chain.getEvidenceState())) {
            downgradeUntrustedGrounding(resources);
        }
        if (envelopeBytes(resources, chain) > MAX_ENVELOPE_BYTES) {
            resources = List.of();
            chain = stateChain("generation_failed", true);
        }
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

    public void restoreEnvelope(AiLeaderMessage message, AiLeaderMessageItem item) {
        item.setMatchedResults(readMatchedResults(message));
        List<AssistantResourceDTO> resources = readResources(message);
        AssistantEvidenceChainDTO evidenceChain = readEvidence(message, resources);
        if (!"available".equals(evidenceChain.getEvidenceState())) {
            downgradeUntrustedGrounding(resources);
        }
        item.setResources(resources);
        item.setEvidenceChain(evidenceChain);
        item.setAttachments(readAttachments(message));
    }

    public void overwriteSsePayload(Object eventPayload, LlmChatResponse response) {
        if (!(eventPayload instanceof Map<?, ?> source)) {
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
        for (Map<String, Object> attachment : internalAttachments == null ? List.<Map<String, Object>>of() : internalAttachments) {
            if (!Boolean.TRUE.equals(attachment.get("serverGenerated"))) {
                continue;
            }
            String storageKey = text(attachment.get("storageKey"), 300);
            List<AssistantResourceDTO> matchingResources = resourcesByStorage.getOrDefault(storageKey, List.of());
            Optional<GeneratedManifest> parsed = parseManifest(attachment);
            if (matchingResources.size() != 1 || parsed.isEmpty()) {
                continue;
            }
            AssistantResourceDTO resource = matchingResources.getFirst();
            GeneratedManifest manifest = parsed.get();
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
        String storageKey = text(attachment.get("storageKey"), 300);
        String capability = text(attachment.get("internalCapability"), 200);
        String digest = normalizeSha256(attachment.get("sha256"));
        Long size = nonNegativeLong(attachment.get("size"));
        String fileName = text(first(attachment, "fileName", "name", "title"), 240);
        String mimeType = text(attachment.get("mimeType"), 160);
        Instant createdAt = instant(attachment.get("createdAt"));
        Instant expiresAt = instant(attachment.get("expiresAt"));
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

    private List<Map<String, Object>> readMatchedResults(AiLeaderMessage message) {
        JsonNode node = readJson(message.getId(), "matchedResults", message.getMatchedResultsJson());
        return node != null && node.isArray() ? sanitizeMatchedResults(objectMapper.convertValue(node, Object.class)) : List.of();
    }

    private List<AssistantResourceDTO> readResources(AiLeaderMessage message) {
        JsonNode node = readJson(message.getId(), "resources", message.getResourcesJson());
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<AssistantResourceDTO> resources = sanitizeResources(objectMapper.convertValue(node, Object.class));
        resources.forEach(resource -> resource.setMessageId(message.getId()));
        return resources;
    }

    private AssistantEvidenceChainDTO readEvidence(AiLeaderMessage message, List<AssistantResourceDTO> resources) {
        if (!StringUtils.hasText(message.getEvidenceChainJson()) || "{}".equals(message.getEvidenceChainJson().trim())) {
            return stateChain("legacy_missing", false);
        }
        JsonNode node = readJson(message.getId(), "evidenceChain", message.getEvidenceChainJson());
        if (node == null || !node.isObject()) {
            return stateChain("malformed", false);
        }
        return validateEvidence(node, resources, "integrity_failed");
    }

    private List<Map<String, Object>> readAttachments(AiLeaderMessage message) {
        JsonNode node = readJson(message.getId(), "attachments", message.getAttachmentsJson());
        return node != null && node.isArray()
                ? sanitizeAttachments(objectMapper.convertValue(node, Object.class))
                : List.of();
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

    private List<AssistantResourceDTO> sanitizeResources(Object value) {
        List<AssistantResourceDTO> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (Map<String, Object> raw : mapList(value)) {
            if (result.size() >= MAX_RESOURCES) {
                break;
            }
            Optional<AssistantResourceDTO> parsed = sanitizeResource(raw);
            if (parsed.isPresent() && ids.add(parsed.get().getId())) {
                result.add(parsed.get());
            }
        }
        return result;
    }

    private Optional<AssistantResourceDTO> sanitizeResource(Map<String, Object> raw) {
        String schemaVersion = text(raw.get("schemaVersion"), 64);
        String id = text(raw.get("id"), 80);
        String kind = text(raw.get("kind"), 40);
        String deliveryType = text(raw.get("deliveryType"), 40);
        String groundingStatus = text(raw.get("groundingStatus"), 40);
        if (!"assistant-resource-v1".equals(schemaVersion)
                || !IDENTIFIER.matcher(id).matches()
                || !RESOURCE_KINDS.contains(kind)
                || !DELIVERY_TYPES.contains(deliveryType)
                || !GROUNDING_STATUSES.contains(groundingStatus)) {
            return Optional.empty();
        }
        String storageKey = text(raw.get("storageKey"), 300);
        if (StringUtils.hasText(storageKey) && !STORAGE_KEY.matcher(storageKey).matches()) {
            return Optional.empty();
        }
        Optional<Map<String, Object>> payload = sanitizePayload(kind, mapValue(raw.get("payload")));
        if (payload.isEmpty()) {
            return Optional.empty();
        }
        List<String> evidenceIds = stringList(raw.get("evidenceIds"), 20, 160);
        if (new HashSet<>(evidenceIds).size() != evidenceIds.size()) {
            return Optional.empty();
        }
        if ("grounded".equals(groundingStatus) != !evidenceIds.isEmpty()) {
            return Optional.empty();
        }
        List<AssistantResourceAction> actions = sanitizeActions(raw.get("actions"));
        AssistantResourceIntegrity integrity = resourceIntegrity(raw.get("integrity"));
        if (StringUtils.hasText(storageKey) && !validFileIntegrity(payload.get(), integrity)) {
            return Optional.empty();
        }
        String createdAt = timestampText(raw.get("createdAt"));
        if (!StringUtils.hasText(createdAt)) {
            return Optional.empty();
        }
        String expiresAt = optionalTimestampText(raw.get("expiresAt"));
        if (raw.get("expiresAt") != null && StringUtils.hasText(String.valueOf(raw.get("expiresAt"))) && expiresAt == null) {
            return Optional.empty();
        }
        String authScope = text(raw.get("authScope"), 40);
        if (!Set.of("session_owner", "request_user", "public").contains(authScope)) {
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
        resource.setUrl(safeUrl(raw.get("url")));
        resource.setPreviewUrl(safeUrl(raw.get("previewUrl")));
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
        if (isLegacyInternalUrl(raw.get("url")) || isLegacyInternalUrl(raw.get("previewUrl"))) {
            markLegacyUnavailable(resource);
        }
        return Optional.of(resource);
    }

    private Optional<Map<String, Object>> sanitizePayload(String kind, Map<String, Object> raw) {
        String type = text(raw.get("type"), 20);
        Map<String, Object> safe = new LinkedHashMap<>();
        safe.put("type", type);
        if ("content".equals(type)) {
            putText(safe, "content", raw.get("content"), 12_000);
            putText(safe, "language", raw.get("language"), 40);
            return Optional.of(safe);
        }
        if ("file".equals(type)) {
            putText(safe, "format", raw.get("format"), 40);
            Long size = nonNegativeLong(raw.get("size"));
            if (size != null) {
                safe.put("size", size);
            }
            String digest = normalizeSha256(raw.get("digest"));
            if (StringUtils.hasText(digest) && SHA256.matcher(digest).matches()) {
                safe.put("digest", digest.startsWith("sha256:") ? digest : "sha256:" + digest);
            }
            return Optional.of(safe);
        }
        if ("business".equals(type) && BUSINESS_FIELDS.containsKey(kind)) {
            for (String key : BUSINESS_FIELDS.get(kind)) {
                Object item = raw.get(key);
                if (item instanceof Number || item instanceof Boolean) {
                    safe.put(key, item);
                } else if ("imageUrl".equals(key)) {
                    String url = safeUrl(item);
                    if (StringUtils.hasText(url)) {
                        safe.put(key, url);
                    }
                } else {
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
            String type = text(raw.get("type"), 40);
            if (!ACTION_TYPES.contains(type)) {
                continue;
            }
            AssistantResourceAction action = new AssistantResourceAction();
            action.setType(type);
            action.setLabel(text(raw.get("label"), 80));
            action.setTarget("resource");
            action.setRequiresAuth(!Boolean.FALSE.equals(raw.get("requiresAuth")));
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
                                                        String invalidState) {
        JsonNode node = value instanceof JsonNode jsonNode ? jsonNode : objectMapper.valueToTree(value);
        if (node == null || !node.isObject()) {
            return stateChain(invalidState, false);
        }
        try {
            if (objectMapper.writeValueAsBytes(node).length > MAX_ENVELOPE_BYTES) {
                return stateChain(invalidState, true);
            }
            JsonNode integrityNode = node.path("integrity");
            String expectedDigest = integrityNode.path("digest").asText("");
            if (!"assistant-evidence-v1".equals(node.path("schemaVersion").asText())
                    || !"SHA-256".equals(integrityNode.path("algorithm").asText())
                    || !"canonical-json-without-integrity".equals(integrityNode.path("scope").asText())
                    || integrityNode.path("signed").asBoolean(true)
                    || !expectedDigest.equals(canonicalEvidenceDigest(node))) {
                return stateChain(invalidState, false);
            }
            AssistantEvidenceChainDTO chain = objectMapper.treeToValue(node, AssistantEvidenceChainDTO.class);
            if (!GROUNDING_STATUSES.contains(chain.getStatus())
                    || !"available".equals(chain.getEvidenceState())
                    || !IDENTIFIER.matcher(defaultText(chain.getChainId(), "")).matches()
                    || !IDENTIFIER.matcher(defaultText(chain.getRequestId(), "")).matches()
                    || !validDigest(chain.getQueryDigest())
                    || !validDigest(chain.getAnswerDigest())
                    || instant(chain.getGeneratedAt()) == null
                    || chain.getGeneration() == null
                    || !StringUtils.hasText(chain.getGeneration().getAgent())
                    || chain.getSources() == null
                    || chain.getSources().size() > MAX_SOURCES
                    || chain.getResourceLinks() == null
                    || !validSources(chain.getSources())
                    || !validResourceLinks(resources, chain.getResourceLinks(), chain.getSources())) {
                return stateChain(invalidState, false);
            }
            boolean hasSources = !chain.getSources().isEmpty();
            if ("grounded".equals(chain.getStatus()) != hasSources) {
                return stateChain(invalidState, false);
            }
            chain.setSources(chain.getSources().stream().limit(MAX_SOURCES).toList());
            refreshEvidenceIntegrity(chain);
            return chain;
        } catch (Exception error) {
            return stateChain(invalidState, false);
        }
    }

    private boolean validSources(List<AssistantEvidenceSource> sources) {
        Set<String> ids = new HashSet<>();
        for (AssistantEvidenceSource source : sources) {
            if (source == null
                    || !IDENTIFIER.matcher(defaultText(source.getEvidenceId(), "")).matches()
                    || !ids.add(source.getEvidenceId())
                    || source.getExcerpt() != null && source.getExcerpt().length() > MAX_EXCERPT_CHARS
                    || !validDigest(source.getContentDigest())
                    || !safeMetadata(source.getMetadata()).equals(source.getMetadata() == null ? Map.of() : source.getMetadata())) {
                return false;
            }
        }
        return true;
    }

    private boolean validResourceLinks(List<AssistantResourceDTO> resources,
                                       List<AssistantResourceLink> links,
                                       List<AssistantEvidenceSource> sources) {
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
        return resourceSide.equals(chainSide);
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

    private List<Map<String, Object>> sanitizeAttachments(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> allowed = Set.of(
                "name", "fileName", "title", "type", "ext", "mimeType", "toolName", "formatLabel",
                "source", "sourceType", "sourceId", "storageKey", "serverGenerated", "sha256", "size",
                "createdAt", "expiresAt", "url", "previewUrl", "status");
        for (Map<String, Object> raw : mapList(value)) {
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
                    if (StringUtils.hasText(url)) {
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
            if ((url.startsWith("/api/") || url.startsWith("/uploads/"))
                    && !url.contains("..") && !url.contains("\\")
                    && !url.toLowerCase(Locale.ROOT).contains("%2e")
                    && !url.toLowerCase(Locale.ROOT).contains("%5c")) {
                return url;
            }
            return "";
        }
        try {
            URI uri = URI.create(url);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !host.contains(".")
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
                    || private172(host)) {
                return "";
            }
            return url;
        } catch (Exception ignored) {
            return "";
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

    private String normalizeSha256(Object value) {
        String digest = text(value, 80).toLowerCase(Locale.ROOT);
        return digest;
    }

    private boolean validDigest(String digest) {
        return StringUtils.hasText(digest) && SHA256.matcher(digest.toLowerCase(Locale.ROOT)).matches();
    }

    private Instant instant(Object value) {
        try {
            return Instant.parse(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String timestampText(Object value) {
        Instant parsed = instant(value);
        return parsed == null ? "" : parsed.toString();
    }

    private String optionalTimestampText(Object value) {
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return null;
        }
        String parsed = timestampText(value);
        return StringUtils.hasText(parsed) ? parsed : null;
    }

    private Long nonNegativeLong(Object value) {
        if (value instanceof Number number) {
            long result = number.longValue();
            return result >= 0 ? result : null;
        }
        try {
            long result = Long.parseLong(String.valueOf(value));
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
}
