package com.example.appbackend.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "public_facility")
@Schema(description = "公共设施实体")
public class PublicFacility {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "设施ID", example = "1")
    private Long id;

    @Column(name = "name", nullable = false, length = 100, columnDefinition = "VARCHAR(100) NOT NULL COMMENT '设施名称'")
    @Schema(description = "设施名称", example = "东门自行车停放点", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Column(name = "type", nullable = false, length = 32, columnDefinition = "VARCHAR(32) NOT NULL COMMENT '设施类型'")
    @Schema(description = "设施类型: BENCH-长椅 STREET_LAMP-路灯 TRASH_BIN-垃圾桶 WATER_DISPENSER-饮水机 BICYCLE_RACK-自行车停放点 OTHER-其他", example = "BICYCLE_RACK")
    private String type;

    @Column(name = "location", length = 200, columnDefinition = "VARCHAR(200) COMMENT '位置描述'")
    @Schema(description = "位置描述", example = "东门入口左侧")
    private String location;

    @Column(name = "description", columnDefinition = "TEXT COMMENT '详细描述'")
    @Schema(description = "详细描述", example = "可容纳20辆自行车，24小时开放")
    private String description;

    @Column(name = "status", nullable = false, length = 16, columnDefinition = "VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态'")
    @Schema(description = "状态: ACTIVE-正常 MAINTENANCE-维护中 INACTIVE-停用", example = "ACTIVE")
    private String status = "ACTIVE";

    @Column(name = "latitude", precision = 18, scale = 14, columnDefinition = "DECIMAL(18,14) COMMENT '纬度'")
    @Schema(description = "纬度", example = "39.90923")
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 18, scale = 14, columnDefinition = "DECIMAL(18,14) COMMENT '经度'")
    @Schema(description = "经度", example = "116.397428")
    private BigDecimal longitude;

    @Column(name = "distance", columnDefinition = "INT COMMENT '距离(米)'")
    @Schema(description = "距离，单位米", example = "150")
    private Integer distance;

    @Column(name = "image_url", length = 500, columnDefinition = "VARCHAR(500) COMMENT '图片URL'")
    @Schema(description = "图片URL", example = "https://example.com/facility.jpg")
    private String imageUrl;

    @Column(name = "created_at", columnDefinition = "DATETIME COMMENT '创建时间'")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", columnDefinition = "DATETIME COMMENT '更新时间'")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = "ACTIVE";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum FacilityType {
        BENCH("BENCH", "长椅", "🪑"),
        STREET_LAMP("STREET_LAMP", "路灯", "💡"),
        TRASH_BIN("TRASH_BIN", "垃圾桶", "🗑️"),
        WATER_DISPENSER("WATER_DISPENSER", "饮水机", "🚰"),
        BICYCLE_RACK("BICYCLE_RACK", "自行车停放点", "🚲"),
        OTHER("OTHER", "其他", "📍");

        private final String code;
        private final String label;
        private final String emoji;

        FacilityType(String code, String label, String emoji) {
            this.code = code;
            this.label = label;
            this.emoji = emoji;
        }

        public String getCode() { return code; }
        public String getLabel() { return label; }
        public String getEmoji() { return emoji; }

        public static FacilityType fromCode(String code) {
            if (code == null) return OTHER;
            for (FacilityType t : values()) {
                if (t.code.equalsIgnoreCase(code)) return t;
            }
            return OTHER;
        }
    }

    public enum FacilityStatus {
        ACTIVE("ACTIVE", "正常"),
        MAINTENANCE("MAINTENANCE", "维护中"),
        INACTIVE("INACTIVE", "停用");

        private final String code;
        private final String label;

        FacilityStatus(String code, String label) {
            this.code = code;
            this.label = label;
        }

        public String getCode() { return code; }
        public String getLabel() { return label; }

        public static FacilityStatus fromCode(String code) {
            if (code == null) return ACTIVE;
            for (FacilityStatus s : values()) {
                if (s.code.equalsIgnoreCase(code)) return s;
            }
            return ACTIVE;
        }
    }
}
