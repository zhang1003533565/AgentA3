-- =============================================
-- 智慧校园系统初始化数据
-- =============================================

-- =============================================
-- 第一部分：表创建语句（如果不存在则创建）
-- =============================================

-- =============================================
-- 校园设施表 - 确保 status 列存在（新增字段，旧库兼容）
-- =============================================
SET @col_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'campus_facility'
      AND COLUMN_NAME = 'status'
);
SET @alter_status_sql = IF(
    @col_exists > 0,
    'SELECT 1',
    'ALTER TABLE campus_facility ADD COLUMN status INT NOT NULL DEFAULT 1 COMMENT ''设施状态: 1-正常/开放 2-维护中 3-关闭/不可用'' AFTER facility_type'
);
PREPARE alter_stmt FROM @alter_status_sql;
EXECUTE alter_stmt;
DEALLOCATE PREPARE alter_stmt;

SET @image_x_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'campus_facility'
      AND COLUMN_NAME = 'image_x'
);
SET @add_image_x_sql = IF(
    @image_x_exists > 0,
    'SELECT 1',
    'ALTER TABLE campus_facility ADD COLUMN image_x DECIMAL(8,6) COMMENT ''地图图片横向坐标(0-1)'' AFTER latitude'
);
PREPARE add_image_x_stmt FROM @add_image_x_sql;
EXECUTE add_image_x_stmt;
DEALLOCATE PREPARE add_image_x_stmt;

SET @image_y_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'campus_facility'
      AND COLUMN_NAME = 'image_y'
);
SET @add_image_y_sql = IF(
    @image_y_exists > 0,
    'SELECT 1',
    'ALTER TABLE campus_facility ADD COLUMN image_y DECIMAL(8,6) COMMENT ''地图图片纵向坐标(0-1)'' AFTER image_x'
);
PREPARE add_image_y_stmt FROM @add_image_y_sql;
EXECUTE add_image_y_stmt;
DEALLOCATE PREPARE add_image_y_stmt;

ALTER TABLE campus_facility
    MODIFY COLUMN longitude DECIMAL(18,14) COMMENT '经度',
    MODIFY COLUMN latitude DECIMAL(18,14) COMMENT '纬度';

-- 校园设施表
CREATE TABLE IF NOT EXISTS campus_facility (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '设施ID',
    facility_name VARCHAR(100) NOT NULL COMMENT '设施名称',
    facility_type INT NOT NULL COMMENT '设施类型: 1-餐厅 2-运动场 3-教学楼 4-宿舍',
    status INT NOT NULL DEFAULT 1 COMMENT '设施状态: 1-正常/开放 2-维护中 3-关闭/不可用',
    description TEXT COMMENT '设施描述',
    location VARCHAR(200) COMMENT '位置描述',
    longitude DECIMAL(18,14) COMMENT '经度',
    latitude DECIMAL(18,14) COMMENT '纬度',
    image_x DECIMAL(8,6) COMMENT '地图图片横向坐标(0-1)',
    image_y DECIMAL(8,6) COMMENT '地图图片纵向坐标(0-1)',
    geometry_type VARCHAR(16) NOT NULL DEFAULT 'POINT' COMMENT '空间形态: POINT-点位 AREA-区域围栏',
    boundary_points TEXT COMMENT '区域围栏坐标(JSON二维数组)',
    images TEXT COMMENT '图片列表(JSON数组)',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='校园设施表';

-- 兼容旧库：补充设施空间形态与区域围栏字段
SET @geometry_type_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'campus_facility'
      AND COLUMN_NAME = 'geometry_type'
);
SET @add_geometry_type_sql = IF(
    @geometry_type_exists > 0,
    'SELECT 1',
    'ALTER TABLE campus_facility ADD COLUMN geometry_type VARCHAR(16) NOT NULL DEFAULT ''POINT'' COMMENT ''空间形态: POINT-点位 AREA-区域围栏'' AFTER image_y'
);
PREPARE add_geometry_type_stmt FROM @add_geometry_type_sql;
EXECUTE add_geometry_type_stmt;
DEALLOCATE PREPARE add_geometry_type_stmt;

SET @boundary_points_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'campus_facility'
      AND COLUMN_NAME = 'boundary_points'
);
SET @add_boundary_points_sql = IF(
    @boundary_points_exists > 0,
    'SELECT 1',
    'ALTER TABLE campus_facility ADD COLUMN boundary_points TEXT COMMENT ''区域围栏坐标(JSON二维数组)'' AFTER geometry_type'
);
PREPARE add_boundary_points_stmt FROM @add_boundary_points_sql;
EXECUTE add_boundary_points_stmt;
DEALLOCATE PREPARE add_boundary_points_stmt;

-- 教室是教学楼内部子资源，不参与地图一级点位与分类
CREATE TABLE IF NOT EXISTS classroom (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '教室 ID',
    building_id BIGINT NOT NULL COMMENT '所属教学楼设施 ID',
    room_no VARCHAR(50) NOT NULL COMMENT '教室编号',
    floor_no INT NOT NULL COMMENT '所在楼层',
    seat_count INT NOT NULL DEFAULT 0 COMMENT '座位数',
    is_smart BIT NOT NULL DEFAULT 0 COMMENT '是否多媒体教室',
    status INT NOT NULL DEFAULT 1 COMMENT '状态: 1-空闲 2-使用中 3-维护中',
    open_time VARCHAR(100) COMMENT '开放时间',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    UNIQUE KEY uk_classroom_building_room (building_id, room_no),
    CONSTRAINT fk_classroom_building FOREIGN KEY (building_id) REFERENCES campus_facility(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教学楼教室子资源';

-- 设施评价表
CREATE TABLE IF NOT EXISTS facility_review (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '评价ID',
    facility_id BIGINT NOT NULL COMMENT '设施ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    score INT NOT NULL COMMENT '评分: 1-5',
    content TEXT COMMENT '评价内容',
    images TEXT COMMENT '图片列表(JSON数组)',
    create_time DATETIME COMMENT '创建时间',
    FOREIGN KEY (facility_id) REFERENCES campus_facility(id),
    FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设施评价表';

-- 兼容旧库：确保 sys_user 表有 status 列（旧版数据库可能缺少）
SET @status_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_user'
      AND COLUMN_NAME = 'status'
);

SET @add_status_sql = IF(
    @status_exists > 0,
    'SELECT 1',
    'ALTER TABLE sys_user ADD COLUMN status INT NOT NULL DEFAULT 1 COMMENT ''状态: 1-正常, 0-禁用'''
);

PREPARE add_status_stmt FROM @add_status_sql;
EXECUTE add_status_stmt;
DEALLOCATE PREPARE add_status_stmt;

-- 地图标记表 - 清理旧description列（如存在）
SET @desc_col_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'map_marker'
      AND COLUMN_NAME = 'description'
);
SET @drop_desc_sql = IF(@desc_col_exists > 0, 'ALTER TABLE map_marker DROP COLUMN description', 'SELECT 1');
PREPARE drop_desc_stmt FROM @drop_desc_sql;
EXECUTE drop_desc_stmt;
DEALLOCATE PREPARE drop_desc_stmt;

-- 地图标记表
CREATE TABLE IF NOT EXISTS map_marker (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '标记ID',
    facility_id BIGINT NOT NULL COMMENT '关联设施ID',
    icon_url VARCHAR(255) COMMENT '自定义图标URL',
    sort INT DEFAULT 0 COMMENT '排序',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    FOREIGN KEY (facility_id) REFERENCES campus_facility(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='地图标记表';

-- 地图配置表
CREATE TABLE IF NOT EXISTS map_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '配置ID',
    config_key VARCHAR(100) NOT NULL UNIQUE COMMENT '配置键',
    config_value TEXT COMMENT '配置值',
    description VARCHAR(255) COMMENT '配置说明',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='地图配置表';

-- 导航记录表
CREATE TABLE IF NOT EXISTS navigation_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '导航记录ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    from_longitude DECIMAL(10,7) COMMENT '起点经度',
    from_latitude DECIMAL(10,7) COMMENT '起点纬度',
    to_marker_id BIGINT NOT NULL COMMENT '目标标记ID',
    distance DECIMAL(10,2) COMMENT '导航距离（米）',
    duration INT COMMENT '预计时长（秒）',
    status INT NOT NULL DEFAULT 1 COMMENT '状态: 1-进行中 2-已完成 3-已取消',
    arrive_time DATETIME COMMENT '实际到达时间',
    create_time DATETIME COMMENT '创建时间',
    FOREIGN KEY (user_id) REFERENCES sys_user(id),
    FOREIGN KEY (to_marker_id) REFERENCES map_marker(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='导航记录表';

-- 收藏目的地表
CREATE TABLE IF NOT EXISTS favorite_destination (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '收藏ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    marker_id BIGINT NOT NULL COMMENT '标记ID',
    marker_name VARCHAR(100) COMMENT '标记名称（快照）',
    longitude DECIMAL(10,7) COMMENT '经度（快照）',
    latitude DECIMAL(10,7) COMMENT '纬度（快照）',
    facility_type INT COMMENT '设施类型（快照）',
    remark VARCHAR(100) COMMENT '用户备注',
    create_time DATETIME COMMENT '创建时间',
    UNIQUE KEY uk_user_marker (user_id, marker_id),
    FOREIGN KEY (user_id) REFERENCES sys_user(id),
    FOREIGN KEY (marker_id) REFERENCES map_marker(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏目的地表';

-- APP消息中心聚合消息表
CREATE TABLE IF NOT EXISTS app_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '消息ID',
    user_id BIGINT NOT NULL COMMENT '接收用户ID',
    module_type VARCHAR(32) NOT NULL COMMENT '模块类型：LOST_FOUND/FORUM/EXAM/MEETING/LEARNING',
    event_type VARCHAR(64) NOT NULL COMMENT '事件类型',
    title VARCHAR(128) NOT NULL COMMENT '消息标题',
    content VARCHAR(512) DEFAULT NULL COMMENT '消息内容',
    target_page VARCHAR(255) DEFAULT NULL COMMENT '点击跳转页面',
    target_params VARCHAR(1000) DEFAULT NULL COMMENT '跳转参数JSON',
    source_id BIGINT DEFAULT NULL COMMENT '来源记录ID',
    source_type VARCHAR(64) DEFAULT NULL COMMENT '来源类型',
    is_read TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已读',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    read_time DATETIME DEFAULT NULL COMMENT '阅读时间',
    UNIQUE KEY uk_app_message_source_user_event (source_type, source_id, user_id, event_type),
    KEY idx_app_message_user_time (user_id, create_time),
    KEY idx_app_message_user_read (user_id, is_read),
    KEY idx_app_message_module (module_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='APP消息中心聚合消息表';

-- =============================================
-- 第二部分：清空表数据（注意顺序，先删除有外键依赖的表）
-- =============================================
SET FOREIGN_KEY_CHECKS = 0;

-- 先清空地图/导航/评价表
TRUNCATE TABLE app_message;
TRUNCATE TABLE favorite_destination;
TRUNCATE TABLE navigation_log;
TRUNCATE TABLE map_config;
TRUNCATE TABLE map_marker;
TRUNCATE TABLE facility_review;
TRUNCATE TABLE campus_facility;

-- 再清空原有表
TRUNCATE TABLE sys_user;
TRUNCATE TABLE sys_role;

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================
-- 1. 角色数据
-- =============================================
INSERT INTO sys_role (id, name) VALUES
(1, 'ADMIN'),
(2, 'TEACHER'),
(3, 'STUDENT'),
(4, 'MERCHANT');

-- =============================================
-- 2. 用户数据 (密码都是 admin123)
-- =============================================
INSERT INTO sys_user (id, username, password, real_name, phone, email, role_id, status, create_time, update_time,jwx_password,jwx_student_id,semester_start,share_code) VALUES
-- 管理员 (用户名：admin, 密码：admin123)
(1, 'admin', 'admin123', '系统管理员', '13800000001', 'admin@campus.edu.cn', 1, 1, NOW(), NOW(),'313','32132313','2026-02-24','SCH000001'),
-- 新增测试管理员 (用户名：test_admin_20260821, 密码：admin123)
(14, 'test_admin_20260821', 'admin123', '测试管理员', '13900000098', 'test_admin@campus.edu.cn', 1, 1, NOW(), NOW(),'313','32132313','2026-02-24','SCH000014'),
-- 教师 (用户名: fjj, 密码: admin123)
(2, 'fjj', 'admin123', '张老师', '13800000002', 'zhanglaoshi@campus.edu.cn', 2, 1, NOW(), NOW(),'313','32132313','2026-02-24','SCH000002'),
-- 教师 (用户名: fjj2, 密码: admin123)
(3, 'fjj2', 'admin123', '李老师', '13800000003', 'lilaoshi@campus.edu.cn', 2, 1, NOW(), NOW(),'313','32132313','2026-02-24','SCH000003'),
-- 学生 (用户名: zzs, 密码: admin123)
(4, 'zzs', 'admin123', 'A3演示学生', '13800000000', 'a3-demo@example.invalid', 3, 1, NOW(), NOW(),'','A3DEMO001','2026-02-24','SCH000004'),
-- 学生 (用户名: qb_peer, 密码: admin123) — 题库公私可见性对照账号
(13, 'qb_peer', 'admin123', '题库对照学生', '13900000099', 'qb_peer@stu.campus.edu.cn', 3, 1, NOW(), NOW(),'','QBPEER001','2026-02-24','SCHQBPEER1'),
-- 学生 (用户名: lisi, 密码: admin123)
(5, 'lisi', 'admin123', '李四', '13800000005', 'lisi@stu.campus.edu.cn', 3, 1, NOW(), NOW(),'313','32132313','2026-02-24','SCH000005'),
-- 学生 (用户名: wangwu, 密码: admin123)
(6, 'wangwu', 'admin123', '王五', '13800000006', 'wangwu@stu.campus.edu.cn', 3, 1, NOW(), NOW(),'313','32132313','2026-02-24','SCH000006'),
-- 学生 (用户名: zhaoliu, 密码: admin123)
(7, 'zhaoliu', 'admin123', '赵六', '13800000007', 'zhaoliu@stu.campus.edu.cn', 3, 1, NOW(), NOW(),'313','32132313','2026-02-24','SCH000007'),
-- 学生 (用户名: student05, 密码: admin123)
(8, 'student05', 'admin123', '钱七', '13800000008', 'qianqi@stu.campus.edu.cn', 3, 1, NOW(), NOW(),'313','32132313','2026-02-24','SCH000008'),
-- 商家用户 (用户名: merchant01~04, 密码: admin123, role_id=4)
(9, 'merchant01', 'admin123', '学府餐厅老板', '13812345601', 'lishilaoban@campus.edu.cn', 4, 1, NOW(), NOW(),'313','32132313','2026-02-24','SCH000009'),
(10, 'merchant02', 'admin123', '书香咖啡老板', '13812345602', 'wanglaoban@campus.edu.cn', 4, 1, NOW(), NOW(),'313','32132313','2026-02-24','SCH000010'),
(11, 'merchant03', 'admin123', '校园超市老板', '13812345603', 'zhanglaoban@campus.edu.cn', 4, 1, NOW(), NOW(),'313','32132313','2026-02-24','SCH000011'),
(12, 'merchant04', 'admin123', '快印图文老板', '13812345604', 'zhaolaoban@campus.edu.cn', 4, 1, NOW(), NOW(),'313','32132313','2026-02-24','SCH000012'),
(13, '20233090117', 'Liu007517!', '刘子鋆', '18330177876', '18330177876@163.com', 3, 1, NOW(), NOW(),'Liu007517!','20233090117','2026-03-02','SCH2026030001');
UPDATE sys_user SET
    avatar = 'https://picsum.photos/seed/avatar-admin/240/240',
    personal_number = 'A20260001',
    college = '信息化管理中心',
    major = '系统运维',
    class_name = '管理员组'
WHERE id = 1;

UPDATE sys_user SET
    avatar = 'https://picsum.photos/seed/avatar-teacher-1/240/240',
    personal_number = 'T20260001',
    college = '信息工程学院',
    major = '软件工程',
    class_name = '教师组'
WHERE id = 2;

UPDATE sys_user SET
    avatar = 'https://picsum.photos/seed/avatar-teacher-2/240/240',
    personal_number = 'T20260002',
    college = '管理学院',
    major = '工商管理',
    class_name = '教师组'
WHERE id = 3;

UPDATE sys_user SET
    avatar = 'https://picsum.photos/seed/avatar-student-zzs/240/240',
    personal_number = '20233090001',
    college = '计算机与人工智能学院',
    major = '软件工程',
    class_name = '软件2301班'
WHERE id = 4;

UPDATE sys_user SET
    avatar = 'https://picsum.photos/seed/avatar-student-lisi/240/240',
    personal_number = '20233090002',
    college = '计算机与人工智能学院',
    major = '数据科学与大数据技术',
    class_name = '数科2302班'
WHERE id = 5;

UPDATE sys_user SET
    avatar = 'https://picsum.photos/seed/avatar-student-wangwu/240/240',
    personal_number = '20233090003',
    college = '经济学院',
    major = '金融学',
    class_name = '金融2301班'
WHERE id = 6;

UPDATE sys_user SET
    avatar = 'https://picsum.photos/seed/avatar-student-zhaoliu/240/240',
    personal_number = '20233090004',
    college = '外国语学院',
    major = '英语',
    class_name = '英语2301班'
WHERE id = 7;

UPDATE sys_user SET
    avatar = 'https://picsum.photos/seed/avatar-student-qianqi/240/240',
    personal_number = '20233090005',
    college = '建筑与设计学院',
    major = '环境设计',
    class_name = '环设2302班'
WHERE id = 8;

UPDATE sys_user SET
    avatar = 'https://picsum.photos/seed/avatar-student-liuziyun/240/240',
    personal_number = '20233090117',
    college = '计算机与人工智能学院',
    major = '网络工程',
    class_name = '网工2301班'
WHERE id = 13;

-- =============================================
-- 3~9. 旧模块（活动/论坛）初始化数据
-- 为避免历史库字段不一致导致启动失败，暂不在 data.sql 中写入这些测试数据
-- =============================================

-- =============================================
-- 第三部分：第三阶段 - 校园设施模块数据
-- =============================================

-- =============================================
-- 校园设施数据
-- =============================================
INSERT INTO campus_facility (id, facility_name, facility_type, status, description, location, longitude, latitude, image_x, image_y, images, create_time, update_time) VALUES
-- 食堂 (类型1，状态1=正常/开放)，images 为占位图；管理端上传后会覆盖为 COS 地址
(1, '学一食堂', 1, 1, '河北建筑工程学院朝阳校区学生食堂。', '朝阳校区', 114.899741, 40.755538, NULL, NULL, '["https://picsum.photos/seed/canteen-1/800/600"]', NOW(), NOW()),
(2, '学二食堂', 1, 1, '河北建筑工程学院朝阳校区学生食堂。', '朝阳校区', 114.899888, 40.756985, NULL, NULL, '["https://picsum.photos/seed/canteen-2/800/600"]', NOW(), NOW()),
(3, '学三食堂', 1, 1, '河北建筑工程学院朝阳校区学生食堂。', '朝阳校区', 114.899948, 40.754134, NULL, NULL, '["https://picsum.photos/seed/canteen-3/800/600"]', NOW(), NOW()),
(11, '蜜雪冰城', 1, 1, '校园商业餐饮服务点。', '朝阳校区', 114.900029, 40.756121, NULL, NULL, '["https://picsum.photos/seed/shop-11/800/600"]', NOW(), NOW()),
-- 运动场 (类型2，状态: 1=正常 2=维护中 3=关闭)
(4, '东区运动场', 2, 1, '东区综合运动场地。', '朝阳校区东区', 114.899512, 40.758585, NULL, NULL, '["https://picsum.photos/seed/sports-4/800/600"]', NOW(), NOW()),
(5, '篮球场', 2, 2, '室外篮球场，维护中。', '朝阳校区', 114.896483, 40.752178, NULL, NULL, '["https://picsum.photos/seed/sports-5/800/600"]', NOW(), NOW()),
(6, '田径场', 2, 1, '标准田径运动场。', '朝阳校区', 114.897610, 40.752413, NULL, NULL, '["https://picsum.photos/seed/sports-6/800/600"]', NOW(), NOW()),
(7, '排球场', 2, 1, '室外排球场。', '朝阳校区', 114.897575, 40.753042, NULL, NULL, '["https://picsum.photos/seed/sports-7/800/600"]', NOW(), NOW()),
-- 教学楼/服务建筑 (类型3)
(8, '明德楼', 3, 1, '教学楼。', '朝阳校区', 114.898999, 40.757583, NULL, NULL, '["https://picsum.photos/seed/teaching-8/800/600"]', NOW(), NOW()),
(9, '崇德楼', 3, 3, '教学楼，暂关闭。', '朝阳校区', 114.89631, 40.758674, NULL, NULL, '["https://picsum.photos/seed/teaching-9/800/600"]', NOW(), NOW()),
(10, '图书馆', 3, 1, '学校图书馆。', '朝阳校区', 114.897021, 40.755812, NULL, NULL, '["https://picsum.photos/seed/library-10/800/600"]', NOW(), NOW()),
(12, '综合服务楼', 3, 1, '校园综合服务楼。', '朝阳校区', 114.898965, 40.756936, NULL, NULL, '["https://picsum.photos/seed/service-12/800/600"]', NOW(), NOW());

-- =============================================
-- 设施评价数据
-- =============================================
INSERT INTO facility_review (id, facility_id, user_id, score, content, images, create_time) VALUES
-- 食堂评价
(1, 1, 4, 5, '学一食堂的味道非常正宗，菜品丰富，价格实惠！', NULL, NOW()),
(2, 1, 5, 4, '味道不错，就是人有点多，排队时间较长。', NULL, NOW()),
(3, 1, 6, 5, '麻辣烫超级好吃，食材新鲜！', '["https://picsum.photos/400/300?random=30"]', NOW()),
(4, 1, 7, 4, '面条做得不错，分量足，价格实惠。', NULL, NOW()),
(5, 2, 4, 4, '学二食堂环境不错，菜品选择多。', NULL, NOW()),
-- 运动场评价
(6, 4, 5, 5, '东区运动场设施很好，地面平整，灯光充足。', NULL, NOW()),
(7, 4, 7, 4, '场地不错，就是有时候人多需要排队。', NULL, NOW()),
(8, 5, 4, 5, '篮球场场地条件不错。', NULL, NOW()),
-- 教学楼评价
(9, 8, 5, 5, '明德楼很宽敞，座位舒适，投影清晰。', NULL, NOW()),
(10, 8, 6, 4, '多媒体教室设备齐全，录播功能很实用。', NULL, NOW());

-- =============================================
-- 第四部分：第四阶段 - 校园地图导航模块数据
-- =============================================

-- =============================================
-- 地图配置数据
-- =============================================
INSERT INTO map_config (id, config_key, config_value, description, create_time, update_time) VALUES
(1, 'map_center_longitude', '114.898507', '地图中心经度', NOW(), NOW()),
(2, 'map_center_latitude', '40.755672', '地图中心纬度', NOW(), NOW()),
(3, 'map_zoom_level', '16', '默认缩放级别(1-20)', NOW(), NOW()),
(4, 'map_provider', 'amap', '地图提供方', NOW(), NOW()),
(5, 'facility_types', '[{"value":1,"label":"食堂"},{"value":2,"label":"运动场"},{"value":3,"label":"教学楼"},{"value":4,"label":"综合服务"},{"value":5,"label":"校内商铺"},{"value":99,"label":"其他"}]', '设施类型字典', NOW(), NOW());

-- =============================================
-- 地图标记数据
-- =============================================
INSERT INTO map_marker (id, facility_id, icon_url, sort, create_time, update_time) VALUES
-- 食堂标记
(1, 1, NULL, 1, NOW(), NOW()),
(2, 2, NULL, 2, NOW(), NOW()),
(3, 3, NULL, 3, NOW(), NOW()),
(11, 11, NULL, 11, NOW(), NOW()),
-- 运动场标记
(4, 4, NULL, 4, NOW(), NOW()),
(5, 5, NULL, 5, NOW(), NOW()),
(6, 6, NULL, 6, NOW(), NOW()),
(7, 7, NULL, 7, NOW(), NOW()),
-- 教学楼/服务建筑标记
(8, 8, NULL, 8, NOW(), NOW()),
(9, 9, NULL, 9, NOW(), NOW()),
(10, 10, NULL, 10, NOW(), NOW()),
(12, 12, NULL, 12, NOW(), NOW());

-- =============================================
-- 导航记录数据
-- =============================================
INSERT INTO navigation_log (id, user_id, from_longitude, from_latitude, to_marker_id, distance, duration, status, arrive_time, create_time) VALUES
(1, 4, 114.899741, 40.755538, 1, 150.50, 120, 2, NOW(), NOW()),
(2, 4, 114.899741, 40.755538, 8, 200.00, 180, 2, NOW(), NOW()),
(3, 5, 114.899512, 40.758585, 4, 300.00, 240, 2, NOW(), NOW()),
(4, 6, 114.897021, 40.755812, 10, 500.00, 400, 2, NOW(), NOW()),
(5, 7, 114.896483, 40.752178, 5, 100.00, 60, 1, NULL, NOW());

-- =============================================
-- 收藏目的地数据
-- =============================================
INSERT INTO favorite_destination (id, user_id, marker_id, marker_name, longitude, latitude, facility_type, remark, create_time) VALUES
(1, 4, 1, '学一食堂', 114.899741, 40.755538, 1, '常去吃饭', NOW()),
(2, 4, 8, '明德楼', 114.898999, 40.757583, 3, '上课地点', NOW()),
(3, 4, 10, '图书馆', 114.897021, 40.755812, 3, '自习', NOW()),
(4, 5, 2, '学二食堂', 114.899888, 40.756985, 1, '午餐', NOW()),
(5, 5, 4, '东区运动场', 114.899512, 40.758585, 2, '打篮球', NOW()),
(6, 6, 3, '学三食堂', 114.899948, 40.754134, 1, '晚餐', NOW()),
(7, 6, 12, '综合服务楼', 114.898965, 40.756936, 3, '办事', NOW()),
(8, 7, 6, '田径场', 114.897610, 40.752413, 2, '跑步', NOW()),
(9, 7, 7, '排球场', 114.897575, 40.753042, 2, '打球', NOW());
-- =============================================
-- 第四部分：第五阶段 - 校园旧物出售模块数据
-- =============================================

-- =============================================
-- 第五阶段：建表语句
-- =============================================

-- 物品分类表
CREATE TABLE IF NOT EXISTS secondhand_category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '分类ID',
    category_name VARCHAR(50) NOT NULL COMMENT '分类名称',
    sort INT DEFAULT 0 COMMENT '排序值（越小越前）',
    create_time DATETIME COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物品分类表';

-- 旧库迁移：移除已废弃列（不影响 secondhand_item 外键；列不存在时跳过）
SET @sc_db := DATABASE();
SET @sc_sql := (SELECT IF(COUNT(*) > 0,
    'ALTER TABLE secondhand_category DROP COLUMN category_icon',
    'SELECT 1') FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @sc_db AND TABLE_NAME = 'secondhand_category' AND COLUMN_NAME = 'category_icon');
PREPARE sc_stmt FROM @sc_sql;
EXECUTE sc_stmt;
DEALLOCATE PREPARE sc_stmt;
SET @sc_sql := (SELECT IF(COUNT(*) > 0,
    'ALTER TABLE secondhand_category DROP COLUMN status',
    'SELECT 1') FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @sc_db AND TABLE_NAME = 'secondhand_category' AND COLUMN_NAME = 'status');
PREPARE sc_stmt FROM @sc_sql;
EXECUTE sc_stmt;
DEALLOCATE PREPARE sc_stmt;

-- 二手物品表
CREATE TABLE IF NOT EXISTS secondhand_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '物品ID',
    user_id BIGINT NOT NULL COMMENT '发布者ID',
    category_id BIGINT COMMENT '分类ID',
    title VARCHAR(200) NOT NULL COMMENT '物品标题',
    description TEXT COMMENT '物品描述',
    images TEXT COMMENT '图片URL列表(JSON数组)',
    price DECIMAL(10,2) COMMENT '售价',
    original_price DECIMAL(10,2) COMMENT '原价',
    `condition` INT COMMENT '新旧程度: 1-全新 2-几乎全新 3-轻微使用痕迹 4-明显使用痕迹 5-仅限零件',
    location VARCHAR(200) COMMENT '期望交易地点',
    campus_id VARCHAR(50) COMMENT '校区ID',
    campus_name VARCHAR(50) COMMENT '校区名称',
    trade_location VARCHAR(100) COMMENT '交易区域',
    pickup_point VARCHAR(200) COMMENT '自提点',
    view_count INT DEFAULT 0 COMMENT '浏览量',
    favorite_count INT DEFAULT 0 COMMENT '收藏数',
    inquiry_count INT DEFAULT 0 COMMENT '咨询次数',
    heat_score INT DEFAULT 0 COMMENT '热度分 = 浏览*1 + 收藏*3 + 咨询*5',
    status INT NOT NULL DEFAULT 2 COMMENT '状态: 2-在售 3-已售出 4-已下架',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    FOREIGN KEY (user_id) REFERENCES sys_user(id),
    FOREIGN KEY (category_id) REFERENCES secondhand_category(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='二手物品表';

UPDATE secondhand_item SET status = 2 WHERE status = 5;

-- 旧库迁移：为 secondhand_item 添加校区/热度字段（列不存在时自动补列）
SET @si_db := DATABASE();
SET @si_sql := (SELECT IF(COUNT(*) > 0,
    'SELECT 1',
    'ALTER TABLE secondhand_item ADD COLUMN campus_id VARCHAR(50) COMMENT ''校区ID'' AFTER location')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @si_db AND TABLE_NAME = 'secondhand_item' AND COLUMN_NAME = 'campus_id');
PREPARE si_stmt FROM @si_sql;
EXECUTE si_stmt;
DEALLOCATE PREPARE si_stmt;
SET @si_sql := (SELECT IF(COUNT(*) > 0,
    'SELECT 1',
    'ALTER TABLE secondhand_item ADD COLUMN campus_name VARCHAR(50) COMMENT ''校区名称'' AFTER campus_id')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @si_db AND TABLE_NAME = 'secondhand_item' AND COLUMN_NAME = 'campus_name');
PREPARE si_stmt FROM @si_sql;
EXECUTE si_stmt;
DEALLOCATE PREPARE si_stmt;
SET @si_sql := (SELECT IF(COUNT(*) > 0,
    'SELECT 1',
    'ALTER TABLE secondhand_item ADD COLUMN trade_location VARCHAR(100) COMMENT ''交易区域'' AFTER campus_name')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @si_db AND TABLE_NAME = 'secondhand_item' AND COLUMN_NAME = 'trade_location');
PREPARE si_stmt FROM @si_sql;
EXECUTE si_stmt;
DEALLOCATE PREPARE si_stmt;
SET @si_sql := (SELECT IF(COUNT(*) > 0,
    'SELECT 1',
    'ALTER TABLE secondhand_item ADD COLUMN pickup_point VARCHAR(200) COMMENT ''自提点'' AFTER trade_location')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @si_db AND TABLE_NAME = 'secondhand_item' AND COLUMN_NAME = 'pickup_point');
PREPARE si_stmt FROM @si_sql;
EXECUTE si_stmt;
DEALLOCATE PREPARE si_stmt;
SET @si_sql := (SELECT IF(COUNT(*) > 0,
    'SELECT 1',
    'ALTER TABLE secondhand_item ADD COLUMN inquiry_count INT DEFAULT 0 COMMENT ''咨询次数'' AFTER favorite_count')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @si_db AND TABLE_NAME = 'secondhand_item' AND COLUMN_NAME = 'inquiry_count');
PREPARE si_stmt FROM @si_sql;
EXECUTE si_stmt;
DEALLOCATE PREPARE si_stmt;
SET @si_sql := (SELECT IF(COUNT(*) > 0,
    'SELECT 1',
    'ALTER TABLE secondhand_item ADD COLUMN heat_score INT DEFAULT 0 COMMENT ''热度分'' AFTER inquiry_count')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @si_db AND TABLE_NAME = 'secondhand_item' AND COLUMN_NAME = 'heat_score');
PREPARE si_stmt FROM @si_sql;
EXECUTE si_stmt;
DEALLOCATE PREPARE si_stmt;

-- 物品收藏表
CREATE TABLE IF NOT EXISTS secondhand_favorite (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '收藏ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    item_id BIGINT NOT NULL COMMENT '物品ID',
    create_time DATETIME COMMENT '收藏时间',
    UNIQUE KEY uk_user_item (user_id, item_id),
    FOREIGN KEY (user_id) REFERENCES sys_user(id),
    FOREIGN KEY (item_id) REFERENCES secondhand_item(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物品收藏表';

-- 聊天会话表
CREATE TABLE IF NOT EXISTS chat_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '会话ID',
    item_id BIGINT NOT NULL COMMENT '关联物品ID',
    buyer_id BIGINT NOT NULL COMMENT '买家ID',
    seller_id BIGINT NOT NULL COMMENT '卖家ID',
    last_message VARCHAR(500) COMMENT '最后一条消息内容',
    last_time DATETIME COMMENT '最后消息时间',
    buyer_unread_count INT DEFAULT 0 COMMENT '买家未读消息数',
    seller_unread_count INT DEFAULT 0 COMMENT '卖家未读消息数',
    create_time DATETIME COMMENT '创建时间',
    FOREIGN KEY (item_id) REFERENCES secondhand_item(id),
    FOREIGN KEY (buyer_id) REFERENCES sys_user(id),
    FOREIGN KEY (seller_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天会话表';

-- 聊天消息表
CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '消息ID',
    session_id BIGINT NOT NULL COMMENT '会话ID',
    sender_id BIGINT NOT NULL COMMENT '发送者ID',
    message_type INT NOT NULL DEFAULT 1 COMMENT '消息类型: 1-文本 2-图片 3-位置',
    content VARCHAR(1000) NOT NULL COMMENT '消息内容',
    is_read TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已读: 0-未读 1-已读',
    create_time DATETIME COMMENT '发送时间',
    FOREIGN KEY (session_id) REFERENCES chat_session(id),
    FOREIGN KEY (sender_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天消息表';

-- =============================================
-- 第五阶段：清空表数据（按依赖顺序）
-- =============================================
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE secondhand_favorite;
TRUNCATE TABLE chat_message;
TRUNCATE TABLE chat_session;
TRUNCATE TABLE secondhand_item;
TRUNCATE TABLE secondhand_category;
SET FOREIGN_KEY_CHECKS = 1;

-- =============================================
-- 物品分类数据
-- =============================================
INSERT INTO secondhand_category (id, category_name, sort, create_time) VALUES
(1, '数码产品', 1, NOW()),
(2, '书籍教材', 2, NOW()),
(3, '服饰鞋包', 3, NOW()),
(4, '生活用品', 4, NOW()),
(5, '文体娱乐', 5, NOW());

-- =============================================
-- 二手物品数据（user_id=4卖家张三, user_id=5卖家李四, user_id=6买家王五, user_id=7买家赵六）
-- =============================================
INSERT INTO secondhand_item (id, user_id, category_id, title, description, images, price, original_price, `condition`, location, view_count, favorite_count, status, create_time, update_time) VALUES
-- 数码产品
(1, 4, 1, 'iPad Air 4 256G WiFi版', '2023年6月购入，功能完全正常，屏幕无划痕，带原装充电器和保护套。', '["https://picsum.photos/800/600?random=101","https://picsum.photos/800/600?random=102"]', 2800.00, 4999.00, 2, '图书馆门口', 256, 18, 2, NOW(), NOW()),
(2, 4, 1, 'AirPods Pro 2代', '今年2月买的，用了两个月，成色几乎全新，配件齐全。', '["https://picsum.photos/800/600?random=103"]', 1200.00, 1899.00, 2, '松园1号楼', 189, 12, 2, NOW(), NOW()),
(3, 5, 1, '小米12手机 8+256G', '骁龙8处理器，屏幕小手感好，拍照清晰，无维修无进水。', '["https://picsum.photos/800/600?random=104"]', 1500.00, 3699.00, 3, '学校南门', 320, 25, 2, NOW(), NOW()),
(4, 5, 1, '联想ThinkPad笔记本电脑', '19年购入，日常办公流畅，键盘手感好，轻薄便携。', '["https://picsum.photos/800/600?random=105","https://picsum.photos/800/600?random=106"]', 2200.00, 5999.00, 3, '致远楼前', 145, 9, 2, NOW(), NOW()),
-- 书籍教材
(5, 4, 2, '考研全套资料 数学一+英语一', '包含高数、线代、概率全套教材及真题集，笔记较少，适合复习使用。', '["https://picsum.photos/800/600?random=107"]', 120.00, 350.00, 3, '博学楼', 480, 32, 2, NOW(), NOW()),
(6, 5, 2, 'Python编程从入门到实践', '书籍保存完好，无折痕，有少量学习笔记，不影响阅读。', '["https://picsum.photos/800/600?random=108"]', 35.00, 84.00, 2, '图书馆', 210, 15, 2, NOW(), NOW()),
(7, 6, 2, '大学物理教材（上下册）', '大二物理学教材，包含全部章节笔记，手写字迹工整。', '["https://picsum.photos/800/600?random=109"]', 45.00, 98.00, 3, '竹园1号楼', 98, 6, 2, NOW(), NOW()),
-- 服饰鞋包
(8, 7, 3, 'Nike Air Force 1 白色 42码', '穿了五六次，鞋底几乎无磨损，清洗后几乎看不出使用痕迹。', '["https://picsum.photos/800/600?random=110"]', 380.00, 799.00, 2, '学校中心广场', 560, 40, 2, NOW(), NOW()),
(9, 7, 3, 'ONLY品牌女士连衣裙 M码', '只穿过一次参加活动，吊牌已剪，不影响穿着效果。', '["https://picsum.photos/800/600?random=111"]', 89.00, 499.00, 2, '南门', 167, 11, 2, NOW(), NOW()),
-- 生活用品
(10, 6, 4, '美的落地风扇', '去年夏天买的，用了一个月，拆卸方便，不占空间。', '["https://picsum.photos/800/600?random=112"]', 120.00, 299.00, 2, '松园2号楼', 88, 5, 2, NOW(), NOW()),
(11, 6, 4, '小米台灯+收纳盒套装', '台灯支持多档亮度调节，收纳盒8格，桌面整理好帮手。', '["https://picsum.photos/800/600?random=113"]', 68.00, 159.00, 2, '竹园2号楼', 130, 8, 2, NOW(), NOW()),
-- 文体娱乐
(12, 8, 5, '威尔逊专业羽毛球拍', '单拍，带原装球拍袋，磅数22磅，进攻型球拍。', '["https://picsum.photos/800/600?random=114"]', 150.00, 380.00, 3, '东区运动场', 75, 4, 2, NOW(), NOW()),
-- 已售出物品
(13, 4, 1, '小米手环7 NFC版', '已售出示例，成色9新。', '["https://picsum.photos/800/600?random=115"]', 180.00, 299.00, 2, '南门', 0, 0, 3, DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY)),
-- 已下架物品
(14, 5, 2, '数字信号处理教材', '已下架示例。', '["https://picsum.photos/800/600?random=116"]', 30.00, 65.00, 4, '博学楼', 0, 0, 4, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY));

-- =============================================
-- 物品收藏数据
-- =============================================
INSERT INTO secondhand_favorite (id, user_id, item_id, create_time) VALUES
(1, 5, 1, NOW()),
(2, 5, 8, NOW()),
(3, 6, 2, NOW()),
(4, 6, 5, NOW()),
(5, 7, 3, NOW()),
(6, 7, 4, NOW()),
(7, 8, 5, NOW()),
(8, 8, 9, NOW());

-- =============================================
-- 聊天会话数据
-- =============================================
INSERT INTO chat_session (id, item_id, buyer_id, seller_id, last_message, last_time, buyer_unread_count, seller_unread_count, create_time) VALUES
(1, 1, 5, 4, '您好，请问还在吗？iPad有发票吗？', NOW(), 1, 0, NOW()),
(2, 3, 6, 5, '可以便宜一点吗？', DATE_SUB(NOW(), INTERVAL 2 HOUR), 0, 1, DATE_SUB(NOW(), INTERVAL 5 HOUR)),
(3, 5, 7, 4, '好的，周末在图书馆见。', DATE_SUB(NOW(), INTERVAL 1 DAY), 0, 0, DATE_SUB(NOW(), INTERVAL 2 DAY)),
(4, 8, 6, 7, '鞋码是标准码吗？', DATE_SUB(NOW(), INTERVAL 3 DAY), 0, 0, DATE_SUB(NOW(), INTERVAL 4 DAY));

-- =============================================
-- 聊天消息数据
-- =============================================
INSERT INTO chat_message (id, session_id, sender_id, message_type, content, is_read, create_time) VALUES
-- 会话1：买家李四(5) 咨询 卖家张三(4) 的iPad
(1, 1, 5, 1, '您好，请问这个iPad还在吗？', 1, DATE_SUB(NOW(), INTERVAL 1 HOUR)),
(2, 1, 4, 1, '在的，有什么想了解的吗？', 1, DATE_SUB(NOW(), INTERVAL 55 MINUTE)),
(3, 1, 5, 1, '您好，请问还在吗？iPad有发票吗？', 0, DATE_SUB(NOW(), INTERVAL 30 MINUTE)),
-- 会话2：买家王五(6) 咨询 卖家李四(5) 的手机
(4, 2, 6, 1, '您好，请问小米12还在卖吗？', 1, DATE_SUB(NOW(), INTERVAL 5 HOUR)),
(5, 2, 5, 1, '在的，随时可以看实物。', 1, DATE_SUB(NOW(), INTERVAL 4 HOUR)),
(6, 2, 6, 1, '可以便宜一点吗？', 0, DATE_SUB(NOW(), INTERVAL 2 HOUR)),
-- 会话3：买家赵六(7) 咨询 卖家张三(4) 的考研资料
(7, 3, 7, 1, '学长你好，考研资料还在吗？', 1, DATE_SUB(NOW(), INTERVAL 2 DAY)),
(8, 3, 4, 1, '在的，数学一和英语一的都有。', 1, DATE_SUB(NOW(), INTERVAL '1 22' DAY_HOUR)),
(9, 3, 7, 1, '价格可以商量吗？', 1, DATE_SUB(NOW(), INTERVAL '1 20' DAY_HOUR)),
(10, 3, 4, 1, '好的，周末在图书馆见。', 0, DATE_SUB(NOW(), INTERVAL 1 DAY)),
-- 会话4：买家王五(6) 咨询 卖家赵六(7) 的球鞋
(11, 4, 6, 1, '你好，请问鞋还在吗？', 1, DATE_SUB(NOW(), INTERVAL 4 DAY)),
(12, 4, 7, 1, '在的，42码标准码。', 1, DATE_SUB(NOW(), INTERVAL '3 23' DAY_HOUR)),
(13, 4, 6, 1, '鞋码是标准码吗？', 0, DATE_SUB(NOW(), INTERVAL 3 DAY));

-- =============================================
-- 第五部分：第六阶段 - 校园特惠模块数据
-- =============================================

-- =============================================
-- 第六阶段：建表语句
-- =============================================

-- 商家分类表
CREATE TABLE IF NOT EXISTS merchant_category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '分类ID',
    category_name VARCHAR(50) NOT NULL COMMENT '分类名称',
    category_icon VARCHAR(255) COMMENT '分类图标URL',
    sort INT DEFAULT 0 COMMENT '排序值（越小越前）',
    status INT NOT NULL DEFAULT 1 COMMENT '状态: 1-启用 2-禁用',
    create_time DATETIME COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家分类表';

-- 商家表
CREATE TABLE IF NOT EXISTS merchant (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '商家ID',
    merchant_name VARCHAR(100) NOT NULL COMMENT '商家名称',
    category_id BIGINT COMMENT '分类ID',
    description TEXT COMMENT '商家介绍',
    logo VARCHAR(255) COMMENT '商家Logo URL',
    images TEXT COMMENT '商家环境图片列表(JSON数组)',
    address VARCHAR(200) NOT NULL COMMENT '商家地址',
    longitude DECIMAL(10,7) COMMENT '经度',
    latitude DECIMAL(10,7) COMMENT '纬度',
    contact_name VARCHAR(50) COMMENT '联系人姓名',
    contact_phone VARCHAR(20) NOT NULL COMMENT '联系电话',
    business_hours VARCHAR(50) COMMENT '营业时间',
    user_id BIGINT NOT NULL COMMENT '关联商家用户ID',
    status INT NOT NULL DEFAULT 1 COMMENT '状态: 1-正常营业 2-暂停营业 3-已禁用',
    avg_score DECIMAL(2,1) DEFAULT 0 COMMENT '平均评分',
    review_count INT DEFAULT 0 COMMENT '评价总数',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    FOREIGN KEY (category_id) REFERENCES merchant_category(id),
    FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家表';

-- 优惠活动表
CREATE TABLE IF NOT EXISTS discount_activity (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '活动ID',
    merchant_id BIGINT NOT NULL COMMENT '商家ID',
    title VARCHAR(200) NOT NULL COMMENT '活动标题',
    description TEXT COMMENT '活动描述',
    cover_image VARCHAR(255) COMMENT '封面图片URL',
    images TEXT COMMENT '活动图片列表(JSON数组)',
    start_time DATETIME COMMENT '活动开始时间',
    end_time DATETIME COMMENT '活动结束时间',
    use_rules TEXT COMMENT '使用规则',
    total_count INT COMMENT '总名额',
    remain_count INT COMMENT '剩余名额',
    status INT NOT NULL DEFAULT 1 COMMENT '状态: 1-进行中 2-已结束',
    create_time DATETIME COMMENT '创建时间',
    FOREIGN KEY (merchant_id) REFERENCES merchant(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠活动表';

-- 旧库迁移：已移除 discount_type / discount_value / current_price / view_count / favorite_count / status（列不存在时跳过）
SET @da_db := DATABASE();
SET @da_sql := (SELECT IF(COUNT(*) > 0,
    'ALTER TABLE discount_activity DROP COLUMN discount_type',
    'SELECT 1') FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @da_db AND TABLE_NAME = 'discount_activity' AND COLUMN_NAME = 'discount_type');
PREPARE da_stmt FROM @da_sql;
EXECUTE da_stmt;
DEALLOCATE PREPARE da_stmt;
SET @da_sql := (SELECT IF(COUNT(*) > 0,
    'ALTER TABLE discount_activity DROP COLUMN discount_value',
    'SELECT 1') FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @da_db AND TABLE_NAME = 'discount_activity' AND COLUMN_NAME = 'discount_value');
PREPARE da_stmt FROM @da_sql;
EXECUTE da_stmt;
DEALLOCATE PREPARE da_stmt;
SET @da_sql := (SELECT IF(COUNT(*) > 0,
    'ALTER TABLE discount_activity DROP COLUMN current_price',
    'SELECT 1') FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @da_db AND TABLE_NAME = 'discount_activity' AND COLUMN_NAME = 'current_price');
PREPARE da_stmt FROM @da_sql;
EXECUTE da_stmt;
DEALLOCATE PREPARE da_stmt;
SET @da_sql := (SELECT IF(COUNT(*) > 0,
    'ALTER TABLE discount_activity DROP COLUMN view_count',
    'SELECT 1') FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @da_db AND TABLE_NAME = 'discount_activity' AND COLUMN_NAME = 'view_count');
PREPARE da_stmt FROM @da_sql;
EXECUTE da_stmt;
DEALLOCATE PREPARE da_stmt;
SET @da_sql := (SELECT IF(COUNT(*) > 0,
    'ALTER TABLE discount_activity DROP COLUMN original_price',
    'SELECT 1') FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @da_db AND TABLE_NAME = 'discount_activity' AND COLUMN_NAME = 'original_price');
PREPARE da_stmt FROM @da_sql;
EXECUTE da_stmt;
DEALLOCATE PREPARE da_stmt;
SET @da_sql := (SELECT IF(COUNT(*) > 0,
    'ALTER TABLE discount_activity DROP COLUMN favorite_count',
    'SELECT 1') FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @da_db AND TABLE_NAME = 'discount_activity' AND COLUMN_NAME = 'favorite_count');
PREPARE da_stmt FROM @da_sql;
EXECUTE da_stmt;
DEALLOCATE PREPARE da_stmt;
SET @da_sql := (SELECT IF(COUNT(*) > 0,
    'ALTER TABLE discount_activity DROP COLUMN status',
    'SELECT 1') FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @da_db AND TABLE_NAME = 'discount_activity' AND COLUMN_NAME = 'status');
PREPARE da_stmt FROM @da_sql;
EXECUTE da_stmt;
DEALLOCATE PREPARE da_stmt;

-- 优惠领取表
CREATE TABLE IF NOT EXISTS discount_claim (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '领取记录ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    activity_id BIGINT NOT NULL COMMENT '优惠活动ID',
    claim_time DATETIME COMMENT '领取时间',
    UNIQUE KEY uk_user_activity (user_id, activity_id),
    FOREIGN KEY (user_id) REFERENCES sys_user(id),
    FOREIGN KEY (activity_id) REFERENCES discount_activity(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠活动领取记录表';

-- =============================================
-- 第六阶段：清空表数据（按依赖顺序）
-- =============================================
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE discount_claim;
TRUNCATE TABLE discount_activity;
TRUNCATE TABLE merchant;
TRUNCATE TABLE merchant_category;
SET FOREIGN_KEY_CHECKS = 1;

-- =============================================
-- 商家分类数据
-- =============================================
INSERT INTO merchant_category (id, category_name, category_icon, sort, status, create_time) VALUES
(1, '餐厅美食', 'https://cdn.example.com/icons/restaurant.png', 1, 1, NOW()),
(2, '饮品甜点', 'https://cdn.example.com/icons/drink.png', 2, 1, NOW()),
(3, '超市便利', 'https://cdn.example.com/icons/supermarket.png', 3, 1, NOW()),
(4, '打印复印', 'https://cdn.example.com/icons/print.png', 4, 1, NOW());

-- =============================================
-- 商家数据（user_id=4张三, user_id=5李四, user_id=6王五, user_id=7赵六 担任商家账号）
-- =============================================
INSERT INTO merchant (id, merchant_name, category_id, description, logo, images, address, longitude, latitude, contact_name, contact_phone, business_hours, user_id, status, create_time, update_time) VALUES
(1, '学府餐厅', 1, '学校北门旁的平价餐厅，以川菜和本地家常菜为主，价格实惠，分量充足，是师生日常就餐的热门选择。', 'https://cdn.example.com/merchant/logo1.jpg', '["https://picsum.photos/800/600?random=201","https://picsum.photos/800/600?random=202"]', '学校北门向东200米', 116.397500, 39.909800, '李老板', '13812345601', '07:00-21:00', 9, 1, NOW(), NOW()),
(2, '书香咖啡', 2, '位于图书馆一楼的咖啡店，环境安静，适合自习和小组讨论，提供咖啡、茶饮和轻食。', 'https://cdn.example.com/merchant/logo2.jpg', '["https://picsum.photos/800/600?random=203"]', '图书馆一层东侧', 116.397600, 39.909300, '王老板', '13812345602', '08:00-22:00', 10, 1, NOW(), NOW()),
(3, '校园便利超市', 3, '日用品、文具、零食一应俱全，价格与外面持平，24小时营业，方便师生随时采购。', 'https://cdn.example.com/merchant/logo3.jpg', '["https://picsum.photos/800/600?random=204"]', '学校南门内50米', 116.397400, 39.909100, '张老板', '13812345603', '24小时营业', 11, 1, NOW(), NOW()),
(4, '快印图文店', 4, '打印、复印、扫描、装订一站式服务，支持彩色打印和大幅面输出，价格公道，速度快。', 'https://cdn.example.com/merchant/logo4.jpg', '["https://picsum.photos/800/600?random=205"]', '博学楼地下一层', 116.397800, 39.909500, '赵老板', '13812345604', '08:00-20:00', 12, 1, NOW(), NOW());

-- =============================================
-- 优惠活动数据
-- =============================================

-- =============================================
-- 第八阶段：课表模块数据
-- =============================================

-- 课表表
CREATE TABLE IF NOT EXISTS course_schedule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '课表 ID',
    user_id BIGINT NOT NULL COMMENT '用户 ID（学号关联）',
    student_id VARCHAR(50) COMMENT '学号',
    course_name VARCHAR(200) NOT NULL COMMENT '课程名称',
    week_range VARCHAR(200) COMMENT '周数范围，如：1-2 周，3-5 周',
    class_sessions VARCHAR(50) COMMENT '节次，如：1-2 节，3-4 节',
    weekday INT COMMENT '星期几：1-星期一，2-星期二，3-星期三，4-星期四，5-星期五，6-星期六，7-星期日',
    location VARCHAR(100) COMMENT '上课地点',
    campus VARCHAR(50) COMMENT '校区',
    teacher_name VARCHAR(50) COMMENT '教师姓名',
    class_code VARCHAR(100) COMMENT '教学班号',
    class_composition VARCHAR(255) COMMENT '教学班组成',
    assessment_type VARCHAR(20) COMMENT '考核方式：考试/考查',
    theory_hours INT DEFAULT 0 COMMENT '理论学时',
    lab_hours INT DEFAULT 0 COMMENT '实验/上机学时',
    weekly_hours INT DEFAULT 0 COMMENT '周学时',
    total_hours INT DEFAULT 0 COMMENT '总学时',
    credit DECIMAL(3,1) DEFAULT 0 COMMENT '学分',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课表表';

-- =============================================
-- 第八阶段：清空表数据
-- =============================================
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE course_schedule;
SET FOREIGN_KEY_CHECKS = 1;

-- =============================================
-- 课表数据（用户 ID: 13, 学号：20233090117）
-- =============================================
INSERT INTO course_schedule (id, user_id, student_id, course_name, week_range, class_sessions, weekday, location, teacher_name, class_code, class_composition, assessment_type, theory_hours, lab_hours, weekly_hours, total_hours, credit, campus, create_time, update_time) VALUES
(368, 13, '20233090117', '软件项目开发 A', '1-2 周', '1-2 节', 1, '朝阳校区明德楼阶梯 110', '李耀辉', '(2025-2026-2)-XX02007-01', '计 231;计 232;计 233;计 234;计 235', '考查', 24, 16, 12, 24, 2.5, NULL, NOW(), NOW()),
(369, 13, '20233090117', '深度学习', '3-5 周，7-9 周，11-13 周', '1-2 节', 1, '朝阳校区明德楼阶梯 110', '赵明瞻', '(2025-2026-2)-XX03016-01', '计 231;计 232;计 233;计 234;计 235', '考查', 18, 14, 2, 18, 2.0, NULL, NOW(), NOW()),
(370, 13, '20233090117', '【调】网络编程', '3-4 周，6-11 周', '1-2 节', 2, '朝阳校区明德楼 505', '付江龙', '(2025-2026-2)-XX02104-01', '计 231;计 232;计 233;计 234;计 235', '考试', 28, 12, 3, 28, 2.5, NULL, NOW(), NOW()),
(371, 13, '20233090117', '软件项目开发 A', '1-2 周', '1-4 节', 2, '朝阳校区 A214', '李耀辉', '(2025-2026-2)-XX02007-01', '计 231;计 232;计 233;计 234;计 235', '考查', 24, 16, 12, 24, 2.5, NULL, NOW(), NOW()),
(372, 13, '20233090117', '网络编程', '7-12 周', '3-4 节', 2, '朝阳校区图书馆一楼公共机房 3', '付江龙', '(2025-2026-2)-XX02104-01A', '计 231;计 232;计 233;计 234;计 235', '未安排', 28, 12, 2, 12, 2.5, NULL, NOW(), NOW()),
(373, 13, '20233090117', '【调】Python 程序设计', '3 周，6-12 周', '1-2 节', 3, '朝阳校区明德楼阶梯 110', '范晶晶', '(2025-2026-2)-XX02303-01', '计 231;计 232;计 233;计 234', '考查', 20, 12, 2, 20, 2.0, NULL, NOW(), NOW()),
(374, 13, '20233090117', '国家安全教育 C', '1-2 周', '1-2 节', 4, '朝阳校区明德楼 503', '樊智华', '(2025-2026-2)-SK030C2-20', '计 231;计 232;计 233;计 234;计 235;数据 231', '考查', 4, 0, 2, 4, 0.3, NULL, NOW(), NOW()),
(375, 13, '20233090117', '【调】Python 程序设计', '12 周', '1-2 节', 4, '朝阳校区明德楼阶梯 110', '范晶晶', '(2025-2026-2)-XX02303-01', '计 231;计 232;计 233;计 234', '考查', 20, 12, 2, 20, 2.0, NULL, NOW(), NOW()),
(376, 13, '20233090117', '软件项目开发 A', '1-2 周', '1-2 节', 5, '朝阳校区明德楼阶梯 110', '李耀辉', '(2025-2026-2)-XX02007-01', '计 231;计 232;计 233;计 234;计 235', '考查', 24, 16, 12, 24, 2.5, NULL, NOW(), NOW()),
(377, 13, '20233090117', 'Python 程序设计', '7-8 周，10-13 周', '1-2 节', 5, '朝阳校区明德楼 503', '范晶晶', '(2025-2026-2)-XX02303-01A', '计 231;计 232;计 233;计 234', '未安排', 20, 12, 2, 12, 2.0, NULL, NOW(), NOW()),
(378, 13, '20233090117', '深度学习', '5-7 周 (单),8-9 周，11-13 周', '3-4 节', 1, '朝阳校区图书馆一楼公共机房 4', '赵明瞻', '(2025-2026-2)-XX03016-01A', '计 231;计 232;计 233;计 234;计 235', '未安排', 18, 14, 2, 14, 2.0, NULL, NOW(), NOW()),
(379, 13, '20233090117', '软件项目开发 A', '1-2 周', '3-4 节', 3, '朝阳校区 A414', '李耀辉', '(2025-2026-2)-XX02007-01', '计 231;计 232;计 233;计 234;计 235', '考查', 24, 16, 12, 24, 2.5, NULL, NOW(), NOW()),
(380, 13, '20233090117', '软件项目开发 A', '1-2 周', '3-4 节', 4, '朝阳校区明德楼 407', '李耀辉', '(2025-2026-2)-XX02007-01', '计 231;计 232;计 233;计 234;计 235', '考查', 24, 16, 12, 24, 2.5, NULL, NOW(), NOW()),
(381, 13, '20233090117', '软件工程', '3-13 周', '3-4 节', 4, '朝阳校区 A414', '李耀辉', '(2025-2026-2)-XX02009-01', '计 231;计 232;计 233;计 234;计 235', '考试', 32, 0, 3, 32, 2.0, NULL, NOW(), NOW()),
(382, 13, '20233090117', '软件工程', '4-12 周 (双)', '3-4 节', 5, '朝阳校区明德楼 303', '李耀辉', '(2025-2026-2)-XX02009-01', '计 231;计 232;计 233;计 234;计 235', '考试', 32, 0, 3, 32, 2.0, NULL, NOW(), NOW()),
(383, 13, '20233090117', 'Linux 系统', '3-5 周，7-9 周', '5-6 节', 1, '朝阳校区明德楼 403', '庞慧', '(2025-2026-2)-XX03012-03', '计 231;计 232;计 233;计 234', '考查', 24, 8, 3, 24, 2.0, NULL, NOW(), NOW()),
(384, 13, '20233090117', 'Linux 系统', '3-5 周 (单),6-7 周，9-10 周', '5-6 节', 3, '朝阳校区明德楼 403', '庞慧', '(2025-2026-2)-XX03012-03', '计 231;计 232;计 233;计 234', '考查', 24, 8, 3, 24, 2.0, NULL, NOW(), NOW()),
(385, 13, '20233090117', '物联网控制基础', '3-14 周', '5-6 节', 4, '朝阳校区明德楼 113', '王利霞', '(2025-2026-2)-XX05302-01', '计 231;计 232;计 233;计 234', '考查', 24, 8, 2, 24, 2.0, NULL, NOW(), NOW()),
(386, 13, '20233090117', '计算机组装与维护', '3-5 周，7-9 周，11-12 周', '7-8 节', 1, '朝阳校区明德楼 503', '李劭杰', '(2025-2026-2)-XX03314-03', '计 231;计 232;计 233;计 234', '考查', 16, 0, 2, 16, 1.0, NULL, NOW(), NOW()),
(387, 13, '20233090117', '形势与政策 F', '7-10 周', '7-8 节', 3, '朝阳校区明德楼阶梯 110', '樊智华', '(2025-2026-2)-SK030F1-14', '计 231;计 232;计 233;计 234;计 235;数据 231', '考查', 8, 0, 2, 8, 0.3, NULL, NOW(), NOW()),
(388, 13, '20233090117', '【调】Python 程序设计', '12 周', '7-8 节', 3, '朝阳校区明德楼阶梯 110', '范晶晶', '(2025-2026-2)-XX02303-01', '计 231;计 232;计 233;计 234', '考查', 20, 12, 2, 20, 2.0, NULL, NOW(), NOW()),
(389, 13, '20233090117', '【调】网络编程', '2-10 周 (双)', '7-8 节', 4, '朝阳校区明德楼 505', '付江龙', '(2025-2026-2)-XX02104-01', '计 231;计 232;计 233;计 234;计 235', '考试', 28, 12, 3, 28, 2.5, NULL, NOW(), NOW());

-- =============================================
-- 第九阶段：公告栏和轮播图表
-- =============================================

-- 公告栏表
CREATE TABLE IF NOT EXISTS announcement (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '公告 ID',
    title VARCHAR(200) NOT NULL COMMENT '公告标题',
    content TEXT COMMENT '公告内容',
    sort_order INT DEFAULT 0 COMMENT '排序，值越小越靠前' NOT NULL,
    enabled TINYINT DEFAULT 1 COMMENT '是否启用：0-禁用，1-启用' NOT NULL,
    is_top TINYINT DEFAULT 0 COMMENT '是否置顶：0-否，1-是' NOT NULL,
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公告栏表';

SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE announcement;
SET FOREIGN_KEY_CHECKS = 1;

INSERT INTO announcement (id, title, content, sort_order, enabled, is_top, create_time, update_time) VALUES
(1, '关于 2026 年春季学期期中教学安排的通知', '本周起进入期中教学检查阶段。请各学院按要求完成课堂秩序、作业批改、实验教学和考勤记录自查，学生如遇课程调整请以教务系统通知为准。', 1, 1, 1, '2026-04-07 09:00:00', '2026-04-07 09:00:00'),
(2, '图书馆自习区开放时间延长', '为配合同学们近期复习备考，图书馆一层和三层自习区自 2026 年 4 月 8 日起延长开放至 23:00，请自觉保持安静并带走个人物品。', 2, 1, 0, '2026-04-08 10:30:00', '2026-04-08 10:30:00'),
(3, '校园网络设备维护公告', '信息化中心将于 2026 年 4 月 13 日 22:30 至 23:30 对宿舍区核心交换设备进行维护。维护期间校园网和部分认证服务可能短时波动，请提前做好数据保存。', 3, 1, 0, '2026-04-11 16:20:00', '2026-04-11 16:20:00'),
(4, '毕业生就业双选会报名提醒', '本周六在大学生活动中心举行春季就业双选会，请 2026 届毕业生提前准备纸质简历和电子简历二维码，入场时需携带学生证。', 4, 1, 0, '2026-04-10 14:00:00', '2026-04-10 14:00:00'),
(5, '宿舍安全检查温馨提示', '近期将开展宿舍安全专项检查，请勿使用违规电器，不要在阳台和走廊堆放杂物，离开宿舍前请确认断电锁门。', 5, 1, 0, '2026-04-09 18:40:00', '2026-04-09 18:40:00'),
(6, '测试草稿公告（禁用）', '这是一条禁用状态的测试公告，不应在前台列表中展示。', 99, 0, 0, '2026-04-06 12:00:00', '2026-04-06 12:00:00');

-- 轮播图表
CREATE TABLE IF NOT EXISTS carousel_banner (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '轮播图 ID',
    title VARCHAR(100) COMMENT '轮播图标题',
    image_url VARCHAR(255) NOT NULL COMMENT '图片 URL',
    target_url VARCHAR(255) COMMENT '目标链接 URL',
    sort INT DEFAULT 0 COMMENT '排序',
    enabled TINYINT DEFAULT 1 COMMENT '是否启用：0-禁用，1-启用' NOT NULL,
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='轮播图表';
INSERT INTO discount_activity (id, merchant_id, title, description, cover_image, images, start_time, end_time, use_rules, total_count, remain_count, create_time) VALUES
-- 学府餐厅活动
(1, 1, '午餐特价套餐', '周一至周五11:00-13:00，特价午餐套餐限量供应，包含主食+两菜+汤。', 'https://picsum.photos/800/400?random=301', '["https://picsum.photos/800/600?random=302"]', '2026-03-01 00:00:00', '2026-12-31 23:59:59', '1. 仅限堂食\n2. 不可与店内其他优惠叠加\n3. 每人不限购买份数', 100, 50, NOW()),
(2, 1, '学生满30减5', '在线支付满30元立减5元，适合多人拼单。', 'https://picsum.photos/800/400?random=303', NULL, '2026-03-01 00:00:00', '2026-12-31 23:59:59', '1. 在线支付可用\n2. 不可与其他满减叠加\n3. 每日限用一次', 0, 0, NOW()),
-- 书香咖啡活动
(4, 2, '学生套餐 咖啡+蛋糕', '周一至周四，指定咖啡搭配任意蛋糕享套餐价。', 'https://picsum.photos/800/400?random=305', '["https://picsum.photos/800/600?random=306"]', '2026-03-01 00:00:00', '2026-12-31 23:59:59', '1. 需出示学生证\n2. 不与其他优惠叠加', 50, 28, NOW()),
-- 校园便利超市活动
(6, 3, '全场饮品8.5折', '指定饮料、牛奶、酸奶品类全场8.5折优惠。', 'https://picsum.photos/800/400?random=308', NULL, '2026-03-15 00:00:00', '2026-12-31 23:59:59', '1. 超市内全场饮品区可用\n2. 特价商品除外', 0, 0, NOW()),
(7, 3, '满50减10', '单笔消费满50元立减10元，超值划算。', 'https://picsum.photos/800/400?random=309', NULL, '2026-03-01 00:00:00', '2026-12-31 23:59:59', '1. 会员专享\n2. 不可与折扣同享', 0, 0, NOW()),
-- 快印图文店活动
(8, 4, '打印套餐10元封顶', '单面黑白打印0.1元/张，10张以内仅收1元，10张以上10元封顶，适合日常打印需求。', 'https://picsum.photos/800/400?random=310', NULL, '2026-03-01 00:00:00', '2026-12-31 23:59:59', '1. 仅限黑白单面打印\n2. 需提前预约', 0, 0, NOW());


-- =============================================
-- 第十阶段：食堂档口模块数据
-- =============================================

-- =============================================
-- 食堂档口表
-- =============================================
CREATE TABLE IF NOT EXISTS canteen_stall (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '档口 ID',
    stall_name VARCHAR(100) NOT NULL COMMENT '档口名称',
    restaurant_id BIGINT NOT NULL COMMENT '所属餐厅 ID',
    floor VARCHAR(20) COMMENT '楼层',
    category VARCHAR(50) COMMENT '品类/菜系',
    location VARCHAR(200) COMMENT '位置描述',
    score DECIMAL(3,2) DEFAULT 0 COMMENT '评分 (0-5)',
    review_count INT DEFAULT 0 COMMENT '评价总数',
    recommend_rate INT DEFAULT 0 COMMENT '推荐率 (%)',
    avg_price DECIMAL(10,2) COMMENT '人均价格',
    business_hours VARCHAR(100) COMMENT '营业时间',
    image VARCHAR(255) COMMENT '档口图片 URL',
    description TEXT COMMENT '档口描述',
    status INT NOT NULL DEFAULT 1 COMMENT '状态: 1-营业中 2-休息中 3-已关闭',
    sort INT DEFAULT 0 COMMENT '排序值',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    FOREIGN KEY (restaurant_id) REFERENCES campus_facility(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='食堂档口表';

-- =============================================
-- 食堂档口数据
-- =============================================
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE canteen_stall;
SET FOREIGN_KEY_CHECKS = 1;

INSERT INTO canteen_stall (id, stall_name, restaurant_id, floor, category, location, score, review_count, recommend_rate, avg_price, business_hours, image, description, status, sort, create_time, update_time) VALUES
-- 学一食堂 (restaurant_id=1) 的档口
(1, '早餐包子铺', 1, '1F', '早餐', '食堂东侧', 4.39, 9, 89, 12.0, '06:30-09:30', 'https://picsum.photos/seed/stall001/400/300', '主营鲜肉包、菜包、豆浆、油条等早餐，现包现蒸，新鲜美味。', 1, 1, NOW(), NOW()),
(2, '麻辣烫档口', 1, '3F', '面食', '食堂西侧', 4.13, 4, 75, 18.0, '10:30-20:30', 'https://picsum.photos/seed/stall002/400/300', '自选麻辣烫，多种食材可选，麻酱香浓，口味正宗。', 1, 2, NOW(), NOW()),
(3, '石锅拌饭', 1, '2F', '米饭', '食堂中部', 4.50, 3, 100, 16.0, '10:30-20:00', 'https://picsum.photos/seed/stall003/400/300', '韩式石锅拌饭，锅巴香脆，牛肉嫩滑，酱料正宗。', 1, 3, NOW(), NOW()),
(4, '黄焖鸡米饭', 1, '2F', '米饭', '食堂北侧', 4.00, 4, 75, 15.0, '10:30-20:00', 'https://picsum.photos/seed/stall004/400/300', '经典黄焖鸡，鸡肉嫩滑，汤汁浓郁，拌饭绝佳。', 1, 4, NOW(), NOW()),
(5, '兰州拉面', 1, '1F', '面食', '食堂南门', 4.50, 3, 100, 14.0, '07:00-20:30', 'https://picsum.photos/seed/stall005/400/300', '手工拉面，汤鲜面劲道，牛肉片足，正宗西北风味。', 1, 5, NOW(), NOW()),
(6, '沙县小吃', 1, '2F', '小吃', '食堂东侧', 4.2, 345, 76, 10.0, '08:00-20:00', 'https://picsum.photos/seed/stall006/400/300', '拌面、扁肉、蒸饺、炖罐等经典沙县美食。', 1, 6, NOW(), NOW()),

-- 学二食堂 (restaurant_id=2) 的档口
(7, '自选快餐', 2, '1F', '米饭', '食堂大厅', 4.4, 478, 80, 13.0, '10:30-19:00', 'https://picsum.photos/seed/stall007/400/300', '多种菜品自选，两荤两素搭配，价格实惠。', 1, 1, NOW(), NOW()),
(8, '奶茶饮品站', 2, '2F', '饮品', '食堂北侧', 4.50, 3, 100, 8.0, '09:00-21:00', 'https://picsum.photos/seed/stall008/400/300', '珍珠奶茶、柠檬茶、芒果冰沙等各式饮品。', 1, 2, NOW(), NOW()),
(9, '煎饼果子', 2, '1F', '早餐', '食堂东门', 4.5, 390, 86, 9.0, '06:30-09:30', 'https://picsum.photos/seed/stall009/400/300', '正宗天津煎饼果子，薄脆加蛋加肠，料足味美。', 1, 3, NOW(), NOW()),
(10, '重庆小面', 2, '3F', '面食', '食堂西侧', 4.25, 2, 100, 15.0, '10:30-20:30', 'https://picsum.photos/seed/stall010/400/300', '麻辣鲜香，正宗重庆风味，豌杂面、酸辣粉可选。', 1, 4, NOW(), NOW()),

-- 学三食堂 (restaurant_id=3) 的档口
(11, '清真牛肉面', 3, '1F', '面食', '食堂大厅', 4.75, 2, 100, 16.0, '07:00-20:00', 'https://picsum.photos/seed/stall011/400/300', '清真认证，牛肉新鲜，汤底醇厚，面条筋道。', 1, 1, NOW(), NOW()),
(12, '新疆大盘鸡', 3, '1F', '米饭', '食堂北侧', 4.25, 2, 100, 22.0, '10:30-20:00', 'https://picsum.photos/seed/stall012/400/300', '正宗新疆风味，鸡肉鲜嫩，土豆软糯，配皮带面。', 1, 2, NOW(), NOW()),
(13, '烤羊肉串', 3, '1F', '小吃', '食堂东门', 4.83, 3, 100, 5.0, '11:00-21:00', 'https://picsum.photos/seed/stall013/400/300', '现烤羊肉串，外焦里嫩，孜然香气扑鼻。', 1, 3, NOW(), NOW());

-- =============================================
-- 第十阶段：优惠券模块数据
-- =============================================

-- 先删除旧表，避免历史字段结构影响初始化
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS user_coupon;
DROP TABLE IF EXISTS promotion_coupon;
SET FOREIGN_KEY_CHECKS = 1;

-- 优惠券表
CREATE TABLE promotion_coupon (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '优惠券 ID',
    coupon_name VARCHAR(100) NOT NULL COMMENT '优惠券名称',
    category VARCHAR(20) COMMENT '分类：coupon-食堂优惠卡，card-校园卡，ad-代理服务，life-生活服务',
    merchant_id BIGINT COMMENT '关联商家 ID',
    stall_id BIGINT COMMENT '关联档口 ID',
    facility_id BIGINT COMMENT '关联设施 ID',
    total_quantity INT NOT NULL COMMENT '发放总量',
    start_date DATE COMMENT '开始日期',
    end_date DATE COMMENT '结束日期',
    image_url VARCHAR(255) COMMENT '图片 URL',
    tag_type VARCHAR(20) COMMENT '标签：new-新品，hot-热门，recommend-推荐',
    pickup_location VARCHAR(255) COMMENT '线下领取位置',
    description TEXT COMMENT '优惠券描述',
    status INT NOT NULL DEFAULT 1 COMMENT '状态：1-上架 2-下架',
    sort_order INT DEFAULT 0 COMMENT '排序值',
    is_banner TINYINT(1) DEFAULT 0 COMMENT '是否 Banner 展示',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    FOREIGN KEY (merchant_id) REFERENCES merchant(id),
    FOREIGN KEY (stall_id) REFERENCES canteen_stall(id),
    FOREIGN KEY (facility_id) REFERENCES campus_facility(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券表';

-- 用户优惠券表（记录领取关系）
CREATE TABLE user_coupon (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '记录 ID',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    coupon_id BIGINT NOT NULL COMMENT '优惠券 ID',
    status INT NOT NULL DEFAULT 1 COMMENT '状态：1-未使用 2-已使用 3-已过期',
    claim_count INT NOT NULL DEFAULT 1 COMMENT '领取次数',
    receiver_name VARCHAR(50) COMMENT '联系人',
    receiver_phone VARCHAR(20) COMMENT '手机号',
    remark VARCHAR(255) COMMENT '备注',
    claim_time DATETIME COMMENT '领取时间',
    use_time DATETIME COMMENT '使用时间',
    expiry_time DATETIME COMMENT '过期时间',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    UNIQUE KEY uk_user_coupon (user_id, coupon_id),
    FOREIGN KEY (user_id) REFERENCES sys_user(id),
    FOREIGN KEY (coupon_id) REFERENCES promotion_coupon(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户优惠券表';

-- 优惠券测试数据
INSERT INTO promotion_coupon (id, coupon_name, category, merchant_id, stall_id, facility_id, total_quantity, start_date, end_date, image_url, tag_type, pickup_location, description, status, sort_order, is_banner, create_time, update_time) VALUES
-- 食堂优惠券
(1, '学一食堂满减券', 'coupon', 1, NULL, 1, 500, '2026-03-01', '2026-12-31', 'https://picsum.photos/200/200?random=501', 'hot', '学一食堂一楼服务台', '线下领取纸质优惠券，凭校园卡每人可领取 1 张，适用于学一食堂堂食消费。', 1, 1, 1, NOW(), NOW()),
(2, '早餐专享券', 'coupon', 1, 1, 1, 300, '2026-03-01', '2026-06-30', 'https://picsum.photos/200/200?random=502', 'new', '学一食堂早餐窗口旁领取点', '早餐时段线下发放，凭学生证领取，适用于早餐包子铺及指定早餐档口。', 1, 2, 1, NOW(), NOW()),
(3, '麻辣烫专享券', 'coupon', 1, 2, 1, 200, '2026-03-15', '2026-09-30', 'https://picsum.photos/200/200?random=503', 'recommend', '学一食堂二楼麻辣烫档口收银台', '线下领取后当日可用，适用于麻辣烫档口消费。', 1, 3, 0, NOW(), NOW()),
(4, '石锅拌饭优惠券', 'coupon', 1, 3, 1, 150, '2026-03-01', '2026-12-31', 'https://picsum.photos/200/200?random=504', '', '学一食堂二楼石锅拌饭窗口', '适用于韩式石锅拌饭窗口，线下领取纸券后点餐出示即可使用。', 1, 4, 0, NOW(), NOW()),
(5, '兰州拉面优惠券', 'coupon', 1, 5, 1, 400, '2026-03-01', '2026-12-31', 'https://picsum.photos/200/200?random=505', 'hot', '学一食堂一楼兰州拉面窗口', '线下领取餐券后可在拉面窗口使用。', 1, 5, 0, NOW(), NOW()),
-- 饮品优惠券
(6, '书香咖啡学生套餐券', 'coupon', 2, NULL, 10, 200, '2026-03-01', '2026-12-31', 'https://picsum.photos/200/200?random=506', 'recommend', '图书馆一楼书香咖啡服务台', '在书香咖啡前台线下领取，适用于咖啡加蛋糕学生套餐。', 1, 6, 1, NOW(), NOW()),
(7, '奶茶专享券', 'coupon', 2, 8, 2, 300, '2026-03-15', '2026-09-30', 'https://picsum.photos/200/200?random=507', 'new', '学二食堂奶茶饮品站收银处', '线下领取后到店使用，适用于指定饮品优惠活动。', 1, 7, 0, NOW(), NOW()),
-- 超市优惠券
(8, '校园超市通用券', 'coupon', 3, NULL, NULL, 500, '2026-03-01', '2026-12-31', 'https://picsum.photos/200/200?random=508', 'hot', '校园便利超市入口服务台', '在超市服务台线下领取，适用于超市日常消费场景。', 1, 8, 1, NOW(), NOW()),
(9, '饮品专区优惠券', 'coupon', 3, NULL, NULL, 300, '2026-03-15', '2026-09-30', 'https://picsum.photos/200/200?random=509', '', '校园便利超市饮品区服务点', '饮品专区线下券，适用于指定饮品区商品。', 1, 9, 0, NOW(), NOW()),
-- 打印优惠券
(10, '打印店专享券', 'coupon', 4, NULL, 8, 100, '2026-03-01', '2026-12-31', 'https://picsum.photos/200/200?random=510', 'recommend', '明德楼快印图文店收银台', '线下登记领取后可直接到店使用，适用于日常打印场景。', 1, 10, 0, NOW(), NOW()),
-- 校园卡类优惠券
(11, '校园卡服务券', 'card', NULL, NULL, NULL, 1000, '2026-03-01', '2026-12-31', 'https://picsum.photos/200/200?random=511', 'hot', '学生活动中心一楼校园卡服务中心', '需本人持校园卡到窗口线下领取，适用于校园卡相关业务办理。', 1, 11, 1, NOW(), NOW()),
-- 代理服务券
(12, '代理服务券', 'ad', NULL, NULL, NULL, 500, '2026-03-01', '2026-12-31', 'https://picsum.photos/200/200?random=512', '', '网络服务中心一楼业务办理处', '线下领取服务券后办理代理充值等相关业务。', 1, 12, 0, NOW(), NOW()),
-- 生活服务券
(13, '洗衣服务券', 'life', NULL, NULL, 12, 200, '2026-03-15', '2026-09-30', 'https://picsum.photos/200/200?random=513', 'new', '综合服务楼自助洗衣房服务台', '在洗衣房服务台线下领取，适用于校园洗衣服务。', 1, 13, 0, NOW(), NOW()),
(14, '理发店体验券', 'life', NULL, NULL, NULL, 100, '2026-03-01', '2026-06-30', 'https://picsum.photos/200/200?random=514', 'recommend', '商业街校园理发店收银台', '新生首次到店可在线下领取体验券。', 1, 14, 0, NOW(), NOW());
-- =============================================
-- 第六部分：第七阶段 - 活动通知模块数据
-- =============================================

-- =============================================
-- 第七阶段：建表语句
-- =============================================

-- 活动通知表
CREATE TABLE IF NOT EXISTS activity_notice (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '通知ID',
    activity_id BIGINT NOT NULL COMMENT '关联活动ID',
    title VARCHAR(200) NOT NULL COMMENT '通知标题',
    content TEXT COMMENT '通知内容',
    publisher_id BIGINT COMMENT '发布人ID',
    publisher_name VARCHAR(50) COMMENT '发布人名称',
    status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED' COMMENT '通知状态: DRAFT-草稿, PUBLISHED-已发布',
    publish_time DATETIME COMMENT '发布时间',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动通知表';

-- =============================================
-- 活动分类表
-- =============================================
CREATE TABLE IF NOT EXISTS activity_category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '分类ID',
    category_name VARCHAR(100) NOT NULL COMMENT '分类名称',
    sort INT DEFAULT 1 COMMENT '排序',
    status INT NOT NULL DEFAULT 1 COMMENT '状态: 1-启用, 0-禁用',
    create_time DATETIME COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动分类表';

-- =============================================
-- 活动表
-- =============================================
CREATE TABLE IF NOT EXISTS activity (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '活动ID',
    title VARCHAR(200) NOT NULL COMMENT '活动标题',
    cover_image VARCHAR(255) COMMENT '封面图片URL',
    category_id BIGINT COMMENT '分类ID',
    organizer_id BIGINT COMMENT '组织者ID',
    organizer_name VARCHAR(100) COMMENT '组织者名称',
    content TEXT COMMENT '活动内容',
    location VARCHAR(200) COMMENT '活动地点',
    max_people INT DEFAULT 0 COMMENT '最大人数',
    current_people INT DEFAULT 0 COMMENT '当前报名人数',
    start_time DATETIME COMMENT '活动开始时间',
    end_time DATETIME COMMENT '活动结束时间',
    signup_start_time DATETIME COMMENT '报名开始时间',
    signup_end_time DATETIME COMMENT '报名结束时间',
    sign_in_start_time DATETIME COMMENT '签到开始时间',
    sign_in_end_time DATETIME COMMENT '签到结束时间',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '活动状态: DRAFT-草稿, PUBLISHED-已发布, REJECTED-已驳回, CANCELLED-已取消, COMPLETED-已完成',
    sign_in_type INT DEFAULT 1 COMMENT '签到类型: 1-现场签到, 2-二维码签到',
    sign_in_open BOOLEAN DEFAULT FALSE COMMENT '签到是否开启',
    requires_audit BOOLEAN DEFAULT FALSE COMMENT '报名是否需要审核',
    cancel_requires_audit BOOLEAN DEFAULT FALSE COMMENT '取消报名是否需要审核',
    score DECIMAL(3,1) DEFAULT 0 COMMENT '活动学分',
    contact_name VARCHAR(50) COMMENT '联系人姓名',
    contact_phone VARCHAR(20) COMMENT '联系电话',
    create_time DATETIME COMMENT '创建时间',
    FOREIGN KEY (category_id) REFERENCES activity_category(id),
    FOREIGN KEY (organizer_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动表';

-- =============================================
-- 第七阶段：清空表数据
-- =============================================
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE activity_category;
TRUNCATE TABLE activity;
TRUNCATE TABLE activity_notice;
SET FOREIGN_KEY_CHECKS = 1;

-- =============================================
-- 活动分类测试数据
-- =============================================
INSERT INTO activity_category (id, category_name, sort, status, create_time) VALUES
(1, '学术讲座', 1, 1, NOW()),
(2, '体育活动', 2, 1, NOW()),
(3, '社团活动', 3, 1, NOW()),
(4, '志愿活动', 4, 1, NOW()),
(5, '新生活动', 5, 1, NOW());

-- =============================================
-- 活动测试数据
-- =============================================
INSERT INTO activity (id, title, cover_image, category_id, organizer_id, organizer_name, content, location, max_people, current_people, start_time, end_time, signup_start_time, signup_end_time, sign_in_start_time, sign_in_end_time, status, sign_in_type, sign_in_open, requires_audit, cancel_requires_audit, score, contact_name, contact_phone, create_time) VALUES
(1, 'AI 学习工作坊', 'https://picsum.photos/800/600?random=101', 1, 2, '张老师', '面向全校同学的 AI 学习工作坊，讲解常见工具和实践方法。', '图书馆报告厅', 120, 46, '2026-04-12 14:00:00', '2026-04-12 17:00:00', '2026-04-01 09:00:00', '2026-04-11 18:00:00', '2026-04-12 13:40:00', '2026-04-12 15:30:00', 'PUBLISHED', 1, false, true, true, 1.5, '张老师', '13800138000', NOW()),
(2, '春季篮球联赛', 'https://picsum.photos/800/600?random=102', 2, 3, '李老师', '春季篮球联赛正在进行，欢迎同学们到场观赛与加油。', '篮球场', 240, 168, '2026-04-06 13:00:00', '2026-04-06 18:00:00', '2026-03-20 08:00:00', '2026-04-05 18:00:00', '2026-04-06 12:45:00', '2026-04-06 15:00:00', 'PUBLISHED', 1, false, false, false, 1.0, '李老师', '13800138001', NOW()),
(3, '社团开放日', 'https://picsum.photos/800/600?random=103', 3, 2, '张老师', '各大社团集中展示招新内容，现场可体验互动项目并咨询报名。', '图书馆前广场', 300, 132, '2026-04-10 10:00:00', '2026-04-10 16:30:00', '2026-04-02 09:00:00', '2026-04-09 20:00:00', '2026-04-10 09:30:00', '2026-04-10 12:30:00', 'PUBLISHED', 1, false, false, false, 0.5, '张老师', '13800138000', NOW()),
(4, '校园环保行动', 'https://picsum.photos/800/600?random=104', 4, 3, '李老师', '组织志愿者进行校园清洁与垃圾分类宣传，活动已经顺利结束。', '校园主干道', 80, 63, '2026-04-03 09:00:00', '2026-04-03 12:00:00', '2026-03-25 08:00:00', '2026-04-02 18:00:00', '2026-04-03 08:30:00', '2026-04-03 10:30:00', 'COMPLETED', 1, false, true, false, 1.0, '李老师', '13800138001', NOW()),
(5, '新生融入分享会', 'https://picsum.photos/800/600?random=105', 5, 2, '张老师', '邀请优秀学长学姐分享学习与生活经验，帮助新生快速适应校园。', '博学楼报告厅', 180, 72, '2026-04-08 19:00:00', '2026-04-08 21:00:00', '2026-04-01 10:00:00', '2026-04-07 18:00:00', '2026-04-08 18:30:00', '2026-04-08 20:00:00', 'PUBLISHED', 1, false, false, true, 0.5, '张老师', '13800138000', NOW()),
(6, '心理健康沙龙', 'https://picsum.photos/800/600?random=106', 1, 3, '李老师', '围绕压力管理与情绪疏导开展交流分享，适合同学们报名参加。', '大学生活动中心 201', 90, 21, '2026-04-15 15:00:00', '2026-04-15 17:00:00', '2026-04-04 08:00:00', '2026-04-14 18:00:00', '2026-04-15 14:40:00', '2026-04-15 16:20:00', 'PUBLISHED', 1, false, true, true, 0.5, '李老师', '13800138001', NOW()),
(7, 'Today Start Campus Activity', 'https://picsum.photos/800/600?random=107', 1, 2, 'Teacher Zhang', 'Activity starts on 2026-05-22 and ends on 2026-05-24.', 'Library Lecture Hall', 120, 45, '2026-05-22 09:00:00', '2026-05-24 18:00:00', '2026-05-22 00:00:00', '2026-05-23 18:00:00', '2026-05-23 09:00:00', '2026-05-24 16:00:00', 'PUBLISHED', 1, false, false, false, 0.2, 'Teacher Zhang', '13800138000', NOW()),
(8, 'Campus Registration Demo', 'https://picsum.photos/800/600?random=108', 1, 2, 'Teacher Zhang', 'This is a demo activity for signup testing.', 'Library Lecture Hall', 120, 0, '2026-05-23 19:00:00', '2026-05-24 21:00:00', '2026-05-22 00:00:00', '2026-05-24 18:00:00', '2026-05-23 19:00:00', '2026-05-24 20:00:00', 'PUBLISHED', 1, false, false, true, 0.2, 'Teacher Zhang', '13800138000', NOW());

-- =============================================
-- 活动通知测试数据
-- =============================================
INSERT INTO activity_notice (id, activity_id, title, content, publisher_id, publisher_name, status, publish_time, create_time, update_time) VALUES
(1, 1, '讲座时间变更通知', '原定于本周五的校园讲座因故推迟到本周六上午9点，地点不变，请同学们相互转告。', 2, '张老师', 'PUBLISHED', NOW(), NOW(), NOW()),
(2, 1, '讲座补充说明', '本次讲座特别邀请了业内知名专家前来分享，建议同学们提前准备好相关问题。', 2, '张老师', 'PUBLISHED', NOW(), NOW(), NOW()),
(3, 2, '体育比赛报名即将截止', '校园篮球赛报名将于本周日截止，还未报名的同学请抓紧时间通过系统报名。', 3, '李老师', 'PUBLISHED', NOW(), NOW(), NOW()),
(4, 3, '社团招新活动通知', '新学期社团招新活动定于下周一至周三在图书馆前广场举行，欢迎各位同学积极参与。', 2, '张老师', 'PUBLISHED', NOW(), NOW(), NOW()),
(5, 4, '志愿者活动预告', '本周六将组织校园环境美化志愿活动，报名成功的同学请准时到达指定集合点。', 3, '李老师', 'PUBLISHED', NOW(), NOW(), NOW()),
(6, 5, '新生见面会安排', '新生见面会将于本周三下午2点在博学楼报告厅举行，请新生准时参加。', 2, '张老师', 'PUBLISHED', NOW(), NOW(), NOW());

-- =============================================
-- 论坛话题（forum_post.topic_id 外键依赖；与小程序分类/发帖选项 id 对齐）
-- =============================================
INSERT IGNORE INTO forum_topic (id, topic_name, post_count, is_hot, status, create_time) VALUES
(1, '热门', 0, 1, 'ACTIVE', NOW()),
(2, '最新', 0, 0, 'ACTIVE', NOW()),
(3, '📢公告', 0, 0, 'ACTIVE', NOW()),
(4, '💰集市', 0, 1, 'ACTIVE', NOW()),
(5, '😊求助', 0, 0, 'ACTIVE', NOW()),
(6, '🔑失物', 0, 0, 'ACTIVE', NOW()),
(7, '💕表白', 0, 0, 'ACTIVE', NOW()),
(8, '🍟美食', 0, 1, 'ACTIVE', NOW()),
(9, '🤝搭子', 0, 0, 'ACTIVE', NOW()),
(10, '📚学习资料', 0, 0, 'ACTIVE', NOW()),
(11, '🌸影忆青春', 0, 0, 'ACTIVE', NOW())
ON DUPLICATE KEY UPDATE topic_name = VALUES(topic_name), status = 'ACTIVE';

CREATE TABLE IF NOT EXISTS forum_follow (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '关注ID',
    user_id BIGINT NOT NULL COMMENT '关注者ID',
    follow_id BIGINT NOT NULL COMMENT '被关注用户ID',
    create_time DATETIME COMMENT '创建时间',
    UNIQUE KEY uk_forum_follow_user_follow (user_id, follow_id),
    FOREIGN KEY (user_id) REFERENCES sys_user(id),
    FOREIGN KEY (follow_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛关注表';

CREATE TABLE IF NOT EXISTS forum_like (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '点赞ID',
    user_id BIGINT NOT NULL COMMENT '点赞用户ID',
    target_id BIGINT NOT NULL COMMENT '目标ID',
    target_type VARCHAR(20) NOT NULL DEFAULT 'POST' COMMENT '点赞目标类型',
    create_time DATETIME COMMENT '创建时间',
    UNIQUE KEY uk_forum_like_user_target (user_id, target_id, target_type),
    FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛帖子点赞表';

-- 论坛帖子与评论测试数据
SET FOREIGN_KEY_CHECKS = 0;
DELETE FROM forum_like WHERE id BETWEEN 1 AND 50;
DELETE FROM forum_follow WHERE id BETWEEN 1 AND 50;
DELETE FROM forum_comment WHERE id BETWEEN 1 AND 20;
DELETE FROM forum_post WHERE id BETWEEN 1 AND 20;
SET FOREIGN_KEY_CHECKS = 1;

INSERT INTO forum_post (id, user_id, title, content, images, topic_id, view_count, like_count, comment_count, create_time, update_time) VALUES
(1, 4, '图书馆最近自习区哪个位置最安静？', '最近准备期中复习，想找一个下午和晚上都比较安静的位置。有没有同学推荐一下图书馆或者教学楼里适合长期自习的角落？', '["https://picsum.photos/seed/forum-post-1/720/480"]', 2, 186, 4, 3, '2026-04-08 19:20:00', '2026-04-08 19:20:00'),
(2, 5, '北区食堂二楼新开的窗口怎么样', '今天路过看到北区食堂二楼新开了一家轻食窗口，想问问已经吃过的同学味道和性价比怎么样，适不适合减脂期？', '["https://picsum.photos/seed/forum-post-2/720/480"]', 6, 243, 4, 2, '2026-04-09 12:10:00', '2026-04-09 12:10:00'),
(3, 6, '春招双选会投简历前要准备什么', '学院通知下周有春招双选会，我是第一次参加线下招聘会。除了简历，还需要提前准备自我介绍或者作品集吗？想听听学长学姐建议。', '[]', 3, 312, 5, 2, '2026-04-09 21:00:00', '2026-04-09 21:00:00'),
(4, 4, '宿舍晚上断网之后还有没有备用方案', '我们宿舍这两天晚上网络不太稳定，刚好又在赶课程作业。大家一般会用手机热点还是去哪里找备用网络？', '[]', 1, 129, 2, 1, '2026-04-10 22:35:00', '2026-04-10 22:35:00'),
(5, 5, '想出一辆九成新的自行车，校内交易走什么方式更安全', '最近准备把平时代步的自行车转掉，担心线下交易容易扯皮。有没有同学分享一下校内二手交易更稳妥的流程或者注意事项？', '["https://picsum.photos/seed/forum-post-5/720/480"]', 4, 204, 3, 1, '2026-04-11 15:45:00', '2026-04-11 15:45:00'),
(6, 7, '求助：学生卡丢了，补办流程复杂吗', '今天晚上在操场附近找不到学生卡了，已经去失物招领问过还没有消息。补办一般多久能下来，临时进图书馆有没有替代办法？', '[]', 7, 267, 3, 1, '2026-04-11 20:18:00', '2026-04-11 20:18:00');

INSERT INTO forum_comment (id, post_id, user_id, parent_id, reply_to_id, content, like_count, create_time) VALUES
(1, 1, 5, NULL, NULL, '三楼靠窗那排如果不是饭点，整体会比较安静，插座也够用。', 9, '2026-04-08 19:35:00'),
(2, 1, 6, NULL, NULL, '我最近都在博学楼空教室复习，晚上人比图书馆少很多。', 6, '2026-04-08 20:02:00'),
(3, 1, 4, 2, 6, '这个建议不错，我明天去看看空教室情况。', 3, '2026-04-08 20:15:00'),
(4, 2, 4, NULL, NULL, '轻食窗口鸡胸肉和玉米杯还可以，价格比校外便宜一点。', 12, '2026-04-09 12:30:00'),
(5, 2, 7, NULL, NULL, '如果你在减脂，建议备注少酱，不然整体热量还是有点高。', 8, '2026-04-09 13:06:00'),
(6, 3, 4, NULL, NULL, '简历建议至少准备 5 份纸质版，很多企业现场会直接收。', 14, '2026-04-09 21:15:00'),
(7, 3, 5, NULL, NULL, '如果有作品集或者项目经历，最好打印一个精简版带上。', 7, '2026-04-09 21:33:00'),
(8, 4, 6, NULL, NULL, '我一般直接去图书馆一楼，用校园网会稳定很多。', 5, '2026-04-10 22:48:00'),
(9, 5, 6, NULL, NULL, '建议当面验车并保留聊天记录，价格和车况提前说清楚。', 11, '2026-04-11 16:10:00'),
(10, 6, 8, NULL, NULL, '补办不算复杂，先在辅导员系统挂失，然后去服务大厅办临时证明。', 10, '2026-04-11 20:40:00');

INSERT INTO forum_like (id, user_id, target_id, target_type, create_time) VALUES
(1, 5, 1, 'POST', '2026-04-08 19:40:00'),
(2, 6, 1, 'POST', '2026-04-08 20:05:00'),
(3, 7, 1, 'POST', '2026-04-08 20:40:00'),
(4, 13, 1, 'POST', '2026-04-08 21:18:00'),
(5, 4, 2, 'POST', '2026-04-09 12:28:00'),
(6, 6, 2, 'POST', '2026-04-09 12:40:00'),
(7, 8, 2, 'POST', '2026-04-09 13:15:00'),
(8, 13, 2, 'POST', '2026-04-09 14:02:00'),
(9, 4, 3, 'POST', '2026-04-09 21:08:00'),
(10, 5, 3, 'POST', '2026-04-09 21:20:00'),
(11, 7, 3, 'POST', '2026-04-09 21:31:00'),
(12, 8, 3, 'POST', '2026-04-09 22:05:00'),
(13, 13, 3, 'POST', '2026-04-09 22:26:00'),
(14, 5, 4, 'POST', '2026-04-10 22:49:00'),
(15, 8, 4, 'POST', '2026-04-10 23:10:00'),
(16, 4, 5, 'POST', '2026-04-11 15:58:00'),
(17, 7, 5, 'POST', '2026-04-11 16:20:00'),
(18, 13, 5, 'POST', '2026-04-11 16:42:00'),
(19, 4, 6, 'POST', '2026-04-11 20:28:00'),
(20, 5, 6, 'POST', '2026-04-11 20:44:00'),
(21, 13, 6, 'POST', '2026-04-11 21:06:00');

INSERT INTO forum_follow (id, user_id, follow_id, create_time) VALUES
(1, 5, 4, '2026-04-06 18:00:00'),
(2, 6, 4, '2026-04-06 18:10:00'),
(3, 7, 5, '2026-04-07 09:20:00'),
(4, 8, 5, '2026-04-07 11:00:00'),
(5, 13, 6, '2026-04-07 16:35:00'),
(6, 4, 6, '2026-04-08 08:45:00'),
(7, 5, 7, '2026-04-08 12:12:00'),
(8, 6, 13, '2026-04-09 10:05:00'),
(9, 7, 4, '2026-04-09 20:15:00'),
(10, 8, 6, '2026-04-10 14:30:00');

UPDATE forum_topic SET post_count = 1 WHERE id IN (1, 3, 4, 6, 7);
UPDATE forum_topic SET post_count = 1 WHERE id = 2;
UPDATE forum_topic SET post_count = 0 WHERE id IN (5, 8);

-- =============================================
-- 菜品表
-- =============================================
DROP TABLE IF EXISTS dish_review;
DROP TABLE IF EXISTS dish;
CREATE TABLE IF NOT EXISTS dish_cuisine (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '菜系 ID',
    canteen_place_id BIGINT NOT NULL COMMENT '所属食堂点位 ID',
    cuisine_name VARCHAR(50) NOT NULL COMMENT '菜系名称',
    status INT NOT NULL DEFAULT 1 COMMENT '状态: 1-启用 0-停用',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序值',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    UNIQUE KEY uk_dish_cuisine_canteen_name (canteen_place_id, cuisine_name),
    INDEX idx_dish_cuisine_canteen (canteen_place_id),
    FOREIGN KEY (canteen_place_id) REFERENCES map_place(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜品菜系表';

CREATE TABLE IF NOT EXISTS dish (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '菜品 ID',
    name VARCHAR(100) NOT NULL COMMENT '菜品名称',
    stall_id BIGINT COMMENT '旧档口业务 ID（兼容字段）',
    stall_place_id BIGINT COMMENT '所属档口点位 ID',
    price DECIMAL(10,2) NOT NULL COMMENT '菜品价格',
    category VARCHAR(50) COMMENT '菜品分类',
    cuisine_id BIGINT COMMENT '菜系分类 ID',
    image_url VARCHAR(255) COMMENT '菜品图片 URL',
    rating DECIMAL(3,2) DEFAULT 0 COMMENT '菜品评分 (0-5)',
    sold_count INT DEFAULT 0 COMMENT '销量',
    is_available TINYINT(1) DEFAULT 1 COMMENT '是否可用：1-可售 0-停售',
    taste VARCHAR(100) COMMENT '口味类型',
    description TEXT COMMENT '菜品描述',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    FOREIGN KEY (stall_id) REFERENCES canteen_stall(id),
    FOREIGN KEY (stall_place_id) REFERENCES map_place(id),
    FOREIGN KEY (cuisine_id) REFERENCES dish_cuisine(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜品表';

-- =============================================
-- 菜品数据
-- =============================================
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE dish;
SET FOREIGN_KEY_CHECKS = 1;

INSERT INTO dish (id, name, stall_id, price, category, image_url, rating, sold_count, is_available, taste, description, create_time, update_time) VALUES
-- 早餐包子铺 (stall_id=1) 的菜品
(1, '鲜肉包', 1, 2.50, '包子', 'https://picsum.photos/seed/dish001/300/300', 4.8, 1200, 1, '咸鲜', '新鲜猪肉馅，皮薄馅大，汁多味美。', NOW(), NOW()),
(2, '菜包', 1, 2.00, '包子', 'https://picsum.photos/seed/dish002/300/300', 4.5, 800, 1, '清淡', '青菜香菇馅，清淡健康。', NOW(), NOW()),
(3, '豆浆', 1, 1.50, '饮品', 'https://picsum.photos/seed/dish003/300/300', 4.6, 950, 1, '微甜', '现磨豆浆，香浓可口。', NOW(), NOW()),
(4, '油条', 1, 2.00, '油炸', 'https://picsum.photos/seed/dish004/300/300', 4.7, 1100, 1, '咸香', '现炸油条，外酥里嫩。', NOW(), NOW()),

-- 麻辣烫档口 (stall_id=2) 的菜品
(5, '麻辣烫自选', 2, 18.00, '麻辣烫', 'https://picsum.photos/seed/dish005/300/300', 4.5, 890, 1, '麻辣', '多种食材自选，麻酱香浓。', NOW(), NOW()),
(6, '方便面', 2, 5.00, '面食', 'https://picsum.photos/seed/dish006/300/300', 4.2, 450, 1, '麻辣', '经典方便面，可加各种配菜。', NOW(), NOW()),

-- 石锅拌饭 (stall_id=3) 的菜品
(7, '牛肉石锅拌饭', 3, 18.00, '拌饭', 'https://picsum.photos/seed/dish007/300/300', 4.6, 620, 1, '微辣', '韩式牛肉石锅拌饭，锅巴香脆。', NOW(), NOW()),
(8, '五花肉石锅拌饭', 3, 16.00, '拌饭', 'https://picsum.photos/seed/dish008/300/300', 4.4, 380, 1, '微辣', '五花肉石锅拌饭，香嫩可口。', NOW(), NOW()),
(9, '芝士年糕', 3, 12.00, '小吃', 'https://picsum.photos/seed/dish009/300/300', 4.5, 290, 1, '微甜', '韩式芝士年糕，拉丝浓郁。', NOW(), NOW()),

-- 黄焖鸡米饭 (stall_id=4) 的菜品
(10, '黄焖鸡米饭', 4, 15.00, '米饭', 'https://picsum.photos/seed/dish010/300/300', 4.3, 750, 1, '咸鲜', '经典黄焖鸡，汤汁浓郁。', NOW(), NOW()),
(11, '黄焖排骨米饭', 4, 18.00, '米饭', 'https://picsum.photos/seed/dish011/300/300', 4.4, 320, 1, '咸鲜', '黄焖排骨，肉质鲜嫩。', NOW(), NOW()),
(12, '金针菇', 4, 3.00, '配菜', 'https://picsum.photos/seed/dish012/300/300', 4.2, 180, 1, '清淡', '新鲜金针菇，配菜佳品。', NOW(), NOW()),

-- 兰州拉面 (stall_id=5) 的菜品
(13, '牛肉拉面', 5, 14.00, '拉面', 'https://picsum.photos/seed/dish013/300/300', 4.7, 980, 1, '清淡', '手工拉面，汤鲜面劲道。', NOW(), NOW()),
(14, '羊肉拉面', 5, 16.00, '拉面', 'https://picsum.photos/seed/dish014/300/300', 4.5, 420, 1, '清淡', '羊肉拉面，西北风味。', NOW(), NOW()),
(15, '凉拌牛肉', 5, 12.00, '凉菜', 'https://picsum.photos/seed/dish015/300/300', 4.6, 280, 1, '五香', '凉拌牛肉，香辣可口。', NOW(), NOW()),

-- 沙县小吃 (stall_id=6) 的菜品
(16, '拌面', 6, 5.00, '面食', 'https://picsum.photos/seed/dish016/300/300', 4.3, 650, 1, '咸香', '经典拌面，花生酱香浓。', NOW(), NOW()),
(17, '扁肉', 6, 6.00, '馄饨', 'https://picsum.photos/seed/dish017/300/300', 4.4, 520, 1, '清淡', '沙县扁肉，皮薄馅嫩。', NOW(), NOW()),
(18, '蒸饺', 6, 8.00, '饺子', 'https://picsum.photos/seed/dish018/300/300', 4.2, 380, 1, '咸鲜', '沙县蒸饺，馅料丰富。', NOW(), NOW()),
(19, '排骨炖罐', 6, 10.00, '炖汤', 'https://picsum.photos/seed/dish019/300/300', 4.5, 290, 1, '清淡', '排骨炖汤，营养丰富。', NOW(), NOW()),

-- 自选快餐 (stall_id=7) 的菜品
(20, '两荤两素套餐', 7, 13.00, '套餐', 'https://picsum.photos/seed/dish020/300/300', 4.4, 720, 1, '家常', '两荤两素搭配，价格实惠。', NOW(), NOW()),
(21, '一荤两素套餐', 7, 11.00, '套餐', 'https://picsum.photos/seed/dish021/300/300', 4.2, 480, 1, '家常', '一荤两素，经济实惠。', NOW(), NOW()),

-- 奶茶饮品站 (stall_id=8) 的菜品
(22, '珍珠奶茶', 8, 8.00, '奶茶', 'https://picsum.photos/seed/dish022/300/300', 4.7, 1100, 1, '微甜', '经典珍珠奶茶，Q 弹可口。', NOW(), NOW()),
(23, '柠檬茶', 8, 6.00, '果茶', 'https://picsum.photos/seed/dish023/300/300', 4.5, 680, 1, '酸甜', '新鲜柠檬茶，清爽解渴。', NOW(), NOW()),
(24, '芒果冰沙', 8, 10.00, '冰沙', 'https://picsum.photos/seed/dish024/300/300', 4.6, 420, 1, '微甜', '新鲜芒果冰沙，夏日必备。', NOW(), NOW()),

-- 煎饼果子 (stall_id=9) 的菜品
(25, '经典煎饼', 9, 6.00, '煎饼', 'https://picsum.photos/seed/dish025/300/300', 4.5, 580, 1, '咸香', '经典煎饼果子，薄脆可口。', NOW(), NOW()),
(26, '加蛋煎饼', 9, 8.00, '煎饼', 'https://picsum.photos/seed/dish026/300/300', 4.6, 420, 1, '咸香', '加蛋煎饼，营养更丰富。', NOW(), NOW()),
(27, '加肠煎饼', 9, 9.00, '煎饼', 'https://picsum.photos/seed/dish027/300/300', 4.5, 350, 1, '咸香', '加肠煎饼，肉香四溢。', NOW(), NOW()),

-- 重庆小面 (stall_id=10) 的菜品
(28, '豌杂面', 10, 15.00, '小面', 'https://picsum.photos/seed/dish028/300/300', 4.6, 680, 1, '麻辣', '豌杂面，豌豆软糯，杂酱香浓。', NOW(), NOW()),
(29, '酸辣粉', 10, 12.00, '米粉', 'https://picsum.photos/seed/dish029/300/300', 4.5, 520, 1, '酸辣', '酸辣粉，酸爽开胃。', NOW(), NOW()),
(30, '肥肠面', 10, 18.00, '小面', 'https://picsum.photos/seed/dish030/300/300', 4.4, 280, 1, '麻辣', '肥肠面，肥肠软糯入味。', NOW(), NOW()),

-- 清真牛肉面 (stall_id=11) 的菜品
(31, '清真牛肉面', 11, 16.00, '牛肉面', 'https://picsum.photos/seed/dish031/300/300', 4.7, 520, 1, '清淡', '清真牛肉面，汤底醇厚。', NOW(), NOW()),
(32, '牛杂面', 11, 18.00, '牛肉面', 'https://picsum.photos/seed/dish032/300/300', 4.5, 280, 1, '清淡', '牛杂面，配料丰富。', NOW(), NOW()),
(33, '凉拌牛腱', 11, 15.00, '凉菜', 'https://picsum.photos/seed/dish033/300/300', 4.6, 190, 1, '五香', '凉拌牛腱，肉质紧实。', NOW(), NOW()),

-- 新疆大盘鸡 (stall_id=12) 的菜品
(34, '大盘鸡', 12, 45.00, '大盘鸡', 'https://picsum.photos/seed/dish034/300/300', 4.7, 380, 1, '麻辣', '新疆大盘鸡，配皮带面。', NOW(), NOW()),
(35, '小盘鸡', 12, 28.00, '大盘鸡', 'https://picsum.photos/seed/dish035/300/300', 4.5, 220, 1, '麻辣', '小盘鸡，适合一人食。', NOW(), NOW()),
(36, '手抓饭', 12, 18.00, '米饭', 'https://picsum.photos/seed/dish036/300/300', 4.4, 180, 1, '咸香', '新疆手抓饭，羊肉香浓。', NOW(), NOW()),

-- 烤羊肉串 (stall_id=13) 的菜品
(37, '烤羊肉串', 13, 5.00, '烤串', 'https://picsum.photos/seed/dish037/300/300', 4.8, 850, 1, '孜然', '现烤羊肉串，外焦里嫩。', NOW(), NOW()),
(38, '烤鸡翅', 13, 8.00, '烤串', 'https://picsum.photos/seed/dish038/300/300', 4.6, 420, 1, '孜然', '烤鸡翅，外酥里嫩。', NOW(), NOW()),
(39, '馕饼', 13, 3.00, '主食', 'https://picsum.photos/seed/dish039/300/300', 4.3, 280, 1, '原味', '新疆馕饼，配烤串绝佳。', NOW(), NOW());

-- =============================================
-- 菜品评价表
-- =============================================
CREATE TABLE IF NOT EXISTS dish_review (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '评价 ID',
    dish_id BIGINT NOT NULL COMMENT '菜品 ID',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    stall_id BIGINT NOT NULL COMMENT '档口 ID',
    rating DECIMAL(3,2) NOT NULL COMMENT '评分 (0-5)',
    content TEXT COMMENT '评价内容',
    images TEXT COMMENT '评价图片 URLs，逗号分隔',
    is_anonymous TINYINT(1) DEFAULT 0 COMMENT '是否匿名：1-匿名 0-公开',
    helpful_count INT DEFAULT 0 COMMENT '有帮助数',
    reply_count INT DEFAULT 0 COMMENT '回复数',
    status INT DEFAULT 1 COMMENT '状态：1-正常 0-隐藏 2-已删除',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    FOREIGN KEY (dish_id) REFERENCES dish(id),
    FOREIGN KEY (stall_id) REFERENCES canteen_stall(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜品评价表';

-- =============================================
-- 菜品评价数据
-- =============================================
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE dish_review;
SET FOREIGN_KEY_CHECKS = 1;

INSERT INTO dish_review (id, dish_id, user_id, stall_id, rating, content, images, is_anonymous, helpful_count, reply_count, status, create_time, update_time) VALUES
-- 鲜肉包 (dish_id=1) 的评价
(1, 1, 4, 1, 5.00, '早八前买最合适，包子皮软乎，肉馅有汤汁，两个下肚很顶饱。', 'https://picsum.photos/seed/review001/200/200', 0, 12, 3, 1, '2026-04-04 07:42:00', '2026-04-04 07:42:00'),
(2, 1, 5, 1, 4.00, '味道稳定，肉馅不柴，就是高峰期排队有点久，赶课的时候不太敢等。', NULL, 0, 7, 1, 1, '2026-04-05 08:11:00', '2026-04-05 08:11:00'),
(3, 1, 6, 1, 5.00, '比校外早餐摊干净，现蒸出来的那一笼最好吃，蘸点醋更提味。', NULL, 0, 9, 0, 1, '2026-04-07 07:25:00', '2026-04-07 07:25:00'),

-- 菜包 (dish_id=2) 的评价
(4, 2, 7, 1, 4.00, '青菜和香菇的比例还行，整体偏清淡，早上不想吃太油的时候会选它。', NULL, 0, 3, 0, 1, '2026-04-03 08:02:00', '2026-04-03 08:02:00'),
(5, 2, 8, 1, 4.50, '价格友好，馅也挺足，配豆浆刚好。就是出锅晚一点口感会差一点。', NULL, 0, 6, 2, 1, '2026-04-08 08:26:00', '2026-04-08 08:26:00'),

-- 豆浆 (dish_id=3) 的评价
(6, 3, 13, 1, 5.00, '不像有些地方冲粉味重，这家喝得出来是现磨的，香味很足。', NULL, 0, 10, 1, 1, '2026-04-02 07:38:00', '2026-04-02 07:38:00'),
(7, 3, 2, 1, 4.00, '豆香味可以，早上刚打出来会比较烫，建议打包路上喝。', NULL, 0, 2, 0, 1, '2026-04-06 07:55:00', '2026-04-06 07:55:00'),

-- 油条 (dish_id=4) 的评价
(8, 4, 3, 1, 4.50, '现炸那会儿很酥，泡豆浆也不会马上散掉，口感不错。', 'https://picsum.photos/seed/review002/200/200', 0, 7, 0, 1, '2026-04-01 07:21:00', '2026-04-01 07:21:00'),
(9, 4, 4, 1, 3.50, '晚一点去买会偏软，建议早点，热的时候明显更香。', NULL, 0, 4, 1, 1, '2026-04-09 08:40:00', '2026-04-09 08:40:00'),

-- 麻辣烫自选 (dish_id=5) 的评价
(10, 5, 5, 2, 4.50, '自选自由度很高，麻酱和蒜汁加一起很香，宿舍几个人经常一起点。', 'https://picsum.photos/seed/review003/200/200', 0, 15, 5, 1, '2026-04-04 18:16:00', '2026-04-04 18:16:00'),
(11, 5, 6, 2, 4.00, '菜品种类不少，丸子和豆皮都还行，就是称重后价格容易超预算。', NULL, 0, 6, 2, 1, '2026-04-06 12:24:00', '2026-04-06 12:24:00'),
(12, 5, 7, 2, 5.00, '辣度能调这点很好，粉和蔬菜都煮得比较到位，汤底也够味。', NULL, 0, 9, 0, 1, '2026-04-10 17:53:00', '2026-04-10 17:53:00'),
(13, 5, 8, 2, 3.00, '味道没问题，但高峰时段出餐慢，饿得厉害的时候体验一般。', NULL, 1, 4, 0, 1, '2026-04-11 18:37:00', '2026-04-11 18:37:00'),

-- 牛肉石锅拌饭 (dish_id=7) 的评价
(14, 7, 13, 3, 4.50, '锅底真的会有一层脆脆的锅巴，拌上酱后很香，牛肉也不柴。', 'https://picsum.photos/seed/review004/200/200', 0, 11, 3, 1, '2026-04-03 12:08:00', '2026-04-03 12:08:00'),
(15, 7, 2, 3, 4.00, '整体味道不错，配菜给得也算均衡，就是男生可能会觉得量略少。', NULL, 0, 4, 1, 1, '2026-04-07 11:56:00', '2026-04-07 11:56:00'),
(16, 7, 4, 3, 5.00, '中午现做那一锅最稳，酱香和米饭都在线，最近吃了三次都没翻车。', NULL, 0, 8, 1, 1, '2026-04-10 12:41:00', '2026-04-10 12:41:00'),

-- 黄焖鸡米饭 (dish_id=10) 的评价
(17, 10, 5, 4, 4.50, '鸡肉炖得挺入味，土豆吸满汤汁后很好吃，适合配两碗米饭。', NULL, 0, 18, 4, 1, '2026-04-02 12:19:00', '2026-04-02 12:19:00'),
(18, 10, 6, 4, 4.00, '整体比较稳，偏家常口味，就是赶上饭点要等现出锅。', NULL, 0, 5, 0, 1, '2026-04-05 18:08:00', '2026-04-05 18:08:00'),
(19, 10, 7, 4, 5.00, '下饭能力太强了，汤汁拌饭几乎不会出错，分量对我来说刚刚好。', 'https://picsum.photos/seed/review005/200/200', 0, 12, 2, 1, '2026-04-09 17:46:00', '2026-04-09 17:46:00'),
(20, 10, 8, 4, 2.50, '这次鸡肉有点碎，辣度也比平时重，体验不如之前那几次。', NULL, 0, 3, 0, 1, '2026-04-11 12:33:00', '2026-04-11 12:33:00'),

-- 牛肉拉面 (dish_id=13) 的评价
(21, 13, 13, 5, 5.00, '汤头清但不寡，面条劲道，牛肉给得在食堂里算很有诚意。', NULL, 0, 20, 6, 1, '2026-04-03 18:05:00', '2026-04-03 18:05:00'),
(22, 13, 3, 5, 4.50, '价格和分量匹配，晚饭来一碗很舒服，冬天吃应该更合适。', NULL, 0, 8, 1, 1, '2026-04-08 17:59:00', '2026-04-08 17:59:00'),
(23, 13, 4, 5, 4.00, '辣油单独加比较合理，面没坨，唯一问题是高峰期座位不好找。', NULL, 1, 5, 0, 1, '2026-04-10 18:22:00', '2026-04-10 18:22:00'),

-- 珍珠奶茶 (dish_id=22) 的评价
(24, 22, 5, 8, 4.50, '珍珠煮得比较软糯，不会硬芯，半糖就够了，课间来一杯很解馋。', 'https://picsum.photos/seed/review006/200/200', 0, 14, 3, 1, '2026-04-04 15:12:00', '2026-04-04 15:12:00'),
(25, 22, 6, 8, 5.00, '比校外连锁便宜一点，冰量和甜度都能沟通，性价比很高。', NULL, 0, 9, 0, 1, '2026-04-08 16:20:00', '2026-04-08 16:20:00'),
(26, 22, 7, 8, 4.00, '味道稳定，但午休时单子太多会等，赶时间不太建议。', NULL, 0, 3, 1, 1, '2026-04-11 14:57:00', '2026-04-11 14:57:00'),

-- 豌杂面 (dish_id=28) 的评价
(27, 28, 4, 10, 4.50, '杂酱和豌豆都给得挺足，面条挂汁，吃完会有点辣但很过瘾。', NULL, 0, 7, 0, 1, '2026-04-06 12:14:00', '2026-04-06 12:14:00'),
(28, 28, 8, 10, 4.00, '香是挺香的，建议微辣起步，不然对不太能吃辣的人还是有压力。', NULL, 0, 4, 0, 1, '2026-04-09 12:48:00', '2026-04-09 12:48:00'),

-- 清真牛肉面 (dish_id=31) 的评价
(29, 31, 6, 11, 5.00, '汤底很干净，牛肉片给得够，面量也足，整体特别稳。', NULL, 0, 10, 2, 1, '2026-04-05 18:44:00', '2026-04-05 18:44:00'),
(30, 31, 13, 11, 4.50, '口味偏清爽，适合晚上吃，不会像重油重辣那样吃完负担大。', NULL, 0, 6, 0, 1, '2026-04-10 19:03:00', '2026-04-10 19:03:00'),

-- 大盘鸡 (dish_id=34) 的评价
(31, 34, 2, 12, 4.50, '适合两三个人拼着吃，鸡肉和土豆都很入味，面拌汤尤其香。', 'https://picsum.photos/seed/review008/200/200', 0, 11, 1, 1, '2026-04-06 18:51:00', '2026-04-06 18:51:00'),
(32, 34, 3, 12, 4.00, '味道不错，分量也足，就是人少的时候点会稍微有点吃不完。', NULL, 0, 5, 0, 1, '2026-04-11 18:26:00', '2026-04-11 18:26:00'),

-- 烤羊肉串 (dish_id=37) 的评价
(33, 37, 4, 13, 5.00, '现烤的时候香味特别冲，肉不柴，孜然和辣椒面撒得很到位。', 'https://picsum.photos/seed/review007/200/200', 0, 25, 8, 1, '2026-04-03 20:06:00', '2026-04-03 20:06:00'),
(34, 37, 5, 13, 4.50, '现点现烤这点很加分，五块一串在校内也还能接受，夜宵首选。', NULL, 0, 11, 2, 1, '2026-04-08 20:19:00', '2026-04-08 20:19:00'),
(35, 37, 7, 13, 5.00, '外焦里嫩，咬下去还有汁，和馕饼一起买更满足。', NULL, 0, 16, 0, 1, '2026-04-11 20:42:00', '2026-04-11 20:42:00');

-- =============================================
-- 优惠活动数据
-- =============================================

CREATE TABLE IF NOT EXISTS system_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '配置 ID',
    config_key VARCHAR(100) NOT NULL UNIQUE COMMENT '配置键',
    config_value TEXT NOT NULL COMMENT '配置值',
    config_group VARCHAR(50) COMMENT '配置分组',
    description VARCHAR(255) COMMENT '配置说明',
    status INT NOT NULL DEFAULT 1 COMMENT '状态：1-启用 0-禁用',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';

CREATE TABLE IF NOT EXISTS langfuse_config (
    id BIGINT PRIMARY KEY COMMENT '固定为 1 的 Langfuse 配置记录',
    enabled TINYINT NOT NULL DEFAULT 0 COMMENT '是否启用 Langfuse 观测',
    base_url VARCHAR(500) NULL COMMENT 'Langfuse 服务地址',
    public_key TEXT NULL COMMENT '加密保存的 Langfuse Public Key',
    secret_key TEXT NULL COMMENT '加密保存的 Langfuse Secret Key',
    create_time DATETIME NULL COMMENT '创建时间',
    update_time DATETIME NULL COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Langfuse AI 观测配置';

DELETE FROM system_config WHERE config_key IN (
  'ai.provider', 'ai.base-url', 'ai.api-key', 'ai.model',
  'ai.app.models', 'ai.app.default-model', 'ai.app.word-count-options', 'ai.app.tone-options',
  'ai.app.tabs', 'ai.app.quick-actions', 'ai.app.tool-categories'
);

INSERT INTO system_config (id, config_key, config_value, config_group, description, status, create_time, update_time) VALUES
(1, 'jwt.secret', '', 'security', 'JWT 签名密钥', 0, NOW(), NOW()),
(2, 'jwt.expiration', '86400000', 'security', 'JWT 过期时间，单位毫秒', 1, NOW(), NOW()),
(3, 'tencent.map.key', '', 'map', '腾讯地图 WebService 密钥', 0, NOW(), NOW()),
(4, 'tencent.map.base-url', 'https://apis.map.qq.com', 'map', '腾讯地图接口基础地址', 1, NOW(), NOW()),
(5, 'aliyun.oss.endpoint', 'oss-cn-beijing.aliyuncs.com', 'oss', '阿里云 OSS 节点', 1, NOW(), NOW()),
(6, 'aliyun.oss.bucket-name', 'smart-campus111', 'oss', '阿里云 OSS Bucket 名称', 1, NOW(), NOW()),
(7, 'aliyun.oss.access-key-id', '', 'oss', '阿里云 OSS AccessKeyId', 0, NOW(), NOW()),
(8, 'aliyun.oss.access-key-secret', '', 'oss', '阿里云 OSS AccessKeySecret', 0, NOW(), NOW()),
(9, 'aliyun.oss.base-url', 'https://smart-campus111.oss-cn-beijing.aliyuncs.com', 'oss', '阿里云 OSS 访问基础地址', 1, NOW(), NOW()),
(10, 'browser.headless', 'true', 'browser', '浏览器自动化是否无头运行', 1, NOW(), NOW()),
(11, 'browser.default-url', 'https://jwx.hebiace.edu.cn/', 'browser', '浏览器自动化默认打开地址', 1, NOW(), NOW()),
(12, 'ai.app.models', '[{"name":"DeepSeek","desc":"深度求索，擅长逻辑推理","icon":"/static/icons/ai create/DeepSeek.png"},{"name":"豆包","desc":"字节跳动，多模态能力强","icon":"/static/icons/ai create/doubao.png"},{"name":"通义千问","desc":"阿里巴巴，综合能力出色","icon":"/static/icons/ai create/Tongyi-Qianwen.png"}]', 'ai', '智能写作模型列表', 1, NOW(), NOW()),
(13, 'ai.app.default-model', 'DeepSeek', 'ai', '智能写作默认模型', 1, NOW(), NOW()),
(14, 'ai.app.word-count-options', '["自动","200字以内","500字左右","800字以上","1000字以上"]', 'ai', '智能写作字数选项', 1, NOW(), NOW()),
(15, 'ai.app.tone-options', '["正式","幽默","严谨","感性","专业"]', 'ai', '智能写作语气选项', 1, NOW(), NOW()),
(16, 'ai.app.tabs', '["热门工具","格式转换","校园必备","职场创意","社交媒体"]', 'ai', 'AI 创作首页标签', 1, NOW(), NOW()),
(17, 'ai.app.quick-actions', '[{"name":"AI对话","icon":"/static/icons/ai create/ai chat.png","themeColor":"#3B82F6","lightColor":"rgba(59, 130, 246, 0.7)"},{"name":"AI伪原创","icon":"/static/icons/ai create/ai original.png","themeColor":"#8B5CF6","lightColor":"rgba(139, 92, 246, 0.7)"},{"name":"文案提取","icon":"/static/icons/ai create/extract.png","themeColor":"#10B981","lightColor":"rgba(16, 185, 129, 0.7)"},{"name":"视频去字幕","icon":"/static/icons/ai create/remove.png","themeColor":"#F59E0B","lightColor":"rgba(245, 158, 11, 0.7)"},{"name":"AI玩图","icon":"/static/icons/ai create/ai img.png","themeColor":"#EC4899","lightColor":"rgba(236, 72, 153, 0.7)"}]', 'ai', 'AI 创作首页快捷工具', 1, NOW(), NOW()),
(18, 'ai.app.tool-categories', '{"hot":[{"name":"去水印","desc":"快速去水印超便捷","icon":"/static/icons/ai create/watermark.png","themeColor":"#FF6B6B","lightColor":"rgba(255, 107, 107, 0.35)"},{"name":"照片跳舞","desc":"唤醒静图灵动起舞","icon":"/static/icons/ai create/dance.png","themeColor":"#FF9F43","lightColor":"rgba(255, 159, 67, 0.35)"},{"name":"图生视频","desc":"图片一键生成视频","icon":"/static/icons/ai create/img-video.png","themeColor":"#A55EEA","lightColor":"rgba(165, 94, 234, 0.35)"},{"name":"提词器","desc":"视频录制提词","icon":"/static/icons/ai create/teleprompter.png","themeColor":"#5C7A99","lightColor":"rgba(92, 122, 153, 0.35)"},{"name":"文章续写","desc":"智能续写妙笔生花","icon":"/static/icons/ai create/writing.png","themeColor":"#FECA57","lightColor":"rgba(254, 202, 87, 0.35)"},{"name":"视频转字幕","desc":"视频转字幕快又准","icon":"/static/icons/ai create/subtitle.png","themeColor":"#48DBFB","lightColor":"rgba(72, 219, 251, 0.35)"},{"name":"AI证件照","desc":"AI证件照超省心","icon":"/static/icons/ai create/id-photo.png","themeColor":"#1DD1A1","lightColor":"rgba(29, 209, 161, 0.35)"},{"name":"视频加字幕","desc":"一键视频添加字幕","icon":"/static/icons/ai create/add-sub.png","themeColor":"#FF6B6B","lightColor":"rgba(255, 107, 107, 0.35)"},{"name":"视频二创","desc":"一键二创视频焕新","icon":"/static/icons/ai create/video-edit.png","themeColor":"#8B5CF6","lightColor":"rgba(139, 92, 246, 0.35)"},{"name":"人声分离","desc":"轻松分离纯净人声","icon":"/static/icons/ai create/vocal.png","themeColor":"#FF9F43","lightColor":"rgba(255, 159, 67, 0.35)"},{"name":"文生图","desc":"一键生成精美图片","icon":"/static/icons/ai create/text-img.png","themeColor":"#5C7A99","lightColor":"rgba(92, 122, 153, 0.35)"},{"name":"更多工具","desc":"总有一款适合你","icon":"/static/icons/ai create/more.png","themeColor":"#C8D6E5","lightColor":"rgba(200, 214, 229, 0.35)"}],"format":[{"name":"PPT转PDF","desc":"一键PPT转PDF","icon":"/static/icons/ai create/ppt-pdf.png","themeColor":"#FF6B6B","lightColor":"rgba(255, 107, 107, 0.35)"},{"name":"PDF转PPT","desc":"一键PDF转PPT","icon":"/static/icons/ai create/pdf-ppt.png","themeColor":"#5C7A99","lightColor":"rgba(92, 122, 153, 0.35)"},{"name":"PDF转Excel","desc":"PDF秒变Excel","icon":"/static/icons/ai create/pdf-excel.png","themeColor":"#1DD1A1","lightColor":"rgba(29, 209, 161, 0.35)"},{"name":"PPT转图片","desc":"一键PPT秒变图片","icon":"/static/icons/ai create/ppt-img.png","themeColor":"#A55EEA","lightColor":"rgba(165, 94, 234, 0.35)"},{"name":"PDF转Word","desc":"PDF转Word快准稳","icon":"/static/icons/ai create/pdf-word.png","themeColor":"#5C7A99","lightColor":"rgba(92, 122, 153, 0.35)"},{"name":"PDF转图片","desc":"一键PDF秒变图片","icon":"/static/icons/ai create/pdf-img.png","themeColor":"#FF9F43","lightColor":"rgba(255, 159, 67, 0.35)"},{"name":"Word转PDF","desc":"Word转PDF快准稳","icon":"/static/icons/ai create/word-pdf.png","themeColor":"#3B82F6","lightColor":"rgba(59, 130, 246, 0.35)"},{"name":"视频格式转换","desc":"一键改变视频格式","icon":"/static/icons/ai create/video-convert.png","themeColor":"#1DD1A1","lightColor":"rgba(29, 209, 161, 0.35)"}],"campus":[{"name":"实践报告","desc":"轻松搞定实践报告","icon":"/static/icons/ai create/report.png","themeColor":"#FF6B6B","lightColor":"rgba(255, 107, 107, 0.35)"},{"name":"课程报告","desc":"课程报告助力提升","icon":"/static/icons/ai create/course.png","themeColor":"#FF9F43","lightColor":"rgba(255, 159, 67, 0.35)"},{"name":"英语作文","desc":"轻松写出高分作文","icon":"/static/icons/ai create/english.png","themeColor":"#A55EEA","lightColor":"rgba(165, 94, 234, 0.35)"},{"name":"活动总结","desc":"快速完成活动复盘","icon":"/static/icons/ai create/summary.png","themeColor":"#FF6B6B","lightColor":"rgba(255, 107, 107, 0.35)"},{"name":"学科出题","desc":"一键出题精准教学","icon":"/static/icons/ai create/exam.png","themeColor":"#5C7A99","lightColor":"rgba(92, 122, 153, 0.35)"},{"name":"学习计划","desc":"定制计划高效学习","icon":"/static/icons/ai create/plan.png","themeColor":"#1DD1A1","lightColor":"rgba(29, 209, 161, 0.35)"},{"name":"考研题目","desc":"一键生成考研好题","icon":"/static/icons/ai create/graduate.png","themeColor":"#5C7A99","lightColor":"rgba(92, 122, 153, 0.35)"},{"name":"文章主题大纲","desc":"轻松搞定文章框架","icon":"/static/icons/ai create/outline.png","themeColor":"#1DD1A1","lightColor":"rgba(29, 209, 161, 0.35)"},{"name":"雅思大作文","desc":"一键生成雅思佳作","icon":"/static/icons/ai create/ielts.png","themeColor":"#FF6B6B","lightColor":"rgba(255, 107, 107, 0.35)"},{"name":"思想汇报","desc":"一键搞定思想汇报","icon":"/static/icons/ai create/thought.png","themeColor":"#A55EEA","lightColor":"rgba(165, 94, 234, 0.35)"}],"work":[{"name":"PPT大纲","desc":"智能规划PPT要点","icon":"/static/icons/ai create/ppt-outline.png","themeColor":"#EF4444","lightColor":"rgba(239, 68, 68, 0.35)"},{"name":"简历制作","desc":"轻松打造吸睛简历","icon":"/static/icons/ai create/resume.png","themeColor":"#5C7A99","lightColor":"rgba(92, 122, 153, 0.35)"},{"name":"心得体会","desc":"一键生成心得感悟","icon":"/static/icons/ai create/feeling.png","themeColor":"#A55EEA","lightColor":"rgba(165, 94, 234, 0.35)"},{"name":"工作总结","desc":"助力产出优质总结","icon":"/static/icons/ai create/work-summary.png","themeColor":"#FF9F43","lightColor":"rgba(255, 159, 67, 0.35)"},{"name":"文本比较","desc":"智能分析文本异同","icon":"/static/icons/ai create/compare.png","themeColor":"#5C7A99","lightColor":"rgba(92, 122, 153, 0.35)"},{"name":"长文本写作","desc":"一键生成优质长文","icon":"/static/icons/ai create/long-text.png","themeColor":"#1DD1A1","lightColor":"rgba(29, 209, 161, 0.35)"},{"name":"周报日报","desc":"轻松搞定周报撰写","icon":"/static/icons/ai create/weekly.png","themeColor":"#5C7A99","lightColor":"rgba(92, 122, 153, 0.35)"},{"name":"影视解说","desc":"助力打造爆款解说","icon":"/static/icons/ai create/movie.png","themeColor":"#A55EEA","lightColor":"rgba(165, 94, 234, 0.35)"},{"name":"文章配图","desc":"快速生成图文搭配","icon":"/static/icons/ai create/article-img.png","themeColor":"#FF6B6B","lightColor":"rgba(255, 107, 107, 0.35)"},{"name":"合同模板","desc":"一键获取合同模板","icon":"/static/icons/ai create/contract.png","themeColor":"#5C7A99","lightColor":"rgba(92, 122, 153, 0.35)"}],"social":[{"name":"视频灵感","desc":"助力开启灵感源泉","icon":"/static/icons/ai create/video-inspire.png","themeColor":"#FF6B6B","lightColor":"rgba(255, 107, 107, 0.35)"},{"name":"短视频文案","desc":"开启爆款视频之路","icon":"/static/icons/ai create/short-video.png","themeColor":"#5C7A99","lightColor":"rgba(92, 122, 153, 0.35)"},{"name":"视频标题","desc":"生成吸睛标题","icon":"/static/icons/ai create/video-title.png","themeColor":"#A55EEA","lightColor":"rgba(165, 94, 234, 0.35)"},{"name":"AI写小说","desc":"智能编写奇妙故事","icon":"/static/icons/ai create/novel.png","themeColor":"#FF9F43","lightColor":"rgba(255, 159, 67, 0.35)"},{"name":"旅游攻略","desc":"开启畅玩旅行指南","icon":"/static/icons/ai create/travel.png","themeColor":"#1DD1A1","lightColor":"rgba(29, 209, 161, 0.35)"},{"name":"视频介绍","desc":"轻松打造亮眼推介","icon":"/static/icons/ai create/video-intro.png","themeColor":"#5C7A99","lightColor":"rgba(92, 122, 153, 0.35)"},{"name":"种草文案","desc":"一键生成心动安利","icon":"/static/icons/ai create/recommend.png","themeColor":"#FF6B6B","lightColor":"rgba(255, 107, 107, 0.35)"},{"name":"智能翻译","desc":"智能打破语言壁垒","icon":"/static/icons/ai create/translate.png","themeColor":"#A55EEA","lightColor":"rgba(165, 94, 234, 0.35)"},{"name":"好评文案","desc":"简单生成诚意好评","icon":"/static/icons/ai create/review.png","themeColor":"#1DD1A1","lightColor":"rgba(29, 209, 161, 0.35)"},{"name":"带货标题","desc":"一键生成吸睛标题","icon":"/static/icons/ai create/sales.png","themeColor":"#5C7A99","lightColor":"rgba(92, 122, 153, 0.35)"}]}', 'ai', 'AI 创作工具分类配置', 1, NOW(), NOW()),
(NULL, 'ai.asr.xfyun.websocket-url', 'wss://office-api-ast-dx.iflyaisol.com/ast/communicate/v1', 'asr', '讯飞实时转写大模型 WebSocket 地址', 1, NOW(), NOW()),
(NULL, 'ai.asr.xfyun.app-id', '', 'asr', '讯飞实时转写大模型 App ID', 1, NOW(), NOW()),
(NULL, 'ai.asr.xfyun.access-key-id', '', 'asr', '讯飞实时转写大模型 AccessKeyId/APIKey', 1, NOW(), NOW()),
(NULL, 'ai.asr.xfyun.access-key-secret', '', 'asr', '讯飞实时转写大模型 AccessKeySecret/APISecret', 1, NOW(), NOW()),
(NULL, 'ai.asr.xfyun.lang', 'autodialect', 'asr', '讯飞实时转写大模型语种', 1, NOW(), NOW()),
(NULL, 'ai.asr.xfyun.audio-encode', 'pcm_s16le', 'asr', '讯飞实时转写大模型音频编码', 1, NOW(), NOW()),
(NULL, 'ai.asr.xfyun.samplerate', '16000', 'asr', '讯飞实时转写大模型采样率', 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
config_value = CASE
  WHEN VALUES(config_key) IN (
    'ai.asr.xfyun.app-id',
    'ai.asr.xfyun.access-key-id',
    'ai.asr.xfyun.access-key-secret'
  ) AND VALUES(config_value) = '' THEN system_config.config_value
  ELSE VALUES(config_value)
END,
config_group = VALUES(config_group),
description = VALUES(description),
status = VALUES(status),
update_time = NOW();

CREATE TABLE IF NOT EXISTS forum_report (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '举报ID',
    reporter_id BIGINT NOT NULL COMMENT '举报人ID',
    target_type INT NOT NULL COMMENT '举报目标类型：1-帖子，2-评论',
    target_id BIGINT NOT NULL COMMENT '举报目标ID',
    reason_type INT COMMENT '举报原因类型',
    reason_text VARCHAR(100) COMMENT '举报原因文本',
    description TEXT COMMENT '举报描述',
    status INT NOT NULL DEFAULT 0 COMMENT '状态：0-待处理，1-已处理，2-已驳回',
    handle_action VARCHAR(32) COMMENT '处理动作：IGNORE/DELETE_CONTENT',
    handle_result VARCHAR(500) COMMENT '处理结果',
    handle_by BIGINT COMMENT '处理人ID',
    handle_time DATETIME COMMENT '处理时间',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    INDEX idx_forum_report_status (status),
    INDEX idx_forum_report_target (target_type, target_id),
    FOREIGN KEY (reporter_id) REFERENCES sys_user(id),
    FOREIGN KEY (handle_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛举报表';
CREATE TABLE IF NOT EXISTS forum_report_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '审计日志ID',
    report_id BIGINT NOT NULL COMMENT '举报ID',
    action VARCHAR(50) NOT NULL COMMENT '操作动作',
    operator_id BIGINT COMMENT '操作人ID',
    target_type INT COMMENT '举报目标类型',
    target_id BIGINT COMMENT '举报目标ID',
    remark VARCHAR(500) COMMENT '备注',
    create_time DATETIME COMMENT '创建时间',
    INDEX idx_forum_report_audit_report (report_id),
    INDEX idx_forum_report_audit_operator (operator_id),
    FOREIGN KEY (report_id) REFERENCES forum_report(id),
    FOREIGN KEY (operator_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛举报审计日志表';

UPDATE forum_post SET status = 'PUBLISHED' WHERE status IS NULL;
UPDATE forum_comment SET status = 'NORMAL' WHERE status IS NULL;

CREATE TABLE IF NOT EXISTS forum_favorite (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '收藏ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    post_id BIGINT NOT NULL COMMENT '帖子ID',
    create_time DATETIME COMMENT '收藏时间',
    UNIQUE KEY uk_forum_favorite_user_post (user_id, post_id),
    INDEX idx_forum_favorite_user_time (user_id, create_time),
    FOREIGN KEY (user_id) REFERENCES sys_user(id),
    FOREIGN KEY (post_id) REFERENCES forum_post(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛帖子收藏表';
