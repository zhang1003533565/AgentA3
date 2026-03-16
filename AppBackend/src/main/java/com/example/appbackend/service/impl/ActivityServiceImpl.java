package com.example.appbackend.service.impl;

import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.entity.Activity;
import com.example.appbackend.entity.Activity.Status;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.ActivityRepository;
import com.example.appbackend.service.ActivityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ActivityServiceImpl implements ActivityService {

    @Autowired
    private ActivityRepository activityRepository;

    @Override
    public PageResponse<Activity> getActivityList(Integer page, Integer size, String title, Long categoryId, Status status) {
        PageRequest pageRequest = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createTime"));
        Page<Activity> activityPage = activityRepository.findByConditions(title, categoryId, status, pageRequest);
        return new PageResponse<>(
            activityPage.getContent(),
            activityPage.getTotalElements(),
            page,
            size
        );
    }

    @Override
    public Activity getActivityById(Long id) {
        return activityRepository.findById(id)
            .orElseThrow(() -> new BusinessException(404, "活动不存在"));
    }

    @Override
    public Activity createActivity(Activity activity, Long userId, String organizerName) {
        activity.setOrganizerId(userId);
        activity.setOrganizerName(organizerName);
        activity.setStatus(Status.DRAFT);
        activity.setCurrentPeople(0);
        return activityRepository.save(activity);
    }

    @Override
    public Activity updateActivity(Long id, Activity activity) {
        Activity existing = getActivityById(id);
        if (existing.getStatus() != Status.DRAFT && existing.getStatus() != Status.REJECTED) {
            throw new BusinessException(400, "只有草稿或被驳回的活动可以编辑");
        }
        activity.setId(id);
        activity.setOrganizerId(existing.getOrganizerId());
        activity.setOrganizerName(existing.getOrganizerName());
        activity.setCurrentPeople(existing.getCurrentPeople());
        return activityRepository.save(activity);
    }

    @Override
    public void deleteActivity(Long id) {
        Activity activity = getActivityById(id);
        activityRepository.delete(activity);
    }

    @Override
    public void updateActivityStatus(Long id, Status status) {
        Activity activity = getActivityById(id);
        activity.setStatus(status);
        activityRepository.save(activity);
    }

    @Override
    public void submitActivity(Long id) {
        Activity activity = getActivityById(id);
        if (activity.getStatus() != Status.DRAFT && activity.getStatus() != Status.REJECTED) {
            throw new BusinessException(400, "只有草稿或被驳回的活动可以提交审核");
        }
        activity.setStatus(Status.PENDING);
        activityRepository.save(activity);
    }

    @Override
    public void auditActivity(Long id, String auditStatus) {
        Activity activity = getActivityById(id);
        if (activity.getStatus() != Status.PENDING) {
            throw new BusinessException(400, "只有待审核的活动可以审核");
        }
        if ("PUBLISHED".equals(auditStatus)) {
            activity.setStatus(Status.PUBLISHED);
        } else if("REJECTED".equals(auditStatus)){
            activity.setStatus(Status.REJECTED);
        }else {
            throw new BusinessException(Result.FORBIDDEN_CODE,"传入正常状态");
        }
        activityRepository.save(activity);
    }

}
