package com.example.appbackend.service.impl;

import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.entity.Activity;
import com.example.appbackend.entity.Favorite;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.ActivityRepository;
import com.example.appbackend.repository.FavoriteRepository;
import com.example.appbackend.repository.UserRepository;
import com.example.appbackend.service.FavoriteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Transactional
public class FavoriteServiceImpl implements FavoriteService {

    @Autowired
    private FavoriteRepository favoriteRepository;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private UserRepository userRepository;

    private void checkUser(Long userId) {
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(401, "用户不存在");
        }
    }

    @Override
    public void addFavorite(Long userId, Long activityId) {
        checkUser(userId);
        if (favoriteRepository.existsByUserIdAndActivityId(userId, activityId)) {
            throw new BusinessException(400, "已经收藏过该活动");
        }
        if (!activityRepository.existsById(activityId)) {
            throw new BusinessException(404, "活动不存在");
        }
        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setActivityId(activityId);
        favoriteRepository.save(favorite);
    }

    @Override
    public void removeFavorite(Long userId, Long activityId) {
        checkUser(userId);
        if (!favoriteRepository.existsByUserIdAndActivityId(userId, activityId)) {
            throw new BusinessException(400, "未收藏该活动");
        }
        favoriteRepository.deleteByUserIdAndActivityId(userId, activityId);
    }

    @Override
    public boolean isFavorited(Long userId, Long activityId) {
        checkUser(userId);
        return favoriteRepository.existsByUserIdAndActivityId(userId, activityId);
    }

    @Override
    public PageResponse<Activity> getUserFavorites(Long userId, Integer page, Integer size) {
        checkUser(userId);
        PageRequest pageRequest = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createTime"));
        Page<Favorite> favoritePage = favoriteRepository.findByUserId(userId, pageRequest);
        List<Activity> activities = favoritePage.getContent().stream()
            .map(favorite -> activityRepository.findById(favorite.getActivityId()).orElse(null))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        return new PageResponse<>(
            activities,
            favoritePage.getTotalElements(),
            page,
            size
        );
    }

    @Override
    public long getFavoriteCount(Long activityId) {
        return favoriteRepository.countByActivityId(activityId);
    }
}
