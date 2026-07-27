package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "map_place", indexes = {
        @Index(name = "idx_map_place_parent", columnList = "parent_id"),
        @Index(name = "idx_map_place_scene", columnList = "scene_type"),
        @Index(name = "idx_map_place_type", columnList = "place_type"),
        @Index(name = "idx_map_place_location", columnList = "longitude,latitude")
})
public class MapPlace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "scene_type", nullable = false, length = 32)
    private String sceneType;

    @Column(name = "place_type", nullable = false, length = 32)
    private String placeType;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 20)
    private String status = "ENABLED";

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "location_desc", length = 255)
    private String locationDesc;

    @Column(name = "map_visible", nullable = false)
    private Boolean mapVisible = true;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

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
