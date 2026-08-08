package com.example.appbackend.repository;

import com.example.appbackend.entity.MindMapRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MindMapRecordRepository extends JpaRepository<MindMapRecord, String> {
    List<MindMapRecord> findTop20ByUserIdOrderByCreateTimeDesc(Long userId);

    Optional<MindMapRecord> findByIdAndUserId(String id, Long userId);
}
