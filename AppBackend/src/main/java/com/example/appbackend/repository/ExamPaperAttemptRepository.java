package com.example.appbackend.repository;

import com.example.appbackend.entity.ExamPaperAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExamPaperAttemptRepository extends JpaRepository<ExamPaperAttempt, Long> {
    Optional<ExamPaperAttempt> findByIdAndUserId(Long id, Long userId);
    Optional<ExamPaperAttempt> findByPaperIdAndUserIdAndStatus(
            Long paperId, Long userId, ExamPaperAttempt.Status status);
    List<ExamPaperAttempt> findByPaperIdAndUserIdOrderByAttemptNoDesc(Long paperId, Long userId);
    long countByPaperIdAndUserId(Long paperId, Long userId);
}
