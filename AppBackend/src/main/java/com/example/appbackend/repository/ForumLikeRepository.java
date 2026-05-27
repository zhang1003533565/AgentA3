package com.example.appbackend.repository;

import com.example.appbackend.entity.ForumLike;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ForumLikeRepository extends JpaRepository<ForumLike, Long> {

    Optional<ForumLike> findByUserIdAndTargetIdAndTargetType(Long userId, Long targetId, String targetType);

    boolean existsByUserIdAndTargetIdAndTargetType(Long userId, Long targetId, String targetType);

    long countByTargetIdAndTargetType(Long targetId, String targetType);

    @Query("SELECT l.targetId FROM ForumLike l WHERE l.userId = :userId AND l.targetId IN :targetIds AND l.targetType = :targetType")
    List<Long> findLikedTargetIds(
            @Param("userId") Long userId,
            @Param("targetIds") List<Long> targetIds,
            @Param("targetType") String targetType);

    Page<ForumLike> findByUserIdAndTargetType(Long userId, String targetType, Pageable pageable);

    @Modifying
    @Query("DELETE FROM ForumLike l WHERE l.targetId = :targetId AND l.targetType = :targetType")
    void deleteByTargetIdAndTargetType(@Param("targetId") Long targetId, @Param("targetType") String targetType);
}
