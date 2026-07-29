package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "campus_course_exam", uniqueConstraints = {
        @UniqueConstraint(name = "uk_course_exam_paper", columnNames = {"course_id", "paper_id"})
})
public class CampusCourseExam {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "paper_id", nullable = false)
    private Long paperId;

    @Column(name = "chapter_scope", length = 300)
    private String chapterScope;

    @Column(name = "deadline")
    private LocalDateTime deadline;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @PrePersist
    void onCreate() {
        createTime = LocalDateTime.now();
    }
}
