package com.example.appbackend.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SignInListItem {
    private Long id;
    private Long activityId;
    private Long userId;
    private String username;
    private String realName;
    private LocalDateTime signInTime;
    private Integer signInStatus;

    public SignInListItem(Long id, Long activityId, Long userId, String username, 
                          String realName, LocalDateTime signInTime, Integer signInStatus) {
        this.id = id;
        this.activityId = activityId;
        this.userId = userId;
        this.username = username;
        this.realName = realName;
        this.signInTime = signInTime;
        this.signInStatus = signInStatus;
    }
}
