package com.example.appbackend.service.impl;

import com.example.appbackend.dto.DishReviewDTO;
import com.example.appbackend.dto.DishReviewSummaryDTO;
import com.example.appbackend.entity.DishReview;
import com.example.appbackend.entity.Dish;
import com.example.appbackend.entity.CanteenStall;
import com.example.appbackend.entity.User;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.DishReviewRepository;
import com.example.appbackend.repository.DishRepository;
import com.example.appbackend.repository.CanteenStallRepository;
import com.example.appbackend.repository.UserRepository;
import com.example.appbackend.service.DishReviewService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DishReviewServiceImpl implements DishReviewService {

    @Autowired
    private DishReviewRepository dishReviewRepository;

    @Autowired
    private DishRepository dishRepository;

    @Autowired
    private CanteenStallRepository canteenStallRepository;

    @Autowired
    private UserRepository userRepository;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public List<DishReviewDTO> getReviewsByDishId(Long dishId) {
        List<DishReview> reviews = dishReviewRepository.findByDishIdAndStatus(dishId, 1);
        return reviews.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Override
    public List<DishReviewDTO> getReviewsByStallId(Long stallId) {
        List<DishReview> reviews = dishReviewRepository.findByStallIdAndStatus(stallId, 1);
        return reviews.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Override
    public DishReviewDTO getReviewById(Long id) {
        DishReview review = dishReviewRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "评价不存在"));
        return convertToDTO(review);
    }

    @Override
    public DishReviewDTO createReview(Long userId, DishReviewDTO request) {
        // 验证菜品是否存在
        Dish dish = dishRepository.findById(request.getDishId())
                .orElseThrow(() -> new BusinessException(404, "菜品不存在"));

        DishReview existingReview = dishReviewRepository.findByDishIdAndUserId(request.getDishId(), userId);
        if (existingReview != null && existingReview.getStatus() != null && existingReview.getStatus() == 1) {
            throw new BusinessException(400, "您已评价过该菜品");
        }

        DishReview review = new DishReview();
        review.setDishId(request.getDishId());
        review.setUserId(userId);
        review.setStallId(dish.getStallId());
        review.setRating(request.getRating());
        review.setContent(request.getContent());
        review.setImages(request.getImages());
        review.setIsAnonymous(request.getIsAnonymous() != null ? request.getIsAnonymous() : false);
        review.setHelpfulCount(0);
        review.setReplyCount(0);
        review.setStatus(1);

        dishReviewRepository.save(review);

        // 更新菜品评分
        updateDishRating(request.getDishId());

        return convertToDTO(review);
    }

    @Override
    public DishReviewDTO updateReview(Long id, Long userId, DishReviewDTO request) {
        DishReview review = dishReviewRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "评价不存在"));

        // 验证权限
        if (!review.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权限修改该评价");
        }

        if (request.getRating() != null) {
            review.setRating(request.getRating());
        }
        if (request.getContent() != null) {
            review.setContent(request.getContent());
        }
        if (request.getImages() != null) {
            review.setImages(request.getImages());
        }
        if (request.getIsAnonymous() != null) {
            review.setIsAnonymous(request.getIsAnonymous());
        }

        dishReviewRepository.save(review);

        // 更新菜品评分
        updateDishRating(review.getDishId());

        return convertToDTO(review);
    }

    @Override
    public void deleteReview(Long id, Long userId) {
        DishReview review = dishReviewRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "评价不存在"));

        // 验证权限
        if (!review.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权限删除该评价");
        }

        Long dishId = review.getDishId();

        // 软删除：将状态设置为 2（已删除）
        review.setStatus(2);
        dishReviewRepository.save(review);

        // 更新菜品评分
        updateDishRating(dishId);
    }

    @Override
    public int countByDishId(Long dishId) {
        return dishReviewRepository.countByDishIdAndStatus(dishId, 1);
    }

    @Override
    public int countByStallId(Long stallId) {
        return dishReviewRepository.countByStallIdAndStatus(stallId, 1);
    }

    @Override
    public DishReviewSummaryDTO getSummaryByDishId(Long dishId) {
        return buildSummary(dishReviewRepository.findByDishIdAndStatus(dishId, 1));
    }

    @Override
    public DishReviewSummaryDTO getSummaryByStallId(Long stallId) {
        return buildSummary(dishReviewRepository.findByStallIdAndStatus(stallId, 1));
    }

    private DishReviewDTO convertToDTO(DishReview review) {
        DishReviewDTO dto = new DishReviewDTO();
        BeanUtils.copyProperties(review, dto);

        // 获取菜品名称
        if (review.getDishId() != null) {
            Dish dish = dishRepository.findById(review.getDishId()).orElse(null);
            if (dish != null) {
                dto.setDishName(dish.getName());
            }
        }

        // 获取档口名称
        if (review.getStallId() != null) {
            CanteenStall stall = canteenStallRepository.findById(review.getStallId()).orElse(null);
            if (stall != null) {
                dto.setStallName(stall.getStallName());
            }
        }

        if (review.getUserId() != null) {
            User user = userRepository.findById(review.getUserId()).orElse(null);
            if (user != null) {
                dto.setUserName(user.getRealName() != null && !user.getRealName().isBlank() ? user.getRealName() : user.getUsername());
                dto.setUserAvatar(user.getAvatar());
            }
        }

        if (review.getCreateTime() != null) {
            dto.setCreateTime(review.getCreateTime().format(DATE_TIME_FORMATTER));
        }

        return dto;
    }

    private DishReviewSummaryDTO buildSummary(List<DishReview> reviews) {
        DishReviewSummaryDTO summary = new DishReviewSummaryDTO();
        int totalCount = reviews.size();
        int recommendCount = (int) reviews.stream()
                .filter(review -> review.getRating() != null && review.getRating().compareTo(BigDecimal.valueOf(4)) >= 0)
                .count();
        int neutralCount = (int) reviews.stream()
                .filter(review -> review.getRating() != null && review.getRating().compareTo(BigDecimal.valueOf(3)) == 0)
                .count();
        int avoidCount = (int) reviews.stream()
                .filter(review -> review.getRating() != null && review.getRating().compareTo(BigDecimal.valueOf(3)) < 0)
                .count();
        int recommendRate = totalCount == 0 ? 0 : Math.round((recommendCount * 100.0f) / totalCount);

        summary.setTotalCount(totalCount);
        summary.setRecommendCount(recommendCount);
        summary.setNeutralCount(neutralCount);
        summary.setAvoidCount(avoidCount);
        summary.setRecommendRate(recommendRate);
        return summary;
    }

    /**
     * 根据用户评价更新菜品评分
     * 计算该菜品所有有效评价的平均分，并更新到 dish 表
     */
    private void updateDishRating(Long dishId) {
        List<DishReview> reviews = dishReviewRepository.findByDishIdAndStatus(dishId, 1);
        if (reviews.isEmpty()) {
            return;
        }

        // 计算平均分
        BigDecimal totalRating = reviews.stream()
                .map(DishReview::getRating)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal avgRating = totalRating.divide(
                BigDecimal.valueOf(reviews.size()),
                2,
                RoundingMode.HALF_UP
        );

        // 更新菜品评分
        Dish dish = dishRepository.findById(dishId).orElse(null);
        if (dish != null) {
            dish.setRating(avgRating);
            dishRepository.save(dish);
        }
    }
}
