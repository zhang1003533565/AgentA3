package com.example.appbackend.repository;

import com.example.appbackend.entity.ForumReportAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ForumReportAuditLogRepository extends JpaRepository<ForumReportAuditLog, Long> {

    List<ForumReportAuditLog> findByReportIdOrderByCreateTimeDesc(Long reportId);
}
