package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "activity")
public class Activity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "cover_image", length = 255)
    private String coverImage;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "organizer_id")
    private Long organizerId;

    @Column(name = "organizer_name", length = 100)
    private String organizerName;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(length = 200)
    private String location;

    @Column(name = "max_people")
    private Integer maxPeople = 0;

    @Column(name = "current_people")
    private Integer currentPeople = 0;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "signup_start_time")
    private LocalDateTime signupStartTime;

    @Column(name = "signup_end_time")
    private LocalDateTime signupEndTime;

    @Column(length = 20)
    private String status = "DRAFT";

    @Column(name = "need_audit")
    private Integer needAudit = 0;

    @Column(name = "sign_in_type")
    private Integer signInType = 1;

    @Column(precision = 3, scale = 1)
    private BigDecimal score = BigDecimal.ZERO;

    @Column(name = "contact_name", length = 50)
    private String contactName;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @Column(name = "create_time")
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
