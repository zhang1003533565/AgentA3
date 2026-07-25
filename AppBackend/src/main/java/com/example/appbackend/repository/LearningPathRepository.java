package com.example.appbackend.repository;

import com.example.appbackend.entity.LearningPath;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LearningPathRepository extends JpaRepository<LearningPath, Long> {

    Optional<LearningPath> findByUserIdAndCourseKeyAndStatus(
            Long userId, String courseKey, String status);

    Optional<LearningPath> findTopByUserIdAndCourseKeyOrderByVersionNoDesc(
            Long userId, String courseKey);

    Optional<LearningPath> findByIdAndUserIdAndCourseKey(
            Long id, Long userId, String courseKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select path from LearningPath path
            where path.id = :pathId
              and path.userId = :userId
              and path.courseKey = :courseKey
            """)
    Optional<LearningPath> findOwnedByIdForUpdate(
            @Param("pathId") Long pathId,
            @Param("userId") Long userId,
            @Param("courseKey") String courseKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select path from LearningPath path
            where path.userId = :userId
              and path.courseKey = :courseKey
              and path.status = :status
            """)
    Optional<LearningPath> findActiveForUpdate(
            @Param("userId") Long userId,
            @Param("courseKey") String courseKey,
            @Param("status") String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select path from LearningPath path
            where path.userId = :userId
              and path.courseKey = :courseKey
            order by path.versionNo desc
            """)
    List<LearningPath> findLatestForUpdate(
            @Param("userId") Long userId,
            @Param("courseKey") String courseKey,
            Pageable pageable);

    long countByUserIdAndCourseKeyAndStatus(Long userId, String courseKey, String status);
}
