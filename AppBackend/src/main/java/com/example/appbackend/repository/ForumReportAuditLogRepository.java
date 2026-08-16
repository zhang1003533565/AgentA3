package com.example.appbackend.repository;

import com.example.appbackend.entity.ForumReportAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ForumReportAuditLogRepository extends JpaRepository<ForumReportAuditLog, Long> {

    List<ForumReportAuditLog> findByReportIdOrderByCreateTimeDesc(Long reportId);

    @Modifying
    @Query("DELETE FROM ForumReportAuditLog l WHERE l.reportId IN :reportIds")
    void deleteByReportIds(@Param("reportIds") List<Long> reportIds);
}
