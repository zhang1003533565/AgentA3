package com.example.appbackend.repository;

import com.example.appbackend.entity.ArchitectureRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ArchitectureRecordRepository extends JpaRepository<ArchitectureRecord, Long> {

    /**
     * 按用户ID分页查询架构图记录，按创建时间倒序。
     */
    Page<ArchitectureRecord> findByUserIdOrderByCreateTimeDesc(Long userId, Pageable pageable);

    /**
     * 按用户ID和记录ID查询单条记录，确保用户只能访问自己的记录。
     */
    Optional<ArchitectureRecord> findByIdAndUserId(Long id, Long userId);
}
