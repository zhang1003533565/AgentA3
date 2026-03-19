package com.example.appbackend.repository;

import com.example.appbackend.entity.ForumFollow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ForumFollowRepository extends JpaRepository<ForumFollow, Long> {

    Optional<ForumFollow> findByUserIdAndFollowId(Long userId, Long followId);

    boolean existsByUserIdAndFollowId(Long userId, Long followId);

    void deleteByUserIdAndFollowId(Long userId, Long followId);

    Page<ForumFollow> findByFollowId(Long followId, Pageable pageable);

    Page<ForumFollow> findByUserId(Long userId, Pageable pageable);

    long countByFollowId(Long followId);

    long countByUserId(Long userId);
}
