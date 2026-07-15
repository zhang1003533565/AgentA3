package com.example.appbackend.service.impl;

import com.example.appbackend.dto.KnowledgeChatDTO;
import com.example.appbackend.dto.LlmChatRequest;
import com.example.appbackend.dto.LlmChatResponse;
import com.example.appbackend.service.KnowledgeChatService;
import com.example.appbackend.service.KnowledgeRetrievalCacheService;
import com.example.appbackend.service.LlmService;
import com.example.appbackend.service.MaxKbKnowledgeService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class KnowledgeChatServiceImpl implements KnowledgeChatService {
    private static final String DEFAULT_AGENT_NAME = "leader_agent";
    private static final String DEFAULT_SEARCH_MODE = "blend";
    private static final int DEFAULT_TOP_NUMBER = 5;
    private static final double DEFAULT_SIMILARITY = 0.6D;
    private static final int MAX_AGENT_INPUT_CHARS = 3900;
    private static final int MAX_CONTEXT_CHARS = 2800;
    private static final int MAX_SINGLE_REFERENCE_CHARS = 900;
    private static final int MAX_REFERENCE_SCAN_DEPTH = 8;
    private static final List<String> REFERENCE_CONTAINER_KEYS = List.of(
            "data",
            "result",
            "results",
            "records",
            "list",
            "items",
            "rows",
            "documents",
            "document_list",
            "documentList",
            "paragraphs",
            "paragraph_list",
            "paragraphList",
            "references",
            "reference_list",
            "referenceList",
            "source_list",
            "sourceList",
            "knowledge_source",
            "knowledgeSource"
    );

    private final MaxKbKnowledgeService maxKbKnowledgeService;
    private final LlmService llmService;
    private final KnowledgeRetrievalCacheService knowledgeRetrievalCacheService;

    public KnowledgeChatServiceImpl(
            MaxKbKnowledgeService maxKbKnowledgeService,
            LlmService llmService,
            KnowledgeRetrievalCacheService knowledgeRetrievalCacheService
    ) {
        this.maxKbKnowledgeService = maxKbKnowledgeService;
        this.llmService = llmService;
        this.knowledgeRetrievalCacheService = knowledgeRetrievalCacheService;
    }

    @Override
    public KnowledgeChatDTO.RetrievalResult retrieve(KnowledgeChatDTO.RetrievalRequest request) {
        int topNumber = request.getTopNumber() == null ? DEFAULT_TOP_NUMBER : request.getTopNumber();
        double similarity = request.getSimilarity() == null ? DEFAULT_SIMILARITY : request.getSimilarity();
        String searchMode = StringUtils.hasText(request.getSearchMode()) ? request.getSearchMode().trim() : DEFAULT_SEARCH_MODE;

        Map<String, Object> hitRequest = new LinkedHashMap<>();
        hitRequest.put("knowledge_id_list", List.of(request.getKnowledgeId()));
        hitRequest.put("query_text", request.getQuery());
        hitRequest.put("top_number", topNumber);
        hitRequest.put("similarity", similarity);
        hitRequest.put("search_mode", searchMode);

        KnowledgeChatDTO.CacheLookupResult cacheLookup = knowledgeRetrievalCacheService.getOrLoad(
                request.getAccountId(),
                request.getKnowledgeId(),
                request.getQuery(),
                searchMode,
                topNumber,
                similarity,
                () -> {
                    Object retrievalRaw = maxKbKnowledgeService.hitTest(request.getAccountId(), hitRequest);
                    KnowledgeChatDTO.RetrievalPayload payload = new KnowledgeChatDTO.RetrievalPayload();
                    payload.setRetrievalRaw(retrievalRaw);
                    payload.setReferences(extractReferences(retrievalRaw, topNumber));
                    return payload;
                }
        );
        KnowledgeChatDTO.RetrievalPayload retrievalPayload = cacheLookup.getPayload();
        List<KnowledgeChatDTO.Reference> references = retrievalPayload == null || retrievalPayload.getReferences() == null
                ? List.of()
                : retrievalPayload.getReferences();

        KnowledgeChatDTO.RetrievalResult result = new KnowledgeChatDTO.RetrievalResult();
        result.setReferences(references);
        result.setRetrievalCache(buildCacheInfo(cacheLookup, retrievalPayload));
        result.setRetrievalRaw(retrievalPayload == null ? null : retrievalPayload.getRetrievalRaw());
        return result;
    }

    @Override
    public KnowledgeChatDTO.ChatResponse chat(KnowledgeChatDTO.ChatRequest request, String authorization) {
        int topNumber = request.getTopNumber() == null ? DEFAULT_TOP_NUMBER : request.getTopNumber();
        double similarity = request.getSimilarity() == null ? DEFAULT_SIMILARITY : request.getSimilarity();
        String searchMode = StringUtils.hasText(request.getSearchMode()) ? request.getSearchMode().trim() : DEFAULT_SEARCH_MODE;

        KnowledgeChatDTO.RetrievalRequest retrievalRequest = new KnowledgeChatDTO.RetrievalRequest();
        retrievalRequest.setAccountId(request.getAccountId());
        retrievalRequest.setKnowledgeId(request.getKnowledgeId());
        retrievalRequest.setQuery(request.getQuestion());
        retrievalRequest.setTopNumber(topNumber);
        retrievalRequest.setSimilarity(similarity);
        retrievalRequest.setSearchMode(searchMode);
        KnowledgeChatDTO.RetrievalResult retrievalResult = retrieve(retrievalRequest);
        List<KnowledgeChatDTO.Reference> references = retrievalResult.getReferences() == null
                ? List.of()
                : retrievalResult.getReferences();

        LlmChatRequest chatRequest = new LlmChatRequest();
        chatRequest.setSessionId(request.getSessionId());
        chatRequest.setAgentName(StringUtils.hasText(request.getAgentName()) ? request.getAgentName().trim() : DEFAULT_AGENT_NAME);
        chatRequest.setLlmModel(StringUtils.hasText(request.getLlmModel()) ? request.getLlmModel().trim() : null);
        chatRequest.setPrompt("你是智慧校园知识库问答助手。回答时优先依据提供的知识库片段；资料不足时明确说明，不要编造。");
        chatRequest.setInput(buildAgentInput(request.getQuestion(), references));

        LlmChatResponse llmResponse = llmService.chat(chatRequest, authorization);
        KnowledgeChatDTO.ChatResponse response = new KnowledgeChatDTO.ChatResponse();
        response.setSessionId(llmResponse == null ? request.getSessionId() : llmResponse.getSessionId());
        response.setSessionToken(llmResponse == null ? null : llmResponse.getSessionToken());
        response.setAgentName(llmResponse == null ? chatRequest.getAgentName() : llmResponse.getAgentName());
        response.setModel(llmResponse == null ? null : llmResponse.getModel());
        response.setAnswer(llmResponse == null ? "" : llmResponse.getAnswer());
        response.setAnswerType(llmResponse == null ? "text" : llmResponse.getAnswerType());
        response.setReferences(references);
        response.setRetrievalCache(retrievalResult.getRetrievalCache());
        response.setMetadata(buildMetadata(request, chatRequest, topNumber, similarity, searchMode, references, response.getRetrievalCache()));
        response.setLlmResponse(llmResponse);
        response.setRetrievalRaw(retrievalResult.getRetrievalRaw());
        return response;
    }

    @Override
    public KnowledgeChatDTO.CacheStats getCacheStats() {
        return knowledgeRetrievalCacheService.getStats();
    }

    @Override
    public void clearCache() {
        knowledgeRetrievalCacheService.clear();
    }

    private Map<String, Object> buildMetadata(
            KnowledgeChatDTO.ChatRequest request,
            LlmChatRequest chatRequest,
            int topNumber,
            double similarity,
            String searchMode,
            List<KnowledgeChatDTO.Reference> references,
            KnowledgeChatDTO.CacheInfo cacheInfo
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("provider", "java-maxkb-agent-chat");
        metadata.put("accountId", request.getAccountId());
        metadata.put("knowledgeId", request.getKnowledgeId());
        metadata.put("agentName", chatRequest.getAgentName());
        metadata.put("llmModel", chatRequest.getLlmModel());
        metadata.put("topNumber", topNumber);
        metadata.put("similarity", similarity);
        metadata.put("searchMode", searchMode);
        metadata.put("referenceCount", references.size());
        if (cacheInfo != null) {
            metadata.put("retrievalCacheHit", cacheInfo.getCacheHit());
            metadata.put("retrievalCacheKey", cacheInfo.getCacheKey());
            metadata.put("retrievalElapsedMs", cacheInfo.getRetrievalElapsedMs());
            metadata.put("retrievalCacheExpiresAt", cacheInfo.getExpiresAt());
        }
        return metadata;
    }

    private KnowledgeChatDTO.CacheInfo buildCacheInfo(
            KnowledgeChatDTO.CacheLookupResult cacheLookup,
            KnowledgeChatDTO.RetrievalPayload retrievalPayload
    ) {
        KnowledgeChatDTO.CacheInfo info = new KnowledgeChatDTO.CacheInfo();
        info.setCacheHit(cacheLookup == null ? false : cacheLookup.getCacheHit());
        info.setCacheKey(cacheLookup == null ? null : cacheLookup.getCacheKey());
        info.setLookupElapsedMs(cacheLookup == null ? null : cacheLookup.getLookupElapsedMs());
        info.setRetrievalElapsedMs(retrievalPayload == null ? null : retrievalPayload.getRetrievalElapsedMs());
        info.setTtlSeconds(knowledgeRetrievalCacheService.getStats().getTtlSeconds());
        info.setExpiresAt(cacheLookup == null ? null : cacheLookup.getExpiresAt());
        return info;
    }

    private String buildAgentInput(String question, List<KnowledgeChatDTO.Reference> references) {
        StringBuilder builder = new StringBuilder();
        builder.append("用户问题：\n").append(question == null ? "" : question.trim()).append("\n\n");
        builder.append("MaxKB 知识库召回片段：\n");
        if (references.isEmpty()) {
            builder.append("未召回到可用片段。\n");
        } else {
            int usedChars = 0;
            for (int index = 0; index < references.size(); index++) {
                KnowledgeChatDTO.Reference reference = references.get(index);
                String content = truncate(reference.getContent(), MAX_SINGLE_REFERENCE_CHARS);
                if (!StringUtils.hasText(content)) {
                    continue;
                }
                String block = "[片段 " + (index + 1) + "] "
                        + safeText(reference.getDocumentName(), reference.getTitle(), reference.getSource(), "未知来源")
                        + "\n"
                        + content
                        + "\n\n";
                if (usedChars + block.length() > MAX_CONTEXT_CHARS) {
                    break;
                }
                builder.append(block);
                usedChars += block.length();
            }
        }
        builder.append("请基于上述片段回答用户问题。若片段中没有答案，请直接说知识库资料不足，并给出下一步需要补充的资料。");
        return truncate(builder.toString(), MAX_AGENT_INPUT_CHARS);
    }

    private List<KnowledgeChatDTO.Reference> extractReferences(Object source, int limit) {
        List<Map<String, Object>> candidates = new ArrayList<>();
        collectCandidateMaps(source, candidates, 0);

        List<KnowledgeChatDTO.Reference> references = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Map<String, Object> candidate : candidates) {
            KnowledgeChatDTO.Reference reference = toReference(candidate);
            if (!StringUtils.hasText(reference.getContent())) {
                continue;
            }
            String dedupeKey = safeText(reference.getId(), reference.getContent(), "");
            if (!seen.add(dedupeKey)) {
                continue;
            }
            references.add(reference);
            if (references.size() >= limit) {
                break;
            }
        }
        return references;
    }

    private void collectCandidateMaps(Object value, List<Map<String, Object>> output, int depth) {
        if (value == null || depth > MAX_REFERENCE_SCAN_DEPTH) {
            return;
        }
        if (value instanceof List<?> list) {
            for (Object item : list) {
                collectCandidateMaps(item, output, depth + 1);
            }
            return;
        }
        if (!(value instanceof Map<?, ?> map)) {
            return;
        }

        Map<String, Object> copy = toStringKeyMap(map);
        if (looksLikeReference(copy)) {
            output.add(copy);
        }

        Set<Object> visitedChildren = Collections.newSetFromMap(new IdentityHashMap<>());
        for (String key : REFERENCE_CONTAINER_KEYS) {
            Object child = copy.get(key);
            if (child != null && child != value && visitedChildren.add(child)) {
                collectCandidateMaps(child, output, depth + 1);
            }
        }
        for (Object child : copy.values()) {
            if ((child instanceof Map<?, ?> || child instanceof List<?>)
                    && child != value
                    && visitedChildren.add(child)) {
                collectCandidateMaps(child, output, depth + 1);
            }
        }
    }

    private KnowledgeChatDTO.Reference toReference(Map<String, Object> raw) {
        Map<String, Object> paragraph = nestedMap(raw, "paragraph");
        Map<String, Object> document = nestedMap(raw, "document");
        Map<String, Object> knowledge = nestedMap(raw, "knowledge");

        KnowledgeChatDTO.Reference reference = new KnowledgeChatDTO.Reference();
        reference.setId(safeText(
                firstString(raw, "id", "paragraph_id", "paragraphId"),
                firstString(paragraph, "id", "paragraph_id", "paragraphId")
        ));
        reference.setTitle(safeText(
                firstString(raw, "title", "name", "paragraph_title", "paragraphTitle"),
                firstString(paragraph, "title", "name")
        ));
        reference.setDocumentName(safeText(
                firstString(raw, "document_name", "documentName", "document", "source"),
                firstString(document, "name", "title", "document_name")
        ));
        reference.setKnowledgeName(safeText(
                firstString(raw, "knowledge_name", "knowledgeName", "knowledge", "dataset_name", "datasetName"),
                firstString(knowledge, "name", "title", "knowledge_name")
        ));
        reference.setKnowledgeType(safeText(
                firstString(raw, "knowledge_type", "knowledgeType", "type"),
                firstString(knowledge, "type", "knowledge_type")
        ));
        reference.setContent(safeText(
                firstString(raw, "content", "text", "paragraph_content", "paragraphContent"),
                firstString(paragraph, "content", "text"),
                raw.get("paragraph") instanceof CharSequence ? String.valueOf(raw.get("paragraph")) : null
        ));
        reference.setSimilarity(firstNumber(raw, "comprehensive_score", "similarity", "score", "relevance_score"));
        reference.setSource(safeText(reference.getDocumentName(), reference.getTitle(), reference.getId()));
        reference.setRaw(raw);
        return reference;
    }

    private boolean looksLikeReference(Map<String, Object> raw) {
        return StringUtils.hasText(safeText(
                firstString(raw, "content", "text", "paragraph_content", "paragraphContent"),
                firstString(nestedMap(raw, "paragraph"), "content", "text"),
                raw.get("paragraph") instanceof CharSequence ? String.valueOf(raw.get("paragraph")) : null
        ));
    }

    private Map<String, Object> nestedMap(Map<String, Object> raw, String key) {
        if (raw == null) {
            return Map.of();
        }
        Object value = raw.get(key);
        if (value instanceof Map<?, ?> nested) {
            return toStringKeyMap(nested);
        }
        return Map.of();
    }

    private Map<String, Object> toStringKeyMap(Map<?, ?> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(String.valueOf(key), value));
        return copy;
    }

    private String firstString(Map<String, Object> raw, String... keys) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            Object value = raw.get(key);
            if (value instanceof CharSequence text && StringUtils.hasText(text)) {
                return text.toString().trim();
            }
        }
        return null;
    }

    private Double firstNumber(Map<String, Object> raw, String... keys) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            Object value = raw.get(key);
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            if (value instanceof CharSequence text && StringUtils.hasText(text)) {
                try {
                    return Double.parseDouble(text.toString().trim());
                } catch (NumberFormatException ignored) {
                    // Try next candidate.
                }
            }
        }
        return null;
    }

    private String safeText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 20)) + "\n...[内容已截断]";
    }
}
