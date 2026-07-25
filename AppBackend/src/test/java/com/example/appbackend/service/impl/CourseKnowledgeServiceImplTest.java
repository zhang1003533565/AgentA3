package com.example.appbackend.service.impl;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.appbackend.dto.KnowledgeChatDTO;
import com.example.appbackend.dto.LearningKnowledgeDTO;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.KnowledgeChatService;
import com.example.appbackend.service.SystemConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourseKnowledgeServiceImplTest {
    private static final String INTERNAL_FAILURE =
            "knowledgeId=kb-private apiKey=sk-private raw={retrievalRaw=private-payload}";
    private static final String SAFE_FAILURE_MESSAGE = "课程知识检索暂时不可用";

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
        retrieval.setRetrievalRaw(Map.of("apiKey", "must-not-leak", "retrievalRaw", "must-not-leak"));
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

    @Test
    void retrieveReplacesDownstreamBusinessExceptionWithFixedSafeFailure() {
        knowledgeChatService.failure = new BusinessException(500, INTERNAL_FAILURE);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.retrieve(pythonRequest()));

        assertEquals(502, error.getCode());
        assertEquals(SAFE_FAILURE_MESSAGE, error.getMessage());
        assertFalse(error.getMessage().contains("knowledgeId"));
        assertFalse(error.getMessage().contains("apiKey"));
        assertFalse(error.getMessage().contains("raw"));
    }

    @Test
    void retrieveLogsOnlySafeTypeAndCorrelationForUnexpectedFailure() {
        Logger logger = (Logger) LoggerFactory.getLogger(CourseKnowledgeServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        knowledgeChatService.failure = new IllegalStateException(INTERNAL_FAILURE);

        try {
            BusinessException error = assertThrows(BusinessException.class,
                    () -> service.retrieve(pythonRequest()));

            assertEquals(502, error.getCode());
            assertEquals(SAFE_FAILURE_MESSAGE, error.getMessage());
            String logs = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .reduce("", (left, right) -> left + "\n" + right);
            assertTrue(logs.contains("requestId="));
            assertTrue(logs.contains("exceptionType=java.lang.IllegalStateException"));
            assertFalse(logs.contains("knowledgeId"));
            assertFalse(logs.contains("kb-private"));
            assertFalse(logs.contains("apiKey"));
            assertFalse(logs.contains("sk-private"));
            assertFalse(logs.contains("retrievalRaw"));
            assertFalse(logs.contains("private-payload"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private LearningKnowledgeDTO.RetrieveRequest pythonRequest() {
        LearningKnowledgeDTO.RetrieveRequest request = new LearningKnowledgeDTO.RetrieveRequest();
        request.setCourseKey("python");
        request.setQuery("列表切片如何工作");
        return request;
    }

    private static class RecordingKnowledgeChatService implements KnowledgeChatService {
        private KnowledgeChatDTO.RetrievalResult result;
        private KnowledgeChatDTO.RetrievalRequest lastRequest;
        private RuntimeException failure;
        private int retrieveCalls;

        @Override
        public KnowledgeChatDTO.RetrievalResult retrieve(KnowledgeChatDTO.RetrievalRequest request) {
            lastRequest = request;
            retrieveCalls++;
            if (failure != null) {
                throw failure;
            }
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
