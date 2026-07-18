package com.example.appbackend.controller;

import com.example.appbackend.dto.LearningPathDTO;
import com.example.appbackend.exception.GlobalExceptionHandler;
import com.example.appbackend.service.LearningWorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AppLearningControllerTest {

    private RecordingLearningWorkflowService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = new RecordingLearningWorkflowService();
        mvc = MockMvcBuilders.standaloneSetup(new AppLearningController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void everyLearningFacadeEndpointRequiresAuthenticatedUser() throws Exception {
        mvc.perform(get("/api/app/learning/courses/python/home"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/app/learning/courses/python/path"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/app/learning/courses/python/recommendations"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/app/learning/workflows/wf-1"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/app/learning/courses/python/path/replan"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/app/learning/path-items/9/start"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/app/learning/path-items/9/complete"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/app/learning/workflows/wf-1/resources/code_lab/retry"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/app/learning/recommendations/9/interactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"view\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/app/learning/courses/python/profile-answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"python_goal\",\"answer\":\"期末复习\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/app/learning/resources/generate/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(generateRequest()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedFacadeMapsAllExactRoutesToTheAuthenticatedUser() throws Exception {
        mvc.perform(get("/api/app/learning/courses/python/home").requestAttr("userId", 42L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courseKey").value("python"));
        mvc.perform(get("/api/app/learning/courses/python/path").requestAttr("userId", 42L))
                .andExpect(status().isOk());
        mvc.perform(get("/api/app/learning/courses/python/recommendations").requestAttr("userId", 42L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
        mvc.perform(get("/api/app/learning/workflows/wf-1").requestAttr("userId", 42L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workflowId").value("wf-1"));
        mvc.perform(post("/api/app/learning/courses/python/path/replan").requestAttr("userId", 42L))
                .andExpect(status().isOk());
        mvc.perform(post("/api/app/learning/path-items/9/start").requestAttr("userId", 42L))
                .andExpect(status().isOk());
        mvc.perform(post("/api/app/learning/path-items/9/complete").requestAttr("userId", 42L))
                .andExpect(status().isOk());
        mvc.perform(post("/api/app/learning/workflows/wf-1/resources/code_lab/retry")
                        .requestAttr("userId", 42L))
                .andExpect(status().isOk());
        mvc.perform(post("/api/app/learning/recommendations/9/interactions")
                        .requestAttr("userId", 42L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"view\"}"))
                .andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertEquals(42L, service.lastUserId);
        org.junit.jupiter.api.Assertions.assertEquals(9L, service.lastItemId);
    }

    @Test
    void profileAnswersAcceptOnlyTheFiveServerOwnedQuestionIdsAndIgnoreClientScores() throws Exception {
        mvc.perform(post("/api/app/learning/courses/python/profile-answers")
                        .requestAttr("userId", 42L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"questionId":"python_weak_topic","answer":"列表切片","score":100,"userId":999}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answeredQuestionIds[0]").value("python_weak_topic"));

        org.junit.jupiter.api.Assertions.assertEquals(42L, service.lastUserId);
        org.junit.jupiter.api.Assertions.assertEquals("python_weak_topic", service.lastQuestionId);

        for (String unsupported : List.of("goal", "python_score", "campus_behavior")) {
            mvc.perform(post("/api/app/learning/courses/python/profile-answers")
                            .requestAttr("userId", 42L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"questionId\":\"" + unsupported + "\",\"answer\":\"x\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void resourceGenerationUsesSseAndNeverUsesClientOwnedIdentity() throws Exception {
        mvc.perform(post("/api/app/learning/resources/generate/stream")
                        .requestAttr("userId", 42L)
                        .header("Authorization", "Bearer student-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("""
                                {
                                  "courseKey":"python",
                                  "topic":"列表切片",
                                  "intent":"resource_package",
                                  "requestedResourceTypes":["knowledge_note","code_lab"],
                                  "userId":999,
                                  "score":100,
                                  "llmModel":"client-controlled-model"
                                }
                                """))
                .andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertEquals(42L, service.lastUserId);
        org.junit.jupiter.api.Assertions.assertEquals("Bearer student-token", service.lastAuthorization);
        org.junit.jupiter.api.Assertions.assertEquals("python", service.lastGenerateRequest.getCourseKey());
        org.junit.jupiter.api.Assertions.assertEquals("列表切片", service.lastGenerateRequest.getTopic());
    }

    private String generateRequest() {
        return """
                {
                  "courseKey":"python",
                  "topic":"列表切片",
                  "requestedResourceTypes":["knowledge_note"]
                }
                """;
    }

    private static final class RecordingLearningWorkflowService implements LearningWorkflowService {
        private Long lastUserId;
        private Long lastItemId;
        private String lastAuthorization;
        private String lastQuestionId;
        private LearningPathDTO.GenerateRequest lastGenerateRequest;

        @Override
        public SseEmitter start(Long userId, LearningPathDTO.GenerateRequest request, String authorization) {
            lastUserId = userId;
            lastGenerateRequest = request;
            lastAuthorization = authorization;
            return new SseEmitter();
        }

        @Override
        public LearningPathDTO.WorkflowView getWorkflow(Long userId, String workflowId) {
            lastUserId = userId;
            LearningPathDTO.WorkflowView view = new LearningPathDTO.WorkflowView();
            view.setWorkflowId(workflowId);
            return view;
        }

        @Override
        public LearningPathDTO.HomeView getPythonHome(Long userId, String authorization) {
            lastUserId = userId;
            LearningPathDTO.HomeView view = new LearningPathDTO.HomeView();
            view.setCourseKey("python");
            return view;
        }

        @Override
        public LearningPathDTO.ProfileAnswerResult answerProfile(
                Long userId, LearningPathDTO.ProfileAnswerRequest request, String authorization) {
            lastUserId = userId;
            lastQuestionId = request.getQuestionId();
            if (!LearningPathDTO.PYTHON_PROFILE_QUESTION_IDS.contains(request.getQuestionId())) {
                throw new com.example.appbackend.exception.BusinessException(400, "画像问题不存在");
            }
            LearningPathDTO.ProfileAnswerResult result = new LearningPathDTO.ProfileAnswerResult();
            result.setAnsweredQuestionIds(List.of(request.getQuestionId()));
            return result;
        }

        @Override
        public LearningPathDTO.PathView getPythonPath(Long userId) {
            lastUserId = userId;
            return new LearningPathDTO.PathView();
        }

        @Override
        public List<LearningPathDTO.Recommendation> getPythonRecommendations(Long userId) {
            lastUserId = userId;
            return List.of(new LearningPathDTO.Recommendation());
        }

        @Override
        public LearningPathDTO.PathItemView recordRecommendationInteraction(
                Long userId, Long itemId, LearningPathDTO.InteractionRequest request) {
            lastUserId = userId;
            lastItemId = itemId;
            return new LearningPathDTO.PathItemView();
        }

        @Override
        public LearningPathDTO.PathItemView startPathItem(Long userId, Long itemId) {
            lastUserId = userId;
            lastItemId = itemId;
            return new LearningPathDTO.PathItemView();
        }

        @Override
        public LearningPathDTO.PathItemView completePathItem(Long userId, Long itemId) {
            lastUserId = userId;
            lastItemId = itemId;
            return new LearningPathDTO.PathItemView();
        }

        @Override
        public LearningPathDTO.PathView replanPythonPath(Long userId, String authorization) {
            lastUserId = userId;
            lastAuthorization = authorization;
            return new LearningPathDTO.PathView();
        }

        @Override
        public LearningPathDTO.WorkflowView retryResource(
                Long userId, String workflowId, String resourceType, String authorization) {
            lastUserId = userId;
            lastAuthorization = authorization;
            LearningPathDTO.WorkflowView view = new LearningPathDTO.WorkflowView();
            view.setWorkflowId(workflowId);
            return view;
        }
    }
}
