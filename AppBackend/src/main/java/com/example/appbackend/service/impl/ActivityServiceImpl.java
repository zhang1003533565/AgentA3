package com.example.appbackend.service.impl;

import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.entity.Activity;
import com.example.appbackend.entity.Activity.Status;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.ActivityNoticeRepository;
import com.example.appbackend.repository.ActivityRepository;
import com.example.appbackend.repository.RegistrationRepository;
import com.example.appbackend.repository.SignInRepository;
import com.example.appbackend.service.ActivityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static com.example.appbackend.entity.Activity.Status.PUBLISHED;

@Service
@Transactional
public class ActivityServiceImpl implements ActivityService {

    @Autowired
    private ActivityRepository activityRepository;
    @Autowired
    private ActivityNoticeRepository activityNoticeRepository;
    @Autowired
    private SignInRepository signInRepository;
    @Autowired
    private RegistrationRepository registrationRepository;

    @Override
    public PageResponse<Activity> getActivityList(Integer page, Integer size, String title, Long categoryId, Status status, String timePhase) {
        PageRequest pageRequest = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createTime"));
        Page<Activity> activityPage = activityRepository.findByConditions(title, categoryId, status, timePhase, java.time.LocalDateTime.now(), pageRequest);
        activityPage.getContent().forEach(this::fillOrganizerAvatar);
        return new PageResponse<>(
            activityPage.getContent(),
            activityPage.getTotalElements(),
            page,
            size
        );
    }

    @Override
    public PageResponse<Activity> getActivitiesByOrganizer(Long organizerId, Integer page, Integer size) {
        PageRequest pageRequest = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createTime"));
        Page<Activity> activityPage = activityRepository.findByOrganizerId(organizerId, pageRequest);
        activityPage.getContent().forEach(this::fillOrganizerAvatar);
        return new PageResponse<>(
            activityPage.getContent(),
            activityPage.getTotalElements(),
            page,
            size
        );
    }

    @Override
    public Activity getActivityById(Long id) {
        Activity activity = activityRepository.findById(id)
            .orElseThrow(() -> new BusinessException(404, "活动不存在"));
        fillOrganizerAvatar(activity);
        return activity;
    }

    @Override
    public Activity draftActivity(Activity activity, Long userId, String organizerName) {
        activity.setOrganizerId(userId);
        // 主办方名称：请求里填写了则使用填写的，否则默认使用当前账号姓名
        if (activity.getOrganizerName() == null || activity.getOrganizerName().isBlank()) {
            activity.setOrganizerName(organizerName);
        }
        activity.setStatus(Status.PUBLISHED);
        activity.setCurrentPeople(0);
        if (activity.getRequiresAudit() == null) {
            activity.setRequiresAudit(false);
        }
        return activityRepository.save(activity);
    }

    @Override
    public Activity updateActivity(Long id, Activity activity) {
        Activity existing = getActivityById(id);
        if (existing.getStatus() != Status.DRAFT && existing.getStatus() != PUBLISHED && existing.getStatus() != Status.COMPLETED) {
            throw new BusinessException(400, "只有草稿、已发布或已完成的活动可以编辑");
        }
        activity.setId(id);
        activity.setOrganizerId(existing.getOrganizerId());
        // 主办方名称：请求里填写了则更新，留空则保留原值
        if (activity.getOrganizerName() == null || activity.getOrganizerName().isBlank()) {
            activity.setOrganizerName(existing.getOrganizerName());
        }
        activity.setCurrentPeople(existing.getCurrentPeople());
        if (activity.getRequiresAudit() == null) {
            activity.setRequiresAudit(Boolean.TRUE.equals(existing.getRequiresAudit()));
        }
        // 状态处理：编辑已完成的活动时，若新的结束时间在未来则恢复为已发布，否则保持已完成
        if (existing.getStatus() == Status.COMPLETED) {
            boolean endInFuture = activity.getEndTime() != null && activity.getEndTime().isAfter(LocalDateTime.now());
            activity.setStatus(endInFuture ? Status.PUBLISHED : Status.COMPLETED);
        } else {
            activity.setStatus(Status.PUBLISHED);
        }
        return activityRepository.save(activity);
    }

    @Override
    public void deleteActivity(Long id, boolean isAdmin) {
        Activity activity = getActivityById(id);
        if (!isAdmin && activity.getStatus() != Status.DRAFT) {
            throw new BusinessException(403, "只有草稿状态的活动可以删除");
        }
        activityNoticeRepository.deleteByActivityId(id);
        signInRepository.deleteByActivityId(id);
        registrationRepository.deleteByActivityId(id);
        activityRepository.delete(activity);
    }

    @Override
    public void deleteActivities(List<Long> ids, boolean isAdmin) {
        List<Activity> activities = activityRepository.findAllById(ids);
        if (activities.isEmpty()) {
            throw new BusinessException(404, "未找到指定的活动中活动");
        }
        // 非管理员只能删除草稿状态的活动
        if (!isAdmin) {
            for (Activity activity : activities) {
                if (activity.getStatus() != Status.DRAFT) {
                    throw new BusinessException(403, "只有草稿状态的活动可以删除");
                }
            }
        }
        for (Activity activity : activities) {
            activityNoticeRepository.deleteByActivityId(activity.getId());
            signInRepository.deleteByActivityId(activity.getId());
            registrationRepository.deleteByActivityId(activity.getId());
        }
        activityRepository.deleteAll(activities);
    }

    @Override
    public void updateActivityStatus(Long id, Status status) {
        Activity activity = getActivityById(id);
        activity.setStatus(status);
        activityRepository.save(activity);
    }





    @Override
    public PageResponse<Activity> searchActivities(Integer page, Integer size, String keyword) {
        PageRequest pageRequest = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createTime"));
        Page<Activity> activityPage = activityRepository.searchByKeyword(keyword, pageRequest);
        return new PageResponse<>(
            activityPage.getContent(),
            activityPage.getTotalElements(),
            page,
            size
        );
    }

    @Override
    public PageResponse<Activity> filterActivities(Integer page, Integer size, Long categoryId, Status status) {
        PageRequest pageRequest = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createTime"));
        Page<Activity> activityPage = activityRepository.filterByCategoryAndStatus(categoryId, status, pageRequest);
        return new PageResponse<>(
            activityPage.getContent(),
            activityPage.getTotalElements(),
            page,
            size
        );
    }

    @Override
    public void publishActivity(Long id) {
        Activity activity=activityRepository.findById(id).orElseThrow(()->new BusinessException(Result.FORBIDDEN_CODE,"活动不存在"));
        activity.setStatus(Status.PUBLISHED);
        activityRepository.save(activity);
    }

    @Override
    public void updateExpiredActivitiesStatus() {
        List<Activity> expiredActivities = activityRepository.findExpiredActivities(
                java.time.LocalDateTime.now(),
                Status.PUBLISHED
        );
        for (Activity activity : expiredActivities) {
            activity.setStatus(Status.COMPLETED);
            activityRepository.save(activity);
        }
    }

    private void fillOrganizerAvatar(Activity activity) {
        if (activity == null) {
            return;
        }
        if (activity.getOrganizer() != null) {
            activity.setOrganizerAvatar(activity.getOrganizer().getAvatar());
        }
    }
}
