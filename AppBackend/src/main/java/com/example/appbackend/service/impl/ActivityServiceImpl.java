package com.example.appbackend.service.impl;

import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.entity.Activity;
import com.example.appbackend.entity.Activity.Status;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.ActivityRepository;
import com.example.appbackend.repository.SignInRepository;
import com.example.appbackend.service.ActivityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.example.appbackend.entity.Activity.Status.PUBLISHED;

@Service
@Transactional
public class ActivityServiceImpl implements ActivityService {

    @Autowired
    private ActivityRepository activityRepository;
    @Autowired
    private SignInRepository signInRepository;

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
    public Activity draftActivity(Activity activity, Long userId, String organizerName) {
        activity.setOrganizerId(userId);
        activity.setOrganizerName(organizerName);
        activity.setStatus(Status.DRAFT);
        activity.setCurrentPeople(0);
        return activityRepository.save(activity);
    }

    @Override
    public Activity updateActivity(Long id, Activity activity) {
        Activity existing = getActivityById(id);
        if (existing.getStatus() != Status.DRAFT && existing.getStatus() != PUBLISHED) {
            throw new BusinessException(400, "只有草稿或已发布的活动可以编辑");
        }
        activity.setId(id);
        activity.setOrganizerId(existing.getOrganizerId());
        activity.setOrganizerName(existing.getOrganizerName());
        activity.setCurrentPeople(existing.getCurrentPeople());
        return activityRepository.save(activity);
    }

    @Override
    public void deleteActivity(Long id) {
        if(signInRepository.existsByActivityId(id)){
            throw new BusinessException(400,"已有学生签到，不可删除该活动");
        };
        Activity activity = getActivityById(id);
        activityRepository.delete(activity);
    }

    @Override
    public void deleteActivities(List<Long> ids) {
        List<Activity> activities = activityRepository.findAllById(ids);
        if (activities.isEmpty()) {
            throw new BusinessException(404, "未找到指定的活动中活动");
        }
        // 检查是否有活动存在签到记录
        for (Activity activity : activities) {
            if (signInRepository.existsByActivityId(activity.getId())) {
                throw new BusinessException(400, "活动「" + activity.getTitle() + "」已有学生签到，不可删除");
            }
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

}
