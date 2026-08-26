package com.example.appbackend.repository;

import com.example.appbackend.entity.DishCuisine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DishCuisineRepository extends JpaRepository<DishCuisine, Long> {

    List<DishCuisine> findByCanteenPlaceIdOrderBySortOrderAscIdAsc(Long canteenPlaceId);

    Optional<DishCuisine> findByCanteenPlaceIdAndCuisineName(Long canteenPlaceId, String cuisineName);

    void deleteByCanteenPlaceId(Long canteenPlaceId);

    boolean existsByCanteenPlaceIdAndCuisineNameAndIdNot(Long canteenPlaceId, String cuisineName, Long id);
}
