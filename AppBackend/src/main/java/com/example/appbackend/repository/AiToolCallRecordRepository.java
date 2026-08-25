package com.example.appbackend.repository;

import com.example.appbackend.entity.AiToolCallRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiToolCallRecordRepository extends JpaRepository<AiToolCallRecord, Long> {

    Page<AiToolCallRecord> findAllByOrderByCreateTimeDescIdDesc(Pageable pageable);
}
