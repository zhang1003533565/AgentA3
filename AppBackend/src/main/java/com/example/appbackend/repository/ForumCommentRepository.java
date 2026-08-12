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

    Page<ForumComment> findByPostIdAndParentIdIsNullAndStatus(Long postId, String status, Pageable pageable);

    @Query("SELECT c FROM ForumComment c WHERE (:postId IS NULL OR c.postId = :postId) " +
           "AND (:status IS NULL OR c.status = :status) " +
           "AND (:keyword IS NULL OR c.content LIKE %:keyword%)")
    Page<ForumComment> findComments(
            @Param("postId") Long postId,
            @Param("status") String status,
            @Param("keyword") String keyword,
            Pageable pageable);

    List<ForumComment> findByParentIdAndStatus(Long parentId, String status);

    List<ForumComment> findByParentId(Long parentId);

    long countByPostId(Long postId);

    long countByPostIdInAndUserIdNot(List<Long> postIds, Long userId);

    @Query("SELECT c FROM ForumComment c WHERE c.postId IN :postIds AND c.userId <> :userId AND c.status = :status ORDER BY c.createTime DESC")
    List<ForumComment> findReceivedByPostIds(@Param("postIds") List<Long> postIds, @Param("userId") Long userId, @Param("status") String status);

    long count();
    long countByStatus(String status);

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

    @Modifying
    @Query("UPDATE ForumComment c SET c.status = :status WHERE c.id = :id")
    void updateStatus(@Param("id") Long id, @Param("status") String status);

    @Modifying
    @Query("UPDATE ForumComment c SET c.status = :status WHERE c.parentId = :parentId")
    void updateChildrenStatus(@Param("parentId") Long parentId, @Param("status") String status);

    @Query("SELECT c.id FROM ForumComment c WHERE c.postId = :postId AND c.id NOT IN " +
           "(SELECT cc.parentId FROM ForumComment cc WHERE cc.parentId IS NOT NULL AND cc.postId = :postId)")
    List<Long> findLeafCommentIdsByPostId(@Param("postId") Long postId);

    @Query("SELECT COUNT(cc.id) FROM ForumComment cc WHERE cc.parentId = :parentId")
    long countChildrenByParentId(@Param("parentId") Long parentId);
}
