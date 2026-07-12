package com.example.appbackend.repository;

import com.example.appbackend.entity.ExamPaper;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ExamPaperRepository extends JpaRepository<ExamPaper, Long> {

    Optional<ExamPaper> findByIdAndStatus(Long id, Integer status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select paper from ExamPaper paper where paper.id = :id and paper.status = :status")
    Optional<ExamPaper> findByIdAndStatusForUpdate(@Param("id") Long id, @Param("status") Integer status);

    Optional<ExamPaper> findByIdAndStatusAndPublishedTrue(Long id, Integer status);

    Page<ExamPaper> findByPublishedTrueAndStatusOrderByPublishTimeDesc(Integer status, Pageable pageable);

    Page<ExamPaper> findByPublishedTrueAndStatusAndTitleContainingOrderByPublishTimeDesc(
            Integer status, String title, Pageable pageable);

    @Query("""
            select paper from ExamPaper paper
            where paper.status = :status
              and (paper.published = true or exists (
                  select attempt.id from ExamPaperAttempt attempt
                  where attempt.paperId = paper.id and attempt.userId = :userId))
            order by paper.publishTime desc, paper.createTime desc
            """)
    Page<ExamPaper> findAppVisible(@Param("status") Integer status, @Param("userId") Long userId, Pageable pageable);

    @Query("""
            select paper from ExamPaper paper
            where paper.status = :status and paper.title like concat('%', :title, '%')
              and (paper.published = true or exists (
                  select attempt.id from ExamPaperAttempt attempt
                  where attempt.paperId = paper.id and attempt.userId = :userId))
            order by paper.publishTime desc, paper.createTime desc
            """)
    Page<ExamPaper> findAppVisibleByTitle(@Param("status") Integer status, @Param("userId") Long userId,
                                         @Param("title") String title, Pageable pageable);

    Page<ExamPaper> findByCreatedByAndStatusOrderByCreateTimeDesc(Long createdBy, Integer status, Pageable pageable);

    Page<ExamPaper> findByCreatedByAndStatusAndTitleContainingOrderByCreateTimeDesc(
            Long createdBy, Integer status, String title, Pageable pageable);
}
