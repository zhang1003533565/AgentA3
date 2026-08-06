package com.example.appbackend.repository;

import com.example.appbackend.entity.SecondhandReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SecondhandReportRepository extends JpaRepository<SecondhandReport, Long> {

    Page<SecondhandReport> findByStatus(Integer status, Pageable pageable);

    Page<SecondhandReport> findAllBy(Pageable pageable);

    List<SecondhandReport> findByItemId(Long itemId);

    long countByStatus(Integer status);

    @Modifying
    @Query("DELETE FROM SecondhandReport sr WHERE sr.itemId = :itemId")
    void deleteByItemId(@Param("itemId") Long itemId);
}
