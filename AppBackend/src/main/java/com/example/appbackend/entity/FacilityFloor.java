package com.example.appbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(
        name = "facility_floor",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_facility_floor_facility_name",
                columnNames = {"facility_id", "floor_name"}
        ),
        indexes = @Index(name = "idx_facility_floor_facility", columnList = "facility_id")
)
public class FacilityFloor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "facility_id", nullable = false)
    private Long facilityId;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facility_id", insertable = false, updatable = false)
    private CampusFacility facility;

    @Column(name = "floor_name", nullable = false, length = 30)
    private String floorName;

    @Column(nullable = false)
    private Integer status = 1;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
