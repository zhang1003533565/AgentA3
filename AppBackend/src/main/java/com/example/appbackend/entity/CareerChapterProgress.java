package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "career_course_chapter_progress", uniqueConstraints = {
        @UniqueConstraint(name = "uk_career_chapter_progress",
                columnNames = {"user_id", "career_id", "skill_id", "course_id", "chapter_id"})
}, indexes = {
        @Index(name = "idx_career_chapter_user", columnList = "user_id,career_id,skill_id")
})
public class CareerChapterProgress {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "career_id", nullable = false, length = 64)
    private String careerId;
    @Column(name = "skill_id", nullable = false, length = 64)
    private String skillId;
    @Column(name = "course_id", nullable = false)
    private Long courseId;
    @Column(name = "chapter_id", nullable = false)
    private Long chapterId;
    @Column(name = "video_material_id")
    private Long videoMaterialId;
    @Column(name = "video_position_seconds", nullable = false)
    private Integer videoPositionSeconds = 0;
    @Column(name = "video_duration_seconds", nullable = false)
    private Integer videoDurationSeconds = 0;
    @Column(name = "effective_watched_seconds", nullable = false)
    private Integer effectiveWatchedSeconds = 0;
    @Column(name = "video_completed", nullable = false)
    private Boolean videoCompleted = false;
    @Column(name = "chapter_completed", nullable = false)
    private Boolean chapterCompleted = false;
    @Column(name = "quiz_completed", nullable = false)
    private Boolean quizCompleted = false;
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    @PrePersist
    @PreUpdate
    void touch() {
        updateTime = LocalDateTime.now();
        if (videoPositionSeconds == null) videoPositionSeconds = 0;
        if (videoDurationSeconds == null) videoDurationSeconds = 0;
        if (effectiveWatchedSeconds == null) effectiveWatchedSeconds = 0;
        if (videoCompleted == null) videoCompleted = false;
        if (chapterCompleted == null) chapterCompleted = false;
        if (quizCompleted == null) quizCompleted = false;
    }
}
