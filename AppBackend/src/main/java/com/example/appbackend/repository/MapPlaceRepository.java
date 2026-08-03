package com.example.appbackend.repository;

import com.example.appbackend.entity.MapPlace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MapPlaceRepository extends JpaRepository<MapPlace, Long> {
    List<MapPlace> findBySceneTypeOrderBySortOrderAscIdAsc(String sceneType);
    List<MapPlace> findByParentIdOrderBySortOrderAscIdAsc(Long parentId);
    boolean existsByParentId(Long parentId);

    @Query("SELECT COUNT(place) FROM MapPlace place " +
            "WHERE place.placeType = 'CANTEEN_STALL' AND (" +
            "place.parentId = :canteenId OR place.parentId IN (" +
            "SELECT floor.id FROM MapPlace floor " +
            "WHERE floor.parentId = :canteenId AND floor.placeType = 'FLOOR'))")
    long countCanteenStalls(@Param("canteenId") Long canteenId);
}
