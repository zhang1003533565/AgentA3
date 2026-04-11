package com.example.appbackend.service.impl;

import com.example.appbackend.entity.Announcement;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.AnnouncementRepository;
import com.example.appbackend.service.AnnouncementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class AnnouncementServiceImpl implements AnnouncementService {

    @Autowired
    private AnnouncementRepository announcementRepository;

    @Override
    @Transactional
    public Announcement createAnnouncement(Announcement announcement) {
        if (announcement.getTitle() == null || announcement.getTitle().trim().isEmpty()) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "公告标题不能为空");
        }
        if (announcement.getContent() == null || announcement.getContent().trim().isEmpty()) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "公告内容不能为空");
        }
        if (announcement.getSortOrder() == null) {
            announcement.setSortOrder(0);
        }
        if (announcement.getEnabled() == null) {
            announcement.setEnabled(true);
        }
        return announcementRepository.save(announcement);
    }

    @Override
    public List<Announcement> getEnabledAnnouncements() {
        return announcementRepository.findByEnabledTrueOrderByIsTopDescSortOrderAsc();
    }

    @Override
    public List<Announcement> getAllAnnouncements() {
        return announcementRepository.findAllByOrderByIsTopDescSortOrderAsc();
    }

    @Override
    public Announcement getAnnouncementById(Long id) {
        return announcementRepository.findById(id)
                .orElseThrow(() -> new BusinessException(Result.NOT_FOUND_CODE, "公告不存在"));
    }

    @Override
    @Transactional
    public Announcement updateAnnouncement(Long id, String title, String content, Integer sortOrder, Boolean enabled, Boolean isTop) {
        Announcement announcement = getAnnouncementById(id);

        if (title != null && !title.trim().isEmpty()) {
            announcement.setTitle(title);
        }
        if (content != null && !content.trim().isEmpty()) {
            announcement.setContent(content);
        }
        if (sortOrder != null) {
            announcement.setSortOrder(sortOrder);
        }
        if (enabled != null) {
            announcement.setEnabled(enabled);
        }
        if (isTop != null) {
            // 如果设置为置顶，先取消其他置顶
            if (isTop) {
                List<Announcement> topAnnouncements = announcementRepository.findByIsTopTrueAndEnabledTrueAndIdNot(id);
                for (Announcement top : topAnnouncements) {
                    top.setIsTop(false);
                    announcementRepository.save(top);
                }
            }
            announcement.setIsTop(isTop);
        }

        return announcementRepository.save(announcement);
    }

    @Override
    @Transactional
    public void deleteAnnouncement(Long id) {
        Announcement announcement = getAnnouncementById(id);
        announcementRepository.delete(announcement);
    }
}
