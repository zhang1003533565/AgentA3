package com.example.appbackend.service.impl;

import com.example.appbackend.dto.*;
import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.entity.*;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.*;
import com.example.appbackend.service.MerchantService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class MerchantServiceImpl implements MerchantService {

    @Autowired private MerchantCategoryRepository categoryRepository;
    @Autowired private MerchantRepository merchantRepository;
    @Autowired private DiscountActivityRepository activityRepository;
    @Autowired private DiscountClaimRepository claimRepository;
    @Autowired private MerchantReviewRepository reviewRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private ObjectMapper objectMapper;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ========== 商家分类 ==========

    @Override
    public List<MerchantDTO.CategoryVO> listCategories() {
        return categoryRepository.findAllByStatusOrderBySortAsc(1).stream()
                .map(c -> {
                    MerchantDTO.CategoryVO vo = new MerchantDTO.CategoryVO();
                    vo.setId(c.getId());
                    vo.setCategoryName(c.getCategoryName());
                    vo.setSort(c.getSort());
                    vo.setStatus(c.getStatus());
                    vo.setStatusText(c.getStatus() != null && c.getStatus() == 1 ? "启用" : "禁用");
                    return vo;
                }).collect(Collectors.toList());
    }

    @Override
    public MerchantDTO.CategoryVO createCategory(MerchantDTO.CategoryRequest req) {
        MerchantCategory c = new MerchantCategory();
        c.setCategoryName(req.getCategoryName());
        c.setSort(req.getSort() != null ? req.getSort() : 0);
        c = categoryRepository.save(c);
        MerchantDTO.CategoryVO vo = new MerchantDTO.CategoryVO();
        vo.setId(c.getId());
        vo.setCategoryName(c.getCategoryName());
        vo.setSort(c.getSort());
        vo.setStatus(c.getStatus());
        vo.setStatusText(c.getStatus() != null && c.getStatus() == 1 ? "启用" : "禁用");
        return vo;
    }

    @Override
    public void updateCategory(Long id, MerchantDTO.CategoryRequest req) {
        MerchantCategory c = categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "分类不存在"));
        if (req.getCategoryName() != null) c.setCategoryName(req.getCategoryName());
        if (req.getSort() != null) c.setSort(req.getSort());
        categoryRepository.save(c);
    }

    @Override
    public void deleteCategory(Long id) {
        if (!categoryRepository.existsById(id))
            throw new BusinessException(404, "分类不存在");
        if (categoryRepository.countByCategoryId(id) > 0)
            throw new BusinessException(400, "该分类下有商家，无法删除");
        categoryRepository.deleteById(id);
    }

    // ========== 商家 ==========

    @Override
    public PageResponse<MerchantDTO.MerchantVO> getMerchantList(Integer current, Integer size, Long categoryId,
                                                                 String keyword, Integer status,
                                                                 BigDecimal lat, BigDecimal lng, String sort) {
        if (current == null) current = 1;
        if (size == null) size = 10;
        PageRequest pageRequest = buildPageRequest(current, size, sort);
        Page<Merchant> page = merchantRepository.findPublicList(categoryId, keyword, status, pageRequest);
        List<MerchantDTO.MerchantVO> records = page.getContent().stream()
                .map(m -> toMerchantVO(m, lat, lng)).collect(Collectors.toList());
        return new PageResponse<>(records, page.getTotalElements(), current, size);
    }

    @Override
    public MerchantDTO.MerchantDetailVO getMerchantDetail(Long id) {
        Merchant m = merchantRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "商家不存在"));
        merchantRepository.incrementViewCount(id);
        MerchantDTO.MerchantDetailVO vo = toMerchantDetailVO(m);
        // 查询当前有剩余名额的活动
        Page<DiscountActivity> acts = activityRepository.findByMerchantId(id,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "id")));
        vo.setActivities(acts.getContent().stream()
                .map(DiscountServiceImpl::toActivityVO)
                .collect(Collectors.toList()));
        return vo;
    }

    @Override
    public MerchantDTO.MerchantVO createMerchant(MerchantDTO.MerchantRequest req) {
        if (userRepository.existsByUsername(req.getUsername()))
            throw new BusinessException(400, "商家账号已存在");
        Role merchantRole = roleRepository.findByName("MERCHANT")
                .orElseThrow(() -> new BusinessException(500, "MERCHANT角色未配置，请先在数据库中创建该角色"));

        User merchantUser = new User();
        merchantUser.setUsername(req.getUsername());
        merchantUser.setPassword(req.getPassword());
        merchantUser.setRole(merchantRole);
        merchantUser.setPhone(req.getContactPhone());
        merchantUser.setRealName(req.getContactName());
        merchantUser = userRepository.save(merchantUser);

        Merchant m = new Merchant();
        applyMerchantRequest(m, req);
        m.setUserId(merchantUser.getId());
        m = merchantRepository.save(m);
        MerchantDTO.MerchantVO vo = toMerchantVO(m, null, null);
        vo.setMerchantUsername(req.getUsername());
        vo.setMerchantPassword(req.getPassword());
        return vo;
    }

    @Override
    public void updateMerchant(Long id, MerchantDTO.MerchantRequest req) {
        Merchant m = merchantRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "商家不存在"));
        applyMerchantRequest(m, req);
        merchantRepository.save(m);
    }

    @Override
    public void deleteMerchant(Long id) {
        if (!merchantRepository.existsById(id))
            throw new BusinessException(404, "商家不存在");
        // 先删除该商家下所有优惠活动的领取记录，再删除活动（优惠活动无上架下架，仅创建/删除）
        activityRepository.findByMerchantId(id, PageRequest.of(0, Integer.MAX_VALUE))
                .getContent().forEach(a -> claimRepository.deleteByActivityId(a.getId()));
        activityRepository.deleteByMerchantId(id);
        merchantRepository.deleteById(id);
    }

    @Override
    public void updateMerchantStatus(Long id, MerchantDTO.StatusRequest req) {
        Merchant m = merchantRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "商家不存在"));
        m.setStatus(req.getStatus());
        merchantRepository.save(m);
    }

    // ========== 统计 ==========

    @Override
    public MerchantDTO.StatisticsVO getStatistics(String startDate, String endDate) {
        MerchantDTO.StatisticsVO vo = new MerchantDTO.StatisticsVO();
        vo.setTotalMerchants(merchantRepository.count());
        vo.setTotalActivities(activityRepository.count());
        vo.setActiveActivities(activityRepository.countActive());
        vo.setTotalReviews(reviewRepository.count());
        vo.setAvgScore(computeGlobalAvgScore());

        LocalDateTime start = parseDate(startDate);
        LocalDateTime end = parseDate(endDate).withHour(23).withMinute(59).withSecond(59);
        List<CountItem> trend = new ArrayList<>();
        LocalDate cursor = start.toLocalDate();
        LocalDate endD = end.toLocalDate();
        while (!cursor.isAfter(endD)) {
            LocalDateTime dayStart = cursor.atStartOfDay();
            LocalDateTime dayEnd = cursor.atTime(LocalTime.MAX);
            long count = activityRepository.countByDateRange(dayStart, dayEnd);
            CountItem item = new CountItem();
            item.setName(cursor.toString());
            item.setValue((int) count);
            trend.add(item);
            cursor = cursor.plusDays(1);
        }
        vo.setActivityTrend(trend);

        List<Merchant> topMerchants = merchantRepository.findAll(
                PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "id"))).getContent();
        List<CountItem> top = topMerchants.stream().map(m -> {
            CountItem ci = new CountItem();
            ci.setName(m.getMerchantName());
            ci.setValue((int) merchantRepository.countActivitiesByMerchantId(m.getId()));
            return ci;
        }).collect(Collectors.toList());
        vo.setTopMerchants(top);
        return vo;
    }

    private Double computeGlobalAvgScore() {
        Double avg = reviewRepository.getGlobalAverageScore();
        if (avg == null) return 0.0;
        return Math.round(avg * 10.0) / 10.0;
    }

    // ========== 商家评价 ==========

    @Override
    public Long createReview(MerchantDTO.ReviewRequest req, Long userId) {
        if (!merchantRepository.existsById(req.getMerchantId()))
            throw new BusinessException(404, "商家不存在");
        Optional<MerchantReview> existing = reviewRepository.findByUserIdAndMerchantIdAndStatus(userId, req.getMerchantId(), 1);
        if (existing.isPresent())
            throw new BusinessException(400, "您已评价过该商家，不能重复评价");
        MerchantReview r = new MerchantReview();
        r.setUserId(userId);
        r.setMerchantId(req.getMerchantId());
        r.setActivityId(req.getActivityId());
        r.setScore(req.getScore());
        r.setContent(req.getContent());
        r.setImages(toJson(req.getImages()));
        r.setStatus(1);
        r = reviewRepository.save(r);
        return r.getId();
    }

    @Override
    public PageResponse<MerchantDTO.ReviewPageVO> getReviewList(Long merchantId, Integer current, Integer size, Integer score) {
        if (current == null) current = 1;
        if (size == null) size = 10;
        Page<MerchantReview> page;
        if (score != null) {
            page = reviewRepository.findByMerchantIdAndStatusAndScore(merchantId, 1, score,
                    PageRequest.of(current - 1, size, Sort.by(Sort.Direction.DESC, "id")));
        } else {
            page = reviewRepository.findByMerchantIdAndStatus(merchantId, 1,
                    PageRequest.of(current - 1, size, Sort.by(Sort.Direction.DESC, "id")));
        }
        List<MerchantDTO.ReviewVO> records = page.getContent().stream().map(this::toReviewVO).collect(Collectors.toList());

        // 计算评分分布
        Map<String, Long> distribution = new LinkedHashMap<>();
        for (int i = 5; i >= 1; i--) {
            long cnt = reviewRepository.countByMerchantIdAndStatusAndScore(merchantId, 1, i);
            distribution.put(String.valueOf(i), cnt);
        }
        MerchantDTO.ReviewPageVO pageVO = new MerchantDTO.ReviewPageVO();
        pageVO.setRecords(records);
        pageVO.setTotal(page.getTotalElements());
        pageVO.setSize(size);
        pageVO.setCurrent(current);
        pageVO.setPages((int) Math.ceil((double) page.getTotalElements() / size));
        pageVO.setAvgScore(reviewRepository.getAverageScoreByMerchantId(merchantId));
        pageVO.setScoreDistribution(distribution);
        return new PageResponse<>(List.of(pageVO), page.getTotalElements(), current, size);
    }

    @Override
    public void deleteReview(Long id, Long userId, boolean isAdmin) {
        MerchantReview r = reviewRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "评价不存在"));
        if (!isAdmin && !r.getUserId().equals(userId))
            throw new BusinessException(403, "无权限");
        reviewRepository.markAsDeleted(id);
    }

    @Override
    public PageResponse<MerchantDTO.ReviewVO> getAdminReviewList(Integer current, Integer size, Long merchantId, Integer status) {
        if (current == null) current = 1;
        if (size == null) size = 10;
        Page<MerchantReview> page;
        if (merchantId != null) {
            page = reviewRepository.findByMerchantIdAndStatus(merchantId, status != null ? status : 1,
                    PageRequest.of(current - 1, size, Sort.by(Sort.Direction.DESC, "id")));
        } else {
            page = reviewRepository.findByStatus(status != null ? status : 1,
                    PageRequest.of(current - 1, size, Sort.by(Sort.Direction.DESC, "id")));
        }
        List<MerchantDTO.ReviewVO> records = page.getContent().stream().map(this::toReviewVO).collect(Collectors.toList());
        return new PageResponse<>(records, page.getTotalElements(), current, size);
    }

    private MerchantDTO.ReviewVO toReviewVO(MerchantReview r) {
        MerchantDTO.ReviewVO vo = new MerchantDTO.ReviewVO();
        vo.setId(r.getId());
        vo.setUserId(r.getUserId());
        vo.setMerchantId(r.getMerchantId());
        vo.setActivityId(r.getActivityId());
        vo.setScore(r.getScore());
        vo.setContent(r.getContent());
        vo.setImages(fromJson(r.getImages()));
        vo.setStatus(r.getStatus());
        vo.setStatusText(r.getStatus() == 1 ? "正常" : "已删除");
        vo.setCreateTime(r.getCreateTime() != null ? r.getCreateTime().format(FMT) : null);
        if (r.getUser() != null) {
            vo.setUsername(r.getUser().getUsername());
            vo.setUserAvatar(r.getUser().getAvatar());
        }
        return vo;
    }

    // ========== 工具方法 ==========

    private MerchantDTO.MerchantVO toMerchantVO(Merchant m, BigDecimal userLat, BigDecimal userLng) {
        MerchantDTO.MerchantVO vo = new MerchantDTO.MerchantVO();
        vo.setId(m.getId());
        vo.setMerchantName(m.getMerchantName());
        vo.setCategoryId(m.getCategoryId());
        if (m.getCategory() != null) vo.setCategoryName(m.getCategory().getCategoryName());
        vo.setLogo(m.getLogo());
        vo.setAddress(m.getAddress());
        vo.setContactPhone(m.getContactPhone());
        vo.setBusinessHours(m.getBusinessHours());
        vo.setStatus(m.getStatus());
        vo.setStatusText(getMerchantStatusText(m.getStatus()));
        vo.setActivityCount(merchantRepository.countActiveActivities(m.getId()));
        vo.setAvgScore(reviewRepository.getAverageScoreByMerchantId(m.getId()));
        vo.setReviewCount((int) reviewRepository.countByMerchantIdAndStatus(m.getId(), 1));
        if (userLat != null && userLng != null && m.getLatitude() != null && m.getLongitude() != null) {
            vo.setDistance(calcDistance(userLat.doubleValue(), userLng.doubleValue(),
                    m.getLatitude().doubleValue(), m.getLongitude().doubleValue()));
        }
        return vo;
    }

    private MerchantDTO.MerchantDetailVO toMerchantDetailVO(Merchant m) {
        MerchantDTO.MerchantDetailVO vo = new MerchantDTO.MerchantDetailVO();
        vo.setId(m.getId());
        vo.setMerchantName(m.getMerchantName());
        vo.setCategoryId(m.getCategoryId());
        if (m.getCategory() != null) vo.setCategoryName(m.getCategory().getCategoryName());
        vo.setDescription(m.getDescription());
        vo.setLogo(m.getLogo());
        vo.setImages(fromJson(m.getImages()));
        vo.setAddress(m.getAddress());
        vo.setLongitude(m.getLongitude());
        vo.setLatitude(m.getLatitude());
        vo.setContactName(m.getContactName());
        vo.setContactPhone(m.getContactPhone());
        vo.setBusinessHours(m.getBusinessHours());
        vo.setStatus(m.getStatus());
        vo.setStatusText(getMerchantStatusText(m.getStatus()));
        vo.setViewCount(m.getViewCount());
        vo.setAvgScore(reviewRepository.getAverageScoreByMerchantId(m.getId()));
        vo.setReviewCount((int) reviewRepository.countByMerchantIdAndStatus(m.getId(), 1));
        return vo;
    }

    private void applyMerchantRequest(Merchant m, MerchantDTO.MerchantRequest req) {
        if (req.getMerchantName() != null) m.setMerchantName(req.getMerchantName());
        if (req.getCategoryId() != null) m.setCategoryId(req.getCategoryId());
        if (req.getDescription() != null) m.setDescription(req.getDescription());
        if (req.getLogo() != null) m.setLogo(req.getLogo());
        if (req.getImages() != null) m.setImages(toJson(req.getImages()));
        if (req.getAddress() != null) m.setAddress(req.getAddress());
        if (req.getLongitude() != null) m.setLongitude(req.getLongitude());
        if (req.getLatitude() != null) m.setLatitude(req.getLatitude());
        if (req.getContactName() != null) m.setContactName(req.getContactName());
        if (req.getContactPhone() != null) m.setContactPhone(req.getContactPhone());
        if (req.getBusinessHours() != null) m.setBusinessHours(req.getBusinessHours());
    }

    private String getMerchantStatusText(Integer status) {
        if (status == null) return "";
        switch (status) {
            case 1: return "正常营业";
            case 2: return "暂停营业";
            case 3: return "已禁用";
            default: return "";
        }
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

    private PageRequest buildPageRequest(Integer current, Integer size, String sort) {
        if ("distance".equals(sort)) {
            return PageRequest.of(current - 1, size, Sort.by(Sort.Direction.ASC, "id"));
        }
        return PageRequest.of(current - 1, size, Sort.by(Sort.Direction.DESC, "id"));
    }

    private LocalDateTime parseDate(String date) {
        if (date == null || date.isEmpty()) return LocalDateTime.now().minusDays(30);
        return LocalDateTime.parse(date + " 00:00:00", FMT);
    }

    private double calcDistance(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
