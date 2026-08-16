package com.example.appbackend.repository;

import com.example.appbackend.entity.ExamPaperAttemptAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExamPaperAttemptAnswerRepository extends JpaRepository<ExamPaperAttemptAnswer, Long> {
    Optional<ExamPaperAttemptAnswer> findByAttemptIdAndPaperQuestionId(Long attemptId, Long paperQuestionId);
    List<ExamPaperAttemptAnswer> findByAttemptId(Long attemptId);
}
