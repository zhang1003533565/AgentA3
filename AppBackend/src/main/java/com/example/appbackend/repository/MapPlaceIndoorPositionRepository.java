package com.example.appbackend.repository;

import com.example.appbackend.entity.MapPlaceIndoorPosition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MapPlaceIndoorPositionRepository extends JpaRepository<MapPlaceIndoorPosition, Long> {
    List<MapPlaceIndoorPosition> findByFloorPlanIdOrderByIdAsc(Long floorPlanId);
    Optional<MapPlaceIndoorPosition> findByPlaceIdAndFloorPlanId(Long placeId, Long floorPlanId);
    void deleteByPlaceId(Long placeId);
    void deleteByFloorPlanId(Long floorPlanId);
}
