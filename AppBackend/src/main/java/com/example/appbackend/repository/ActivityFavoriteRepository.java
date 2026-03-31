package com.example.appbackend.repository;

import com.example.appbackend.entity.ActivityFavorite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ActivityFavoriteRepository extends JpaRepository<ActivityFavorite, Long> {

    Optional<ActivityFavorite> findByUserIdAndActivityId(Long userId, Long activityId);

    boolean existsByUserIdAndActivityId(Long userId, Long activityId);

    void deleteByUserIdAndActivityId(Long userId, Long activityId);

    Page<ActivityFavorite> findByUserId(Long userId, Pageable pageable);

    long countByActivityId(Long activityId);
}
