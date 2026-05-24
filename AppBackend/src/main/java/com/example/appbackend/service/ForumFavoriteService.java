package com.example.appbackend.service;

import com.example.appbackend.dto.FavoriteStatusResponse;
import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.dto.PostListItem;

public interface ForumFavoriteService {

    FavoriteStatusResponse toggleFavorite(Long postId, Long userId);

    FavoriteStatusResponse getFavoriteStatus(Long postId, Long userId);

    PageResponse<PostListItem> getMyFavorites(Long userId, Integer pageNum, Integer pageSize);
}
