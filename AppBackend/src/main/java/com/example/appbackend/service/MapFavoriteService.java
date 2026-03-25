package com.example.appbackend.service;

import com.example.appbackend.dto.*;

import java.util.List;

public interface MapFavoriteService {

    FavoriteItem addFavorite(FavoriteRequest request, Long userId);

    List<FavoriteItem> getFavoriteList(Long userId);

    void deleteFavorite(Long id, Long userId);
}
