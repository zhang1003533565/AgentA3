package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "campus_course_enrollment", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_course", columnNames = {"user_id", "course_id"})
}, indexes = {
        @Index(name = "idx_enrollment_user", columnList = "user_id,enrolled_time")
})
public class CampusCourseEnrollment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "enrolled_time")
    private LocalDateTime enrolledTime;

    @PrePersist
    void onCreate() {
        enrolledTime = LocalDateTime.now();
    }
}
