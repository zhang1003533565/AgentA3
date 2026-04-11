package com.example.appbackend.service;

import com.example.appbackend.entity.Announcement;
import java.util.List;

public interface AnnouncementService {

    /**
     * 创建公告
     */
    Announcement createAnnouncement(Announcement announcement);

    /**
     * 获取所有已启用的公告（供前端展示）
     */
    List<Announcement> getEnabledAnnouncements();

    /**
     * 获取所有公告（供后台管理）
     */
    List<Announcement> getAllAnnouncements();

    /**
     * 根据ID获取公告详情
     */
    Announcement getAnnouncementById(Long id);

    /**
     * 更新公告信息
     */
    Announcement updateAnnouncement(Long id, String title, String content, Integer sortOrder, Boolean enabled, Boolean isTop);

    /**
     * 删除公告
     */
    void deleteAnnouncement(Long id);
}
