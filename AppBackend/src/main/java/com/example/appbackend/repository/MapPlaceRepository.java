package com.example.appbackend.repository;

import com.example.appbackend.entity.MapPlace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MapPlaceRepository extends JpaRepository<MapPlace, Long> {
    List<MapPlace> findBySceneTypeOrderBySortOrderAscIdAsc(String sceneType);
    List<MapPlace> findByParentIdOrderBySortOrderAscIdAsc(Long parentId);
    boolean existsByParentId(Long parentId);
}
