package com.example.appbackend.repository;

import com.example.appbackend.entity.SecondhandItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SecondhandItemRepository extends JpaRepository<SecondhandItem, Long>, JpaSpecificationExecutor<SecondhandItem> {

    Page<SecondhandItem> findByUserId(Long userId, Pageable pageable);

    Page<SecondhandItem> findByUserIdAndStatus(Long userId, Integer status, Pageable pageable);

    Page<SecondhandItem> findByUserIdAndStatusIn(Long userId, List<Integer> statuses, Pageable pageable);

    Page<SecondhandItem> findByUserIdAndTradeType(Long userId, String tradeType, Pageable pageable);

    Page<SecondhandItem> findByUserIdAndStatusAndTradeType(Long userId, Integer status, String tradeType, Pageable pageable);

    @Query("SELECT s FROM SecondhandItem s WHERE s.status = 2 " +
           "AND (:categoryId IS NULL OR s.categoryId = :categoryId) " +
           "AND (:keyword IS NULL OR s.title LIKE CONCAT('%', :keyword, '%')) " +
           "AND (:condition IS NULL OR s.condition = :condition) " +
           "AND (:minPrice IS NULL OR s.price >= :minPrice) " +
           "AND (:maxPrice IS NULL OR s.price <= :maxPrice)")
    Page<SecondhandItem> findPublicList(@Param("categoryId") Long categoryId,
                                        @Param("keyword") String keyword,
                                        @Param("condition") Integer condition,
                                        @Param("minPrice") java.math.BigDecimal minPrice,
                                        @Param("maxPrice") java.math.BigDecimal maxPrice,
                                        Pageable pageable);

    @Query("SELECT s FROM SecondhandItem s WHERE " +
           "(:status IS NULL OR s.status = :status) " +
           "AND (:categoryId IS NULL OR s.categoryId = :categoryId) " +
           "AND (:userId IS NULL OR s.userId = :userId) " +
           "AND (:tradeType IS NULL OR s.tradeType = :tradeType) " +
           "AND (:keyword IS NULL OR s.title LIKE CONCAT('%', :keyword, '%') OR s.description LIKE CONCAT('%', :keyword, '%'))")
    Page<SecondhandItem> findAdminList(@Param("status") Integer status,
                                       @Param("categoryId") Long categoryId,
                                       @Param("userId") Long userId,
                                       @Param("tradeType") String tradeType,
                                       @Param("keyword") String keyword,
                                       Pageable pageable);

    @Modifying
    @Query("UPDATE SecondhandItem s SET s.viewCount = s.viewCount + 1 WHERE s.id = :id")
    void incrementViewCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE SecondhandItem s SET s.favoriteCount = s.favoriteCount + :delta WHERE s.id = :id")
    void updateFavoriteCount(@Param("id") Long id, @Param("delta") int delta);

    @Modifying
    @Query("UPDATE SecondhandItem s SET s.inquiryCount = s.inquiryCount + 1 WHERE s.id = :id")
    void incrementInquiryCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE SecondhandItem s SET s.heatScore = (COALESCE(s.viewCount, 0) * 1 + COALESCE(s.favoriteCount, 0) * 3 + COALESCE(s.inquiryCount, 0) * 5) WHERE s.id = :id")
    void updateHeatScore(@Param("id") Long id);

    @Modifying
    @Query("UPDATE SecondhandItem s SET s.status = :toStatus WHERE s.id = :id AND s.status = :fromStatus")
    int updateStatusIfCurrent(@Param("id") Long id, @Param("fromStatus") Integer fromStatus, @Param("toStatus") Integer toStatus);

    @Query("SELECT COUNT(s) FROM SecondhandItem s WHERE s.status = 2")
    long countOnSale();

    @Query("SELECT COUNT(s) FROM SecondhandItem s WHERE s.status = 3")
    long countSold();

    @Query("SELECT COUNT(s) FROM SecondhandItem s WHERE s.status = 4")
    long countOffline();

    @Query("SELECT COUNT(s) FROM SecondhandItem s WHERE s.createTime >= :start AND s.createTime <= :end")
    long countByDateRange(@Param("start") java.time.LocalDateTime start, @Param("end") java.time.LocalDateTime end);

    List<SecondhandItem> findByIdIn(List<Long> ids);

    List<SecondhandItem> findByCategoryId(Long categoryId);
}
