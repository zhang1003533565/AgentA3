package com.example.appbackend.service.impl;

import com.example.appbackend.dto.DishDTO;
import com.example.appbackend.entity.Dish;
import com.example.appbackend.entity.CanteenStall;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.DishRepository;
import com.example.appbackend.repository.CanteenStallRepository;
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

    @Override
    public List<DishDTO> getDishesByStallId(Long stallId) {
        List<Dish> dishes = dishRepository.findByStallIdAndIsAvailable(stallId, true);
        return dishes.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Override
    public List<DishDTO> getAllDishes() {
        List<Dish> dishes = dishRepository.findAll();
        return dishes.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Override
    public DishDTO getDishById(Long id) {
        Dish dish = dishRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "菜品不存在"));
        return convertToDTO(dish);
    }

    @Override
    public DishDTO createDish(DishDTO request) {
        Dish dish = new Dish();
        BeanUtils.copyProperties(request, dish);

        // 验证档口是否存在
        if (!canteenStallRepository.existsById(request.getStallId())) {
            throw new BusinessException(404, "所属档口不存在");
        }

        dishRepository.save(dish);
        return convertToDTO(dish);
    }

    @Override
    public DishDTO updateDish(Long id, DishDTO request) {
        Dish dish = dishRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "菜品不存在"));

        if (request.getName() != null) {
            dish.setName(request.getName());
        }
        if (request.getPrice() != null) {
            dish.setPrice(BigDecimal.valueOf(request.getPrice().doubleValue()));
        }
        if (request.getCategory() != null) {
            dish.setCategory(request.getCategory());
        }
        if (request.getImageUrl() != null) {
            dish.setImageUrl(request.getImageUrl());
        }
        if (request.getRating() != null) {
            dish.setRating(BigDecimal.valueOf(request.getRating().doubleValue()));
        }
        if (request.getSoldCount() != null) {
            dish.setSoldCount(request.getSoldCount());
        }
        if (request.getIsAvailable() != null) {
            dish.setIsAvailable(request.getIsAvailable());
        }
        if (request.getTaste() != null) {
            dish.setTaste(request.getTaste());
        }
        if (request.getDescription() != null) {
            dish.setDescription(request.getDescription());
        }

        dishRepository.save(dish);
        return convertToDTO(dish);
    }

    @Override
    public void deleteDish(Long id) {
        if (!dishRepository.existsById(id)) {
            throw new BusinessException(404, "菜品不存在");
        }
        dishRepository.deleteById(id);
    }

    @Override
    public List<DishDTO> getDishesByCategory(String category) {
        List<Dish> dishes = dishRepository.findByCategory(category);
        return dishes.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Override
    public List<DishDTO> getDishesByTaste(String taste) {
        List<Dish> dishes = dishRepository.findByTaste(taste);
        return dishes.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Override
    public List<DishDTO> searchDishesByName(String keyword) {
        List<Dish> dishes = dishRepository.findByNameContaining(keyword);
        return dishes.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    private DishDTO convertToDTO(Dish dish) {
        DishDTO dto = new DishDTO();
        BeanUtils.copyProperties(dish, dto);

        // 获取档口名称
        if (dish.getStallId() != null) {
            CanteenStall stall = canteenStallRepository.findById(dish.getStallId()).orElse(null);
            if (stall != null) {
                dto.setStallName(stall.getStallName());
            }
        }

        return dto;
    }
}