package com.example.appbackend.service.impl;

import com.example.appbackend.dto.FacilityCategoryRequest;
import com.example.appbackend.dto.FacilityFloorRequest;
import com.example.appbackend.entity.CampusFacility;
import com.example.appbackend.entity.FacilityFloor;
import com.example.appbackend.entity.StallCuisine;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.CanteenStallRepository;
import com.example.appbackend.repository.FacilityRepository;
import com.example.appbackend.repository.FacilityFloorRepository;
import com.example.appbackend.repository.StallCuisineRepository;
import com.example.appbackend.service.DiningCategoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class DiningCategoryServiceImpl implements DiningCategoryService {

    private final FacilityFloorRepository floorRepository;
    private final StallCuisineRepository cuisineRepository;
    private final CanteenStallRepository stallRepository;
    private final FacilityRepository facilityRepository;

    public DiningCategoryServiceImpl(
            FacilityFloorRepository floorRepository,
            StallCuisineRepository cuisineRepository,
            CanteenStallRepository stallRepository,
            FacilityRepository facilityRepository) {
        this.floorRepository = floorRepository;
        this.cuisineRepository = cuisineRepository;
        this.stallRepository = stallRepository;
        this.facilityRepository = facilityRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FacilityFloor> listFloors(Long facilityId) {
        validateFacility(facilityId);
        return floorRepository.findByFacilityIdOrderBySortOrderAscIdAsc(facilityId);
    }

    @Override
    public FacilityFloor createFloor(FacilityFloorRequest request) {
        validateFacility(request.getFacilityId());
        String name = normalizeName(request.getName());
        floorRepository.findByFacilityIdAndFloorName(request.getFacilityId(), name)
                .ifPresent(item -> {
                    throw new BusinessException(400, "该设施已存在同名楼层");
                });
        FacilityFloor floor = new FacilityFloor();
        floor.setFacilityId(request.getFacilityId());
        applyFloor(floor, request, name);
        return floorRepository.save(floor);
    }

    @Override
    public FacilityFloor updateFloor(Long id, FacilityFloorRequest request) {
        FacilityFloor floor = floorRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "楼层分类不存在"));
        validateFacility(request.getFacilityId());
        String name = normalizeName(request.getName());
        if (floorRepository.existsByFacilityIdAndFloorNameAndIdNot(request.getFacilityId(), name, id)) {
            throw new BusinessException(400, "该设施已存在同名楼层");
        }
        floor.setFacilityId(request.getFacilityId());
        applyFloor(floor, request, name);
        FacilityFloor saved = floorRepository.save(floor);
        stallRepository.findByFloorId(id).forEach(stall -> stall.setFloor(saved.getFloorName()));
        return saved;
    }

    @Override
    public void deleteFloor(Long id) {
        FacilityFloor floor = floorRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "楼层分类不存在"));
        if (stallRepository.existsByFloorId(id)) {
            throw new BusinessException(400, "该楼层仍有关联档口，请先调整档口或停用该楼层");
        }
        floorRepository.delete(floor);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StallCuisine> listCuisines(Long restaurantId) {
        validateRestaurant(restaurantId);
        return cuisineRepository.findByRestaurantIdOrderBySortOrderAscIdAsc(restaurantId);
    }

    @Override
    public StallCuisine createCuisine(FacilityCategoryRequest request) {
        validateRestaurant(request.getRestaurantId());
        String name = normalizeName(request.getName());
        cuisineRepository.findByRestaurantIdAndCuisineName(request.getRestaurantId(), name)
                .ifPresent(item -> {
                    throw new BusinessException(400, "该食堂已存在同名菜系");
                });
        StallCuisine cuisine = new StallCuisine();
        cuisine.setRestaurantId(request.getRestaurantId());
        applyCuisine(cuisine, request, name);
        return cuisineRepository.save(cuisine);
    }

    @Override
    public StallCuisine updateCuisine(Long id, FacilityCategoryRequest request) {
        StallCuisine cuisine = cuisineRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "菜系分类不存在"));
        validateRestaurant(request.getRestaurantId());
        String name = normalizeName(request.getName());
        if (cuisineRepository.existsByRestaurantIdAndCuisineNameAndIdNot(request.getRestaurantId(), name, id)) {
            throw new BusinessException(400, "该食堂已存在同名菜系");
        }
        cuisine.setRestaurantId(request.getRestaurantId());
        applyCuisine(cuisine, request, name);
        StallCuisine saved = cuisineRepository.save(cuisine);
        stallRepository.findByCuisineId(id).forEach(stall -> stall.setCategory(saved.getCuisineName()));
        return saved;
    }

    @Override
    public void deleteCuisine(Long id) {
        StallCuisine cuisine = cuisineRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "菜系分类不存在"));
        if (stallRepository.existsByCuisineId(id)) {
            throw new BusinessException(400, "该菜系仍有关联档口，请先调整档口或停用该菜系");
        }
        cuisineRepository.delete(cuisine);
    }

    private void validateRestaurant(Long restaurantId) {
        CampusFacility restaurant = facilityRepository.findById(restaurantId)
                .orElseThrow(() -> new BusinessException(404, "所属食堂不存在"));
        if (!Integer.valueOf(1).equals(restaurant.getFacilityType())) {
            throw new BusinessException(400, "所选校园设施不是食堂");
        }
    }

    private void validateFacility(Long facilityId) {
        facilityRepository.findById(facilityId)
                .orElseThrow(() -> new BusinessException(404, "所属校园设施不存在"));
    }

    private String normalizeName(String name) {
        return name == null ? "" : name.trim();
    }

    private void applyFloor(FacilityFloor floor, FacilityFloorRequest request, String name) {
        floor.setFloorName(name);
        floor.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        floor.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
    }

    private void applyCuisine(StallCuisine cuisine, FacilityCategoryRequest request, String name) {
        cuisine.setCuisineName(name);
        cuisine.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        cuisine.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
    }
}
