package com.example.appbackend.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class RegistrationListItem {
    private Long id;
    private Long activityId;
    private Long userId;
    private String username;
    private String realName;
    private String status;
    private LocalDateTime signupTime;
    private LocalDateTime auditTime;
    private String remark;

    public RegistrationListItem(Long id, Long activityId, Long userId, String username, 
                               String realName, String status, LocalDateTime signupTime, 
                               LocalDateTime auditTime, String remark) {
        this.id = id;
        this.activityId = activityId;
        this.userId = userId;
        this.username = username;
        this.realName = realName;
        this.status = status;
        this.signupTime = signupTime;
        this.auditTime = auditTime;
        this.remark = remark;
    }
}
