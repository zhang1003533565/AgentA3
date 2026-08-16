package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "map_place_indoor_position", uniqueConstraints = {
        @UniqueConstraint(name = "uk_map_indoor_place_plan", columnNames = {"place_id", "floor_plan_id"})
}, indexes = {
        @Index(name = "idx_map_indoor_plan", columnList = "floor_plan_id")
})
public class MapPlaceIndoorPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Column(name = "floor_plan_id", nullable = false)
    private Long floorPlanId;

    @Column(name = "x_ratio", nullable = false, precision = 7, scale = 4)
    private BigDecimal xRatio;

    @Column(name = "y_ratio", nullable = false, precision = 7, scale = 4)
    private BigDecimal yRatio;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
