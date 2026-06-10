package com.example.appbackend.repository;

import com.example.appbackend.entity.Dataset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DatasetRepository extends JpaRepository<Dataset, Long> {

    List<Dataset> findAllByCreatedByOrderByCreateTimeDesc(Long createdBy);

    Page<Dataset> findAllByCreatedByOrderByCreateTimeDesc(Long createdBy, Pageable pageable);

    @Query("""
            SELECT d FROM Dataset d
            WHERE d.createdBy = :userId
              AND (
                :keyword IS NULL
                OR :keyword = ''
                OR LOWER(d.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(d.description) LIKE LOWER(CONCAT('%', :keyword, '%'))
              )
            ORDER BY d.createTime DESC
            """)
    Page<Dataset> searchByUserId(@Param("userId") Long userId,
                                 @Param("keyword") String keyword,
                                 Pageable pageable);

    @Query("""
            SELECT d FROM Dataset d
            WHERE (:keyword IS NULL
                OR :keyword = ''
                OR LOWER(d.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(d.description) LIKE LOWER(CONCAT('%', :keyword, '%'))
              )
            ORDER BY d.createTime DESC
            """)
    Page<Dataset> searchAll(@Param("keyword") String keyword, Pageable pageable);
}
