package com.example.appbackend.service.impl;

import com.example.appbackend.dto.DishCuisineRequest;
import com.example.appbackend.entity.DishCuisine;
import com.example.appbackend.entity.MapPlace;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.DishCuisineRepository;
import com.example.appbackend.repository.DishRepository;
import com.example.appbackend.repository.MapPlaceRepository;
import com.example.appbackend.service.DishCuisineService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class DishCuisineServiceImpl implements DishCuisineService {

    private final DishCuisineRepository cuisineRepository;
    private final DishRepository dishRepository;
    private final MapPlaceRepository mapPlaceRepository;

    public DishCuisineServiceImpl(
            DishCuisineRepository cuisineRepository,
            DishRepository dishRepository,
            MapPlaceRepository mapPlaceRepository) {
        this.cuisineRepository = cuisineRepository;
        this.dishRepository = dishRepository;
        this.mapPlaceRepository = mapPlaceRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DishCuisine> list(Long canteenPlaceId) {
        requireCanteen(canteenPlaceId);
        return cuisineRepository.findByCanteenPlaceIdOrderBySortOrderAscIdAsc(canteenPlaceId);
    }

    @Override
    public DishCuisine create(DishCuisineRequest request) {
        requireCanteen(request.getCanteenPlaceId());
        String name = normalize(request.getName());
        cuisineRepository.findByCanteenPlaceIdAndCuisineName(request.getCanteenPlaceId(), name)
                .ifPresent(item -> {
                    throw new BusinessException(400, "该食堂已存在同名菜系");
                });
        DishCuisine cuisine = new DishCuisine();
        cuisine.setCanteenPlaceId(request.getCanteenPlaceId());
        apply(cuisine, request, name);
        return cuisineRepository.save(cuisine);
    }

    @Override
    public DishCuisine update(Long id, DishCuisineRequest request) {
        DishCuisine cuisine = cuisineRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "菜系不存在"));
        requireCanteen(request.getCanteenPlaceId());
        String name = normalize(request.getName());
        if (cuisineRepository.existsByCanteenPlaceIdAndCuisineNameAndIdNot(
                request.getCanteenPlaceId(), name, id)) {
            throw new BusinessException(400, "该食堂已存在同名菜系");
        }
        cuisine.setCanteenPlaceId(request.getCanteenPlaceId());
        apply(cuisine, request, name);
        DishCuisine saved = cuisineRepository.save(cuisine);
        dishRepository.findByCuisineId(id).forEach(dish -> dish.setCategory(saved.getCuisineName()));
        return saved;
    }

    @Override
    public void delete(Long id) {
        DishCuisine cuisine = cuisineRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "菜系不存在"));
        if (dishRepository.existsByCuisineId(id)) {
            throw new BusinessException(400, "该菜系仍有关联菜品，请先调整菜品或停用该菜系");
        }
        cuisineRepository.delete(cuisine);
    }

    private void requireCanteen(Long id) {
        MapPlace place = mapPlaceRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "所属食堂点位不存在"));
        if (place.getParentId() != null || !"CANTEEN".equals(place.getPlaceType())) {
            throw new BusinessException(400, "所选点位不是食堂");
        }
    }

    private void apply(DishCuisine cuisine, DishCuisineRequest request, String name) {
        cuisine.setCuisineName(name);
        cuisine.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        cuisine.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
    }

    private String normalize(String name) {
        return name == null ? "" : name.trim();
    }
}
