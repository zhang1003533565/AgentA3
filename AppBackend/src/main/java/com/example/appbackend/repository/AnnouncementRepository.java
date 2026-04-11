package com.example.appbackend.repository;

import com.example.appbackend.entity.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    /**
     * 查询所有已启用的公告，按置顶降序、排序字段升序排列
     */
    List<Announcement> findByEnabledTrueOrderByIsTopDescSortOrderAsc();

    /**
     * 查询所有公告，按置顶降序、排序字段升序排列
     */
    List<Announcement> findAllByOrderByIsTopDescSortOrderAsc();

    /**
     * 查询所有已置顶且启用的公告（排除指定ID）
     */
    List<Announcement> findByIsTopTrueAndEnabledTrueAndIdNot(Long id);
}
