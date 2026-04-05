package com.example.appbackend.service.impl;

import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.dto.RegistrationListItem;
import com.example.appbackend.entity.Activity;
import com.example.appbackend.entity.Registration;
import com.example.appbackend.entity.Result;
import com.example.appbackend.entity.User;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.ActivityRepository;
import com.example.appbackend.repository.RegistrationRepository;
import com.example.appbackend.repository.UserRepository;
import com.example.appbackend.service.RegistrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class RegistrationServiceImpl implements RegistrationService {

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private UserRepository userRepository;

    @Override
    public Registration registerActivity(Long activityId, Long userId) {
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new BusinessException(404, "活动不存在"));

        if (activity.getStatus() != Activity.Status.PUBLISHED) {
            throw new BusinessException(400, "活动未发布，无法报名");
        }

        LocalDateTime now = LocalDateTime.now();
        if (activity.getSignupEndTime() != null && now.isAfter(activity.getSignupEndTime())) {
            throw new BusinessException(400, "报名已结束");
        }
        if (activity.getStartTime() != null && !now.isBefore(activity.getStartTime())) {
            throw new BusinessException(400, "活动已开始，无法继续报名");
        }

        // 名额校验：currentPeople 已达到 maxPeople 才算真正满额
        // 原逻辑使用 currentPeople + 1 >= maxPeople 会导致 maxPeople=1 时首次报名也被拦截
        if (activity.getMaxPeople() > 0 && activity.getCurrentPeople() >= activity.getMaxPeople()) {
            throw new BusinessException(400, "报名名额已满");
        }

        if (registrationRepository.existsByActivityIdAndUserId(activityId, userId)) {
            throw new BusinessException(400, "您已经报名过该活动");
        }

        Registration registration = new Registration();
        registration.setActivityId(activityId);
        registration.setUserId(userId);
        registration.setStatus("APPROVED");
        registration.setSignupTime(LocalDateTime.now());

        Registration saved = registrationRepository.save(registration);

        activity.setCurrentPeople(activity.getCurrentPeople() + 1);
        activityRepository.save(activity);

        return saved;
    }

    @Override
    public void cancelRegistration(Long registrationId, Long userId) {
        Registration registration = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new BusinessException(404, "报名记录不存在"));

        if (!registration.getUserId().equals(userId)) {
            throw new BusinessException(Result.FORBIDDEN_CODE, "无权取消该报名");
        }

        Activity activity = activityRepository.findById(registration.getActivityId())
                .orElseThrow(() -> new BusinessException(404, "活动不存在"));
        activity.setCurrentPeople(Math.max(0, activity.getCurrentPeople() - 1));
        activityRepository.save(activity);

        registrationRepository.delete(registration);
    }

    @Override
    public void removeRegistrationByManager(Long registrationId) {
        Registration registration = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new BusinessException(404, "报名记录不存在"));

        Activity activity = activityRepository.findById(registration.getActivityId())
                .orElseThrow(() -> new BusinessException(404, "活动不存在"));
        activity.setCurrentPeople(Math.max(0, activity.getCurrentPeople() - 1));
        activityRepository.save(activity);

        registrationRepository.delete(registration);
    }

    @Override
    public PageResponse<Registration> getMyRegistrations(Long userId, Integer page, Integer size) {
        PageRequest pageRequest = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "signupTime"));
        Page<Registration> registrationPage = registrationRepository.findByUserId(userId, pageRequest);
        return new PageResponse<>(
                registrationPage.getContent(),
                registrationPage.getTotalElements(),
                page,
                size
        );
    }

    @Override
    public PageResponse<RegistrationListItem> getActivityRegistrations(Long activityId, Integer page, Integer size) {
        PageRequest pageRequest = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "signupTime"));
        Page<Registration> registrationPage = registrationRepository.findByActivityId(activityId, pageRequest);
        
        List<RegistrationListItem> items = registrationPage.getContent().stream()
                .map(reg -> {
                    User user = userRepository.findById(reg.getUserId()).orElse(null);
                    return new RegistrationListItem(
                            reg.getId(),
                            reg.getActivityId(),
                            reg.getActivity() != null ? reg.getActivity().getTitle() : null,
                            reg.getUserId(),
                            user != null ? user.getUsername() : null,
                            user != null ? user.getRealName() : null,
                            user != null ? user.getPersonalNumber() : null,
                            user != null ? user.getPhone() : null,
                            reg.getStatus(),
                            reg.getSignupTime(),
                            reg.getAuditTime(),
                            reg.getRemark()
                    );
                })
                .collect(Collectors.toList());
        
        return new PageResponse<>(
                items,
                registrationPage.getTotalElements(),
                page,
                size
        );
    }

    @Override
    public void auditRegistration(Long registrationId, String auditStatus, Long auditorId, String remark) {
        Registration registration = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new BusinessException(404, "报名记录不存在"));

        List<String> validStatuses = Arrays.asList("APPROVED", "REJECTED");
        if (!validStatuses.contains(auditStatus)) {
            throw new BusinessException(400, "无效的审核状态");
        }

        registration.setStatus(auditStatus);
        registration.setAuditTime(LocalDateTime.now());
        registration.setAuditBy(auditorId);
        registration.setRemark(remark);

        registrationRepository.save(registration);
    }

    @Override
    public void batchAuditRegistration(Long[] registrationIds, String auditStatus, Long auditorId, String remark) {
        List<String> validStatuses = Arrays.asList("APPROVED", "REJECTED");
        if (!validStatuses.contains(auditStatus)) {
            throw new BusinessException(400, "无效的审核状态");
        }

        for (Long registrationId : registrationIds) {
            registrationRepository.findById(registrationId).ifPresent(registration -> {
                registration.setStatus(auditStatus);
                registration.setAuditTime(LocalDateTime.now());
                registration.setAuditBy(auditorId);
                registration.setRemark(remark);
                registrationRepository.save(registration);
            });
        }
    }
}
