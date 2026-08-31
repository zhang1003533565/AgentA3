package com.example.appbackend.repository;

import com.example.appbackend.entity.JobFavorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobFavoriteRepository extends JpaRepository<JobFavorite, Long> {

    List<JobFavorite> findByUserId(Long userId);

    Optional<JobFavorite> findByUserIdAndRecommendationId(Long userId, Long recommendationId);

    boolean existsByUserIdAndRecommendationId(Long userId, Long recommendationId);

    @Transactional
    void deleteByUserIdAndRecommendationId(Long userId, Long recommendationId);
}
