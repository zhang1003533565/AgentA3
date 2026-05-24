package com.example.appbackend.repository;

import com.example.appbackend.entity.ForumFavorite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

@Repository
public interface ForumFavoriteRepository extends JpaRepository<ForumFavorite, Long> {

    Optional<ForumFavorite> findByUserIdAndPostId(Long userId, Long postId);

    boolean existsByUserIdAndPostId(Long userId, Long postId);

    Page<ForumFavorite> findByUserId(Long userId, Pageable pageable);

    Page<ForumFavorite> findByUserIdAndPost_Status(Long userId, String status, Pageable pageable);

    void deleteByPostId(Long postId);

    @Query("SELECT f.postId FROM ForumFavorite f WHERE f.userId = :userId AND f.postId IN :postIds")
    Set<Long> findFavoritePostIds(@Param("userId") Long userId, @Param("postIds") Collection<Long> postIds);
}
