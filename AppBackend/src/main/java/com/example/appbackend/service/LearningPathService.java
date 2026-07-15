package com.example.appbackend.service;

import com.example.appbackend.dto.LearningPathDTO;

public interface LearningPathService {

    LearningPathDTO.HomeView getHome(Long userId, String courseKey);

    /**
     * Acquires the same per-user write lock used by assessment and path replacement,
     * then returns the feedback baseline inside the caller's transaction.
     */
    LearningPathDTO.HomeView getHomeForFeedback(Long userId, String courseKey);

    LearningPathDTO.PathView getActivePath(Long userId, String courseKey);

    LearningPathDTO.PathView replaceActivePath(Long userId, LearningPathDTO.PathDraft draft);

    LearningPathDTO.PathItemView recordResourceInteraction(
            Long userId, Long itemId, LearningPathDTO.InteractionRequest request);

    LearningPathDTO.MasteryView applyAssessment(LearningPathDTO.AssessmentObservation observation);
}
