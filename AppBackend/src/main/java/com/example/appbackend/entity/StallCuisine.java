package com.example.appbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(
        name = "stall_cuisine",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_stall_cuisine_restaurant_name",
                columnNames = {"restaurant_id", "cuisine_name"}
        ),
        indexes = @Index(name = "idx_stall_cuisine_restaurant", columnList = "restaurant_id")
)
public class StallCuisine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", insertable = false, updatable = false)
    private CampusFacility restaurant;

    @Column(name = "cuisine_name", nullable = false, length = 50)
    private String cuisineName;

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
