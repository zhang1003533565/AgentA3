package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "map_place_fence", uniqueConstraints = {
        @UniqueConstraint(name = "uk_map_place_fence_place", columnNames = "place_id")
})
public class MapPlaceFence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Column(name = "geometry_type", nullable = false, length = 20)
    private String geometryType;

    @Column(name = "geometry_data", nullable = false, columnDefinition = "LONGTEXT")
    private String geometryData;

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
