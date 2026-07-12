package com.example.appbackend.repository;

import com.example.appbackend.entity.ExamPaperQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExamPaperQuestionRepository extends JpaRepository<ExamPaperQuestion, Long> {

    List<ExamPaperQuestion> findByPaperIdOrderBySortOrderAsc(Long paperId);

    List<ExamPaperQuestion> findByPaperIdOrderBySortOrderAscIdAsc(Long paperId);

    Optional<ExamPaperQuestion> findByIdAndPaperId(Long id, Long paperId);
}
