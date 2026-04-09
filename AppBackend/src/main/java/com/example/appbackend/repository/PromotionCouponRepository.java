package com.example.appbackend.repository;

import com.example.appbackend.entity.PromotionCoupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 优惠券 Repository
 */
@Repository
public interface PromotionCouponRepository extends JpaRepository<PromotionCoupon, Long> {

    /**
     * 根据分类查询优惠券
     */
    List<PromotionCoupon> findByCategoryAndStatus(String category, Integer status);

    /**
     * 根据商家 ID 查询优惠券
     */
    List<PromotionCoupon> findByMerchantIdAndStatus(Long merchantId, Integer status);

    /**
     * 根据档口 ID 查询优惠券
     */
    List<PromotionCoupon> findByStallIdAndStatus(Long stallId, Integer status);

    /**
     * 根据设施 ID 查询优惠券
     */
    List<PromotionCoupon> findByFacilityIdAndStatus(Long facilityId, Integer status);

    /**
     * 查询所有上架的优惠券
     */
    List<PromotionCoupon> findByStatusOrderBySortOrderAsc(Integer status);

    /**
     * 查询上架中的优惠券
     */
    @Query("SELECT p FROM PromotionCoupon p WHERE p.status = 1")
    List<PromotionCoupon> findAvailableCoupons();

    /**
     * 根据标签类型查询优惠券
     */
    List<PromotionCoupon> findByTagTypeAndStatus(String tagType, Integer status);

    /**
     * 查询 Banner 展示的优惠券
     */
    List<PromotionCoupon> findByIsBannerTrueAndStatus(Integer status);
}
