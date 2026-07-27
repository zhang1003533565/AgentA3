package com.example.appbackend.repository;

import com.example.appbackend.entity.MapPlaceImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MapPlaceImageRepository extends JpaRepository<MapPlaceImage, Long> {
    List<MapPlaceImage> findByPlaceIdOrderBySortOrderAscIdAsc(Long placeId);
    void deleteByPlaceId(Long placeId);
}
