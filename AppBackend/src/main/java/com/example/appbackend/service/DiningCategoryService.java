package com.example.appbackend.service;

import com.example.appbackend.dto.FacilityCategoryRequest;
import com.example.appbackend.dto.FacilityFloorRequest;
import com.example.appbackend.entity.FacilityFloor;
import com.example.appbackend.entity.StallCuisine;

import java.util.List;

public interface DiningCategoryService {

    List<FacilityFloor> listFloors(Long facilityId);

    FacilityFloor createFloor(FacilityFloorRequest request);

    FacilityFloor updateFloor(Long id, FacilityFloorRequest request);

    void deleteFloor(Long id);

    List<StallCuisine> listCuisines(Long restaurantId);

    StallCuisine createCuisine(FacilityCategoryRequest request);

    StallCuisine updateCuisine(Long id, FacilityCategoryRequest request);

    void deleteCuisine(Long id);
}
