package com.example.appbackend.repository;

import com.example.appbackend.entity.DocumentSegment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DocumentSegmentRepository extends JpaRepository<DocumentSegment, Long> {

    List<DocumentSegment> findByDocumentIdOrderByPositionAsc(Long documentId);

    Page<DocumentSegment> findByDocumentIdOrderByPositionAsc(Long documentId, Pageable pageable);

    Page<DocumentSegment> findByDatasetIdOrderByPositionAsc(Long datasetId, Pageable pageable);

    long countByDocumentId(Long documentId);

    long countByDatasetId(Long datasetId);

    @Query("""
            SELECT s FROM DocumentSegment s
            WHERE s.documentId = :documentId
              AND (:keyword IS NULL
                OR :keyword = ''
                OR LOWER(s.content) LIKE LOWER(CONCAT('%', :keyword, '%'))
              )
            ORDER BY s.position ASC
            """)
    Page<DocumentSegment> searchByDocumentId(@Param("documentId") Long documentId,
                                              @Param("keyword") String keyword,
                                              Pageable pageable);

    @Query("SELECT COALESCE(SUM(s.wordCount), 0) FROM DocumentSegment s WHERE s.documentId = :documentId")
    long sumWordCountByDocumentId(@Param("documentId") Long documentId);

    List<DocumentSegment> findByDocumentIdAndEnabled(Long documentId, Integer enabled);
}
