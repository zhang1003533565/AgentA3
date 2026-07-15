package com.example.appbackend.repository;

import com.example.appbackend.entity.LearningPath;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LearningPathRepository extends JpaRepository<LearningPath, Long> {

    Optional<LearningPath> findByUserIdAndCourseKeyAndStatus(
            Long userId, String courseKey, String status);

    Optional<LearningPath> findTopByUserIdAndCourseKeyOrderByVersionNoDesc(
            Long userId, String courseKey);

    long countByUserIdAndCourseKeyAndStatus(Long userId, String courseKey, String status);
}
