package com.example.appbackend.repository;

import com.example.appbackend.entity.LearningKnowledgeMastery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LearningKnowledgeMasteryRepository
        extends JpaRepository<LearningKnowledgeMastery, Long> {

    Optional<LearningKnowledgeMastery> findByUserIdAndCourseKeyAndKnowledgePointKey(
            Long userId, String courseKey, String knowledgePointKey);

    List<LearningKnowledgeMastery> findByUserIdAndCourseKeyOrderByKnowledgePointKeyAsc(
            Long userId, String courseKey);
}
