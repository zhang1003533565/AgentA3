package com.example.appbackend.repository;

import com.example.appbackend.entity.ForumLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ForumLikeRepository extends JpaRepository<ForumLike, Long> {

    Optional<ForumLike> findByUserIdAndTargetId(Long userId, Long targetId);

    boolean existsByUserIdAndTargetId(Long userId, Long targetId);

    long countByTargetId(Long targetId);

    List<Long> findByUserIdAndTargetIdIn(Long userId, List<Long> targetIds);
}
