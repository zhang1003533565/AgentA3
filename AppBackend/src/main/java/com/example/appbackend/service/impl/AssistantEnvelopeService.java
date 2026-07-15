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

import java.net.InetAddress;
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
    private static final int MAX_CAPABILITY_LENGTH = 2_048;
    private static final int MAX_CAPABILITY_COUNT = 64;
    private static final int MAX_INTERNAL_REFERENCE_SCAN_LENGTH = 32 * 1_024;
    private static final String SAFE_UNAVAILABLE_ANSWER = "内容暂不可用。";
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9:_-]{1,160}");
    private static final Pattern SHA256 = Pattern.compile("(?:sha256:)?[0-9a-f]{64}");
    private static final Pattern EVIDENCE_DIGEST = Pattern.compile("sha256:[0-9a-f]{64}");
    private static final Pattern RESERVED_IPV4_REFERENCE = Pattern.compile(
            "(?i)(?<![0-9])(?:127\\.|169\\.254(?:\\.|(?![0-9]))|0\\.0\\.0\\.0(?![0-9])|"
                    + "10\\.|192\\.168\\.|172\\.(?:1[6-9]|2[0-9]|3[01])\\.)");
    private static final Pattern RESERVED_IPV6_REFERENCE = Pattern.compile(
            "(?i)(?:\\[::1\\]|(?<![0-9a-f])::1(?![0-9a-f])|"
                    + "\\[?fe[89ab][0-9a-f]:[0-9a-f:]+\\]?|"
                    + "\\[?f[cd][0-9a-f]{2}:[0-9a-f:]+\\]?)");
    private static final Pattern BRACKETED_IP_LITERAL = Pattern.compile("\\[([0-9A-Fa-f:.]+)]");
    private static final Pattern STORAGE_KEY = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.[a-z0-9]{1,16}");
    private static final Set<String> BUSINESS_KINDS = Set.of(
            "course", "activity", "meeting", "dining", "facility", "secondhand");
    private static final Set<String> FILE_DELIVERIES = Set.of(
            "image", "video", "audio", "document", "presentation", "spreadsheet", "bundle");
    private static final Set<String> CONTENT_KINDS = Set.of(
            "explanation", "mind_map", "diagram", "exercise", "code_example", "extended_reading");
    private static final Set<String> BUSINESS_NUMBER_FIELDS = Set.of(
            "weekday", "startSection", "endSection", "rating", "price", "longitude", "latitude");
    private static final Set<String> AVAILABILITY_STATES = Set.of(
            "active", "expired", "unavailable", "legacy_unavailable");
    private static final Set<String> GROUNDING_STATUSES = Set.of("grounded", "context_only", "model_only");
    private static final Set<String> ACTION_TYPES = Set.of("open_resource", "download", "preview", "follow_up");
    private static final Set<String> EVIDENCE_STATES = Set.of(
            "available", "legacy_missing", "malformed", "integrity_failed", "generation_failed");
    private static final Set<String> INTERNAL_CAPABILITY_KEYS = Set.of(
            "internalcapability", "pythoncapability", "exportcapability", "capability");
    private static final Set<String> SAFE_METADATA_KEYS = Set.of(
            "source", "sourceId", "sourceType", "sourceVersion", "retrievedAt", "title", "type", "kind",
            "time", "location", "route", "status", "legacy", "serverGenerated",
            "courseKey", "knowledgePoint", "learningPathId", "learningPathItemKey",
            "resourceKind", "resourceType", "reviewStatus");
    private static final Set<String> SAFE_STEP_DETAIL_KEYS = Set.of(
            "agentName", "targetAgent", "toolName", "toolDisplayName", "routeReason", "intent",
            "resultCount", "documentCount", "strategy", "summarizedByModel");
    private static final Set<String> DEFAULT_SSE_FIELDS = Set.of(
            "message", "status", "stage", "progress");
    private static final Set<String> LEARNING_SSE_EVENTS = Set.of(
            "accepted", "profile", "retrieval", "planning", "agent_start", "agent_done",
            "agent_failed", "review_start", "review_result", "exporting", "pathing",
            "persisting", "retrying", "dependency_unavailable", "error");
    private static final Set<String> LEARNING_SSE_FIELDS = Set.of(
            "workflowId", "stage", "progress", "agentName", "resourceType",
            "message", "retryable", "status");
    private static final Map<String, Set<String>> SSE_EVENT_FIELDS = Map.of(
            "status", Set.of("message", "status", "stage", "progress", "agentName"),
            "tool_start", Set.of("message", "status", "stage", "agentName", "toolName", "toolDisplayName"),
            "session", Set.of("message", "status", "sessionId"),
            "search", Set.of("message", "status", "query", "keyword", "resultCount", "documentCount"),
            "delta", Set.of("content", "delta", "answer", "index", "status"));
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

    public record PreparedEnvelope(List<Map<String, Object>> internalAttachments,
                                   Set<String> internalCapabilities) {
        @Override
        public String toString() {
            return "PreparedEnvelope[internalAttachments=<redacted>]";
        }
    }

    public record CapabilityScan(Set<String> values, boolean malformed) {
    }

    public PreparedEnvelope prepareLiveResponse(LlmChatResponse response,
                                                Map<String, Object> rawResult,
                                                String expectedQuery) {
        return prepareLiveResponse(response, rawResult, expectedQuery, Set.of());
    }

    public PreparedEnvelope prepareLiveResponse(LlmChatResponse response,
                                                Map<String, Object> rawResult,
                                                String expectedQuery,
                                                Set<String> knownCapabilities) {
        CapabilityScan capabilityScan = scanInternalCapabilities(rawResult);
        CapabilityScan mergedCapabilities = mergeInternalCapabilities(
                knownCapabilities, capabilityScan.values());
        if (capabilityScan.malformed() || mergedCapabilities.malformed()) {
            failClosedResponse(response, expectedQuery);
            return new PreparedEnvelope(List.of(), Set.of());
        }
        Set<String> capabilities = mergedCapabilities.values();
        sanitizeResponseScalars(response, capabilities);
        response.setOutputMeta(withoutCapabilityValues(sanitizeGenericMap(response.getOutputMeta(), 0), capabilities));
        response.setRetrievalMeta(withoutCapabilityValues(sanitizeGenericMap(response.getRetrievalMeta(), 0), capabilities));
        response.setTrace(withoutCapabilityMaps(sanitizeTrace(response.getTrace()), capabilities));
        Object matched = rawResult.containsKey("documents") ? rawResult.get("documents") : rawResult.get("matchedResults");
        response.setMatchedResults(withoutCapabilityMaps(sanitizeMatchedResults(matched), capabilities));
        List<Map<String, Object>> internalAttachments = mapList(rawResult.get("attachments"));
        response.setAttachments(sanitizeAttachments(internalAttachments, capabilities));
        Object rawResources = rawResult.get("resources");
        List<AssistantResourceDTO> resources = sanitizeResources(rawResources, false, capabilities);
        boolean resourcesTruncated = mapList(rawResources).size() > MAX_RESOURCES;
        AssistantEvidenceChainDTO chain = validateEvidence(
                rawResult.get("evidenceChain"), resources, "generation_failed",
                response.getAnswer(), expectedQuery, resourcesTruncated, capabilities);
        if (containsCapability(objectMapper.convertValue(
                chain, new TypeReference<Map<String, Object>>() { }), capabilities)) {
            chain = stateChain("generation_failed", chain.isTruncated());
        }
        if (!"available".equals(chain.getEvidenceState())) {
            downgradeUntrustedGrounding(resources);
            chain = sealFailureChain(chain, resources, response, expectedQuery);
        }
        fitEnvelope(resources, chain);
        response.setResources(resources);
        response.setEvidenceChain(chain);
        return new PreparedEnvelope(copyMapList(internalAttachments), capabilities);
    }

    public CapabilityScan scanInternalCapabilities(Object rawValue) {
        Set<String> capabilities = new LinkedHashSet<>();
        boolean malformed = collectInternalCapabilities(rawValue, 0, capabilities);
        return new CapabilityScan(Set.copyOf(capabilities), malformed);
    }

    public Set<String> internalCapabilities(Map<String, Object> rawResult) {
        CapabilityScan scan = scanInternalCapabilities(rawResult);
        return scan.malformed() ? Set.of() : scan.values();
    }

    public CapabilityScan mergeInternalCapabilities(Set<String> existing, Set<String> discovered) {
        Set<String> merged = new LinkedHashSet<>();
        if (existing != null) {
            merged.addAll(existing);
        }
        if (discovered != null) {
            merged.addAll(discovered);
        }
        boolean malformed = merged.size() > MAX_CAPABILITY_COUNT
                || merged.stream().anyMatch(value -> !StringUtils.hasText(value)
                || value.length() > MAX_CAPABILITY_LENGTH);
        return new CapabilityScan(malformed ? Set.of() : Set.copyOf(merged), malformed);
    }

    private boolean collectInternalCapabilities(Object value,
                                                int depth,
                                                Set<String> capabilities) {
        if (value == null) {
            return false;
        }
        if (depth > 32) {
            return true;
        }
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String normalizedKey = String.valueOf(entry.getKey())
                        .replaceAll("[^A-Za-z0-9]", "")
                        .toLowerCase(Locale.ROOT);
                Object item = entry.getValue();
                if (INTERNAL_CAPABILITY_KEYS.contains(normalizedKey)) {
                    if (!(item instanceof String capability)
                            || !StringUtils.hasText(capability)
                            || capability.length() > MAX_CAPABILITY_LENGTH) {
                        return true;
                    }
                    capabilities.add(capability);
                    if (capabilities.size() > MAX_CAPABILITY_COUNT) {
                        return true;
                    }
                }
                if (collectInternalCapabilities(item, depth + 1, capabilities)) {
                    return true;
                }
            }
            return false;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (collectInternalCapabilities(item, depth + 1, capabilities)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void failClosedResponse(LlmChatResponse response, String expectedQuery) {
        response.setSessionToken(null);
        response.setModel("");
        response.setRagStrategy("");
        response.setAgentName("leader_agent");
        response.setSearchKeyword("");
        response.setMatchedResults(List.of());
        response.setRetrievalMeta(Map.of());
        response.setTrace(List.of());
        response.setAnswer(SAFE_UNAVAILABLE_ANSWER);
        response.setAnswerType("text");
        response.setOutputType("text");
        response.setOutputTypes(List.of("text"));
        response.setOutputMeta(Map.of());
        response.setAttachments(List.of());
        response.setResources(List.of());
        AssistantEvidenceChainDTO chain = sealFailureChain(
                stateChain("generation_failed", false), List.of(), response, expectedQuery);
        response.setEvidenceChain(chain);
    }

    public void sanitizeSseEventPayload(String eventName,
                                        Object eventPayload,
                                        Set<String> internalCapabilities) {
        if (!(eventPayload instanceof Map<?, ?> source)) {
            return;
        }
        Set<String> allowed = SSE_EVENT_FIELDS.getOrDefault(eventName, DEFAULT_SSE_FIELDS);
        Map<String, Object> safe = new LinkedHashMap<>();
        for (String key : allowed) {
            Object value = source.get(key);
            if (value instanceof Number number && finite(number)) {
                safe.put(key, value);
            } else if (value instanceof Boolean) {
                safe.put(key, value);
            } else if (value instanceof String text
                    && text.length() <= 1_000
                    && !unsafePublicText(text, internalCapabilities)) {
                safe.put(key, text);
            }
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> target = (Map<String, Object>) source;
        target.clear();
        target.putAll(safe);
    }

    /**
     * Learning events use a closed contract and may carry one already-normalized
     * assistant resource. The ordinary campus SSE allowlists above remain unchanged.
     */
    public void sanitizeLearningSseEventPayload(String eventName,
                                                Object eventPayload,
                                                Set<String> internalCapabilities) {
        if (!(eventPayload instanceof Map<?, ?> source) || !LEARNING_SSE_EVENTS.contains(eventName)) {
            return;
        }
        Map<String, Object> safe = new LinkedHashMap<>();
        for (String key : LEARNING_SSE_FIELDS) {
            Object value = source.get(key);
            if (value instanceof Number number && finite(number)) {
                safe.put(key, value);
            } else if (value instanceof Boolean) {
                safe.put(key, value);
            } else if (value instanceof String text
                    && text.length() <= 1_000
                    && !unsafePublicText(text, internalCapabilities)) {
                safe.put(key, text);
            }
        }
        if ("agent_done".equals(eventName) && source.get("resource") instanceof Map<?, ?> resource) {
            List<AssistantResourceDTO> sanitized = sanitizeResources(
                    List.of(resource), false, internalCapabilities);
            if (!sanitized.isEmpty()) {
                safe.put("resource", objectMapper.convertValue(
                        sanitized.getFirst(), new TypeReference<Map<String, Object>>() { }));
            }
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> target = (Map<String, Object>) source;
        target.clear();
        target.putAll(safe);
    }

    @Transactional
    public AiLeaderMessage persistAssistantMessage(Long userId,
                                                   AiLeaderSession session,
                                                   LlmChatResponse response,
                                                   List<Map<String, Object>> internalAttachments,
                                                   Set<String> knownCapabilities,
                                                   AiLeaderMessage existing) {
        CapabilityScan capabilityScan = scanInternalCapabilities(
                internalAttachments == null ? List.of() : internalAttachments);
        CapabilityScan mergedCapabilities = mergeInternalCapabilities(
                knownCapabilities, capabilityScan.values());
        if (capabilityScan.malformed() || mergedCapabilities.malformed()) {
            throw new IllegalStateException("assistant capability manifest validation failed");
        }
        assertPublicResponseSafe(response, mergedCapabilities.values());
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
        assertPublicResponseSafe(response, mergedCapabilities.values());
        fillMessage(message, response);
        return messageRepository.save(message);
    }

    private void assertPublicResponseSafe(LlmChatResponse response, Set<String> capabilities) {
        Map<String, Object> publicResponse = objectMapper.convertValue(
                response, new TypeReference<Map<String, Object>>() { });
        if (containsCapability(publicResponse, capabilities) || containsInternalUrl(publicResponse)) {
            throw new IllegalStateException("assistant public response safety validation failed");
        }
    }

    public void restoreEnvelope(AiLeaderMessage message,
                                AiLeaderMessageItem item,
                                String expectedQuery) {
        sanitizeHistoryItem(item);
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

    private void sanitizeHistoryItem(AiLeaderMessageItem item) {
        item.setContent(safePublicText(item.getContent(), SAFE_UNAVAILABLE_ANSWER, Set.of()));
        item.setAnswerType(safeEvidenceText(item.getAnswerType(), "text", Set.of()));
        item.setOutputType(safeEvidenceText(item.getOutputType(), "text", Set.of()));
        item.setAgentName(safeEvidenceText(item.getAgentName(), "leader_agent", Set.of()));
        item.setSearchKeyword(safePublicText(item.getSearchKeyword(), "", Set.of()));
        item.setOutputTypes(item.getOutputTypes() == null ? List.of() : item.getOutputTypes().stream()
                .map(value -> safePublicText(value, "", Set.of()))
                .filter(StringUtils::hasText)
                .toList());
        item.setOutputMeta(withoutCapabilityValues(item.getOutputMeta(), Set.of()));
        item.setRetrievalMeta(withoutCapabilityValues(item.getRetrievalMeta(), Set.of()));
        item.setTrace(withoutCapabilityMaps(item.getTrace(), Set.of()));
    }

    public void overwriteSsePayload(Object eventPayload, LlmChatResponse response) {
        if (response == null || !(eventPayload instanceof Map<?, ?> source)) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> target = (Map<String, Object>) source;
        Map<String, Object> replacement = objectMapper.convertValue(
                response, new TypeReference<Map<String, Object>>() { });
        target.clear();
        target.putAll(replacement);
    }

    private void bindGeneratedExports(Long userId,
                                      AiLeaderSession session,
                                      AiLeaderMessage message,
                                      LlmChatResponse response,
                                      List<Map<String, Object>> internalAttachments) {
        Set<String> capabilities = internalCapabilities(Map.of(
                "attachments", internalAttachments == null ? List.of() : internalAttachments));
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

        List<Map<String, Object>> attachments = sanitizeAttachments(internalAttachments, capabilities);
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
                || containsCapability(fileName, Set.of(capability))
                || containsCapability(mimeType, Set.of(capability))
                || isInternalReference(fileName)
                || isInternalReference(mimeType)
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
        List<Map<String, Object>> sanitized = withoutCapabilityMaps(sanitizeMatchedResults(raw), Set.of());
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
        if (node == null) {
            return stateChain("malformed", false);
        }
        if (!node.isObject()) {
            logMalformedField(message.getId(), "evidenceChain", "rootType");
            return stateChain("malformed", false);
        }
        EvidenceValidation validation = validateEvidenceResult(node, resources, "malformed",
                message.getContent(), expectedQuery, false, Set.of());
        if (validation.failure() == EvidenceFailure.TYPED_VALIDATION) {
            logMalformedField(message.getId(), "evidenceChain", "typedValidation");
        }
        return validation.chain();
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
        return sanitizeResources(value, persisted, Set.of());
    }

    private List<AssistantResourceDTO> sanitizeResources(Object value,
                                                          boolean persisted,
                                                          Set<String> capabilities) {
        List<AssistantResourceDTO> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (Map<String, Object> raw : mapList(value)) {
            if (result.size() >= MAX_RESOURCES) {
                break;
            }
            if (containsCapability(raw, capabilities) || unsafeResourceInternalText(raw, persisted)) {
                continue;
            }
            Optional<AssistantResourceDTO> parsed = sanitizeResource(raw, persisted);
            if (parsed.isPresent() && ids.add(parsed.get().getId())) {
                result.add(parsed.get());
            }
        }
        return result;
    }

    private boolean unsafeResourceInternalText(Map<String, Object> raw, boolean persisted) {
        if (!containsInternalUrl(raw)) {
            return false;
        }
        if (!persisted) {
            return true;
        }
        Map<String, Object> withoutLegacyUrls = new LinkedHashMap<>(raw);
        withoutLegacyUrls.remove("url");
        withoutLegacyUrls.remove("previewUrl");
        return containsInternalUrl(withoutLegacyUrls);
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
        String title = text(raw.get("title"), AssistantResourceContract.MAX_TITLE_LENGTH);
        if (!AssistantResourceContract.isValidCore(schemaVersion, id, kind, deliveryType, title)
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
        resource.setTitle(title);
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
        if (isInternalReference(raw.get("url")) || isInternalReference(raw.get("previewUrl"))) {
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

    private enum EvidenceFailure {
        NONE,
        TYPED_VALIDATION,
        INTEGRITY
    }

    private record EvidenceValidation(AssistantEvidenceChainDTO chain, EvidenceFailure failure) {
    }

    private AssistantEvidenceChainDTO validateEvidence(Object value,
                                                        List<AssistantResourceDTO> resources,
                                                        String invalidState,
                                                        String expectedAnswer,
                                                        String expectedQuery,
                                                        boolean resourcesTruncated,
                                                        Set<String> capabilities) {
        return validateEvidenceResult(value, resources, invalidState, expectedAnswer, expectedQuery,
                resourcesTruncated, capabilities).chain();
    }

    private EvidenceValidation validateEvidenceResult(Object value,
                                                       List<AssistantResourceDTO> resources,
                                                       String invalidState,
                                                       String expectedAnswer,
                                                       String expectedQuery,
                                                       boolean resourcesTruncated,
                                                       Set<String> capabilities) {
        JsonNode node = value instanceof JsonNode jsonNode ? jsonNode : objectMapper.valueToTree(value);
        if (node == null || !node.isObject()) {
            return typedEvidenceFailure(invalidState, false);
        }
        try {
            if (objectMapper.writeValueAsBytes(node).length > MAX_RAW_ENVELOPE_BYTES) {
                return typedEvidenceFailure(invalidState, true);
            }
            if (!validEvidenceNodeTypes(node)) {
                return typedEvidenceFailure(invalidState, false);
            }
            JsonNode integrityNode = node.path("integrity");
            String expectedDigest = integrityNode.path("digest").asText("");
            if (!"assistant-evidence-v1".equals(node.path("schemaVersion").asText())
                    || !"SHA-256".equals(integrityNode.path("algorithm").asText())
                    || !"canonical-json-without-integrity".equals(integrityNode.path("scope").asText())
                    || integrityNode.path("signed").asBoolean(true)
                    || !EVIDENCE_DIGEST.matcher(expectedDigest).matches()) {
                return typedEvidenceFailure(invalidState, false);
            }
            AssistantEvidenceChainDTO chain = objectMapper.treeToValue(node, AssistantEvidenceChainDTO.class);
            if (!validDigest(chain.getQueryDigest())
                    || !validDigest(chain.getAnswerDigest())
                    || !utcTimestamp(chain.getGeneratedAt())) {
                return typedEvidenceFailure(invalidState, false);
            }
            if (!GROUNDING_STATUSES.contains(chain.getStatus())
                    || !EVIDENCE_STATES.contains(chain.getEvidenceState())
                    || !IDENTIFIER.matcher(defaultText(chain.getChainId(), "")).matches()
                    || !IDENTIFIER.matcher(defaultText(chain.getRequestId(), "")).matches()
                    || chain.getGeneration() == null
                    || !validGeneration(chain, capabilities)
                    || chain.getSources() == null
                    || chain.getSteps() == null
                    || chain.getResourceLinks() == null
                    || !validSources(chain.getSources(), capabilities)
                    || !validSteps(chain.getSteps(), capabilities)
                    || !validResourceLinks(
                    resources, chain.getResourceLinks(), chain.getSources(), resourcesTruncated)) {
                return typedEvidenceFailure(invalidState, false);
            }
            if (!expectedDigest.equals(canonicalEvidenceDigest(node))) {
                return integrityEvidenceFailure(false);
            }
            if (!chain.getAnswerDigest().equals(sha256Text(expectedAnswer))) {
                return integrityEvidenceFailure(false);
            }
            if (expectedQuery != null && !chain.getQueryDigest().equals(sha256Text(expectedQuery))) {
                return integrityEvidenceFailure(false);
            }
            if (!"available".equals(chain.getEvidenceState())) {
                AssistantEvidenceChainDTO result = "model_only".equals(chain.getStatus())
                        && chain.getSources().isEmpty()
                        && chain.getSteps().isEmpty()
                        ? chain : stateChain(invalidState, false);
                return new EvidenceValidation(result,
                        result == chain ? EvidenceFailure.NONE : EvidenceFailure.TYPED_VALIDATION);
            }
            boolean hasSources = !chain.getSources().isEmpty();
            if ("grounded".equals(chain.getStatus()) != hasSources) {
                return typedEvidenceFailure(invalidState, false);
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
            return new EvidenceValidation(chain, EvidenceFailure.NONE);
        } catch (Exception error) {
            return typedEvidenceFailure(invalidState, false);
        }
    }

    private EvidenceValidation typedEvidenceFailure(String state, boolean truncated) {
        return new EvidenceValidation(stateChain(state, truncated), EvidenceFailure.TYPED_VALIDATION);
    }

    private EvidenceValidation integrityEvidenceFailure(boolean truncated) {
        return new EvidenceValidation(stateChain("integrity_failed", truncated), EvidenceFailure.INTEGRITY);
    }

    private boolean validSources(List<AssistantEvidenceSource> sources, Set<String> capabilities) {
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
                    || unsafePublicText(source.getEvidenceId(), capabilities)
                    || unsafePublicText(source.getSourceType(), capabilities)
                    || unsafePublicText(source.getSourceId(), capabilities)
                    || unsafePublicText(source.getTitle(), capabilities)
                    || unsafePublicText(source.getExcerpt(), capabilities)
                    || unsafePublicText(source.getSourceVersion(), capabilities)
                    || unsafePublicText(source.getAccessScope(), capabilities)
                    || containsCapability(source.getMetadata(), capabilities)
                    || metadataHasUnsafePublicText(source.getMetadata(), capabilities)
                    || !safeMetadata(source.getMetadata()).equals(source.getMetadata() == null ? Map.of() : source.getMetadata())) {
                return false;
            }
        }
        return true;
    }

    private boolean metadataHasUnsafePublicText(Map<String, Object> metadata, Set<String> capabilities) {
        if (metadata == null) {
            return false;
        }
        return metadata.entrySet().stream().anyMatch(entry ->
                forbidden(entry.getKey())
                        || entry.getValue() instanceof String text && unsafePublicText(text, capabilities));
    }

    private boolean validGeneration(AssistantEvidenceChainDTO chain, Set<String> capabilities) {
        return StringUtils.hasText(chain.getGeneration().getAgent())
                && StringUtils.hasText(chain.getGeneration().getAnswerType())
                && boundedText(chain.getGeneration().getAgent(), 64)
                && boundedText(chain.getGeneration().getModel(), 128)
                && boundedText(chain.getGeneration().getAnswerType(), 64)
                && !unsafePublicText(chain.getGeneration().getAgent(), capabilities)
                && !unsafePublicText(chain.getGeneration().getModel(), capabilities)
                && !unsafePublicText(chain.getGeneration().getAnswerType(), capabilities);
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

    private boolean validSteps(List<AssistantEvidenceStep> steps, Set<String> capabilities) {
        if (steps.size() > 100) {
            return false;
        }
        for (AssistantEvidenceStep step : steps) {
            if (step == null
                    || !IDENTIFIER.matcher(defaultText(step.getStage(), "")).matches()
                    || unsafePublicText(step.getStage(), capabilities)) {
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
                        && (text.length() > 300 || unsafePublicText(text, capabilities))) {
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
        return sanitizeAttachments(value, Set.of());
    }

    private List<Map<String, Object>> sanitizeAttachments(Object value, Set<String> capabilities) {
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
            if (isInternalReference(raw.get("url")) || isInternalReference(raw.get("previewUrl"))) {
                safe.remove("url");
                safe.remove("previewUrl");
                safe.put("status", "legacy_unavailable");
            }
            if (!safe.isEmpty()
                    && !containsCapability(safe, capabilities)
                    && !containsInternalUrl(safe)) {
                result.add(safe);
            }
        }
        return result;
    }

    private boolean invalidAttachmentUrl(Object value, Object storageKeyValue) {
        String raw = text(value, 1_000);
        if (!StringUtils.hasText(raw) || isInternalReference(raw)) {
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

    private void sanitizeResponseScalars(LlmChatResponse response, Set<String> capabilities) {
        if (response == null) {
            return;
        }
        response.setSessionId(safePublicText(response.getSessionId(), null, capabilities));
        response.setSessionToken(safePublicText(response.getSessionToken(), null, capabilities));
        response.setModel(safeEvidenceText(response.getModel(), "", capabilities));
        response.setRagStrategy(safeEvidenceText(response.getRagStrategy(), "", capabilities));
        response.setAgentName(safeEvidenceText(response.getAgentName(), "leader_agent", capabilities));
        response.setSearchKeyword(safePublicText(response.getSearchKeyword(), "", capabilities));
        response.setAnswer(safePublicText(response.getAnswer(), SAFE_UNAVAILABLE_ANSWER, capabilities));
        response.setAnswerType(safeEvidenceText(response.getAnswerType(), "text", capabilities));
        response.setOutputType(safeEvidenceText(response.getOutputType(), "text", capabilities));
        if (response.getOutputTypes() != null) {
            response.setOutputTypes(response.getOutputTypes().stream()
                    .map(value -> safePublicText(value, "", capabilities))
                    .filter(StringUtils::hasText)
                    .toList());
        }
    }

    private String safePublicText(String value, String fallback, Set<String> capabilities) {
        String sanitized = redactCapabilities(value, capabilities);
        return isInternalReference(sanitized) ? fallback : defaultText(sanitized, fallback);
    }

    private String safeEvidenceText(String value, String fallback, Set<String> capabilities) {
        String sanitized = redactCapabilities(value, capabilities);
        return unsafePublicText(sanitized, Set.of()) ? fallback : defaultText(sanitized, fallback);
    }

    private String redactCapabilities(String value, Set<String> capabilities) {
        if (value == null || capabilities.isEmpty()) {
            return value;
        }
        String sanitized = value;
        for (String capability : capabilities) {
            sanitized = sanitized.replace(capability, "");
        }
        return sanitized;
    }

    private List<Map<String, Object>> withoutCapabilityMaps(List<Map<String, Object>> values,
                                                             Set<String> capabilities) {
        if (values == null || values.isEmpty()) {
            return values == null ? List.of() : values;
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> value : values) {
            Map<String, Object> sanitized = withoutCapabilityValues(value, capabilities);
            if (!sanitized.isEmpty()) {
                result.add(sanitized);
            }
        }
        return result;
    }

    private Map<String, Object> withoutCapabilityValues(Map<String, Object> values,
                                                         Set<String> capabilities) {
        if (values == null || values.isEmpty()) {
            return values == null ? Map.of() : values;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (containsCapability(entry.getKey(), capabilities) || isInternalReference(entry.getKey())) {
                continue;
            }
            Object sanitized = withoutCapabilityValue(entry.getValue(), capabilities);
            if (sanitized != null) {
                result.put(entry.getKey(), sanitized);
            }
        }
        return result;
    }

    private Object withoutCapabilityValue(Object value, Set<String> capabilities) {
        if (value instanceof String text) {
            return containsCapability(text, capabilities) || isInternalReference(text) ? null : text;
        }
        if (value instanceof Map<?, ?>) {
            return withoutCapabilityValues(mapValue(value), capabilities);
        }
        if (value instanceof List<?> list) {
            List<Object> safe = new ArrayList<>();
            for (Object item : list) {
                Object sanitized = withoutCapabilityValue(item, capabilities);
                if (sanitized != null) {
                    safe.add(sanitized);
                }
            }
            return safe;
        }
        return value;
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

    private boolean isInternalReference(Object value) {
        if (value == null
                || value instanceof Map<?, ?>
                || value instanceof Iterable<?>
                || value.getClass().isArray()) {
            return false;
        }
        String reference = String.valueOf(value).trim();
        if (!StringUtils.hasText(reference)) {
            return false;
        }
        if (reference.length() > MAX_INTERNAL_REFERENCE_SCAN_LENGTH) {
            return true;
        }
        String lower = reference.trim().toLowerCase(Locale.ROOT);
        if (lower.equals("/internal")
                || lower.startsWith("/internal/")
                || lower.contains("/internal/")
                || lower.contains("/internal?")
                || lower.contains("/internal#")
                || lower.contains("/generated/")
                || lower.contains("localhost")
                || lower.contains(".internal")
                || lower.contains(".local")
                || RESERVED_IPV4_REFERENCE.matcher(lower).find()
                || RESERVED_IPV6_REFERENCE.matcher(lower).find()) {
            return true;
        }
        var bracketedIpLiterals = BRACKETED_IP_LITERAL.matcher(lower);
        while (bracketedIpLiterals.find()) {
            if (isInternalHost(bracketedIpLiterals.group(1))) {
                return true;
            }
        }
        try {
            return isInternalHost(URI.create(reference.trim()).getHost());
        } catch (Exception ignored) {
            return false;
        }
    }

    private String safeUrl(Object value) {
        String url = text(value, 1_000);
        if (!StringUtils.hasText(url) || isInternalReference(url)) {
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
                    || isInternalHost(host)
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
        if (persisted && (isInternalReference(rawUrl) || isInternalReference(rawPreviewUrl))) {
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

    private boolean isInternalHost(String rawHost) {
        if (!StringUtils.hasText(rawHost)) {
            return false;
        }
        String host = rawHost.trim().toLowerCase(Locale.ROOT);
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        if (host.equals("localhost")
                || host.endsWith(".localhost")
                || host.endsWith(".local")
                || host.endsWith(".internal")
                || host.equals("0.0.0.0")
                || host.equals("127")
                || host.startsWith("127.")
                || host.equals("10")
                || host.startsWith("10.")
                || host.equals("169.254")
                || host.startsWith("169.254.")
                || host.equals("192.168")
                || host.startsWith("192.168.")
                || private172(host)
                || host.equals("::1")) {
            return true;
        }
        if (isInternalIpLiteral(host)) {
            return true;
        }
        int separator = host.indexOf(':');
        if (separator <= 0) {
            return false;
        }
        try {
            int firstHextet = Integer.parseInt(host.substring(0, separator), 16);
            return firstHextet >= 0xfe80 && firstHextet <= 0xfebf
                    || firstHextet >= 0xfc00 && firstHextet <= 0xfdff;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private boolean isInternalIpLiteral(String host) {
        if (!host.contains(":") || !host.matches("[0-9a-f:.]+")) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(host);
            byte[] bytes = address.getAddress();
            return address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
        } catch (Exception ignored) {
            return false;
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

    private boolean unsafePublicText(String value, Set<String> capabilities) {
        return StringUtils.hasText(value)
                && (isInternalReference(value)
                || forbiddenText(value)
                || containsCapability(value, capabilities));
    }

    private boolean containsCapability(Object value, Set<String> capabilities) {
        if (value == null || capabilities == null || capabilities.isEmpty()) {
            return false;
        }
        if (value instanceof String text) {
            return capabilities.stream().anyMatch(text::contains);
        }
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream().anyMatch(entry ->
                    containsCapability(String.valueOf(entry.getKey()), capabilities)
                            || containsCapability(entry.getValue(), capabilities));
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (containsCapability(item, capabilities)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean containsInternalUrl(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String text) {
            return isInternalReference(text);
        }
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream().anyMatch(entry ->
                    containsInternalUrl(String.valueOf(entry.getKey()))
                            || containsInternalUrl(entry.getValue()));
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (containsInternalUrl(item)) {
                    return true;
                }
            }
        }
        return false;
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
