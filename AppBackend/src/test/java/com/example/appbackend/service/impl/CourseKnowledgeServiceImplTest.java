package com.example.appbackend.service.impl;

import com.example.appbackend.dto.KnowledgeChatDTO;
import com.example.appbackend.dto.LearningKnowledgeDTO;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.KnowledgeChatService;
import com.example.appbackend.service.SystemConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CourseKnowledgeServiceImplTest {

    private RecordingKnowledgeChatService knowledgeChatService;
    private StubSystemConfigService systemConfigService;
    private CourseKnowledgeServiceImpl service;

    @BeforeEach
    void setUp() {
        knowledgeChatService = new RecordingKnowledgeChatService();
        systemConfigService = new StubSystemConfigService();
        service = new CourseKnowledgeServiceImpl(knowledgeChatService, systemConfigService);
    }

    @Test
    void retrievePythonCourseHidesInternalBindingAndRawPayload() {
        KnowledgeChatDTO.Reference internalReference = new KnowledgeChatDTO.Reference();
        internalReference.setId("paragraph-1");
        internalReference.setTitle("列表切片");
        internalReference.setDocumentName("Python 程序设计");
        internalReference.setKnowledgeName("internal-python-knowledge");
        internalReference.setKnowledgeType("private");
        internalReference.setContent("切片内容".repeat(400));
        internalReference.setSimilarity(0.93D);
        internalReference.setSource("第三章");
        internalReference.setRaw(Map.of("apiKey", "must-not-leak", "retrievalRaw", "must-not-leak"));
        KnowledgeChatDTO.RetrievalResult retrieval = new KnowledgeChatDTO.RetrievalResult();
        retrieval.setReferences(List.of(internalReference));
        knowledgeChatService.result = retrieval;

        LearningKnowledgeDTO.RetrieveRequest request = new LearningKnowledgeDTO.RetrieveRequest();
        request.setCourseKey("python");
        request.setQuery("列表切片如何工作");
        request.setTopNumber(6);

        LearningKnowledgeDTO.RetrieveResponse response = service.retrieve(request);

        assertEquals("python", response.getCourseKey());
        assertFalse(response.getReferences().isEmpty());
        assertNull(response.getAccountId());
        assertNull(response.getKnowledgeId());
        assertNull(response.getRaw());
        LearningKnowledgeDTO.Reference reference = response.getReferences().getFirst();
        assertEquals("paragraph-1", reference.getId());
        assertEquals("列表切片", reference.getTitle());
        assertEquals("Python 程序设计", reference.getDocumentName());
        assertEquals(1_200, reference.getContent().length());
        assertEquals(0.93D, reference.getSimilarity());
        assertEquals("第三章", reference.getSource());

        assertEquals(1, knowledgeChatService.retrieveCalls);
        assertEquals(17L, knowledgeChatService.lastRequest.getAccountId());
        assertEquals("kb-python", knowledgeChatService.lastRequest.getKnowledgeId());
        assertEquals("列表切片如何工作", knowledgeChatService.lastRequest.getQuery());
        assertEquals(6, knowledgeChatService.lastRequest.getTopNumber());
    }

    @Test
    void retrieveRejectsAnyCourseOtherThanExactPythonKey() {
        LearningKnowledgeDTO.RetrieveRequest request = new LearningKnowledgeDTO.RetrieveRequest();
        request.setCourseKey("java");
        request.setQuery("集合如何工作");

        BusinessException error = assertThrows(BusinessException.class, () -> service.retrieve(request));

        assertEquals(400, error.getCode());
        assertEquals(0, knowledgeChatService.retrieveCalls);
    }

    @Test
    void retrieveFailsClosedWhenPythonCourseIsDisabled() {
        systemConfigService.enabled = false;
        LearningKnowledgeDTO.RetrieveRequest request = new LearningKnowledgeDTO.RetrieveRequest();
        request.setCourseKey("python");
        request.setQuery("列表切片如何工作");

        BusinessException error = assertThrows(BusinessException.class, () -> service.retrieve(request));

        assertEquals(503, error.getCode());
        assertEquals(0, knowledgeChatService.retrieveCalls);
    }

    @Test
    void retrieveFailsClosedWhenServerBindingIsMissing() {
        systemConfigService.accountId = null;
        LearningKnowledgeDTO.RetrieveRequest request = new LearningKnowledgeDTO.RetrieveRequest();
        request.setCourseKey("python");
        request.setQuery("列表切片如何工作");

        BusinessException error = assertThrows(BusinessException.class, () -> service.retrieve(request));

        assertEquals(503, error.getCode());
        assertEquals(0, knowledgeChatService.retrieveCalls);
    }

    private static class RecordingKnowledgeChatService implements KnowledgeChatService {
        private KnowledgeChatDTO.RetrievalResult result;
        private KnowledgeChatDTO.RetrievalRequest lastRequest;
        private int retrieveCalls;

        @Override
        public KnowledgeChatDTO.RetrievalResult retrieve(KnowledgeChatDTO.RetrievalRequest request) {
            lastRequest = request;
            retrieveCalls++;
            return result;
        }

        @Override
        public KnowledgeChatDTO.ChatResponse chat(KnowledgeChatDTO.ChatRequest request, String authorization) {
            return null;
        }

        @Override
        public KnowledgeChatDTO.CacheStats getCacheStats() {
            return null;
        }

        @Override
        public void clearCache() {
        }
    }

    private static class StubSystemConfigService implements SystemConfigService {
        private boolean enabled = true;
        private Long accountId = 17L;
        private String knowledgeId = "kb-python";

        @Override
        public String getValue(String key, String defaultValue) {
            return "ai.learning.python.maxkb.knowledge-id".equals(key) ? knowledgeId : defaultValue;
        }

        @Override
        public Long getLongValue(String key, Long defaultValue) {
            return "ai.learning.python.maxkb.account-id".equals(key) ? accountId : defaultValue;
        }

        @Override
        public Boolean getBooleanValue(String key, Boolean defaultValue) {
            return "ai.learning.python.enabled".equals(key) ? enabled : defaultValue;
        }
    }
}
