package com.example.appbackend.repository;

import com.example.appbackend.entity.ExamQuestion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ExamQuestionRepository extends JpaRepository<ExamQuestion, Long> {
    List<ExamQuestion> findBySourceAgentAndStatusOrderByIdAsc(String sourceAgent, Integer status);

    @Query("""
            SELECT q FROM ExamQuestion q
            WHERE q.status = 1
              AND q.type = :type
              AND (:difficulty IS NULL OR q.difficulty = :difficulty)
              AND (q.visibility = 'PUBLIC' OR (q.visibility = 'PRIVATE' AND q.ownerUserId = :userId))
            """)
    List<ExamQuestion> findVisibleActiveCandidates(@Param("type") String type,
                                                   @Param("difficulty") String difficulty,
                                                   @Param("userId") Long userId);

    @Query("""
            SELECT q FROM ExamQuestion q
            WHERE q.status = 1
              AND (:type IS NULL OR q.type = :type)
              AND (:difficulty IS NULL OR q.difficulty = :difficulty)
              AND (:keyword IS NULL OR q.stem LIKE CONCAT('%', :keyword, '%'))
              AND (q.visibility = 'PUBLIC' OR (q.visibility = 'PRIVATE' AND q.ownerUserId = :userId))
            """)
    Page<ExamQuestion> searchVisible(
            @Param("type") String type,
            @Param("difficulty") String difficulty,
            @Param("keyword") String keyword,
            @Param("userId") Long userId,
            Pageable pageable);

    @Query("""
            SELECT q FROM ExamQuestion q
            WHERE q.id = :id
              AND q.status = 1
              AND (q.visibility = 'PUBLIC' OR (q.visibility = 'PRIVATE' AND q.ownerUserId = :userId))
            """)
    Optional<ExamQuestion> findVisibleById(@Param("id") Long id, @Param("userId") Long userId);

    @Query("""
            SELECT q FROM ExamQuestion q
            WHERE q.id IN :ids
              AND q.status = 1
              AND (q.visibility = 'PUBLIC' OR (q.visibility = 'PRIVATE' AND q.ownerUserId = :userId))
            """)
    List<ExamQuestion> findAllVisibleById(@Param("ids") List<Long> ids, @Param("userId") Long userId);
}
