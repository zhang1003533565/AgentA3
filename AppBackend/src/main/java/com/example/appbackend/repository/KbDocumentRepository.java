package com.example.appbackend.repository;

import com.example.appbackend.entity.KbDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface KbDocumentRepository extends JpaRepository<KbDocument, Long> {

    List<KbDocument> findByDatasetIdOrderByPositionAsc(Long datasetId);

    Page<KbDocument> findByDatasetIdOrderByPositionAsc(Long datasetId, Pageable pageable);

    long countByDatasetId(Long datasetId);

    @Query("SELECT COALESCE(SUM(d.wordCount), 0) FROM KbDocument d WHERE d.datasetId = :datasetId")
    long sumWordCountByDatasetId(@Param("datasetId") Long datasetId);

    List<KbDocument> findByDatasetIdAndIndexingStatusIn(Long datasetId, List<String> statuses);

    List<KbDocument> findByIndexingStatusIn(List<String> statuses);

    @Query("""
            SELECT d FROM KbDocument d
            WHERE d.datasetId = :datasetId
              AND (:keyword IS NULL
                OR :keyword = ''
                OR LOWER(d.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
              )
            ORDER BY d.position ASC
            """)
    Page<KbDocument> searchByDatasetId(@Param("datasetId") Long datasetId,
                                       @Param("keyword") String keyword,
                                       Pageable pageable);

    @Query("""
            SELECT d FROM KbDocument d
            WHERE d.datasetId = :datasetId
              AND (:keyword IS NULL
                OR :keyword = ''
                OR LOWER(d.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
              )
            """)
    Page<KbDocument> searchByDatasetIdWithSort(@Param("datasetId") Long datasetId,
                                               @Param("keyword") String keyword,
                                               Pageable pageable);

    @Modifying
    @Query("UPDATE KbDocument d SET d.segmentCount = :count WHERE d.id = :id")
    void updateSegmentCount(@Param("id") Long id, @Param("count") int count);

    @Modifying
    @Query("UPDATE KbDocument d SET d.indexingStatus = :status, d.errorMessage = :error WHERE d.id = :id")
    void updateIndexingStatus(@Param("id") Long id, @Param("status") String status, @Param("error") String error);
}
