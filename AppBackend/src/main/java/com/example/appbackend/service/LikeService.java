package com.example.appbackend.service;

import com.example.appbackend.dto.LikeRequest;
import com.example.appbackend.dto.LikeStatusResponse;

public interface LikeService {

    LikeStatusResponse toggleLike(LikeRequest request, Long userId);

    LikeStatusResponse getLikeStatus(Long targetId, String targetType, Long currentUserId);
}
