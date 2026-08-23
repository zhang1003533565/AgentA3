package com.example.appbackend.repository;

import com.example.appbackend.entity.MapPlaceFence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MapPlaceFenceRepository extends JpaRepository<MapPlaceFence, Long> {
    Optional<MapPlaceFence> findByPlaceId(Long placeId);
    List<MapPlaceFence> findByPlaceIdIn(Collection<Long> placeIds);
    void deleteByPlaceId(Long placeId);
}
