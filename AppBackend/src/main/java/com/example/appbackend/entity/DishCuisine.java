package com.example.appbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(
        name = "dish_cuisine",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_dish_cuisine_canteen_name",
                columnNames = {"canteen_place_id", "cuisine_name"}
        ),
        indexes = @Index(name = "idx_dish_cuisine_canteen", columnList = "canteen_place_id")
)
public class DishCuisine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "canteen_place_id", nullable = false)
    private Long canteenPlaceId;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "canteen_place_id", insertable = false, updatable = false)
    private MapPlace canteenPlace;

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
