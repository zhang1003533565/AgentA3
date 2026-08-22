package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "论坛举报响应")
public class ForumReportResponse {

    private Long id;
    private Long reporterId;
    private String reporterName;
    private Integer targetType;
    private Long targetId;
    private String targetTitle;
    private String targetContent;
    private Long targetAuthorId;
    private String targetAuthor;
    private Integer reasonType;
    private String reasonText;
    private String description;
    private Integer status;
    private String handleAction;
    private String handleResult;
    private Long handleBy;
    private String handleByName;
    private LocalDateTime handleTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
