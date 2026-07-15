package com.example.appbackend.service;

import com.example.appbackend.dto.LearningPathDTO;
import lombok.Data;

import java.util.Map;
import java.util.Optional;

public interface LearningWorkflowStateStore {

    void save(WorkflowState state);

    Optional<WorkflowState> find(String workflowId);

    @Data
    class WorkflowState {
        private String workflowId;
        private Long ownerUserId;
        private LearningPathDTO.WorkflowView view;
        private Map<String, Object> context;
        private Long sessionDatabaseId;
        private Integer lastProgress;
        private Boolean terminal;
    }
}
