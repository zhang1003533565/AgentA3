package com.example.appbackend.repository;

import com.example.appbackend.entity.MapFloorPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MapFloorPlanRepository extends JpaRepository<MapFloorPlan, Long> {
    Optional<MapFloorPlan> findByFloorPlaceId(Long floorPlaceId);
    void deleteByFloorPlaceId(Long floorPlaceId);
}
