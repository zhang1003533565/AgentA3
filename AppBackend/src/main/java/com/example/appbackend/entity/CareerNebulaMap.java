package com.example.appbackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "career_nebula_map")
public class CareerNebulaMap {

    @Id
    @Column(name = "map_key", nullable = false, length = 40)
    private String mapKey = "default";

    @Lob
    @Column(name = "payload_json", nullable = false, columnDefinition = "LONGTEXT")
    private String payloadJson;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void updateTimestamp() {
        updatedAt = LocalDateTime.now();
    }
}
