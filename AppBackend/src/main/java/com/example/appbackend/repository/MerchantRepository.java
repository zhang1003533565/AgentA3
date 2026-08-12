package com.example.appbackend.repository;

import com.example.appbackend.entity.Merchant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MerchantRepository extends JpaRepository<Merchant, Long>, JpaSpecificationExecutor<Merchant> {

    Page<Merchant> findByCategoryId(Long categoryId, Pageable pageable);

    Page<Merchant> findByStatus(Integer status, Pageable pageable);

    @Query("SELECT m FROM Merchant m WHERE 1=1 " +
           "AND (:categoryId IS NULL OR m.categoryId = :categoryId) " +
           "AND (:keyword IS NULL OR m.merchantName LIKE CONCAT('%', :keyword, '%')) " +
           "AND (:status IS NULL OR m.status = :status)")
    Page<Merchant> findPublicList(@Param("categoryId") Long categoryId,
                                  @Param("keyword") String keyword,
                                  @Param("status") Integer status,
                                   Pageable pageable);

    @Query("SELECT COUNT(a) FROM DiscountActivity a WHERE a.merchantId = :merchantId AND (a.remainCount IS NULL OR a.remainCount > 0)")
    int countActiveActivities(@Param("merchantId") Long merchantId);

    @Query("SELECT COUNT(a) FROM DiscountActivity a WHERE a.merchantId = :merchantId")
    long countActivitiesByMerchantId(@Param("merchantId") Long merchantId);

    Merchant findByUserId(Long userId);

    @Modifying
    @Query("UPDATE Merchant m SET m.viewCount = m.viewCount + 1 WHERE m.id = :id")
    void incrementViewCount(@Param("id") Long id);
}
