package com.example.appbackend.service;

import com.example.appbackend.dto.LearningPathDTO;

import java.util.List;

public interface LearningPathService {

    LearningPathDTO.HomeView getHome(Long userId, String courseKey);

    /**
     * Acquires the same per-user write lock used by assessment and path replacement,
     * then returns the feedback baseline inside the caller's transaction.
     */
    LearningPathDTO.HomeView getHomeForFeedback(Long userId, String courseKey);

    LearningPathDTO.PathView getActivePath(Long userId, String courseKey);

    LearningPathDTO.PathView getPathSnapshot(
            Long userId, Long pathId, Integer version, Long sourceMessageId);

    void validatePathDraft(Long userId, LearningPathDTO.PathDraft draft);

    LearningPathDTO.PathView replaceActivePath(Long userId, LearningPathDTO.PathDraft draft);

    LearningPathDTO.PathView appendResourcesToPath(
            Long userId,
            Long pathId,
            Integer expectedVersion,
            Long expectedSourceMessageId,
            List<String> resourceIds,
            Long sourceMessageId);

    LearningPathDTO.PathItemView recordResourceInteraction(
            Long userId, Long itemId, LearningPathDTO.InteractionRequest request);

    LearningPathDTO.MasteryView applyAssessment(LearningPathDTO.AssessmentObservation observation);
}
