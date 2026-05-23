package com.example.appbackend.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "activity")
@Schema(description = "活动实体")
public class Activity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "活动ID", example = "1")
    private Long id;

    @Column(nullable = false, length = 200, columnDefinition = "VARCHAR(200) NOT NULL COMMENT '活动标题'")
    @Schema(description = "活动标题", example = "Python编程讲座", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Column(name = "cover_image", length = 255, columnDefinition = "VARCHAR(255) COMMENT '封面图片URL'")
    @Schema(description = "封面图片URL", example = "https://example.com/cover.jpg")
    private String coverImage;

    @Column(columnDefinition = "TEXT COMMENT '活动图片列表(JSON数组)'")
    @Schema(description = "活动图片列表(JSON数组)", example = "[\"https://example.com/1.jpg\"]")
    private String images;

    @Column(name = "category_id", columnDefinition = "BIGINT COMMENT '分类ID'")
    @Schema(description = "分类ID", example = "1")
    private Long categoryId;

    @Column(name = "organizer_id", columnDefinition = "BIGINT COMMENT '组织者ID'")
    @Schema(description = "组织者ID", example = "1")
    private Long organizerId;

    @Column(name = "organizer_name", length = 100, columnDefinition = "VARCHAR(100) COMMENT '组织者名称'")
    @Schema(description = "组织者名称", example = "计算机学院")
    private String organizerName;

    @Transient
    @Schema(description = "组织者头像", example = "https://example.com/avatar.jpg")
    private String organizerAvatar;

    @Column(columnDefinition = "TEXT COMMENT '活动内容'")
    @Schema(description = "活动内容", example = "本次讲座将介绍Python编程基础知识")
    private String content;

    @Column(length = 200, columnDefinition = "VARCHAR(200) COMMENT '活动地点'")
    @Schema(description = "活动地点", example = "图书馆报告厅")
    private String location;

    @Column(name = "max_people", columnDefinition = "INT DEFAULT 0 COMMENT '最大人数'")
    @Schema(description = "最大人数", example = "100")
    private Integer maxPeople = 0;

    @Column(name = "current_people", columnDefinition = "INT DEFAULT 0 COMMENT '当前报名人数'")
    @Schema(description = "当前报名人数", example = "50")
    private Integer currentPeople = 0;

    @Column(name = "start_time", columnDefinition = "DATETIME COMMENT '活动开始时间'")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "活动开始时间", example = "2026-03-20 14:00:00")
    private LocalDateTime startTime;

    @Column(name = "end_time", columnDefinition = "DATETIME COMMENT '活动结束时间'")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "活动结束时间", example = "2026-03-20 16:00:00")
    private LocalDateTime endTime;

    @Column(name = "signup_start_time", columnDefinition = "DATETIME COMMENT '报名开始时间'")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "报名开始时间", example = "2026-03-15 08:00:00")
    private LocalDateTime signupStartTime;

    @Column(name = "signup_end_time", columnDefinition = "DATETIME COMMENT '报名结束时间'")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "报名结束时间", example = "2026-03-19 18:00:00")
    private LocalDateTime signupEndTime;

    @Column(name = "sign_in_start_time", columnDefinition = "DATETIME COMMENT '签到开始时间'")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "签到开始时间", example = "2026-03-20 13:30:00")
    private LocalDateTime signInStartTime;

    @Column(name = "sign_in_end_time", columnDefinition = "DATETIME COMMENT '签到结束时间'")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "签到结束时间", example = "2026-03-20 15:30:00")
    private LocalDateTime signInEndTime;

    public enum Status {
        DRAFT("草稿"),
        PUBLISHED("进行中"),
        COMPLETED("已结束");

        private final String description;

        Status(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    @Enumerated(EnumType.STRING)
    @Column(length = 20, columnDefinition = "VARCHAR(20) DEFAULT 'DRAFT' COMMENT '活动状态: DRAFT-草稿,  PUBLISHED-已发布, REJECTED-已驳回, CANCELLED-已取消, COMPLETED-已完成'")
    @Schema(description = "活动状态: DRAFT-草稿 PUBLISHED-已发布, REJECTED-已驳回, CANCELLED-已取消, COMPLETED-已完成", example = "DRAFT")
    private Status status = Status.DRAFT;


    @Column(name = "sign_in_type", columnDefinition = "INT DEFAULT 1 COMMENT '签到类型: 1-现场签到, 2-二维码签到'")
    @Schema(description = "签到类型: 1-现场签到, 2-二维码签到", example = "1")
    private Integer signInType = 1;

    @Column(name = "sign_in_open", columnDefinition = "BOOLEAN DEFAULT FALSE COMMENT '签到是否开启'")
    @Schema(description = "签到是否开启", example = "false")
    private Boolean signInOpen = false;

    @Column(name = "requires_audit", columnDefinition = "BOOLEAN DEFAULT FALSE COMMENT '报名是否需要审核'")
    @Schema(description = "报名是否需要审核", example = "false")
    private Boolean requiresAudit = false;

    @Column(name = "cancel_requires_audit", columnDefinition = "BOOLEAN DEFAULT FALSE COMMENT '取消报名是否需要审核'")
    @Schema(description = "取消报名是否需要审核", example = "false")
    private Boolean cancelRequiresAudit = false;

    @Column(precision = 3, scale = 1, columnDefinition = "DECIMAL(3,1) DEFAULT 0 COMMENT '活动学分'")
    @Schema(description = "活动学分", example = "2.0")
    private BigDecimal score = BigDecimal.ZERO;

    @Column(name = "credit_config", columnDefinition = "TEXT COMMENT '角色学分分配(JSON)'")
    @Schema(description = "角色学分分配(JSON)", example = "[{\"role\":\"主持人\",\"score\":1.5},{\"role\":\"观众\",\"score\":0.2}]")
    private String creditConfig;

    @Column(name = "contact_name", length = 50, columnDefinition = "VARCHAR(50) COMMENT '联系人姓名'")
    @Schema(description = "联系人姓名", example = "张老师")
    private String contactName;

    @Column(name = "contact_phone", length = 20, columnDefinition = "VARCHAR(20) COMMENT '联系电话'")
    @Schema(description = "联系电话", example = "13800138000")
    private String contactPhone;

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '创建时间'")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", insertable = false, updatable = false)
    private ActivityCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizer_id", insertable = false, updatable = false)
    private User organizer;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}
