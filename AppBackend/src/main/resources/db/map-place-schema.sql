-- 精简后的校园设施空间模型。
-- 项目默认由 Hibernate ddl-auto=update 创建表；此文件用于数据库评审或手工初始化。

CREATE TABLE IF NOT EXISTS map_place (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    parent_id BIGINT NULL,
    scene_type VARCHAR(32) NOT NULL,
    place_type VARCHAR(32) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
    longitude DECIMAL(10,7) NULL,
    latitude DECIMAL(10,7) NULL,
    location_desc VARCHAR(255) NULL,
    map_visible TINYINT(1) NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_map_place_parent (parent_id),
    INDEX idx_map_place_scene (scene_type),
    INDEX idx_map_place_type (place_type),
    INDEX idx_map_place_location (longitude, latitude),
    CONSTRAINT fk_map_place_parent FOREIGN KEY (parent_id) REFERENCES map_place(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS map_place_image (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    place_id BIGINT NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    focus_x INT NOT NULL DEFAULT 50,
    focus_y INT NOT NULL DEFAULT 50,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_map_place_image_place (place_id),
    CONSTRAINT fk_map_place_image_place FOREIGN KEY (place_id) REFERENCES map_place(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS map_place_fence (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    place_id BIGINT NOT NULL,
    geometry_type VARCHAR(20) NOT NULL,
    geometry_data JSON NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_map_place_fence_place (place_id),
    CONSTRAINT fk_map_place_fence_place FOREIGN KEY (place_id) REFERENCES map_place(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS map_floor_plan (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    floor_place_id BIGINT NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_map_floor_plan_floor (floor_place_id),
    CONSTRAINT fk_map_floor_plan_floor FOREIGN KEY (floor_place_id) REFERENCES map_place(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS map_place_indoor_position (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    place_id BIGINT NOT NULL,
    floor_plan_id BIGINT NOT NULL,
    x_ratio DECIMAL(7,4) NOT NULL,
    y_ratio DECIMAL(7,4) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_map_indoor_place_plan (place_id, floor_plan_id),
    INDEX idx_map_indoor_plan (floor_plan_id),
    CONSTRAINT fk_map_indoor_place FOREIGN KEY (place_id) REFERENCES map_place(id),
    CONSTRAINT fk_map_indoor_plan FOREIGN KEY (floor_plan_id) REFERENCES map_floor_plan(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
