package com.example.appbackend.service.impl;

import com.example.appbackend.dto.*;
import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.entity.*;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.*;
import com.example.appbackend.service.SecondhandService;
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
public class SecondhandServiceImpl implements SecondhandService {

    @Autowired private SecondhandCategoryRepository categoryRepository;
    @Autowired private SecondhandItemRepository itemRepository;
    @Autowired private SecondhandFavoriteRepository favoriteRepository;
    @Autowired private ChatSessionRepository chatSessionRepository;
    @Autowired private ChatMessageRepository chatMessageRepository;
    @Autowired private TradeRecordRepository tradeRecordRepository;
    @Autowired private SecondhandReportRepository reportRepository;
    @Autowired private ObjectMapper objectMapper;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ========== 分类 ==========

    @Override
    public List<SecondhandDTO.CategoryVO> listCategories() {
        return categoryRepository.findAllByOrderBySortAsc().stream()
                .map(c -> {
                    SecondhandDTO.CategoryVO vo = new SecondhandDTO.CategoryVO();
                    vo.setId(c.getId());
                    vo.setCategoryName(c.getCategoryName());
                    vo.setSort(c.getSort());
                    return vo;
                }).collect(Collectors.toList());
    }

    @Override
    public SecondhandDTO.CategoryVO createCategory(SecondhandDTO.CategoryRequest req) {
        SecondhandCategory c = new SecondhandCategory();
        c.setCategoryName(req.getCategoryName());
        c.setSort(req.getSort() != null ? req.getSort() : 0);
        c = categoryRepository.save(c);
        SecondhandDTO.CategoryVO vo = new SecondhandDTO.CategoryVO();
        vo.setId(c.getId());
        vo.setCategoryName(c.getCategoryName());
        vo.setSort(c.getSort());
        return vo;
    }

    @Override
    public void updateCategory(Long id, SecondhandDTO.CategoryRequest req) {
        SecondhandCategory c = categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "分类不存在"));
        if (req.getCategoryName() != null) c.setCategoryName(req.getCategoryName());
        if (req.getSort() != null) c.setSort(req.getSort());
        categoryRepository.save(c);
    }

    @Override
    public void deleteCategory(Long id) {
        if (!categoryRepository.existsById(id))
            throw new BusinessException(404, "分类不存在");
        for (SecondhandItem item : itemRepository.findByCategoryId(id)) {
            deleteSecondhandItemWithRelations(item.getId());
        }
        categoryRepository.deleteById(id);
    }

    /** 删除物品及其交易记录、举报、收藏、聊天会话与消息（避免外键残留） */
    private void deleteSecondhandItemWithRelations(Long itemId) {
        for (ChatSession session : chatSessionRepository.findByItemId(itemId)) {
            chatMessageRepository.deleteBySessionId(session.getId());
            chatSessionRepository.delete(session);
        }
        favoriteRepository.deleteByItemId(itemId);
        tradeRecordRepository.deleteByItemId(itemId);
        reportRepository.deleteByItemId(itemId);
        itemRepository.deleteById(itemId);
    }

    // ========== 物品 ==========

    @Override
    public PageResponse<SecondhandDTO.ItemVO> getItemList(Integer current, Integer size, Long categoryId,
                                                          String keyword, Integer condition, BigDecimal minPrice,
                                                          BigDecimal maxPrice, String sort) {
        if (current == null) current = 1;
        if (size == null) size = 10;
        PageRequest pageRequest = buildPageRequest(current, size, sort);
        Page<SecondhandItem> page = itemRepository.findPublicList(categoryId, keyword, condition, minPrice, maxPrice, pageRequest);
        List<SecondhandDTO.ItemVO> records = page.getContent().stream()
                .map(this::toItemVO).collect(Collectors.toList());
        return new PageResponse<>(records, page.getTotalElements(), current, size);
    }

    @Override
    public SecondhandDTO.ItemDetailVO getItemDetail(Long id, Long currentUserId) {
        SecondhandItem item = itemRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "物品不存在"));
        // 已下架商品仅发布者可查看
        if (Integer.valueOf(4).equals(item.getStatus())
                && (currentUserId == null || !currentUserId.equals(item.getUserId()))) {
            throw new BusinessException(403, "该物品已下架");
        }
        itemRepository.incrementViewCount(id);
        itemRepository.updateHeatScore(id);
        item = itemRepository.findById(id).orElse(item);
        SecondhandDTO.ItemDetailVO vo = toItemDetailVO(item);
        if (currentUserId != null) {
            vo.setIsFavorited(favoriteRepository.existsByUserIdAndItemId(currentUserId, id));
        } else {
            vo.setIsFavorited(false);
        }
        return vo;
    }

    @Override
    public SecondhandDTO.ItemVO createItem(SecondhandDTO.ItemRequest req, Long userId) {
        SecondhandItem item = new SecondhandItem();
        item.setUserId(userId);
        item.setCategoryId(req.getCategoryId());
        item.setTitle(req.getTitle());
        item.setDescription(req.getDescription());
        item.setImages(toJson(req.getImages()));
        item.setPrice(req.getPrice());
        item.setOriginalPrice(req.getOriginalPrice());
        item.setCondition(req.getCondition());
        item.setLocation(req.getLocation());
        item.setCampusId(req.getCampusId());
        item.setCampusName(req.getCampusName());
        item.setTradeLocation(req.getTradeLocation());
        item.setPickupPoint(req.getPickupPoint());
        item.setTradeType(req.getTradeType() != null ? req.getTradeType() : "sell");
        item.setStatus(2);
        item = itemRepository.save(item);
        return toItemVO(item);
    }

    @Override
    public void updateItem(Long id, SecondhandDTO.ItemRequest req, Long userId) {
        SecondhandItem item = itemRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "物品不存在"));
        if (!item.getUserId().equals(userId))
            throw new BusinessException(403, "无权限");
        if (item.getStatus() == 3)
            throw new BusinessException(400, "已售出的物品不可编辑");
        if (req.getCategoryId() != null) item.setCategoryId(req.getCategoryId());
        if (req.getTitle() != null) item.setTitle(req.getTitle());
        if (req.getDescription() != null) item.setDescription(req.getDescription());
        if (req.getImages() != null) item.setImages(toJson(req.getImages()));
        if (req.getPrice() != null) item.setPrice(req.getPrice());
        if (req.getOriginalPrice() != null) item.setOriginalPrice(req.getOriginalPrice());
        if (req.getCondition() != null) item.setCondition(req.getCondition());
        if (req.getLocation() != null) item.setLocation(req.getLocation());
        if (req.getCampusId() != null) item.setCampusId(req.getCampusId());
        if (req.getCampusName() != null) item.setCampusName(req.getCampusName());
        if (req.getTradeLocation() != null) item.setTradeLocation(req.getTradeLocation());
        if (req.getPickupPoint() != null) item.setPickupPoint(req.getPickupPoint());
        if (req.getTradeType() != null) item.setTradeType(req.getTradeType());
        itemRepository.save(item);
    }

    @Override
    public void deleteItem(Long id, Long userId, boolean isAdmin) {
        SecondhandItem item = itemRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "物品不存在"));
        if (!isAdmin && !item.getUserId().equals(userId))
            throw new BusinessException(403, "无权限");
        deleteSecondhandItemWithRelations(id);
    }

    @Override
    public PageResponse<SecondhandDTO.ItemVO> getMyItems(Long userId, Integer current, Integer size, Integer status) {
        if (current == null) current = 1;
        if (size == null) size = 10;
        Page<SecondhandItem> page;
        if (status == null) {
            page = itemRepository.findByUserId(userId, PageRequest.of(current - 1, size, Sort.by(Sort.Direction.DESC, "id")));
        } else {
            page = itemRepository.findByUserIdAndStatus(userId, status, PageRequest.of(current - 1, size, Sort.by(Sort.Direction.DESC, "id")));
        }
        List<SecondhandDTO.ItemVO> records = page.getContent().stream().map(this::toItemVO).collect(Collectors.toList());
        return new PageResponse<>(records, page.getTotalElements(), current, size);
    }

    @Override
    public void offlineItem(Long id, Long userId, boolean isAdmin) {
        SecondhandItem item = itemRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "物品不存在"));
        if (!isAdmin && !item.getUserId().equals(userId)) throw new BusinessException(403, "无权限");
        if (item.getStatus() != 2) throw new BusinessException(400, "只有在售物品才能下架");
        item.setStatus(4);
        itemRepository.save(item);
    }

    @Override
    public void onlineItem(Long id, Long userId, boolean isAdmin) {
        SecondhandItem item = itemRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "物品不存在"));
        if (!isAdmin && !item.getUserId().equals(userId)) throw new BusinessException(403, "无权限");
        if (item.getStatus() != 4) throw new BusinessException(400, "只有已下架物品才能重新上架");
        item.setStatus(2);
        itemRepository.save(item);
    }

    @Override
    public void soldItem(Long id, Long userId, boolean isAdmin) {
        SecondhandItem item = itemRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "物品不存在"));
        if (!isAdmin && !item.getUserId().equals(userId)) throw new BusinessException(403, "无权限");
        item.setStatus(3);
        itemRepository.save(item);
    }

    @Override
    public PageResponse<SecondhandDTO.ItemVO> getAdminList(Integer current, Integer size, String keyword,
                                                             Long categoryId, Integer status, String tradeType, Long userId) {
        if (current == null) current = 1;
        if (size == null) size = 10;
        String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        PageRequest pageRequest = PageRequest.of(current - 1, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<SecondhandItem> page = itemRepository.findAdminList(status, categoryId, userId, tradeType, normalizedKeyword, pageRequest);
        List<SecondhandDTO.ItemVO> records = page.getContent().stream()
                .map(this::toItemVO).collect(Collectors.toList());
        return new PageResponse<>(records, page.getTotalElements(), current, size);
    }

    @Override
    public void batchOperation(SecondhandDTO.BatchRequest req) {
        List<SecondhandItem> items = itemRepository.findByIdIn(req.getIds());
        if (items.isEmpty()) throw new BusinessException(404, "没有找到对应的物品");
        if ("offline".equals(req.getAction())) {
            for (SecondhandItem item : items) {
                if (item.getStatus() == 2) {
                    item.setStatus(4);
                    itemRepository.save(item);
                }
            }
        } else if ("delete".equals(req.getAction())) {
            for (SecondhandItem item : items) {
                deleteSecondhandItemWithRelations(item.getId());
            }
        } else {
            throw new BusinessException(400, "不支持的操作类型");
        }
    }

    // ========== 收藏 ==========

    @Override
    public void favoriteItem(Long itemId, Long userId) {
        if (!itemRepository.existsById(itemId))
            throw new BusinessException(404, "物品不存在");
        SecondhandItem item = itemRepository.findById(itemId).get();
        if (item.getUserId().equals(userId))
            throw new BusinessException(400, "不能收藏自己的物品");
        if (favoriteRepository.existsByUserIdAndItemId(userId, itemId))
            throw new BusinessException(400, "已收藏过该物品");
        SecondhandFavorite f = new SecondhandFavorite();
        f.setUserId(userId);
        f.setItemId(itemId);
        favoriteRepository.save(f);
        itemRepository.updateFavoriteCount(itemId, 1);
        itemRepository.updateHeatScore(itemId);
    }

    @Override
    public void unfavoriteItem(Long itemId, Long userId) {
        SecondhandFavorite f = favoriteRepository.findByUserIdAndItemId(userId, itemId)
                .orElseThrow(() -> new BusinessException(404, "收藏记录不存在"));
        favoriteRepository.delete(f);
        itemRepository.updateFavoriteCount(itemId, -1);
        itemRepository.updateHeatScore(itemId);
    }

    @Override
    public PageResponse<SecondhandDTO.ItemVO> getMyFavorites(Long userId, Integer current, Integer size) {
        if (current == null) current = 1;
        if (size == null) size = 10;
        Page<SecondhandFavorite> page = favoriteRepository.findByUserId(userId, PageRequest.of(current - 1, size));
        List<SecondhandDTO.ItemVO> records = page.getContent().stream()
                .map(f -> itemRepository.findById(f.getItemId()).orElse(null))
                .filter(Objects::nonNull)
                .filter(item -> !Integer.valueOf(4).equals(item.getStatus()))
                .map(this::toItemVO)
                .collect(Collectors.toList());
        return new PageResponse<>(records, page.getTotalElements(), current, size);
    }

    // ========== 统计 ==========

    @Override
    public SecondhandDTO.StatisticsVO getStatistics(String startDate, String endDate) {
        SecondhandDTO.StatisticsVO vo = new SecondhandDTO.StatisticsVO();
        vo.setTotalItems(itemRepository.count());
        vo.setOnSaleItems(itemRepository.countOnSale());
        vo.setSoldItems(itemRepository.countSold());
        vo.setOfflineItems(itemRepository.countOffline());

        LocalDateTime start = parseDate(startDate);
        LocalDateTime end = parseDate(endDate).withHour(23).withMinute(59).withSecond(59);
        List<CountItem> trend = new ArrayList<>();
        LocalDate cursor = start.toLocalDate();
        LocalDate endD = end.toLocalDate();
        while (!cursor.isAfter(endD)) {
            LocalDateTime dayStart = cursor.atStartOfDay();
            LocalDateTime dayEnd = cursor.atTime(LocalTime.MAX);
            long count = itemRepository.countByDateRange(dayStart, dayEnd);
            CountItem item = new CountItem();
            item.setName(cursor.toString());
            item.setValue((int) count);
            trend.add(item);
            cursor = cursor.plusDays(1);
        }
        vo.setDailyPublishTrend(trend);

        List<SecondhandCategory> categories = categoryRepository.findAll();
        List<CountItem> dist = new ArrayList<>();
        for (SecondhandCategory cat : categories) {
            long cnt = categoryRepository.countByCategoryId(cat.getId());
            CountItem item = new CountItem();
            item.setName(cat.getCategoryName());
            item.setValue((int) cnt);
            dist.add(item);
        }
        vo.setCategoryDistribution(dist);
        return vo;
    }

    // ========== 工具方法 ==========

    private SecondhandDTO.ItemVO toItemVO(SecondhandItem item) {
        SecondhandDTO.ItemVO vo = new SecondhandDTO.ItemVO();
        vo.setId(item.getId());
        vo.setUserId(item.getUserId());
        vo.setCategoryId(item.getCategoryId());
        if (item.getCategory() != null) vo.setCategoryName(item.getCategory().getCategoryName());
        vo.setTitle(item.getTitle());
        vo.setDescription(item.getDescription());
        vo.setImages(fromJson(item.getImages()));
        vo.setPrice(item.getPrice());
        vo.setOriginalPrice(item.getOriginalPrice());
        vo.setCondition(item.getCondition());
        vo.setConditionText(getConditionText(item.getCondition()));
        vo.setLocation(item.getLocation());
        vo.setCampusId(item.getCampusId());
        vo.setCampusName(item.getCampusName());
        vo.setTradeLocation(item.getTradeLocation());
        vo.setPickupPoint(item.getPickupPoint());
        vo.setTradeType(item.getTradeType());
        vo.setViewCount(item.getViewCount());
        vo.setFavoriteCount(item.getFavoriteCount());
        vo.setInquiryCount(item.getInquiryCount());
        vo.setHeatScore(item.getHeatScore());
        vo.setStatus(item.getStatus());
        vo.setStatusText(getStatusText(item.getStatus()));
        vo.setCreateTime(item.getCreateTime() != null ? item.getCreateTime().format(FMT) : null);
        if (item.getUser() != null) {
            SecondhandDTO.SellerVO seller = new SecondhandDTO.SellerVO();
            seller.setId(item.getUser().getId());
            seller.setUsername(item.getUser().getUsername());
            seller.setAvatar(item.getUser().getAvatar());
            vo.setSeller(seller);
        }
        return vo;
    }

    private SecondhandDTO.ItemDetailVO toItemDetailVO(SecondhandItem item) {
        SecondhandDTO.ItemDetailVO vo = new SecondhandDTO.ItemDetailVO();
        copyItemVO(item, vo);
        vo.setUpdateTime(item.getUpdateTime() != null ? item.getUpdateTime().format(FMT) : null);
        if (item.getUser() != null) {
            SecondhandDTO.SellerVO seller = new SecondhandDTO.SellerVO();
            seller.setId(item.getUser().getId());
            seller.setUsername(item.getUser().getUsername());
            seller.setAvatar(item.getUser().getAvatar());
            seller.setPhone(maskPhone(item.getUser().getPhone()));
            vo.setSeller(seller);
        }
        return vo;
    }

    private void copyItemVO(SecondhandItem item, SecondhandDTO.ItemVO vo) {
        vo.setId(item.getId());
        vo.setUserId(item.getUserId());
        vo.setCategoryId(item.getCategoryId());
        if (item.getCategory() != null) vo.setCategoryName(item.getCategory().getCategoryName());
        vo.setTitle(item.getTitle());
        vo.setDescription(item.getDescription());
        vo.setImages(fromJson(item.getImages()));
        vo.setPrice(item.getPrice());
        vo.setOriginalPrice(item.getOriginalPrice());
        vo.setCondition(item.getCondition());
        vo.setConditionText(getConditionText(item.getCondition()));
        vo.setLocation(item.getLocation());
        vo.setCampusId(item.getCampusId());
        vo.setCampusName(item.getCampusName());
        vo.setTradeLocation(item.getTradeLocation());
        vo.setPickupPoint(item.getPickupPoint());
        vo.setTradeType(item.getTradeType());
        vo.setViewCount(item.getViewCount());
        vo.setFavoriteCount(item.getFavoriteCount());
        vo.setInquiryCount(item.getInquiryCount());
        vo.setHeatScore(item.getHeatScore());
        vo.setStatus(item.getStatus());
        vo.setStatusText(getStatusText(item.getStatus()));
        vo.setCreateTime(item.getCreateTime() != null ? item.getCreateTime().format(FMT) : null);
    }

    private String getConditionText(Integer condition) {
        if (condition == null) return "";
        switch (condition) {
            case 1: return "全新";
            case 2: return "几乎全新";
            case 3: return "轻微使用痕迹";
            case 4: return "明显使用痕迹";
            case 5: return "仅限零件";
            default: return "";
        }
    }

    private String getStatusText(Integer status) {
        if (status == null) return "";
        switch (status) {
            case 2: return "在售";
            case 3: return "已售出";
            case 4: return "已下架";
            default: return "";
        }
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
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
        Sort.Direction direction = Sort.Direction.DESC;
        String sortField = "id";
        if ("price_asc".equals(sort)) { sortField = "price"; direction = Sort.Direction.ASC; }
        else if ("price_desc".equals(sort)) { sortField = "price"; direction = Sort.Direction.DESC; }
        else if ("hot".equals(sort)) { sortField = "heatScore"; direction = Sort.Direction.DESC; }
        else if ("latest".equals(sort) || sort == null) { sortField = "id"; direction = Sort.Direction.DESC; }
        return PageRequest.of(current - 1, size, Sort.by(direction, sortField));
    }

    private LocalDateTime parseDate(String date) {
        if (date == null || date.isEmpty()) {
            return LocalDateTime.now().minusDays(30);
        }
        return LocalDateTime.parse(date + " 00:00:00", FMT);
    }
}
