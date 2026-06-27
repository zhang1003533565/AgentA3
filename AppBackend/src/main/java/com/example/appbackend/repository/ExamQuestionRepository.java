package com.example.appbackend.repository;

import com.example.appbackend.entity.ExamQuestion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExamQuestionRepository extends JpaRepository<ExamQuestion, Long> {

    @Query("""
            SELECT q FROM ExamQuestion q
            WHERE q.status = 1
              AND (:type IS NULL OR q.type = :type)
              AND (:difficulty IS NULL OR q.difficulty = :difficulty)
              AND (:keyword IS NULL OR q.stem LIKE CONCAT('%', :keyword, '%'))
            """)
    Page<ExamQuestion> search(
            @Param("type") String type,
            @Param("difficulty") String difficulty,
            @Param("keyword") String keyword,
            Pageable pageable);
}
