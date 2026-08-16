package com.example.appbackend.repository;

import com.example.appbackend.entity.UserProfileDimension;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserProfileDimensionRepository extends JpaRepository<UserProfileDimension, Long> {

    List<UserProfileDimension> findByUserIdOrderByDimensionKey(Long userId);

    Optional<UserProfileDimension> findByUserIdAndDimensionKey(Long userId, String dimensionKey);
}
