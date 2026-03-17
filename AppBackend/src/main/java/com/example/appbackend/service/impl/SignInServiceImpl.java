package com.example.appbackend.service.impl;

import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.dto.SignInListItem;
import com.example.appbackend.entity.Activity;
import com.example.appbackend.entity.Registration;
import com.example.appbackend.entity.SignIn;
import com.example.appbackend.entity.User;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.ActivityRepository;
import com.example.appbackend.repository.RegistrationRepository;
import com.example.appbackend.repository.SignInRepository;
import com.example.appbackend.repository.UserRepository;
import com.example.appbackend.service.SignInService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class SignInServiceImpl implements SignInService {

    @Autowired
    private SignInRepository signInRepository;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private UserRepository userRepository;

    @Override
    public void openSignIn(Long activityId) {
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new BusinessException(404, "活动不存在"));

        if (activity.getStatus() != Activity.Status.PUBLISHED && activity.getStatus() != Activity.Status.COMPLETED) {
            throw new BusinessException(400, "活动未发布，无法发起签到");
        }

        if (Boolean.TRUE.equals(activity.getSignInOpen())) {
            throw new BusinessException(400, "签到已开启");
        }

        activity.setSignInOpen(true);
        activityRepository.save(activity);
    }

    @Override
    public void closeSignIn(Long activityId) {
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new BusinessException(404, "活动不存在"));

        if (!Boolean.TRUE.equals(activity.getSignInOpen())) {
            throw new BusinessException(400, "签到未开启");
        }

        activity.setSignInOpen(false);
        activityRepository.save(activity);
    }

    @Override
    public boolean isSignInOpen(Long activityId) {
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new BusinessException(404, "活动不存在"));
        return Boolean.TRUE.equals(activity.getSignInOpen());
    }

    @Override
    public SignIn signIn(Long activityId, Long userId) {
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new BusinessException(404, "活动不存在"));

        if (!Boolean.TRUE.equals(activity.getSignInOpen())) {
            throw new BusinessException(400, "签到未开启，请联系老师开启签到");
        }

        if (activity.getStatus() != Activity.Status.PUBLISHED && activity.getStatus() != Activity.Status.COMPLETED) {
            throw new BusinessException(400, "活动未发布，无法签到");
        }

        LocalDateTime now = LocalDateTime.now();


        if (activity.getEndTime() != null && now.isAfter(activity.getEndTime().plusHours(1))) {
            throw new BusinessException(400, "活动已结束超过1小时，无法签到");
        }

        if (signInRepository.existsByActivityIdAndUserId(activityId, userId)) {
            throw new BusinessException(400, "您已经签到过了");
        }

        Registration registration = registrationRepository.findByActivityIdAndUserId(activityId, userId)
                .orElse(null);

        if (registration != null && !"APPROVED".equals(registration.getStatus())) {
            throw new BusinessException(400, "您的报名未通过审核，无法签到");
        }

        SignIn signIn = new SignIn();
        signIn.setActivityId(activityId);
        signIn.setUserId(userId);
        signIn.setRegistrationId(registration != null ? registration.getId() : null);

       signIn.setSignInStatus(1);

        signIn.setSignInTime(now);

        return signInRepository.save(signIn);
    }

    @Override
    public SignIn supplementSignInByActivityAndUser(Long activityId, Long studentId, Long teacherId) {
        // 验证活动存在
        activityRepository.findById(activityId)
                .orElseThrow(() -> new BusinessException(404, "活动不存在"));

        // 验证学生存在
        userRepository.findById(studentId)
                .orElseThrow(() -> new BusinessException(404, "学生不存在"));

        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new BusinessException(404, "教师不存在"));

        if (teacher.getRole() == null || !"TEACHER".equals(teacher.getRole().getName())) {
            throw new BusinessException(403, "只有教师可以进行补签");
        }

        // 检查是否已存在签到记录
        if (signInRepository.existsByActivityIdAndUserId(activityId, studentId)) {
            throw new BusinessException(400, "该学生已有签到记录，请使用原补签接口");
        }

        Registration registration = registrationRepository.findByActivityIdAndUserId(activityId, studentId)
                .orElse(null);

        SignIn signIn = new SignIn();
        signIn.setActivityId(activityId);
        signIn.setUserId(studentId);
        signIn.setRegistrationId(registration != null ? registration.getId() : null);
        signIn.setSignInStatus(1); // 补签状态为正常
        signIn.setSignInTime(LocalDateTime.now());

        return signInRepository.save(signIn);
    }

    @Override
    public PageResponse<SignInListItem> getActivitySignIns(Long activityId, Integer page, Integer size) {
        PageRequest pageRequest = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "signInTime"));
        Page<SignIn> signInPage = signInRepository.findByActivityId(activityId, pageRequest);
        
        List<SignInListItem> items = signInPage.getContent().stream()
                .map(signIn -> {
                    User user = userRepository.findById(signIn.getUserId()).orElse(null);
                    return new SignInListItem(
                            signIn.getId(),
                            signIn.getActivityId(),
                            signIn.getUserId(),
                            user != null ? user.getUsername() : null,
                            user != null ? user.getRealName() : null,
                            signIn.getSignInTime(),
                            signIn.getSignInStatus()
                    );
                })
                .collect(Collectors.toList());
        
        return new PageResponse<>(
                items,
                signInPage.getTotalElements(),
                page,
                size
        );
    }

    @Override
    public SignIn getSignInStatus(Long activityId, Long userId) {
        return signInRepository.findByActivityIdAndUserId(activityId, userId)
                .orElse(null);
    }
}
