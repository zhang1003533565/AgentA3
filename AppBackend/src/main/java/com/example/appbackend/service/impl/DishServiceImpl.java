package com.example.appbackend.service.impl;

import com.example.appbackend.dto.DishDTO;
import com.example.appbackend.entity.CanteenStall;
import com.example.appbackend.entity.Dish;
import com.example.appbackend.entity.DishCuisine;
import com.example.appbackend.entity.MapPlace;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.CanteenStallRepository;
import com.example.appbackend.repository.DishCuisineRepository;
import com.example.appbackend.repository.DishRepository;
import com.example.appbackend.repository.MapPlaceRepository;
import com.example.appbackend.service.DishService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DishServiceImpl implements DishService {

    @Autowired
    private DishRepository dishRepository;

    @Autowired
    private CanteenStallRepository canteenStallRepository;

    @Autowired
    private DishCuisineRepository dishCuisineRepository;

    @Autowired
    private MapPlaceRepository mapPlaceRepository;

    @Override
    public List<DishDTO> getDishesByStallId(Long stallId) {
        return dishRepository.findByStallIdAndIsAvailable(stallId, true).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<DishDTO> getDishesByStallPlaceId(Long stallPlaceId) {
        requireStallPlace(stallPlaceId);
        return dishRepository.findByStallPlaceId(stallPlaceId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<DishDTO> getDishesByFloorPlaceId(Long floorPlaceId) {
        MapPlace floor = mapPlaceRepository.findById(floorPlaceId)
                .orElseThrow(() -> new BusinessException(404, "楼层不存在"));
        if (!"FLOOR".equals(floor.getPlaceType()) || !"CANTEEN".equals(floor.getSceneType())) {
            throw new BusinessException(400, "所选点位不是食堂楼层");
        }
        return dishRepository.findAvailableByFloorPlaceId(floorPlaceId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<DishDTO> getAllDishes() {
        return dishRepository.findAll().stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Override
    public DishDTO getDishById(Long id) {
        return convertToDTO(dishRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "菜品不存在")));
    }

    @Override
    public DishDTO createDish(DishDTO request) {
        Dish dish = new Dish();
        BeanUtils.copyProperties(request, dish);
        validateStallReference(dish);
        applyCuisine(dish, request);
        return convertToDTO(dishRepository.save(dish));
    }

    @Override
    public DishDTO updateDish(Long id, DishDTO request) {
        Dish dish = dishRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "菜品不存在"));
        if (request.getName() != null) dish.setName(request.getName());
        if (request.getPrice() != null) dish.setPrice(BigDecimal.valueOf(request.getPrice().doubleValue()));
        if (request.getCategory() != null) dish.setCategory(request.getCategory());
        if (request.getImageUrl() != null) dish.setImageUrl(request.getImageUrl());
        if (request.getRating() != null) dish.setRating(BigDecimal.valueOf(request.getRating().doubleValue()));
        if (request.getSoldCount() != null) dish.setSoldCount(request.getSoldCount());
        if (request.getIsAvailable() != null) dish.setIsAvailable(request.getIsAvailable());
        if (request.getTaste() != null) dish.setTaste(request.getTaste());
        if (request.getDescription() != null) dish.setDescription(request.getDescription());
        if (request.getStallPlaceId() != null) {
            dish.setStallPlaceId(request.getStallPlaceId());
            dish.setStallId(null);
        }
        validateStallReference(dish);
        applyCuisine(dish, request);
        return convertToDTO(dishRepository.save(dish));
    }

    @Override
    public void deleteDish(Long id) {
        if (!dishRepository.existsById(id)) throw new BusinessException(404, "菜品不存在");
        dishRepository.deleteById(id);
    }

    @Override
    public List<DishDTO> getDishesByCategory(String category) {
        return dishRepository.findByCategory(category).stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Override
    public List<DishDTO> getDishesByTaste(String taste) {
        return dishRepository.findByTaste(taste).stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Override
    public List<DishDTO> searchDishesByName(String keyword) {
        return dishRepository.findByNameContaining(keyword).stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    private DishDTO convertToDTO(Dish dish) {
        DishDTO dto = new DishDTO();
        BeanUtils.copyProperties(dish, dto);
        if (dish.getStallPlaceId() != null) {
            mapPlaceRepository.findById(dish.getStallPlaceId()).ifPresent(place -> dto.setStallName(place.getName()));
        } else if (dish.getStallId() != null) {
            CanteenStall stall = canteenStallRepository.findById(dish.getStallId()).orElse(null);
            if (stall != null) dto.setStallName(stall.getStallName());
        }
        return dto;
    }

    private void validateStallReference(Dish dish) {
        if (dish.getStallPlaceId() != null) {
            requireStallPlace(dish.getStallPlaceId());
            return;
        }
        if (dish.getStallId() == null || !canteenStallRepository.existsById(dish.getStallId())) {
            throw new BusinessException(404, "所属档口不存在");
        }
    }

    private void applyCuisine(Dish dish, DishDTO request) {
        if (request.getCuisineId() != null) {
            if (dish.getStallPlaceId() == null) {
                throw new BusinessException(400, "旧档口数据不能绑定点位菜系");
            }
            DishCuisine cuisine = dishCuisineRepository.findById(request.getCuisineId())
                    .orElseThrow(() -> new BusinessException(404, "所选菜系不存在"));
            Long canteenPlaceId = findCanteenPlaceId(requireStallPlace(dish.getStallPlaceId()));
            if (!canteenPlaceId.equals(cuisine.getCanteenPlaceId())) {
                throw new BusinessException(400, "所选菜系不属于当前食堂");
            }
            dish.setCuisineId(cuisine.getId());
            dish.setCategory(cuisine.getCuisineName());
        } else if (request.getCategory() != null && !request.getCategory().isBlank()) {
            dish.setCategory(request.getCategory().trim());
        }
    }

    private MapPlace requireStallPlace(Long id) {
        MapPlace place = mapPlaceRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "所属档口点位不存在"));
        if (!"CANTEEN_STALL".equals(place.getPlaceType())) {
            throw new BusinessException(400, "所选点位不是食堂档口");
        }
        return place;
    }

    private Long findCanteenPlaceId(MapPlace stall) {
        MapPlace cursor = stall;
        while (cursor.getParentId() != null) {
            cursor = mapPlaceRepository.findById(cursor.getParentId())
                    .orElseThrow(() -> new BusinessException(404, "档口上级点位不存在"));
        }
        if (!"CANTEEN".equals(cursor.getPlaceType())) {
            throw new BusinessException(400, "档口不属于食堂点位");
        }
        return cursor.getId();
    }
}
