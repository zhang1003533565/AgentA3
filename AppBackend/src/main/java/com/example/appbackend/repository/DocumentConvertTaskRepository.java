package com.example.appbackend.repository;

import com.example.appbackend.entity.DocumentConvertTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DocumentConvertTaskRepository extends JpaRepository<DocumentConvertTask, Long> {

    Optional<DocumentConvertTask> findByTaskIdAndUserId(String taskId, Long userId);

    List<DocumentConvertTask> findByUserIdOrderByCreateTimeDesc(Long userId);

    long countByUserIdAndStatusIn(Long userId, Collection<String> statuses);

    Page<DocumentConvertTask> findByUserIdAndConvertType(Long userId, String convertType, Pageable pageable);
}
