package com.example.appbackend.service.impl;

import com.example.appbackend.dto.KnowledgeChatDTO;
import com.example.appbackend.dto.LlmChatRequest;
import com.example.appbackend.dto.LlmChatResponse;
import com.example.appbackend.dto.MaxKbKnowledgeDTO;
import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.service.LlmService;
import com.example.appbackend.service.MaxKbKnowledgeService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

class KnowledgeChatServiceImplTest {

    @Test
    void chat_shouldRetrieveFromMaxKbThenCallOwnAgent() {
        AtomicReference<Map<String, Object>> hitRequestRef = new AtomicReference<>();
        AtomicReference<LlmChatRequest> llmRequestRef = new AtomicReference<>();
        MaxKbKnowledgeService maxKbKnowledgeService = new StubMaxKbKnowledgeService(hitRequestRef);
        LlmService llmService = new StubLlmService(llmRequestRef);
        KnowledgeChatServiceImpl service = new KnowledgeChatServiceImpl(
                maxKbKnowledgeService,
                llmService,
                new KnowledgeRetrievalCacheServiceImpl(300, 100)
        );

        KnowledgeChatDTO.ChatRequest request = new KnowledgeChatDTO.ChatRequest();
        request.setAccountId(1L);
        request.setKnowledgeId("kb-1");
        request.setQuestion("这份报告的设计目标是什么？");
        request.setAgentName("textbook_knowledge_agent");
        request.setLlmModel("ai.service.text.deepseek-chat");
        request.setTopNumber(3);
        request.setSimilarity(0.7D);
        request.setSearchMode("blend");

        KnowledgeChatDTO.ChatResponse response = service.chat(request, "Bearer token");

        Assertions.assertEquals(List.of("kb-1"), hitRequestRef.get().get("knowledge_id_list"));
        Assertions.assertEquals("这份报告的设计目标是什么？", hitRequestRef.get().get("query_text"));
        Assertions.assertEquals(3, hitRequestRef.get().get("top_number"));
        Assertions.assertEquals(0.7D, hitRequestRef.get().get("similarity"));
        Assertions.assertEquals("blend", hitRequestRef.get().get("search_mode"));

        Assertions.assertEquals("textbook_knowledge_agent", llmRequestRef.get().getAgentName());
        Assertions.assertEquals("ai.service.text.deepseek-chat", llmRequestRef.get().getLlmModel());
        Assertions.assertTrue(llmRequestRef.get().getInput().contains("沉浸式教学系统"));
        Assertions.assertTrue(llmRequestRef.get().getInput().contains("召回片段"));

        Assertions.assertEquals("智能体回答", response.getAnswer());
        Assertions.assertEquals(1, response.getReferences().size());
        Assertions.assertEquals("课程报告.docx", response.getReferences().get(0).getDocumentName());
        Assertions.assertEquals(0.91D, response.getReferences().get(0).getSimilarity());
        Assertions.assertFalse(response.getRetrievalCache().getCacheHit());
        Assertions.assertEquals("java-maxkb-agent-chat", response.getMetadata().get("provider"));
    }

    @Test
    void chat_shouldExtractNestedMaxKbParagraphSources() {
        AtomicReference<Map<String, Object>> hitRequestRef = new AtomicReference<>();
        AtomicReference<LlmChatRequest> llmRequestRef = new AtomicReference<>();

        Map<String, Object> paragraph = new LinkedHashMap<>();
        paragraph.put("id", "paragraph-1");
        paragraph.put("title", "设计过程与技术说明");
        paragraph.put("content", "通过 Trigger 区域识别玩家进入任务起点、安全区、危险区。");

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("paragraph", paragraph);
        row.put("document_name", "课程大作业报告.docx");
        row.put("knowledge_name", "python");
        row.put("comprehensive_score", 1.42D);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("paragraph_list", List.of(row));

        Map<String, Object> wrappedResponse = new LinkedHashMap<>();
        wrappedResponse.put("code", 200);
        wrappedResponse.put("data", data);

        KnowledgeChatServiceImpl service = new KnowledgeChatServiceImpl(
                new StubMaxKbKnowledgeService(hitRequestRef, wrappedResponse),
                new StubLlmService(llmRequestRef),
                new KnowledgeRetrievalCacheServiceImpl(300, 100)
        );

        KnowledgeChatDTO.ChatRequest request = new KnowledgeChatDTO.ChatRequest();
        request.setAccountId(1L);
        request.setKnowledgeId("kb-1");
        request.setQuestion("任务触发怎么做？");

        KnowledgeChatDTO.ChatResponse response = service.chat(request, "Bearer token");

        Assertions.assertEquals(1, response.getReferences().size());
        Assertions.assertEquals("课程大作业报告.docx", response.getReferences().get(0).getDocumentName());
        Assertions.assertEquals("python", response.getReferences().get(0).getKnowledgeName());
        Assertions.assertEquals(1.42D, response.getReferences().get(0).getSimilarity());
        Assertions.assertTrue(llmRequestRef.get().getInput().contains("Trigger 区域"));
    }

    @Test
    void chat_shouldUseRetrievalCacheForSameScopedRequest() {
        AtomicReference<Map<String, Object>> hitRequestRef = new AtomicReference<>();
        AtomicReference<LlmChatRequest> llmRequestRef = new AtomicReference<>();
        StubMaxKbKnowledgeService maxKbKnowledgeService = new StubMaxKbKnowledgeService(hitRequestRef);
        KnowledgeRetrievalCacheServiceImpl cacheService = new KnowledgeRetrievalCacheServiceImpl(300, 100);
        KnowledgeChatServiceImpl service = new KnowledgeChatServiceImpl(
                maxKbKnowledgeService,
                new StubLlmService(llmRequestRef),
                cacheService
        );

        KnowledgeChatDTO.ChatRequest request = new KnowledgeChatDTO.ChatRequest();
        request.setAccountId(1L);
        request.setKnowledgeId("kb-1");
        request.setQuestion("这份报告的设计目标是什么？");
        request.setTopNumber(3);
        request.setSimilarity(0.7D);
        request.setSearchMode("blend");

        KnowledgeChatDTO.ChatResponse first = service.chat(request, "Bearer token");
        KnowledgeChatDTO.ChatResponse second = service.chat(request, "Bearer token");

        Assertions.assertFalse(first.getRetrievalCache().getCacheHit());
        Assertions.assertTrue(second.getRetrievalCache().getCacheHit());
        Assertions.assertEquals(1, maxKbKnowledgeService.hitCallCount);
        Assertions.assertEquals(2L, service.getCacheStats().getRequestCount());
        Assertions.assertEquals(1L, service.getCacheStats().getHitCount());
        Assertions.assertEquals(1L, service.getCacheStats().getMissCount());
    }

    private static class StubLlmService implements LlmService {
        private final AtomicReference<LlmChatRequest> requestRef;

        private StubLlmService(AtomicReference<LlmChatRequest> requestRef) {
            this.requestRef = requestRef;
        }

        @Override
        public LlmChatResponse chat(LlmChatRequest request, String token) {
            requestRef.set(request);
            LlmChatResponse response = new LlmChatResponse();
            response.setSessionId("session-1");
            response.setAgentName(request.getAgentName());
            response.setModel("deepseek-chat");
            response.setAnswer("智能体回答");
            response.setAnswerType("markdown");
            return response;
        }

        @Override
        public SseEmitter streamChat(LlmChatRequest request, String token) {
            return new SseEmitter();
        }
    }

    private static class StubMaxKbKnowledgeService implements MaxKbKnowledgeService {
        private final AtomicReference<Map<String, Object>> requestRef;
        private final Object response;
        private int hitCallCount;

        private StubMaxKbKnowledgeService(AtomicReference<Map<String, Object>> requestRef) {
            this.requestRef = requestRef;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", "p-1");
            row.put("document_name", "课程报告.docx");
            row.put("title", "设计目标");
            row.put("content", "本作品是一个面向春游安全教育的 VR 沉浸式教学系统。");
            row.put("similarity", 0.91D);
            this.response = List.of(row);
        }

        private StubMaxKbKnowledgeService(AtomicReference<Map<String, Object>> requestRef, Object response) {
            this.requestRef = requestRef;
            this.response = response;
        }

        @Override
        public Object hitTest(Long accountId, Map<String, Object> request) {
            requestRef.set(request);
            hitCallCount++;
            return response;
        }

        @Override
        public List<MaxKbKnowledgeDTO.EnvironmentOption> listEnvironmentOptions() {
            return List.of();
        }

        @Override
        public PageResponse<MaxKbKnowledgeDTO.AccountVO> listAccounts(Integer current, Integer size, String keyword, String environment, Integer status) {
            return null;
        }

        @Override
        public MaxKbKnowledgeDTO.AccountVO createAccount(MaxKbKnowledgeDTO.AccountCreateRequest request) {
            return null;
        }

        @Override
        public MaxKbKnowledgeDTO.AccountVO updateAccount(Long accountId, MaxKbKnowledgeDTO.AccountUpdateRequest request) {
            return null;
        }

        @Override
        public void deleteAccount(Long accountId) {
        }

        @Override
        public MaxKbKnowledgeDTO.AccountVO updateAccountStatus(Long accountId, Integer status) {
            return null;
        }

        @Override
        public Object testConnection(Long accountId) {
            return null;
        }

        @Override
        public Object docs(Long accountId) {
            return null;
        }

        @Override
        public Object listKnowledges(Long accountId, Map<String, String> queryParams) {
            return null;
        }

        @Override
        public Object getKnowledge(Long accountId, String knowledgeId) {
            return null;
        }

        @Override
        public Object listDocuments(Long accountId, String knowledgeId, Map<String, String> queryParams) {
            return null;
        }

        @Override
        public Object uploadDocuments(Long accountId, String knowledgeId, List<MultipartFile> files, Integer limit, List<String> patterns, Boolean withFilter, String splitStrategy, String modelId) {
            return null;
        }

        @Override
        public Object listParagraphs(Long accountId, String knowledgeId, String documentId, Map<String, String> queryParams) {
            return null;
        }

        @Override
        public ResponseEntity<byte[]> proxyAsset(Long accountId, String path) {
            return null;
        }
    }
}
