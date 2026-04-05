package com.example.appbackend.repository;

import com.example.appbackend.entity.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    /**
     * 查询所有已启用的公告，按排序字段升序排列
     */
    List<Announcement> findByEnabledTrueOrderBySortOrderAsc();

    /**
     * 查询所有公告，按排序字段升序排列
     */
    List<Announcement> findAllByOrderBySortOrderAsc();
}
