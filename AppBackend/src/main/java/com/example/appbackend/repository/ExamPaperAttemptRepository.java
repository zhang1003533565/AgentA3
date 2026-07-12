package com.example.appbackend.repository;

import com.example.appbackend.entity.ExamPaperAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExamPaperAttemptRepository extends JpaRepository<ExamPaperAttempt, Long> {
    Optional<ExamPaperAttempt> findByIdAndUserId(Long id, Long userId);
    Optional<ExamPaperAttempt> findByPaperIdAndUserIdAndActiveMarker(
            Long paperId, Long userId, Integer activeMarker);

    List<ExamPaperAttempt> findByPaperIdAndUserIdAndStatusInOrderBySubmittedAtDesc(
            Long paperId, Long userId, Collection<ExamPaperAttempt.Status> statuses);

    long countByPaperIdAndUserIdAndStatusIn(
            Long paperId, Long userId, Collection<ExamPaperAttempt.Status> statuses);

    @Query("""
            select coalesce(max(attempt.attemptNo), 0) from ExamPaperAttempt attempt
            where attempt.paperId = :paperId and attempt.userId = :userId
            """)
    int findMaxAttemptNoByPaperIdAndUserId(
            @Param("paperId") Long paperId, @Param("userId") Long userId);
}
