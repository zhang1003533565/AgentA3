package com.example.appbackend.repository;

import com.example.appbackend.entity.UserProfileSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserProfileSnapshotRepository extends JpaRepository<UserProfileSnapshot, Long> {

    Optional<UserProfileSnapshot> findByUserId(Long userId);
}
