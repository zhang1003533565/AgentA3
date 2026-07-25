package com.example.appbackend.service;

import com.example.appbackend.dto.LearningPathDTO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface LearningWorkflowService {

    SseEmitter start(Long userId, LearningPathDTO.GenerateRequest request, String authorization);

    LearningPathDTO.WorkflowView getWorkflow(Long userId, String workflowId);

    LearningPathDTO.HomeView getPythonHome(Long userId, String authorization);

    LearningPathDTO.ProfileAnswerResult answerProfile(
            Long userId, LearningPathDTO.ProfileAnswerRequest request, String authorization);

    LearningPathDTO.PathView getPythonPath(Long userId);

    List<LearningPathDTO.Recommendation> getPythonRecommendations(Long userId);

    LearningPathDTO.PathItemView recordRecommendationInteraction(
            Long userId, Long itemId, LearningPathDTO.InteractionRequest request);

    LearningPathDTO.PathItemView startPathItem(Long userId, Long itemId);

    LearningPathDTO.PathItemView completePathItem(Long userId, Long itemId);

    LearningPathDTO.PathView replanPythonPath(Long userId, String authorization);

    LearningPathDTO.WorkflowView retryResource(
            Long userId, String workflowId, String resourceType, String authorization);
}
