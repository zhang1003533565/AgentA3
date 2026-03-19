package com.example.appbackend.repository;

import com.example.appbackend.entity.ForumComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ForumCommentRepository extends JpaRepository<ForumComment, Long> {

    Page<ForumComment> findByPostIdAndStatusAndParentIdIsNull(Long postId, String status, Pageable pageable);

    List<ForumComment> findByParentIdAndStatus(Long parentId, String status);

    long countByPostIdAndStatus(Long postId, String status);

    @Modifying
    @Query("UPDATE ForumComment c SET c.likeCount = c.likeCount + 1 WHERE c.id = :id")
    void incrementLikeCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE ForumComment c SET c.likeCount = c.likeCount - 1 WHERE c.id = :id AND c.likeCount > 0")
    void decrementLikeCount(@Param("id") Long id);

    @Modifying
    @Query("DELETE FROM ForumComment c WHERE c.id = :id")
    void deleteById(@Param("id") Long id);

    @Modifying
    @Query("DELETE FROM ForumComment c WHERE c.postId = :postId")
    void deleteByPostId(@Param("postId") Long postId);
}
