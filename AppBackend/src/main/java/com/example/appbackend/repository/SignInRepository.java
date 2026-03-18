package com.example.appbackend.repository;

import com.example.appbackend.entity.SignIn;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SignInRepository extends JpaRepository<SignIn, Long> {

    Optional<SignIn> findByActivityIdAndUserId(Long activityId, Long userId);

    Page<SignIn> findByActivityId(Long activityId, Pageable pageable);

    List<SignIn> findByUserId(Long userId);

    boolean existsByActivityIdAndUserId(Long activityId, Long userId);

    boolean existsByActivityId(Long id);

    void deleteByActivityId(Long activityId);
}
