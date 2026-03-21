package com.example.appbackend.scheduler;

import com.example.appbackend.service.ActivityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ActivityStatusScheduler {

    private static final Logger logger = LoggerFactory.getLogger(ActivityStatusScheduler.class);

    @Autowired
    private ActivityService activityService;

    @Scheduled(fixedRate = 60000)
    public void updateExpiredActivitiesStatus() {
        try {
            activityService.updateExpiredActivitiesStatus();
            logger.info("定时任务：已检查并更新过期活动状态");
        } catch (Exception e) {
            logger.error("定时任务：更新过期活动状态失败", e);
        }
    }
}
