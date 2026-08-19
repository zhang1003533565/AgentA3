package com.example.appbackend.repository;

import com.example.appbackend.entity.Registration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RegistrationRepository extends JpaRepository<Registration, Long> {

    Optional<Registration> findByActivityIdAndUserId(Long activityId, Long userId);

    Page<Registration> findByUserId(Long userId, Pageable pageable);

    Page<Registration> findByActivityId(Long activityId, Pageable pageable);

    boolean existsByActivityIdAndUserId(Long activityId, Long userId);

    void deleteByActivityId(Long activityId);

    @Query("SELECT r FROM Registration r WHERE " +
            "(:activityId IS NULL OR r.activityId = :activityId) AND " +
            "(:status IS NULL OR r.status = :status) AND " +
            "(:keyword IS NULL OR LOWER(r.user.username) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(r.user.realName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(r.user.personalNumber) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Registration> searchRegistrations(@Param("activityId") Long activityId,
                                           @Param("status") String status,
                                           @Param("keyword") String keyword,
                                           Pageable pageable);
}
