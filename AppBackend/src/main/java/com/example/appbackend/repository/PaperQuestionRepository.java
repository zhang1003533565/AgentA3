package com.example.appbackend.repository;
import com.example.appbackend.entity.PaperQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface PaperQuestionRepository extends JpaRepository<PaperQuestion, Long> {
    List<PaperQuestion> findByPaperIdOrderByQuestionOrderAsc(Long paperId);
    Optional<PaperQuestion> findByPaperIdAndQuestionId(Long paperId, Long questionId);
    void deleteByPaperIdAndQuestionId(Long paperId, Long questionId);
}
