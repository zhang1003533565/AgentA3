package com.example.appbackend.repository;

import com.example.appbackend.entity.CanteenStall;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CanteenStallRepository extends JpaRepository<CanteenStall, Long> {

    List<CanteenStall> findByRestaurantId(Long restaurantId);

    List<CanteenStall> findByRestaurantIdAndStatus(Long restaurantId, Integer status);

    List<CanteenStall> findByCategory(String category);

    List<CanteenStall> findByFloor(String floor);

    List<CanteenStall> findByFloorId(Long floorId);

    boolean existsByFloorId(Long floorId);

    List<CanteenStall> findByCuisineId(Long cuisineId);

    boolean existsByCuisineId(Long cuisineId);
}
