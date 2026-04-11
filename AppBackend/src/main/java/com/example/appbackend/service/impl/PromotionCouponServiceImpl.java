package com.example.appbackend.service.impl;

import com.example.appbackend.dto.PromotionCouponClaimRequest;
import com.example.appbackend.dto.PromotionCouponDTO;
import com.example.appbackend.dto.UserCouponDTO;
import com.example.appbackend.entity.PromotionCoupon;
import com.example.appbackend.entity.Merchant;
import com.example.appbackend.entity.CanteenStall;
import com.example.appbackend.entity.CampusFacility;
import com.example.appbackend.entity.User;
import com.example.appbackend.entity.UserCoupon;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.PromotionCouponRepository;
import com.example.appbackend.repository.MerchantRepository;
import com.example.appbackend.repository.CanteenStallRepository;
import com.example.appbackend.repository.FacilityRepository;
import com.example.appbackend.repository.UserCouponRepository;
import com.example.appbackend.repository.UserRepository;
import com.example.appbackend.service.PromotionCouponService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 优惠券 Service 实现
 */
@Service
public class PromotionCouponServiceImpl implements PromotionCouponService {

    @Autowired
    private PromotionCouponRepository promotionCouponRepository;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private CanteenStallRepository canteenStallRepository;

    @Autowired
    private FacilityRepository facilityRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private UserRepository userRepository;

    @Override
    public List<PromotionCouponDTO> getAllCoupons() {
        List<PromotionCoupon> coupons = promotionCouponRepository.findByStatusOrderBySortOrderAsc(1);
        return coupons.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Override
    public List<PromotionCouponDTO> getCouponsByCategory(String category) {
        List<PromotionCoupon> coupons = promotionCouponRepository.findByCategoryAndStatus(category, 1);
        return coupons.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Override
    public List<PromotionCouponDTO> getCouponsByMerchantId(Long merchantId) {
        List<PromotionCoupon> coupons = promotionCouponRepository.findByMerchantIdAndStatus(merchantId, 1);
        return coupons.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Override
    public List<PromotionCouponDTO> getCouponsByStallId(Long stallId) {
        List<PromotionCoupon> coupons = promotionCouponRepository.findByStallIdAndStatus(stallId, 1);
        return coupons.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Override
    public List<PromotionCouponDTO> getCouponsByFacilityId(Long facilityId) {
        List<PromotionCoupon> coupons = promotionCouponRepository.findByFacilityIdAndStatus(facilityId, 1);
        return coupons.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Override
    public List<PromotionCouponDTO> getCouponsByTagType(String tagType) {
        List<PromotionCoupon> coupons = promotionCouponRepository.findByTagTypeAndStatus(tagType, 1);
        return coupons.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Override
    public PromotionCouponDTO getCouponById(Long id) {
        PromotionCoupon coupon = promotionCouponRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "优惠券不存在"));
        return convertToDTO(coupon);
    }

    @Override
    public PromotionCouponDTO createCoupon(PromotionCouponDTO request) {
        PromotionCoupon coupon = new PromotionCoupon();
        BeanUtils.copyProperties(request, coupon);

        // 验证关联实体
        if (request.getMerchantId() != null) {
            Merchant merchant = merchantRepository.findById(request.getMerchantId())
                    .orElseThrow(() -> new BusinessException(404, "关联商家不存在"));
        }
        if (request.getStallId() != null) {
            canteenStallRepository.findById(request.getStallId())
                    .orElseThrow(() -> new BusinessException(404, "关联档口不存在"));
        }
        if (request.getFacilityId() != null) {
            facilityRepository.findById(request.getFacilityId())
                    .orElseThrow(() -> new BusinessException(404, "关联设施不存在"));
        }

        promotionCouponRepository.save(coupon);
        return convertToDTO(coupon);
    }

    @Override
    public PromotionCouponDTO updateCoupon(Long id, PromotionCouponDTO request) {
        PromotionCoupon coupon = promotionCouponRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "优惠券不存在"));

        // 更新字段
        if (request.getCouponName() != null) {
            coupon.setCouponName(request.getCouponName());
        }
        if (request.getCategory() != null) {
            coupon.setCategory(request.getCategory());
        }
        if (request.getTotalQuantity() != null) {
            coupon.setTotalQuantity(request.getTotalQuantity());
        }
        if (request.getStartDate() != null) {
            coupon.setStartDate(request.getStartDate());
        }
        if (request.getEndDate() != null) {
            coupon.setEndDate(request.getEndDate());
        }
        if (request.getImageUrl() != null) {
            coupon.setImageUrl(request.getImageUrl());
        }
        if (request.getTagType() != null) {
            coupon.setTagType(request.getTagType());
        }
        if (request.getPickupLocation() != null) {
            coupon.setPickupLocation(request.getPickupLocation());
        }
        if (request.getDescription() != null) {
            coupon.setDescription(request.getDescription());
        }
        if (request.getStatus() != null) {
            coupon.setStatus(request.getStatus());
        }
        if (request.getSortOrder() != null) {
            coupon.setSortOrder(request.getSortOrder());
        }
        if (request.getIsBanner() != null) {
            coupon.setIsBanner(request.getIsBanner());
        }

        promotionCouponRepository.save(coupon);
        return convertToDTO(coupon);
    }

    @Override
    public void deleteCoupon(Long id) {
        if (!promotionCouponRepository.existsById(id)) {
            throw new BusinessException(404, "优惠券不存在");
        }
        promotionCouponRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void claimCoupon(Long couponId, Long userId, PromotionCouponClaimRequest request) {
        PromotionCoupon coupon = promotionCouponRepository.findById(couponId)
                .orElseThrow(() -> new BusinessException(404, "优惠券不存在"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));

        if (coupon.getStatus() != 1) {
            throw new BusinessException(400, "优惠券已下架");
        }

        LocalDate today = LocalDate.now();
        if (coupon.getStartDate() != null && today.isBefore(LocalDate.parse(coupon.getStartDate()))) {
            throw new BusinessException(400, "优惠券尚未开始领取");
        }
        if (coupon.getEndDate() != null && today.isAfter(LocalDate.parse(coupon.getEndDate()))) {
            throw new BusinessException(400, "优惠券已过期");
        }

        UserCoupon userCoupon = userCouponRepository.findByUserIdAndCouponId(userId, couponId).orElse(null);
        if (userCoupon == null) {
            userCoupon = new UserCoupon();
            userCoupon.setUserId(userId);
            userCoupon.setCouponId(couponId);
            userCoupon.setClaimCount(1);
        }
        userCoupon.setStatus(1);
        userCoupon.setReceiverName(resolveReceiverName(user));
        userCoupon.setReceiverPhone(user.getPhone());
        userCoupon.setRemark(request == null ? null : request.getRemark());
        userCoupon.setClaimTime(LocalDateTime.now());
        if (coupon.getEndDate() != null) {
            userCoupon.setExpiryTime(LocalDate.parse(coupon.getEndDate()).atTime(23, 59, 59));
        }
        userCouponRepository.save(userCoupon);
    }

    @Override
    public List<PromotionCouponDTO> getBannerCoupons() {
        List<PromotionCoupon> coupons = promotionCouponRepository.findByIsBannerTrueAndStatus(1);
        return coupons.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Override
    public List<UserCouponDTO> getMyCoupons(Long userId) {
        return userCouponRepository.findByUserIdOrderByClaimTimeDesc(userId)
                .stream()
                .map(this::convertToUserCouponDTO)
                .collect(Collectors.toList());
    }

    private PromotionCouponDTO convertToDTO(PromotionCoupon coupon) {
        PromotionCouponDTO dto = new PromotionCouponDTO();
        BeanUtils.copyProperties(coupon, dto);

        // 获取商家名称
        if (coupon.getMerchantId() != null) {
            Merchant merchant = merchantRepository.findById(coupon.getMerchantId()).orElse(null);
            if (merchant != null) {
                dto.setMerchantName(merchant.getMerchantName());
            }
        }

        // 获取档口名称
        if (coupon.getStallId() != null) {
            CanteenStall stall = canteenStallRepository.findById(coupon.getStallId()).orElse(null);
            if (stall != null) {
                dto.setStallName(stall.getStallName());
            }
        }

        // 获取设施名称
        if (coupon.getFacilityId() != null) {
            CampusFacility facility = facilityRepository.findById(coupon.getFacilityId()).orElse(null);
            if (facility != null) {
                dto.setFacilityName(facility.getFacilityName());
            }
        }

        return dto;
    }

    private UserCouponDTO convertToUserCouponDTO(UserCoupon userCoupon) {
        UserCouponDTO dto = new UserCouponDTO();
        dto.setId(userCoupon.getId());
        dto.setUserId(userCoupon.getUserId());
        dto.setCouponId(userCoupon.getCouponId());
        dto.setClaimCount(userCoupon.getClaimCount());
        dto.setStatus(userCoupon.getStatus());
        dto.setReceiverName(userCoupon.getReceiverName());
        dto.setReceiverPhone(userCoupon.getReceiverPhone());
        dto.setRemark(userCoupon.getRemark());
        dto.setClaimTime(userCoupon.getClaimTime());
        dto.setExpiryTime(userCoupon.getExpiryTime());

        PromotionCoupon coupon = promotionCouponRepository.findById(userCoupon.getCouponId()).orElse(null);
        if (coupon != null) {
            dto.setCoupon(convertToDTO(coupon));
        }
        return dto;
    }

    private String resolveReceiverName(User user) {
        if (user.getRealName() != null && !user.getRealName().isBlank()) {
            return user.getRealName();
        }
        return user.getUsername();
    }
}
