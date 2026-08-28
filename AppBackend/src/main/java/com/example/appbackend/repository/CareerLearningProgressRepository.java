package com.example.appbackend.repository;

import com.example.appbackend.entity.CareerLearningProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CareerLearningProgressRepository extends JpaRepository<CareerLearningProgress, Long> {
    List<CareerLearningProgress> findAllByUserId(Long userId);

    Optional<CareerLearningProgress> findByUserIdAndItemId(Long userId, String itemId);
}
