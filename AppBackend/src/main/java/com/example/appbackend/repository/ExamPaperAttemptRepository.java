package com.example.appbackend.repository;

import com.example.appbackend.entity.ExamPaperAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ExamPaperAttemptRepository extends JpaRepository<ExamPaperAttempt, Long> {
    Optional<ExamPaperAttempt> findByIdAndUserId(Long id, Long userId);
    Optional<ExamPaperAttempt> findByPaperIdAndUserIdAndActiveMarker(
            Long paperId, Long userId, Integer activeMarker);

    @Query("""
            select attempt from ExamPaperAttempt attempt
            where attempt.paperId = :paperId
              and attempt.userId = :userId
              and attempt.status in (
                  com.example.appbackend.entity.ExamPaperAttempt.Status.SUBMITTED,
                  com.example.appbackend.entity.ExamPaperAttempt.Status.AUTO_SUBMITTED
              )
            order by attempt.submittedAt desc
            """)
    List<ExamPaperAttempt> findHistoryByPaperIdAndUserId(
            @Param("paperId") Long paperId, @Param("userId") Long userId);

    @Query("""
            select count(attempt) from ExamPaperAttempt attempt
            where attempt.paperId = :paperId
              and attempt.userId = :userId
              and attempt.status in (
                  com.example.appbackend.entity.ExamPaperAttempt.Status.SUBMITTED,
                  com.example.appbackend.entity.ExamPaperAttempt.Status.AUTO_SUBMITTED
              )
            """)
    long countCompletedByPaperIdAndUserId(
            @Param("paperId") Long paperId, @Param("userId") Long userId);
}
