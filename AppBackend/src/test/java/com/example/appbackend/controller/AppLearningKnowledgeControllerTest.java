package com.example.appbackend.controller;

import com.example.appbackend.dto.KnowledgeChatDTO;
import com.example.appbackend.dto.LearningKnowledgeDTO;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.exception.GlobalExceptionHandler;
import com.example.appbackend.service.CourseKnowledgeService;
import com.example.appbackend.service.KnowledgeChatService;
import com.example.appbackend.service.SystemConfigService;
import com.example.appbackend.service.impl.CourseKnowledgeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AppLearningKnowledgeControllerTest {

    private StubCourseKnowledgeService courseKnowledgeService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        courseKnowledgeService = new StubCourseKnowledgeService();
        mvc = MockMvcBuilders.standaloneSetup(new AppLearningKnowledgeController(courseKnowledgeService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void retrieveRejectsUnauthenticatedRequests() throws Exception {
        mvc.perform(post("/api/app/learning/knowledge/retrieve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseKey\":\"python\",\"query\":\"列表切片如何工作\"}"))
                .andExpect(status().isUnauthorized());

        org.junit.jupiter.api.Assertions.assertEquals(0, courseKnowledgeService.retrieveCalls);
    }

    @Test
    void retrieveResponseContainsOnlyStudentSafeFields() throws Exception {
        LearningKnowledgeDTO.Reference reference = new LearningKnowledgeDTO.Reference();
        reference.setId("paragraph-1");
        reference.setTitle("列表切片");
        reference.setDocumentName("Python 程序设计");
        reference.setContent("切片通过 start、stop 和 step 选取序列片段。");
        reference.setSimilarity(0.93D);
        reference.setSource("第三章");
        LearningKnowledgeDTO.RetrieveResponse response = new LearningKnowledgeDTO.RetrieveResponse();
        response.setCourseKey("python");
        response.setReferences(List.of(reference));
        response.setAccountId(17L);
        response.setKnowledgeId("kb-python");
        response.setRaw(Map.of("apiKey", "must-not-leak", "retrievalRaw", "must-not-leak"));
        courseKnowledgeService.response = response;

        mvc.perform(post("/api/app/learning/knowledge/retrieve")
                        .requestAttr("userId", 42L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseKey\":\"python\",\"query\":\"列表切片如何工作\",\"topNumber\":6}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.courseKey").value("python"))
                .andExpect(jsonPath("$.data.references[0].id").value("paragraph-1"))
                .andExpect(jsonPath("$.data.accountId").doesNotExist())
                .andExpect(jsonPath("$.data.knowledgeId").doesNotExist())
                .andExpect(jsonPath("$.data.raw").doesNotExist())
                .andExpect(content().string(not(containsString("apiKey"))))
                .andExpect(content().string(not(containsString("retrievalRaw"))));
    }

    @Test
    void retrieveHidesDownstreamFailureDetailsFromHttpResponse() throws Exception {
        String internalFailure =
                "knowledgeId=kb-private apiKey=sk-private raw={retrievalRaw=private-payload}";
        CourseKnowledgeService actualService = new CourseKnowledgeServiceImpl(
                new FailingKnowledgeChatService(new BusinessException(500, internalFailure)),
                new FixedSystemConfigService()
        );
        MockMvc secureMvc = MockMvcBuilders.standaloneSetup(new AppLearningKnowledgeController(actualService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        secureMvc.perform(post("/api/app/learning/knowledge/retrieve")
                        .requestAttr("userId", 42L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseKey\":\"python\",\"query\":\"列表切片如何工作\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(502))
                .andExpect(jsonPath("$.msg").value("课程知识检索暂时不可用"))
                .andExpect(content().string(not(containsString("knowledgeId"))))
                .andExpect(content().string(not(containsString("kb-private"))))
                .andExpect(content().string(not(containsString("apiKey"))))
                .andExpect(content().string(not(containsString("sk-private"))))
                .andExpect(content().string(not(containsString("raw"))))
                .andExpect(content().string(not(containsString("retrievalRaw"))))
                .andExpect(content().string(not(containsString("private-payload"))));
    }

    private static class StubCourseKnowledgeService implements CourseKnowledgeService {
        private LearningKnowledgeDTO.RetrieveResponse response;
        private int retrieveCalls;

        @Override
        public LearningKnowledgeDTO.RetrieveResponse retrieve(LearningKnowledgeDTO.RetrieveRequest request) {
            retrieveCalls++;
            return response;
        }
    }

    private static class FailingKnowledgeChatService implements KnowledgeChatService {
        private final RuntimeException failure;

        private FailingKnowledgeChatService(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public KnowledgeChatDTO.RetrievalResult retrieve(KnowledgeChatDTO.RetrievalRequest request) {
            throw failure;
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

    private static class FixedSystemConfigService implements SystemConfigService {
        @Override
        public String getValue(String key, String defaultValue) {
            return "ai.learning.python.maxkb.knowledge-id".equals(key) ? "kb-python" : defaultValue;
        }

        @Override
        public Long getLongValue(String key, Long defaultValue) {
            return "ai.learning.python.maxkb.account-id".equals(key) ? 17L : defaultValue;
        }

        @Override
        public Boolean getBooleanValue(String key, Boolean defaultValue) {
            return "ai.learning.python.enabled".equals(key) ? true : defaultValue;
        }
    }
}
