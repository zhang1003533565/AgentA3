package com.example.appbackend.repository;

import com.example.appbackend.entity.ForumPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ForumPostRepository extends JpaRepository<ForumPost, Long> {

    @Query("SELECT p FROM ForumPost p WHERE " +
           "(:topicId IS NULL OR p.topicId = :topicId) " +
           "AND (:userId IS NULL OR p.userId = :userId) " +
           "AND (:status IS NULL OR p.status = :status) " +
           "AND (:keyword IS NULL OR p.title LIKE %:keyword% OR p.content LIKE %:keyword%)")
    Page<ForumPost> findPosts(
            @Param("topicId") Long topicId,
            @Param("userId") Long userId,
            @Param("status") String status,
            @Param("keyword") String keyword,
            Pageable pageable);

    @Query("SELECT p FROM ForumPost p WHERE p.status = 'PUBLISHED' ORDER BY p.likeCount DESC, p.viewCount DESC")
    Page<ForumPost> findHotPosts(Pageable pageable);

    Page<ForumPost> findByUserIdAndStatus(Long userId, String status, Pageable pageable);

    @Query("SELECT p FROM ForumPost p WHERE p.userId = :userId AND p.status = 'PUBLISHED' ORDER BY p.createTime DESC")
    Page<ForumPost> findUserPosts(@Param("userId") Long userId, Pageable pageable);

    @Modifying
    @Query("UPDATE ForumPost p SET p.status = :status WHERE p.id = :id")
    void updateStatus(@Param("id") Long id, @Param("status") String status);

    @Modifying
    @Query("UPDATE ForumPost p SET p.viewCount = p.viewCount + 1 WHERE p.id = :id")
    void incrementViewCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE ForumPost p SET p.commentCount = p.commentCount + 1 WHERE p.id = :id")
    void incrementCommentCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE ForumPost p SET p.commentCount = p.commentCount - 1 WHERE p.id = :id AND p.commentCount > 0")
    void decrementCommentCount(@Param("id") Long id);

    @Modifying
    @Query("DELETE FROM ForumPost p WHERE p.id = :id")
    void deleteById(@Param("id") Long id);

    @Modifying
    @Query("DELETE FROM ForumPost p WHERE p.id IN :ids")
    void deleteByIds(@Param("ids") List<Long> ids);

    @Modifying
    @Query("UPDATE ForumPost p SET p.likeCount = p.likeCount + 1 WHERE p.id = :id")
    void incrementLikeCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE ForumPost p SET p.likeCount = p.likeCount - 1 WHERE p.id = :id AND p.likeCount > 0")
    void decrementLikeCount(@Param("id") Long id);
}
