package com.example.appbackend.service.impl;

import com.example.appbackend.dto.CanteenStallDTO;
import com.example.appbackend.entity.CanteenStall;
import com.example.appbackend.entity.CampusFacility;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.CanteenStallRepository;
import com.example.appbackend.repository.FacilityRepository;
import com.example.appbackend.service.CanteenStallService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CanteenStallServiceImpl implements CanteenStallService {

    @Autowired
    private CanteenStallRepository canteenStallRepository;

    @Autowired
    private FacilityRepository facilityRepository;

    @Override
    public List<CanteenStallDTO> getStallListByRestaurantId(Long restaurantId) {
        List<CanteenStall> stalls = canteenStallRepository.findByRestaurantIdAndStatus(restaurantId, 1);
        return stalls.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Override
    public List<CanteenStallDTO> getAllStalls() {
        List<CanteenStall> stalls = canteenStallRepository.findAll();
        return stalls.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Override
    public CanteenStallDTO getStallById(Long id) {
        CanteenStall stall = canteenStallRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "档口不存在"));
        return convertToDTO(stall);
    }

    @Override
    public CanteenStallDTO createStall(CanteenStallDTO request) {
        CanteenStall stall = new CanteenStall();
        BeanUtils.copyProperties(request, stall);

        // 验证餐厅是否存在
        CampusFacility restaurant = facilityRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new BusinessException(404, "所属餐厅不存在"));

        canteenStallRepository.save(stall);
        return convertToDTO(stall);
    }

    @Override
    public CanteenStallDTO updateStall(Long id, CanteenStallDTO request) {
        CanteenStall stall = canteenStallRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "档口不存在"));

        if (request.getStallName() != null) {
            stall.setStallName(request.getStallName());
        }
        if (request.getFloor() != null) {
            stall.setFloor(request.getFloor());
        }
        if (request.getCategory() != null) {
            stall.setCategory(request.getCategory());
        }
        if (request.getLocation() != null) {
            stall.setLocation(request.getLocation());
        }
        if (request.getAvgPrice() != null) {
            stall.setAvgPrice(BigDecimal.valueOf(request.getAvgPrice().doubleValue()));
        }
        if (request.getScore() != null) {
            stall.setScore(BigDecimal.valueOf(request.getScore().doubleValue()));
        }
        if (request.getReviewCount() != null) {
            stall.setReviewCount(request.getReviewCount());
        }
        if (request.getRecommendRate() != null) {
            stall.setRecommendRate(request.getRecommendRate());
        }
        if (request.getBusinessHours() != null) {
            stall.setBusinessHours(request.getBusinessHours());
        }
        if (request.getImage() != null) {
            stall.setImage(request.getImage());
        }
        if (request.getDescription() != null) {
            stall.setDescription(request.getDescription());
        }
        if (request.getStatus() != null) {
            stall.setStatus(request.getStatus());
        }
        if (request.getSort() != null) {
            stall.setSort(request.getSort());
        }

        canteenStallRepository.save(stall);
        return convertToDTO(stall);
    }

    @Override
    public void deleteStall(Long id) {
        if (!canteenStallRepository.existsById(id)) {
            throw new BusinessException(404, "档口不存在");
        }
        canteenStallRepository.deleteById(id);
    }

    @Override
    public List<CanteenStallDTO> getStallsByCategory(String category) {
        List<CanteenStall> stalls = canteenStallRepository.findByCategory(category);
        return stalls.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Override
    public List<CanteenStallDTO> getStallsByFloor(String floor) {
        List<CanteenStall> stalls = canteenStallRepository.findByFloor(floor);
        return stalls.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    private CanteenStallDTO convertToDTO(CanteenStall stall) {
        CanteenStallDTO dto = new CanteenStallDTO();
        BeanUtils.copyProperties(stall, dto);

        // 获取餐厅名称
        if (stall.getRestaurantId() != null) {
            CampusFacility restaurant = facilityRepository.findById(stall.getRestaurantId()).orElse(null);
            if (restaurant != null) {
                dto.setRestaurantName(restaurant.getFacilityName());
            }
        }

        return dto;
    }
}