package com.example.appbackend.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SignInListItem {
    private Long id;
    private Long activityId;
    private Long registrationId;
    private String activityTitle;
    private Long userId;
    private String username;
    private String realName;
    private String personalNumber;
    private String phone;
    private LocalDateTime signInTime;
    private Integer signInStatus;
    private Integer signInType;
    private String reviewStatus;
    private String reviewRemark;

    public SignInListItem(Long id, Long activityId, Long registrationId, String activityTitle, Long userId, String username,
                          String realName, String personalNumber, String phone, LocalDateTime signInTime, Integer signInStatus,
                          Integer signInType, String reviewStatus, String reviewRemark) {
        this.id = id;
        this.activityId = activityId;
        this.registrationId = registrationId;
        this.activityTitle = activityTitle;
        this.userId = userId;
        this.username = username;
        this.realName = realName;
        this.personalNumber = personalNumber;
        this.phone = phone;
        this.signInTime = signInTime;
        this.signInStatus = signInStatus;
        this.signInType = signInType;
        this.reviewStatus = reviewStatus;
        this.reviewRemark = reviewRemark;
    }
}
