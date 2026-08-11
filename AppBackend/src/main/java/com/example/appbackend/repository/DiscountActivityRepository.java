package com.example.appbackend.repository;

import com.example.appbackend.entity.DiscountActivity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface DiscountActivityRepository extends JpaRepository<DiscountActivity, Long>, JpaSpecificationExecutor<DiscountActivity> {

    Page<DiscountActivity> findByMerchantId(Long merchantId, Pageable pageable);

    @Query("SELECT da FROM DiscountActivity da LEFT JOIN da.merchant m " +
           "WHERE (da.remainCount IS NULL OR da.remainCount > 0) " +
           "AND (:merchantId IS NULL OR da.merchantId = :merchantId) " +
           "AND (:categoryId IS NULL OR m.categoryId = :categoryId) " +
           "AND (:keyword IS NULL OR m.merchantName LIKE CONCAT('%', :keyword, '%')) " +
           "AND (:status IS NULL OR da.status = :status)")
    Page<DiscountActivity> findPublicList(@Param("merchantId") Long merchantId,
                                          @Param("categoryId") Long categoryId,
                                          @Param("keyword") String keyword,
                                          @Param("status") Integer status,
                                          Pageable pageable);

    @Modifying
    @Query("UPDATE DiscountActivity da SET da.status = 3 WHERE da.endTime < :now AND da.status != 3")
    int expireActivities(@Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE DiscountActivity da SET da.status = 0 WHERE da.startTime > :now AND da.status NOT IN (0, 2, 3)")
    int markPendingActivities(@Param("now") LocalDateTime now);

    @Query("SELECT COUNT(da) FROM DiscountActivity da WHERE da.remainCount IS NULL OR da.remainCount > 0")
    long countActive();

    @Query("SELECT COUNT(da) FROM DiscountActivity da WHERE da.createTime >= :start AND da.createTime <= :end")
    long countByDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    void deleteByMerchantId(Long merchantId);

    @Modifying
    @Query("UPDATE DiscountActivity da SET da.remainCount = da.remainCount - 1 WHERE da.id = :activityId AND da.remainCount > 0")
    int decrementRemainCount(@Param("activityId") Long activityId);
}
