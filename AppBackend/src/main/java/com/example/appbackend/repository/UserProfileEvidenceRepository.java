package com.example.appbackend.repository;

import com.example.appbackend.entity.UserProfileEvidence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface UserProfileEvidenceRepository extends JpaRepository<UserProfileEvidence, Long> {

    List<UserProfileEvidence> findByStatusAndCreateTimeBefore(String status, LocalDateTime createTime);

    List<UserProfileEvidence> findByUserIdAndDimensionKeyAndStatusAndCreateTimeAfter(
            Long userId,
            String dimensionKey,
            String status,
            LocalDateTime createTime
    );
}
