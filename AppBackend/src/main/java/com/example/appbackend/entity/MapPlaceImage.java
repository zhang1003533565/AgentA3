package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "map_place_image", indexes = {
        @Index(name = "idx_map_place_image_place", columnList = "place_id")
})
public class MapPlaceImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
