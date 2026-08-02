package com.example.appbackend.service;

import com.example.appbackend.dto.DishCuisineRequest;
import com.example.appbackend.entity.DishCuisine;

import java.util.List;

public interface DishCuisineService {
    List<DishCuisine> list(Long canteenPlaceId);
    DishCuisine create(DishCuisineRequest request);
    DishCuisine update(Long id, DishCuisineRequest request);
    void delete(Long id);
}
