package com.example.appbackend.repository;

import com.example.appbackend.entity.StallCuisine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StallCuisineRepository extends JpaRepository<StallCuisine, Long> {

    List<StallCuisine> findByRestaurantIdOrderBySortOrderAscIdAsc(Long restaurantId);

    Optional<StallCuisine> findByRestaurantIdAndCuisineName(Long restaurantId, String cuisineName);

    boolean existsByRestaurantIdAndCuisineNameAndIdNot(Long restaurantId, String cuisineName, Long id);
}
