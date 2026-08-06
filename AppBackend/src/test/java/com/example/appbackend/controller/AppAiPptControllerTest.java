package com.example.appbackend.controller;

import com.example.appbackend.dto.AiPptDTO;
import com.example.appbackend.exception.GlobalExceptionHandler;
import com.example.appbackend.service.AiPptService;
import com.example.appbackend.service.impl.PythonAiProxyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AppAiPptControllerTest {
    private MockMvc mvc;
    private RecordingService service;

    @BeforeEach
    void setUp() {
        service = new RecordingService();
        mvc = MockMvcBuilders.standaloneSetup(new AppAiPptController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void allPptEndpointsRequireAuthenticatedUser() throws Exception {
        mvc.perform(get("/api/app/ai/ppt/options"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/app/ai/ppt/outlines").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceName\":\"a.txt\",\"sourceContent\":\"content\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/app/ai/ppt/slides").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outline\":{}}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/app/ai/ppt/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceName\":\"a.txt\",\"outline\":{},\"slides\":[{},{}]}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/app/ai/ppt/tasks/ppt_task_0123456789abcdef0123456789abcdef"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedRoutesUseServerOwnedUserIdentity() throws Exception {
        mvc.perform(get("/api/app/ai/ppt/options")
                        .requestAttr("userId", 42L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scenes[0].value").value("review"));

        mvc.perform(post("/api/app/ai/ppt/outlines")
                        .requestAttr("userId", 42L)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceName\":\"a.txt\",\"sourceContent\":\"content\",\"userId\":999}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outlineId").value("outline_1"));

        mvc.perform(get("/api/app/ai/ppt/tasks/ppt_task_0123456789abcdef0123456789abcdef")
                        .requestAttr("userId", 42L)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskId").value("ppt_task_0123456789abcdef0123456789abcdef"));

        org.junit.jupiter.api.Assertions.assertEquals(42L, service.userId);
        org.junit.jupiter.api.Assertions.assertEquals("Bearer token", service.authorization);
    }

    private static final class RecordingService implements AiPptService {
        private Long userId;
        private String authorization;

        @Override
        public AiPptDTO.OptionsResponse getOptions(Long userId) {
            this.userId = userId;
            AiPptDTO.SceneOption scene = new AiPptDTO.SceneOption();
            scene.setValue("review");
            scene.setLabel("复习资料");
            AiPptDTO.OptionsResponse response = new AiPptDTO.OptionsResponse();
            response.setScenes(java.util.List.of(scene));
            response.setCacheTtlSeconds(86400);
            return response;
        }

        @Override
        public Object generateOutline(Long userId, AiPptDTO.OutlineRequest request, String authorization) {
            this.userId = userId;
            this.authorization = authorization;
            return Map.of("outlineId", "outline_1");
        }

        @Override
        public Object generateSlides(Long userId, AiPptDTO.SlidesRequest request, String authorization) {
            this.userId = userId;
            return Map.of("slides", java.util.List.of());
        }

        @Override
        public Object createTask(Long userId, AiPptDTO.TaskRequest request, String authorization) {
            this.userId = userId;
            return Map.of("taskId", "ppt_task_0123456789abcdef0123456789abcdef");
        }

        @Override
        public Object getTask(Long userId, String taskId, String authorization) {
            this.userId = userId;
            this.authorization = authorization;
            return Map.of("taskId", taskId);
        }

        @Override
        public SseEmitter streamTask(Long userId, String taskId, String authorization) {
            return new SseEmitter();
        }

        @Override
        public PythonAiProxyService.GeneratedExportResponse downloadFile(
                Long userId, String taskId, String format, String authorization) {
            return new PythonAiProxyService.GeneratedExportResponse(new byte[0], MediaType.APPLICATION_OCTET_STREAM, 0);
        }

        @Override
        public PythonAiProxyService.GeneratedExportResponse downloadPreview(
                Long userId, String taskId, Integer slideIndex, String authorization) {
            return new PythonAiProxyService.GeneratedExportResponse(new byte[0], MediaType.IMAGE_PNG, 0);
        }
    }
}
