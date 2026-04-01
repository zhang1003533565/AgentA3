package com.example.appbackend.repository;

import com.example.appbackend.entity.ActivityNotice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActivityNoticeRepository extends JpaRepository<ActivityNotice, Long> {

    Page<ActivityNotice> findByActivityId(Long activityId, Pageable pageable);

    List<ActivityNotice> findByActivityIdOrderByCreateTimeDesc(Long activityId);

    @Query("SELECT n FROM ActivityNotice n WHERE " +
           "(:activityId IS NULL OR n.activityId = :activityId) " +
           "AND (:title IS NULL OR n.title LIKE %:title%) " +
           "AND (:status IS NULL OR n.status = :status)")
    Page<ActivityNotice> findByConditions(
            @Param("activityId") Long activityId,
            @Param("title") String title,
            @Param("status") ActivityNotice.NoticeStatus status,
            Pageable pageable);

    List<ActivityNotice> findByActivityIdAndStatusOrderByCreateTimeDesc(
            Long activityId, ActivityNotice.NoticeStatus status);

    @Modifying
    @Query("DELETE FROM ActivityNotice n WHERE n.activityId = :activityId")
    void deleteByActivityId(@Param("activityId") Long activityId);
}
