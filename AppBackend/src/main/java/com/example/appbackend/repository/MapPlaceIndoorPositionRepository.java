package com.example.appbackend.repository;

import com.example.appbackend.entity.MapPlaceIndoorPosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface MapPlaceIndoorPositionRepository extends JpaRepository<MapPlaceIndoorPosition, Long> {
    List<MapPlaceIndoorPosition> findByFloorPlanIdOrderByIdAsc(Long floorPlanId);
    Optional<MapPlaceIndoorPosition> findByPlaceIdAndFloorPlanId(Long placeId, Long floorPlanId);

    @Transactional
    void deleteByPlaceId(Long placeId);

    @Transactional
    void deleteByFloorPlanId(Long floorPlanId);
}
