package com.example.appbackend.repository;

import com.example.appbackend.entity.LearningMaterialProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LearningMaterialProgressRepository extends JpaRepository<LearningMaterialProgress, Long> {

    Optional<LearningMaterialProgress> findByUserIdAndMaterialId(Long userId, Long materialId);

    List<LearningMaterialProgress> findByUserIdAndMaterialIdIn(Long userId, Collection<Long> materialIds);
}
