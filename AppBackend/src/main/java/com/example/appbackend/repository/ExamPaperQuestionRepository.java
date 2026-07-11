package com.example.appbackend.repository;

import com.example.appbackend.entity.ExamPaperQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExamPaperQuestionRepository extends JpaRepository<ExamPaperQuestion, Long> {

    List<ExamPaperQuestion> findByPaperIdOrderBySortOrderAscIdAsc(Long paperId);
}
