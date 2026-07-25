package com.example.appbackend.repository;

import com.example.appbackend.entity.LearningKnowledgeMastery;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LearningKnowledgeMasteryRepository
        extends JpaRepository<LearningKnowledgeMastery, Long> {

    Optional<LearningKnowledgeMastery> findByUserIdAndCourseKeyAndKnowledgePointKey(
            Long userId, String courseKey, String knowledgePointKey);

    List<LearningKnowledgeMastery> findByUserIdAndCourseKeyOrderByKnowledgePointKeyAsc(
            Long userId, String courseKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select mastery from LearningKnowledgeMastery mastery
            where mastery.userId = :userId
              and mastery.courseKey = :courseKey
            order by mastery.knowledgePointKey asc
            """)
    List<LearningKnowledgeMastery> findByUserIdAndCourseKeyForUpdate(
            @Param("userId") Long userId,
            @Param("courseKey") String courseKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select mastery from LearningKnowledgeMastery mastery
            where mastery.userId = :userId
              and mastery.courseKey = :courseKey
              and mastery.knowledgePointKey = :knowledgePointKey
            """)
    Optional<LearningKnowledgeMastery> findOneForUpdate(
            @Param("userId") Long userId,
            @Param("courseKey") String courseKey,
            @Param("knowledgePointKey") String knowledgePointKey);
}
