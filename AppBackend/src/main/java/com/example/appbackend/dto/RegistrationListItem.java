package com.example.appbackend.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class RegistrationListItem {
    private Long id;
    private Long activityId;
    private String activityTitle;
    private Long userId;
    private String username;
    private String realName;
    private String personalNumber;
    private String phone;
    private String status;
    private LocalDateTime signupTime;
    private LocalDateTime auditTime;
    private String remark;

    public RegistrationListItem(Long id, Long activityId, String activityTitle, Long userId, String username,
                               String realName, String personalNumber, String phone, String status, LocalDateTime signupTime,
                               LocalDateTime auditTime, String remark) {
        this.id = id;
        this.activityId = activityId;
        this.activityTitle = activityTitle;
        this.userId = userId;
        this.username = username;
        this.realName = realName;
        this.personalNumber = personalNumber;
        this.phone = phone;
        this.status = status;
        this.signupTime = signupTime;
        this.auditTime = auditTime;
        this.remark = remark;
    }
}
