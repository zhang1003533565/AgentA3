package com.example.appbackend.service.impl;

import com.example.appbackend.dto.CanteenStallDTO;
import com.example.appbackend.entity.CanteenStall;
import com.example.appbackend.entity.FacilityFloor;
import com.example.appbackend.entity.CampusFacility;
import com.example.appbackend.entity.StallCuisine;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.CanteenStallRepository;
import com.example.appbackend.repository.FacilityRepository;
import com.example.appbackend.repository.FacilityFloorRepository;
import com.example.appbackend.repository.StallCuisineRepository;
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

    @Autowired
    private FacilityFloorRepository facilityFloorRepository;

    @Autowired
    private StallCuisineRepository stallCuisineRepository;

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
        facilityRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new BusinessException(404, "所属餐厅不存在"));

        applyDiningCategories(stall, request);
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

        applyDiningCategories(stall, request);
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

    private void applyDiningCategories(CanteenStall stall, CanteenStallDTO request) {
        Long restaurantId = stall.getRestaurantId();

        if (request.getFloorId() != null) {
            FacilityFloor floor = facilityFloorRepository.findById(request.getFloorId())
                    .orElseThrow(() -> new BusinessException(404, "所选楼层不存在"));
            if (!restaurantId.equals(floor.getFacilityId())) {
                throw new BusinessException(400, "所选楼层不属于当前食堂设施");
            }
            stall.setFloorId(floor.getId());
            stall.setFloor(floor.getFloorName());
        } else if (request.getFloor() != null && !request.getFloor().isBlank()) {
            String floorName = request.getFloor().trim();
            FacilityFloor floor = facilityFloorRepository
                    .findByFacilityIdAndFloorName(restaurantId, floorName)
                    .orElseGet(() -> {
                        FacilityFloor created = new FacilityFloor();
                        created.setFacilityId(restaurantId);
                        created.setFloorName(floorName);
                        created.setStatus(1);
                        created.setSortOrder(0);
                        return facilityFloorRepository.save(created);
                    });
            stall.setFloorId(floor.getId());
            stall.setFloor(floor.getFloorName());
        }

        if (request.getCuisineId() != null) {
            StallCuisine cuisine = stallCuisineRepository.findById(request.getCuisineId())
                    .orElseThrow(() -> new BusinessException(404, "所选菜系不存在"));
            if (!restaurantId.equals(cuisine.getRestaurantId())) {
                throw new BusinessException(400, "所选菜系不属于当前食堂");
            }
            stall.setCuisineId(cuisine.getId());
            stall.setCategory(cuisine.getCuisineName());
        } else if (request.getCategory() != null && !request.getCategory().isBlank()) {
            String cuisineName = request.getCategory().trim();
            StallCuisine cuisine = stallCuisineRepository
                    .findByRestaurantIdAndCuisineName(restaurantId, cuisineName)
                    .orElseGet(() -> {
                        StallCuisine created = new StallCuisine();
                        created.setRestaurantId(restaurantId);
                        created.setCuisineName(cuisineName);
                        created.setStatus(1);
                        created.setSortOrder(0);
                        return stallCuisineRepository.save(created);
                    });
            stall.setCuisineId(cuisine.getId());
            stall.setCategory(cuisine.getCuisineName());
        }
    }
}
