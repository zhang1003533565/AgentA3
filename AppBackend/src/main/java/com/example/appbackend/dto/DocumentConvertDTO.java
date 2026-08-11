package com.example.appbackend.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

public final class DocumentConvertDTO {
    private DocumentConvertDTO() {
    }

    @Data
    public static class TaskAccepted {
        private String taskId;
        private String status;
        private Integer progress;
        private String message;
    }

    @Data
    public static class TaskView {
        private String taskId;
        private String convertType;
        private String status;
        private Integer progress;
        private String message;
        private String errorMessage;
        private String sourceFileName;
        private Long sourceFileSize;
        private String resultFileName;
        private Long resultFileSize;
        private LocalDateTime createTime;
        private LocalDateTime startTime;
        private LocalDateTime completeTime;
    }

    @Data
    public static class TaskSummary {
        private String taskId;
        private String convertType;
        private String status;
        private Integer progress;
        private String sourceFileName;
        private String resultFileName;
        private LocalDateTime createTime;
        private LocalDateTime completeTime;
    }

    @Data
    public static class BatchDeleteRequest {
        @NotEmpty(message = "请选择要删除的记录")
        private List<String> taskIds;
    }
}
