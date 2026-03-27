package com.example.appbackend.repository;

import com.example.appbackend.entity.DiscountClaim;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DiscountClaimRepository extends JpaRepository<DiscountClaim, Long> {

    Optional<DiscountClaim> findByUserIdAndActivityId(Long userId, Long activityId);

    boolean existsByUserIdAndActivityId(Long userId, Long activityId);

    Page<DiscountClaim> findByUserId(Long userId, Pageable pageable);

    void deleteByActivityId(Long activityId);
}
