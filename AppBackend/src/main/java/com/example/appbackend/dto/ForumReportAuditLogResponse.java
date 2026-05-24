package com.example.appbackend.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ForumReportAuditLogResponse {

    private Long id;
    private Long reportId;
    private String action;
    private Long operatorId;
    private String operatorName;
    private Integer targetType;
    private Long targetId;
    private String remark;
    private LocalDateTime createTime;
}
