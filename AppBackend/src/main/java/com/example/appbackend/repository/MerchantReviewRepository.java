package com.example.appbackend.repository;

import com.example.appbackend.entity.MerchantReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MerchantReviewRepository extends JpaRepository<MerchantReview, Long> {

    Optional<MerchantReview> findByUserIdAndMerchantIdAndStatus(Long userId, Long merchantId, Integer status);

    Page<MerchantReview> findByMerchantIdAndStatus(Long merchantId, Integer status, Pageable pageable);

    Page<MerchantReview> findByMerchantIdAndStatusAndScore(Long merchantId, Integer status, Integer score, Pageable pageable);

    Page<MerchantReview> findByStatus(Integer status, Pageable pageable);

    long countByMerchantIdAndStatus(Long merchantId, Integer status);

    long countByMerchantIdAndStatusAndScore(Long merchantId, Integer status, Integer score);

    @Query("SELECT AVG(r.score) FROM MerchantReview r WHERE r.merchantId = :merchantId AND r.status = 1")
    Double getAverageScoreByMerchantId(@Param("merchantId") Long merchantId);

    @Query("SELECT AVG(r.score) FROM MerchantReview r WHERE r.status = 1")
    Double getGlobalAverageScore();

    @Modifying
    @Query("UPDATE MerchantReview r SET r.status = 2 WHERE r.id = :id")
    void markAsDeleted(@Param("id") Long id);
}
