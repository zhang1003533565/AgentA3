package com.example.appbackend.repository;

import com.example.appbackend.entity.Registration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RegistrationRepository extends JpaRepository<Registration, Long> {

    Optional<Registration> findByActivityIdAndUserId(Long activityId, Long userId);

    Page<Registration> findByUserId(Long userId, Pageable pageable);

    Page<Registration> findByActivityId(Long activityId, Pageable pageable);

    boolean existsByActivityIdAndUserId(Long activityId, Long userId);

    void deleteByActivityId(Long activityId);
}
