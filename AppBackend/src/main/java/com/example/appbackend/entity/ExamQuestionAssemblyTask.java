package com.example.appbackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "exam_question_assembly_task", indexes = {
        @Index(name = "idx_question_assembly_task_owner_time", columnList = "user_id,create_time"),
        @Index(name = "idx_question_assembly_task_status", columnList = "status")
})
public class ExamQuestionAssemblyTask {

    public static final String STATUS_QUEUED = "QUEUED";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false, unique = true, length = 36)
    private String taskId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(nullable = false)
    private Integer progress;

    @Column(length = 255)
    private String message;

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    @Column(name = "request_json", nullable = false, columnDefinition = "LONGTEXT")
    private String requestJson;

    @Column(name = "result_json", columnDefinition = "LONGTEXT")
    private String resultJson;

    @Column(name = "imported_count")
    private Integer importedCount;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "complete_time")
    private LocalDateTime completeTime;

    @PrePersist
    void onCreate() {
        if (createTime == null) createTime = LocalDateTime.now();
        if (status == null) status = STATUS_QUEUED;
        if (progress == null) progress = 0;
    }
}
