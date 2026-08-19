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
                .orElseThrow(() -> new BusinessException(404, "Activity not found"));

        if (activity.getStatus() != Activity.Status.PUBLISHED) {
            throw new BusinessException(400, "Activity is not published");
        }

        LocalDateTime now = LocalDateTime.now();
        if (activity.getSignupEndTime() != null && now.isAfter(activity.getSignupEndTime())) {
            throw new BusinessException(400, "Signup is closed");
        }
        if (activity.getStartTime() != null && !now.isBefore(activity.getStartTime())) {
            throw new BusinessException(400, "Activity already started");
        }

        if (activity.getMaxPeople() > 0 && activity.getCurrentPeople() >= activity.getMaxPeople()) {
            throw new BusinessException(400, "No seats left");
        }

        if (registrationRepository.existsByActivityIdAndUserId(activityId, userId)) {
            throw new BusinessException(400, "You already registered for this activity");
        }

        Registration registration = new Registration();
        registration.setActivityId(activityId);
        registration.setUserId(userId);
        registration.setStatus(Boolean.TRUE.equals(activity.getRequiresAudit()) ? "PENDING" : "APPROVED");
        registration.setSignupTime(LocalDateTime.now());
        registration.setCreditAuditStatus("PENDING");
        registration.setCreditGranted(false);

        Registration saved = registrationRepository.save(registration);
        activity.setCurrentPeople(activity.getCurrentPeople() + 1);
        activityRepository.save(activity);
        return saved;
    }

    @Override
    public Registration adminRegisterActivity(Long activityId, Long userId) {
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new BusinessException(404, "活动不存在"));

        if (registrationRepository.existsByActivityIdAndUserId(activityId, userId)) {
            throw new BusinessException(400, "该学生已报名此活动");
        }

        if (activity.getMaxPeople() > 0 && activity.getCurrentPeople() >= activity.getMaxPeople()) {
            throw new BusinessException(400, "活动名额已满");
        }

        Registration registration = new Registration();
        registration.setActivityId(activityId);
        registration.setUserId(userId);
        registration.setStatus("APPROVED");
        registration.setSignupTime(LocalDateTime.now());
        registration.setCreditAuditStatus("PENDING");
        registration.setCreditGranted(false);

        Registration saved = registrationRepository.save(registration);
        activity.setCurrentPeople(activity.getCurrentPeople() + 1);
        activityRepository.save(activity);
        return saved;
    }

    @Override
    public Registration cancelRegistration(Long registrationId, Long userId) {
        Registration registration = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new BusinessException(404, "Registration not found"));

        if (!registration.getUserId().equals(userId)) {
            throw new BusinessException(Result.FORBIDDEN_CODE, "No permission to cancel this registration");
        }

        Activity activity = activityRepository.findById(registration.getActivityId())
                .orElseThrow(() -> new BusinessException(404, "Activity not found"));
        boolean needCancelAudit = Boolean.TRUE.equals(activity.getCancelRequiresAudit());

        if ("PENDING".equals(registration.getStatus()) || "CANCEL_PENDING".equals(registration.getStatus())) {
            throw new BusinessException(400, "Cancellation is not allowed in current status");
        }

        if (needCancelAudit) {
            registration.setStatus("CANCEL_PENDING");
            registration.setAuditTime(LocalDateTime.now());
            registration.setAuditBy(userId);
            registration.setRemark("Cancel request pending review");
            return registrationRepository.save(registration);
        }

        activity.setCurrentPeople(Math.max(0, activity.getCurrentPeople() - 1));
        activityRepository.save(activity);
        registrationRepository.delete(registration);
        return registration;
    }

    @Override
    public void removeRegistrationByManager(Long registrationId) {
        Registration registration = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new BusinessException(404, "Registration not found"));

        Activity activity = activityRepository.findById(registration.getActivityId())
                .orElseThrow(() -> new BusinessException(404, "Activity not found"));
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
    public PageResponse<RegistrationListItem> getAllRegistrations(Long activityId, String status, String keyword, Integer page, Integer size) {
        PageRequest pageRequest = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "signupTime"));
        String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        Page<Registration> registrationPage = registrationRepository.searchRegistrations(activityId, status, normalizedKeyword, pageRequest);

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

        return new PageResponse<>(items, registrationPage.getTotalElements(), page, size);
    }

    @Override
    public void auditRegistration(Long registrationId, String auditStatus, Long auditorId, String remark) {
        Registration registration = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new BusinessException(404, "Registration not found"));
        String originalStatus = registration.getStatus();

        List<String> validStatuses = Arrays.asList("APPROVED", "REJECTED");
        if (!validStatuses.contains(auditStatus)) {
            throw new BusinessException(400, "Invalid audit status");
        }

        if ("CANCEL_PENDING".equals(originalStatus)) {
            if ("APPROVED".equals(auditStatus)) {
                Activity activity = activityRepository.findById(registration.getActivityId()).orElse(null);
                if (activity != null) {
                    activity.setCurrentPeople(Math.max(0, activity.getCurrentPeople() - 1));
                    activityRepository.save(activity);
                }
                registrationRepository.delete(registration);
                return;
            }
            registration.setStatus("APPROVED");
            registration.setAuditTime(LocalDateTime.now());
            registration.setAuditBy(auditorId);
            registration.setRemark(remark == null || remark.isBlank() ? "Cancel request rejected, keep registered" : remark);
            registrationRepository.save(registration);
            return;
        }

        registration.setStatus(auditStatus);
        registration.setAuditTime(LocalDateTime.now());
        registration.setAuditBy(auditorId);
        registration.setRemark(remark);
        if ("REJECTED".equals(auditStatus)) {
            registration.setCreditAuditStatus("REJECTED");
            registration.setCreditGranted(false);
            registration.setCreditGrantedTime(null);
        }
        registrationRepository.save(registration);
    }

    @Override
    public void batchAuditRegistration(Long[] registrationIds, String auditStatus, Long auditorId, String remark) {
        List<String> validStatuses = Arrays.asList("APPROVED", "REJECTED");
        if (!validStatuses.contains(auditStatus)) {
            throw new BusinessException(400, "Invalid audit status");
        }

        for (Long registrationId : registrationIds) {
            registrationRepository.findById(registrationId).ifPresent(registration ->
                    auditRegistration(registration.getId(), auditStatus, auditorId, remark)
            );
        }
    }
}
