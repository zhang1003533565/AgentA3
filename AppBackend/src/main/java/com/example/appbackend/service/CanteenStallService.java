package com.example.appbackend.service;

import com.example.appbackend.dto.CanteenStallDTO;
import com.example.appbackend.dto.PageResponse;

import java.util.List;

public interface CanteenStallService {

    List<CanteenStallDTO> getStallListByRestaurantId(Long restaurantId);

    List<CanteenStallDTO> getAllStalls();

    CanteenStallDTO getStallById(Long id);

    CanteenStallDTO createStall(CanteenStallDTO request);

    CanteenStallDTO updateStall(Long id, CanteenStallDTO request);

    void deleteStall(Long id);

    List<CanteenStallDTO> getStallsByCategory(String category);

    List<CanteenStallDTO> getStallsByFloor(String floor);
}