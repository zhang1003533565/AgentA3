package com.example.appbackend.repository;

import com.example.appbackend.entity.ExamPaper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamPaperRepository extends JpaRepository<ExamPaper, Long> {

    Page<ExamPaper> findByCreatedByAndStatusOrderByCreateTimeDesc(Long createdBy, Integer status, Pageable pageable);
}
