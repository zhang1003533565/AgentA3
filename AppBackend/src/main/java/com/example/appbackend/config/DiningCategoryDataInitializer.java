package com.example.appbackend.config;

import com.example.appbackend.entity.CanteenStall;
import com.example.appbackend.entity.FacilityFloor;
import com.example.appbackend.entity.StallCuisine;
import com.example.appbackend.repository.CanteenStallRepository;
import com.example.appbackend.repository.FacilityFloorRepository;
import com.example.appbackend.repository.StallCuisineRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DiningCategoryDataInitializer {

    private final CanteenStallRepository stallRepository;
    private final FacilityFloorRepository floorRepository;
    private final StallCuisineRepository cuisineRepository;

    public DiningCategoryDataInitializer(
            CanteenStallRepository stallRepository,
            FacilityFloorRepository floorRepository,
            StallCuisineRepository cuisineRepository) {
        this.stallRepository = stallRepository;
        this.floorRepository = floorRepository;
        this.cuisineRepository = cuisineRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void migrateLegacyDiningCategories() {
        for (CanteenStall stall : stallRepository.findAll()) {
            boolean changed = false;

            if (stall.getFloorId() == null && stall.getFloor() != null && !stall.getFloor().isBlank()) {
                String name = stall.getFloor().trim();
                FacilityFloor floor = floorRepository
                        .findByFacilityIdAndFloorName(stall.getRestaurantId(), name)
                        .orElseGet(() -> createFloor(stall.getRestaurantId(), name));
                stall.setFloorId(floor.getId());
                stall.setFloor(floor.getFloorName());
                changed = true;
            }

            if (stall.getCuisineId() == null && stall.getCategory() != null && !stall.getCategory().isBlank()) {
                String name = stall.getCategory().trim();
                StallCuisine cuisine = cuisineRepository
                        .findByRestaurantIdAndCuisineName(stall.getRestaurantId(), name)
                        .orElseGet(() -> createCuisine(stall.getRestaurantId(), name));
                stall.setCuisineId(cuisine.getId());
                stall.setCategory(cuisine.getCuisineName());
                changed = true;
            }

            if (changed) {
                stallRepository.save(stall);
            }
        }
    }

    private FacilityFloor createFloor(Long facilityId, String name) {
        FacilityFloor floor = new FacilityFloor();
        floor.setFacilityId(facilityId);
        floor.setFloorName(name);
        floor.setStatus(1);
        floor.setSortOrder(0);
        return floorRepository.save(floor);
    }

    private StallCuisine createCuisine(Long restaurantId, String name) {
        StallCuisine cuisine = new StallCuisine();
        cuisine.setRestaurantId(restaurantId);
        cuisine.setCuisineName(name);
        cuisine.setStatus(1);
        cuisine.setSortOrder(0);
        return cuisineRepository.save(cuisine);
    }
}
