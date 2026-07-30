package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "campus_course_progress", uniqueConstraints = {
        @UniqueConstraint(name = "uk_course_chapter_user", columnNames = {"course_id", "chapter_id", "user_id"})
}, indexes = {
        @Index(name = "idx_course_progress_user", columnList = "user_id,course_id")
})
public class CampusCourseProgress {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "chapter_id", nullable = false)
    private Long chapterId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Boolean completed = false;

    @Column(name = "completed_time")
    private LocalDateTime completedTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    @PreUpdate
    void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
