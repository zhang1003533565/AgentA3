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

    Page<ForumComment> findByPostIdAndParentIdIsNull(Long postId, Pageable pageable);

    List<ForumComment> findByParentId(Long parentId);

    long countByPostId(Long postId);

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

    @Modifying
    @Query("DELETE FROM ForumComment c WHERE c.id IN :ids")
    void deleteByIds(@Param("ids") List<Long> ids);

    @Query("SELECT c.id FROM ForumComment c WHERE c.postId = :postId AND c.id NOT IN " +
           "(SELECT cc.parentId FROM ForumComment cc WHERE cc.parentId IS NOT NULL AND cc.postId = :postId)")
    List<Long> findLeafCommentIdsByPostId(@Param("postId") Long postId);

    @Query("SELECT COUNT(cc.id) FROM ForumComment cc WHERE cc.parentId = :parentId")
    long countChildrenByParentId(@Param("parentId") Long parentId);
}
