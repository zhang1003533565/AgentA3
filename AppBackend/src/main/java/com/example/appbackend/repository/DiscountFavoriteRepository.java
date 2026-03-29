package com.example.appbackend.repository;

import com.example.appbackend.entity.DiscountFavorite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DiscountFavoriteRepository extends JpaRepository<DiscountFavorite, Long> {

    Optional<DiscountFavorite> findByUserIdAndActivityId(Long userId, Long activityId);

    boolean existsByUserIdAndActivityId(Long userId, Long activityId);

    Page<DiscountFavorite> findByUserId(Long userId, Pageable pageable);

    void deleteByActivityId(Long activityId);
}
