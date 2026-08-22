package com.example.appbackend.repository;

import com.example.appbackend.entity.ForumReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ForumReportRepository extends JpaRepository<ForumReport, Long> {

    @Query("SELECT r FROM ForumReport r WHERE " +
           "(:status IS NULL OR r.status = :status) AND " +
           "(:targetType IS NULL OR r.targetType = :targetType) AND " +
           "(:keyword IS NULL OR r.reasonText LIKE %:keyword% OR r.description LIKE %:keyword% " +
           " OR r.reporter.username LIKE %:keyword% OR r.reporter.realName LIKE %:keyword%)")
    Page<ForumReport> findReports(
            @Param("status") Integer status,
            @Param("targetType") Integer targetType,
            @Param("keyword") String keyword,
            Pageable pageable);

    long countByStatus(Integer status);

    long countByTargetType(Integer targetType);

    @Modifying
    @Query("DELETE FROM ForumReport r WHERE r.id IN :ids")
    void deleteByIds(@Param("ids") List<Long> ids);
}
