package com.example.appbackend.repository;

import com.example.appbackend.entity.FlowchartRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FlowchartRecordRepository extends JpaRepository<FlowchartRecord, String> {
    List<FlowchartRecord> findTop20ByUserIdOrderByCreateTimeDesc(Long userId);

    Optional<FlowchartRecord> findByIdAndUserId(String id, Long userId);
}
