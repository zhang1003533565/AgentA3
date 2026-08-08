package com.example.appbackend.service.impl;

import com.example.appbackend.dto.*;
import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.entity.*;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.*;
import com.example.appbackend.service.DiscountService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class DiscountServiceImpl implements DiscountService {

    @Autowired private DiscountActivityRepository activityRepository;
    @Autowired private DiscountClaimRepository claimRepository;
    @Autowired private DiscountFavoriteRepository favoriteRepository;
    @Autowired private MerchantRepository merchantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ObjectMapper objectMapper;

    private static final String ROLE_ADMIN = "ADMIN";

    private void checkOwnership(Long activityId, Long currentUserId) {
        if (currentUserId == null) throw new BusinessException(401, "请先登录");
        if (ROLE_ADMIN.equals(getRoleByUserId(currentUserId))) return;
        DiscountActivity a = activityRepository.findById(activityId)
                .orElseThrow(() -> new BusinessException(404, "活动不存在"));
        Merchant m = merchantRepository.findByUserId(currentUserId);
        if (m == null || !m.getId().equals(a.getMerchantId()))
            throw new BusinessException(403, "无权限操作该活动");
    }

    private void checkCreatePermission(Long currentUserId) {
        if (currentUserId == null) throw new BusinessException(401, "请先登录");
        String role = getRoleByUserId(currentUserId);
        if (!ROLE_ADMIN.equals(role)) {
            Merchant m = merchantRepository.findByUserId(currentUserId);
            if (m == null) throw new BusinessException(403, "只有商家或管理员可发布活动");
        }
    }

    private String getRoleByUserId(Long userId) {
        return userRepository.findRoleNameById(userId);
    }

    private Long resolveMerchantId(Long currentUserId) {
        if (ROLE_ADMIN.equals(getRoleByUserId(currentUserId))) return null;
        Merchant m = merchantRepository.findByUserId(currentUserId);
        return m != null ? m.getId() : null;
    }

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public DiscountDTO.ActivityVO createActivity(DiscountDTO.ActivityRequest req, Long currentUserId) {
        checkCreatePermission(currentUserId);
        DiscountActivity a = new DiscountActivity();
        Long merchantId = resolveMerchantId(currentUserId);
        if (merchantId == null) merchantId = req.getMerchantId();
        applyActivityRequest(a, req, merchantId);
        a = activityRepository.save(a);
        return toActivityVO(a);
    }

    @Override
    public PageResponse<DiscountDTO.ActivityVO> getActivityList(Integer current, Integer size, Long merchantId,
                                                                 Long categoryId, String keyword,
                                                                 Integer status,
                                                                 BigDecimal lat, BigDecimal lng,
                                                                 String sort, Long currentUserId) {
        if (current == null) current = 1;
        if (size == null) size = 10;
        PageRequest pageRequest = buildPageRequest(current, size, sort);
        Page<DiscountActivity> page = activityRepository.findPublicList(merchantId, categoryId, keyword, pageRequest);
        List<DiscountDTO.ActivityVO> records = page.getContent().stream()
                .map(a -> toActivityVO(a, lat, lng)).collect(Collectors.toList());
        return new PageResponse<>(records, page.getTotalElements(), current, size);
    }

    @Override
    public DiscountDTO.ActivityDetailVO getActivityDetail(Long id, Long currentUserId) {
        DiscountActivity a = activityRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "活动不存在"));
        DiscountDTO.ActivityDetailVO vo = toActivityDetailVO(a);
        if (currentUserId != null) {
            vo.setIsFavorited(favoriteRepository.existsByUserIdAndActivityId(currentUserId, id));
        } else {
            vo.setIsFavorited(false);
        }
        return vo;
    }

    @Override
    public void updateActivity(Long id, DiscountDTO.ActivityRequest req, Long currentUserId) {
        checkOwnership(id, currentUserId);
        DiscountActivity a = activityRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "活动不存在"));
        applyActivityRequest(a, req, a.getMerchantId());
        activityRepository.save(a);
    }

    @Override
    public void deleteActivity(Long id, Long currentUserId) {
        checkOwnership(id, currentUserId);
        if (!activityRepository.existsById(id))
            throw new BusinessException(404, "活动不存在");
        claimRepository.deleteByActivityId(id);
        activityRepository.deleteById(id);
    }

    @Override
    public PageResponse<DiscountDTO.ActivityVO> getMerchantActivities(Long merchantId, Integer current, Integer size, Long currentUserId) {
        if (current == null) current = 1;
        if (size == null) size = 10;
        Page<DiscountActivity> page = activityRepository.findByMerchantId(merchantId,
                PageRequest.of(current - 1, size, Sort.by(Sort.Direction.DESC, "id")));
        List<DiscountDTO.ActivityVO> records = page.getContent().stream()
                .map(DiscountServiceImpl::toActivityVO).collect(Collectors.toList());
        return new PageResponse<>(records, page.getTotalElements(), current, size);
    }

    @Override
    public void offlineActivity(Long id, Long currentUserId) {
        checkOwnership(id, currentUserId);
        DiscountActivity a = activityRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "活动不存在"));
        a.setEndTime(LocalDateTime.now().minusSeconds(1));
        activityRepository.save(a);
    }

    // ========== 领取 ==========

    @Override
    public void claimActivity(Long activityId, Long userId) {
        DiscountActivity a = activityRepository.findById(activityId)
                .orElseThrow(() -> new BusinessException(404, "活动不存在"));
        Integer realStatus = getRealStatus(a.getStartTime(), a.getEndTime());
        if (realStatus == 2) throw new BusinessException(400, "活动已结束");
        if (a.getStartTime() != null && a.getStartTime().isAfter(LocalDateTime.now()))
            throw new BusinessException(400, "活动尚未开始");
        if (claimRepository.existsByUserIdAndActivityId(userId, activityId))
            throw new BusinessException(400, "您已领取过该活动");
        if (a.getRemainCount() != null && a.getRemainCount() <= 0)
            throw new BusinessException(400, "名额已满");
        int updated = activityRepository.decrementRemainCount(activityId);
        if (updated == 0 && a.getRemainCount() != null && a.getRemainCount() <= 0)
            throw new BusinessException(400, "名额已满");
        DiscountClaim claim = new DiscountClaim();
        claim.setUserId(userId);
        claim.setActivityId(activityId);
        claimRepository.save(claim);
    }

    @Override
    public PageResponse<DiscountDTO.ClaimVO> getMyClaims(Long userId, Integer current, Integer size) {
        if (current == null) current = 1;
        if (size == null) size = 10;
        Page<DiscountClaim> page = claimRepository.findByUserId(userId, PageRequest.of(current - 1, size, Sort.by(Sort.Direction.DESC, "id")));
        List<DiscountDTO.ClaimVO> records = page.getContent().stream()
                .map(this::toClaimVO)
                .collect(Collectors.toList());
        return new PageResponse<>(records, page.getTotalElements(), current, size);
    }

    DiscountDTO.ClaimVO toClaimVO(DiscountClaim c) {
        DiscountDTO.ClaimVO vo = new DiscountDTO.ClaimVO();
        vo.setId(c.getId());
        vo.setActivityId(c.getActivityId());
        vo.setClaimTime(c.getClaimTime() != null ? c.getClaimTime().format(FMT) : null);
        DiscountActivity a = c.getActivity();
        if (a != null) {
            vo.setTitle(a.getTitle());
            vo.setCoverImage(a.getCoverImage());
            vo.setStartTime(a.getStartTime() != null ? a.getStartTime().format(FMT) : null);
            vo.setEndTime(a.getEndTime() != null ? a.getEndTime().format(FMT) : null);
            Integer realStatus = getRealStatus(a.getStartTime(), a.getEndTime());
            vo.setStatus(realStatus);
            vo.setStatusText(getActivityStatusText(realStatus));
            if (a.getMerchant() != null) {
                vo.setMerchantName(a.getMerchant().getMerchantName());
                vo.setMerchantAddress(a.getMerchant().getAddress());
            }
        }
        return vo;
    }

    // ========== 收藏 ==========

    @Override
    public void favoriteActivity(Long activityId, Long userId) {
        if (!activityRepository.existsById(activityId))
            throw new BusinessException(404, "活动不存在");
        if (favoriteRepository.existsByUserIdAndActivityId(userId, activityId))
            throw new BusinessException(400, "已收藏过该活动");
        DiscountFavorite f = new DiscountFavorite();
        f.setUserId(userId);
        f.setActivityId(activityId);
        favoriteRepository.save(f);
    }

    @Override
    public void unfavoriteActivity(Long activityId, Long userId) {
        DiscountFavorite f = favoriteRepository.findByUserIdAndActivityId(userId, activityId)
                .orElseThrow(() -> new BusinessException(404, "收藏记录不存在"));
        favoriteRepository.delete(f);
    }

    @Override
    public PageResponse<DiscountDTO.ActivityVO> getMyFavoriteActivities(Long userId, Integer current, Integer size) {
        if (current == null) current = 1;
        if (size == null) size = 10;
        Page<DiscountFavorite> page = favoriteRepository.findByUserId(userId, PageRequest.of(current - 1, size));
        List<DiscountDTO.ActivityVO> records = page.getContent().stream()
                .map(f -> activityRepository.findById(f.getActivityId()).orElse(null))
                .filter(Objects::nonNull)
                .map(DiscountServiceImpl::toActivityVO)
                .collect(Collectors.toList());
        return new PageResponse<>(records, page.getTotalElements(), current, size);
    }

    private void applyActivityRequest(DiscountActivity a, DiscountDTO.ActivityRequest req, Long merchantId) {
        if (merchantId != null) a.setMerchantId(merchantId);
        if (req.getTitle() != null) a.setTitle(req.getTitle());
        if (req.getDescription() != null) a.setDescription(req.getDescription());
        if (req.getCoverImage() != null) a.setCoverImage(req.getCoverImage());
        if (req.getImages() != null) a.setImages(toJson(req.getImages()));
        if (req.getStartTime() != null) a.setStartTime(parseActivityTime(req.getStartTime(), "开始时间"));
        if (req.getEndTime() != null) a.setEndTime(parseActivityTime(req.getEndTime(), "结束时间"));
        if (req.getUseRules() != null) a.setUseRules(req.getUseRules());
        if (req.getTotalCount() != null) a.setTotalCount(req.getTotalCount());
        if (req.getRemainCount() != null) a.setRemainCount(req.getRemainCount());
    }

    static DiscountDTO.ActivityVO toActivityVO(DiscountActivity a) {
        return toActivityVO(a, null, null);
    }

    static DiscountDTO.ActivityVO toActivityVO(DiscountActivity a, BigDecimal userLat, BigDecimal userLng) {
        DiscountDTO.ActivityVO vo = new DiscountDTO.ActivityVO();
        vo.setId(a.getId());
        vo.setMerchantId(a.getMerchantId());
        if (a.getMerchant() != null) {
            vo.setMerchantName(a.getMerchant().getMerchantName());
            vo.setMerchantLogo(a.getMerchant().getLogo());
        }
        vo.setTitle(a.getTitle());
        vo.setDescription(a.getDescription());
        vo.setCoverImage(a.getCoverImage());
        vo.setStartTime(a.getStartTime() != null ? a.getStartTime().format(FMT) : null);
        vo.setEndTime(a.getEndTime() != null ? a.getEndTime().format(FMT) : null);
        vo.setRemainCount(a.getRemainCount());
        Integer realStatus = getRealStatus(a.getStartTime(), a.getEndTime());
        vo.setStatus(realStatus);
        vo.setStatusText(getActivityStatusText(realStatus));
        vo.setCreateTime(a.getCreateTime() != null ? a.getCreateTime().format(FMT) : null);
        if (userLat != null && userLng != null && a.getMerchant() != null
                && a.getMerchant().getLatitude() != null && a.getMerchant().getLongitude() != null) {
            vo.setDistance(calcDistance(userLat.doubleValue(), userLng.doubleValue(),
                    a.getMerchant().getLatitude().doubleValue(), a.getMerchant().getLongitude().doubleValue()));
        }
        return vo;
    }

    private DiscountDTO.ActivityDetailVO toActivityDetailVO(DiscountActivity a) {
        DiscountDTO.ActivityDetailVO vo = new DiscountDTO.ActivityDetailVO();
        copyActivityVO(a, vo);
        vo.setImages(fromJson(a.getImages()));
        vo.setStartTime(a.getStartTime() != null ? a.getStartTime().format(FMT) : null);
        vo.setUseRules(a.getUseRules());
        vo.setTotalCount(a.getTotalCount());
        if (a.getTotalCount() != null && a.getRemainCount() != null) {
            vo.setClaimedCount(a.getTotalCount() - a.getRemainCount());
        } else {
            vo.setClaimedCount(0);
        }
        if (a.getMerchant() != null) {
            vo.setMerchantAddress(a.getMerchant().getAddress());
            vo.setMerchantContactPhone(a.getMerchant().getContactPhone());
        }
        return vo;
    }

    private void copyActivityVO(DiscountActivity a, DiscountDTO.ActivityVO vo) {
        vo.setId(a.getId());
        vo.setMerchantId(a.getMerchantId());
        if (a.getMerchant() != null) {
            vo.setMerchantName(a.getMerchant().getMerchantName());
            vo.setMerchantLogo(a.getMerchant().getLogo());
        }
        vo.setTitle(a.getTitle());
        vo.setDescription(a.getDescription());
        vo.setCoverImage(a.getCoverImage());
        vo.setStartTime(a.getStartTime() != null ? a.getStartTime().format(FMT) : null);
        vo.setEndTime(a.getEndTime() != null ? a.getEndTime().format(FMT) : null);
        vo.setRemainCount(a.getRemainCount());
        Integer realStatus = getRealStatus(a.getStartTime(), a.getEndTime());
        vo.setStatus(realStatus);
        vo.setStatusText(getActivityStatusText(realStatus));
        vo.setCreateTime(a.getCreateTime() != null ? a.getCreateTime().format(FMT) : null);
    }

    private static String getActivityStatusText(Integer status) {
        if (status == null) return "";
        switch (status) {
            case 0: return "未开始";
            case 1: return "进行中";
            case 2: return "已结束";
            default: return "";
        }
    }

    private static Integer getRealStatus(LocalDateTime startTime, LocalDateTime endTime) {
        LocalDateTime now = LocalDateTime.now();
        if (startTime != null && startTime.isAfter(now)) return 0;
        if (endTime != null && endTime.isBefore(now)) return 2;
        return 1;
    }

    private String toJson(List<String> list) {
        if (list == null || list.isEmpty()) return "[]";
        try { return objectMapper.writeValueAsString(list); }
        catch (Exception e) { return "[]"; }
    }

    private List<String> fromJson(String json) {
        if (json == null || json.isEmpty()) return Collections.emptyList();
        try { return objectMapper.readValue(json, new TypeReference<List<String>>() {}); }
        catch (Exception e) { return Collections.emptyList(); }
    }

    private LocalDateTime parseActivityTime(String raw, String label) {
        try {
            return LocalDateTime.parse(raw, FMT);
        } catch (DateTimeParseException e) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, label + "须为有效时间，格式 yyyy-MM-dd HH:mm:ss");
        }
    }

    private PageRequest buildPageRequest(Integer current, Integer size, String sort) {
        String sortField = "id";
        Sort.Direction direction = Sort.Direction.DESC;
        if ("expiring".equals(sort)) { sortField = "endTime"; direction = Sort.Direction.ASC; }
        return PageRequest.of(current - 1, size, Sort.by(direction, sortField));
    }

    private static double calcDistance(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
