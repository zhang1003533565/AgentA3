package com.example.appbackend.service;

import com.example.appbackend.dto.FollowListItem;
import com.example.appbackend.dto.FollowRequest;
import com.example.appbackend.dto.FollowStatusResponse;
import com.example.appbackend.dto.PageResponse;

public interface FollowService {

    boolean toggleFollow(FollowRequest request, Long userId);

    PageResponse<FollowListItem> getFollowers(Long userId, Long currentUserId, Integer pageNum, Integer pageSize);

    PageResponse<FollowListItem> getFollowing(Long userId, Long currentUserId, Integer pageNum, Integer pageSize);

    FollowStatusResponse getFollowStatus(Long targetUserId, Long currentUserId);
}
