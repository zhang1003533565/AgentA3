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
@Table(name = "document_convert_task", indexes = {
        @Index(name = "idx_convert_task_owner_time", columnList = "user_id,create_time"),
        @Index(name = "idx_convert_task_status", columnList = "status"),
        @Index(name = "idx_convert_task_type", columnList = "convert_type")
})
public class DocumentConvertTask {

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

    @Column(name = "convert_type", nullable = false, length = 32)
    private String convertType;

    @Column(name = "convert_mode", length = 32)
    private String convertMode;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(nullable = false)
    private Integer progress;

    @Column(length = 255)
    private String message;

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    @Column(name = "source_file_name", length = 255)
    private String sourceFileName;

    @Column(name = "source_file_url", length = 500)
    private String sourceFileUrl;

    @Column(name = "source_file_size")
    private Long sourceFileSize;

    @Column(name = "source_storage_key", length = 500)
    private String sourceStorageKey;

    @Column(name = "result_file_name", length = 255)
    private String resultFileName;

    @Column(name = "result_file_url", length = 500)
    private String resultFileUrl;

    @Column(name = "result_file_size")
    private Long resultFileSize;

    @Column(name = "result_storage_key", length = 500)
    private String resultStorageKey;

    @Column(name = "result_extra_json", columnDefinition = "LONGTEXT")
    private String resultExtraJson;

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
        if (convertMode == null) convertMode = "image";
    }
}
