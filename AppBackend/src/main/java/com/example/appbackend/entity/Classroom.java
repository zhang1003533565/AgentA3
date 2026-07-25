package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "classroom", uniqueConstraints = @UniqueConstraint(columnNames = {"building_id", "room_no"}))
public class Classroom {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "building_id", nullable = false)
    private Long buildingId;

    @Column(name = "room_no", nullable = false, length = 50)
    private String roomNo;

    @Column(name = "floor_no", nullable = false)
    private Integer floorNo;

    @Column(name = "seat_count", nullable = false)
    private Integer seatCount = 0;

    @Column(name = "is_smart", nullable = false)
    private Boolean smart = false;

    @Column(nullable = false)
    private Integer status = 1;

    @Column(name = "open_time", length = 100)
    private String openTime;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = createTime;
    }

    @PreUpdate
    void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
