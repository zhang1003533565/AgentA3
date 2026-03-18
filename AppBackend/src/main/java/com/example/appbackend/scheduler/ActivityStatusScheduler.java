package com.example.appbackend.scheduler;

import com.example.appbackend.entity.Activity;
import com.example.appbackend.entity.Activity.Status;
import com.example.appbackend.repository.ActivityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class ActivityStatusScheduler {

    private static final Logger logger = LoggerFactory.getLogger(ActivityStatusScheduler.class);

    @Autowired
    private ActivityRepository activityRepository;

    @Scheduled(fixedRate = 300000)
    @Transactional
    public void updateExpiredActivitiesStatus() {
        List<Activity> expiredActivities = activityRepository.findExpiredActivities(
                LocalDateTime.now(),
                Status.PUBLISHED
        );

        if (!expiredActivities.isEmpty()) {
            for (Activity activity : expiredActivities) {
                activity.setStatus(Status.COMPLETED);
                activityRepository.save(activity);
                logger.info("活动已自动结束: id={}, title={}", activity.getId(), activity.getTitle());
            }
        }
    }
}
