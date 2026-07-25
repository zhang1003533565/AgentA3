package com.example.appbackend.service;

import com.example.appbackend.dto.LearningPathDTO;
import lombok.Data;

import java.util.Map;
import java.util.Optional;

public interface LearningWorkflowStateStore {

    void save(WorkflowState state);

    Optional<WorkflowState> find(String workflowId);

    /**
     * Reads the latest distributed snapshot without falling back to a process-local cache.
     * Retry owners must call this after acquiring the workflow claim.
     */
    Optional<WorkflowState> findAuthoritatively(String workflowId);

    /**
     * Acquires one workflow-wide retry claim. The resource type is diagnostic context only;
     * different resources in the same workflow must contend for the same claim.
     */
    Optional<String> claimRetry(String workflowId, String resourceType);

    boolean isRetryClaimOwner(String workflowId, String resourceType, String claimToken);

    /**
     * Renews the workflow-wide retry lease only when the supplied token still owns it.
     */
    boolean renewRetryClaim(String workflowId, String resourceType, String claimToken);

    /**
     * Atomically verifies the retry token, persists state, and renews the lease.
     */
    boolean saveRetryState(WorkflowState state, String resourceType, String claimToken);

    /**
     * Atomically verifies the retry token, persists terminal state, and then releases the claim.
     */
    boolean completeRetryState(WorkflowState state, String resourceType, String claimToken);

    void releaseRetryClaim(String workflowId, String resourceType, String claimToken);

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
