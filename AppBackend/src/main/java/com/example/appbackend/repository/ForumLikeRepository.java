package com.example.appbackend.repository;

import com.example.appbackend.entity.ForumLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ForumLikeRepository extends JpaRepository<ForumLike, Long> {

    Optional<ForumLike> findByUserIdAndTargetIdAndTargetType(Long userId, Long targetId, Integer targetType);

    boolean existsByUserIdAndTargetIdAndTargetType(Long userId, Long targetId, Integer targetType);

    long countByTargetIdAndTargetType(Long targetId, Integer targetType);

    @Modifying
    void deleteByUserIdAndTargetIdAndTargetType(Long userId, Long targetId, Integer targetType);

    @Query("SELECT l.targetId FROM ForumLike l WHERE l.userId = :userId AND l.targetType = :targetType AND l.targetId IN :targetIds")
    java.util.List<Long> findLikedTargetIds(@Param("userId") Long userId, @Param("targetType") Integer targetType, @Param("targetIds") java.util.List<Long> targetIds);
}
