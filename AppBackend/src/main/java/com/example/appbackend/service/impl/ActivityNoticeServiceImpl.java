package com.example.appbackend.service.impl;

import com.example.appbackend.dto.ActivityNoticeRequest;
import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.entity.ActivityNotice;
import com.example.appbackend.entity.ActivityNotice.NoticeStatus;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.ActivityNoticeRepository;
import com.example.appbackend.repository.ActivityRepository;
import com.example.appbackend.service.ActivityNoticeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ActivityNoticeServiceImpl implements ActivityNoticeService {

    @Autowired
    private ActivityNoticeRepository activityNoticeRepository;

    @Autowired
    private ActivityRepository activityRepository;

    @Override
    public PageResponse<ActivityNotice> getNoticeList(Long activityId, String title, String status, Integer pageNum, Integer pageSize) {
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "createTime"));
        NoticeStatus statusEnum = status != null ? NoticeStatus.valueOf(status) : null;
        Page<ActivityNotice> page = activityNoticeRepository.findByConditions(activityId, title, statusEnum, pageable);
        return new PageResponse<>(page.getContent(), page.getTotalElements(), page.getNumber() + 1, page.getSize());
    }

    @Override
    public ActivityNotice getNoticeById(Long id) {
        return activityNoticeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(Result.NOT_FOUND_CODE, "通知不存在"));
    }

    @Override
    @Transactional
    public ActivityNotice createNotice(ActivityNoticeRequest request, Long userId, String userName) {
        if (!activityRepository.existsById(request.getActivityId())) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "关联的活动不存在");
        }
        ActivityNotice notice = new ActivityNotice();
        notice.setActivityId(request.getActivityId());
        notice.setTitle(request.getTitle());
        notice.setContent(request.getContent());
        notice.setPublisherId(userId);
        notice.setPublisherName(userName);
        notice.setStatus(request.getStatus() != null ? NoticeStatus.valueOf(request.getStatus()) : NoticeStatus.PUBLISHED);
        if (notice.getStatus() == NoticeStatus.PUBLISHED) {
            notice.setPublishTime(LocalDateTime.now());
        }
        return activityNoticeRepository.save(notice);
    }

    @Override
    @Transactional
    public ActivityNotice updateNotice(Long id, ActivityNoticeRequest request) {
        ActivityNotice notice = getNoticeById(id);
        notice.setTitle(request.getTitle());
        notice.setContent(request.getContent());
        if (request.getStatus() != null) {
            NoticeStatus newStatus = NoticeStatus.valueOf(request.getStatus());
            notice.setStatus(newStatus);
            if (newStatus == NoticeStatus.PUBLISHED && notice.getPublishTime() == null) {
                notice.setPublishTime(LocalDateTime.now());
            }
        }
        return activityNoticeRepository.save(notice);
    }

    @Override
    @Transactional
    public void deleteNotice(Long id) {
        if (!activityNoticeRepository.existsById(id)) {
            throw new BusinessException(Result.NOT_FOUND_CODE, "通知不存在");
        }
        activityNoticeRepository.deleteById(id);
    }

    @Override
    public PageResponse<ActivityNotice> getNoticesByActivityId(Long activityId, Integer pageNum, Integer pageSize) {
        if (!activityRepository.existsById(activityId)) {
            throw new BusinessException(Result.NOT_FOUND_CODE, "活动不存在");
        }
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "createTime"));
        Page<ActivityNotice> page = activityNoticeRepository.findByActivityId(activityId, pageable);
        return new PageResponse<>(page.getContent(), page.getTotalElements(), page.getNumber() + 1, page.getSize());
    }
}
