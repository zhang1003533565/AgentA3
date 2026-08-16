package com.example.appbackend.repository;

import com.example.appbackend.entity.ForumTopic;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ForumTopicRepository extends JpaRepository<ForumTopic, Long> {

    Page<ForumTopic> findByStatus(String status, Pageable pageable);

    Page<ForumTopic> findByIsHotAndStatus(Integer isHot, String status, Pageable pageable);

    long count();
    long countByStatus(String status);
    long countByIsHot(Integer isHot);
    long countById(Long id);

    /** 可编辑话题中的热门话题数（排除系统内置的「热门」id=1、「最新」id=2） */
    @Query("SELECT COUNT(t) FROM ForumTopic t WHERE t.isHot = 1 AND t.id NOT IN (1, 2)")
    long countEditableHotTopics();

    List<ForumTopic> findByStatusOrderByPostCountDesc(String status, Pageable pageable);

    @Modifying
    @Query("UPDATE ForumTopic t SET t.postCount = t.postCount + 1 WHERE t.id = :id")
    void incrementPostCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE ForumTopic t SET t.postCount = t.postCount - 1 WHERE t.id = :id AND t.postCount > 0")
    void decrementPostCount(@Param("id") Long id);
}
