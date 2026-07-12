package com.example.appbackend.repository;

import com.example.appbackend.entity.ExamPaper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ExamPaperRepository extends JpaRepository<ExamPaper, Long> {

    Optional<ExamPaper> findByIdAndStatus(Long id, Integer status);

    Page<ExamPaper> findByCreatedByAndStatusOrderByCreateTimeDesc(Long createdBy, Integer status, Pageable pageable);

    Page<ExamPaper> findByCreatedByAndStatusAndTitleContainingOrderByCreateTimeDesc(
            Long createdBy, Integer status, String title, Pageable pageable);
}
