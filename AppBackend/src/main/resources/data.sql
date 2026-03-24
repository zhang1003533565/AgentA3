-- =============================================
-- 智慧校园系统初始化数据
-- =============================================

-- =============================================
-- 第一部分：表创建语句（如果不存在则创建）
-- =============================================

-- 校园设施表
CREATE TABLE IF NOT EXISTS campus_facility (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '设施ID',
    facility_name VARCHAR(100) NOT NULL COMMENT '设施名称',
    facility_type INT NOT NULL COMMENT '设施类型: 1-餐厅 2-运动场 3-教学楼 4-宿舍',
    description TEXT COMMENT '设施描述',
    location VARCHAR(200) COMMENT '位置描述',
    longitude DECIMAL(10,7) COMMENT '经度',
    latitude DECIMAL(10,7) COMMENT '纬度',
    images TEXT COMMENT '图片列表(JSON数组)',
    status INT NOT NULL DEFAULT 1 COMMENT '状态: 0-禁用 1-启用',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='校园设施表';

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

-- 地图标记表
CREATE TABLE IF NOT EXISTS map_marker (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '标记ID',
    facility_id BIGINT NOT NULL COMMENT '关联设施ID',
    icon_url VARCHAR(255) COMMENT '自定义图标URL',
    description TEXT COMMENT '描述信息',
    sort INT DEFAULT 0 COMMENT '排序',
    status INT NOT NULL DEFAULT 1 COMMENT '状态: 0-禁用 1-启用',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
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

-- =============================================
-- 第二部分：清空表数据（注意顺序，先删除有外键依赖的表）
-- =============================================
SET FOREIGN_KEY_CHECKS = 0;

-- 先清空地图/导航/评价表
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
(3, 'STUDENT');

-- =============================================
-- 2. 用户数据 (密码都是 admin123)
-- =============================================
INSERT INTO sys_user (id, username, password, real_name, phone, email, role_id, status, create_time, update_time) VALUES
-- 管理员 (用户名: admin, 密码: admin123)
(1, 'admin', 'admin123', '系统管理员', '13800000001', 'admin@campus.edu.cn', 1, 1, NOW(), NOW()),
-- 教师 (用户名: fjj, 密码: admin123)
(2, 'fjj', 'admin123', '张老师', '13800000002', 'zhanglaoshi@campus.edu.cn', 2, 1, NOW(), NOW()),
-- 教师 (用户名: fjj2, 密码: admin123)
(3, 'fjj2', 'admin123', '李老师', '13800000003', 'lilaoshi@campus.edu.cn', 2, 1, NOW(), NOW()),
-- 学生 (用户名: zzs, 密码: admin123)
(4, 'zzs', 'admin123', '张三', '13800000004', 'zhangsan@stu.campus.edu.cn', 3, 1, NOW(), NOW()),
-- 学生 (用户名: lisi, 密码: admin123)
(5, 'lisi', 'admin123', '李四', '13800000005', 'lisi@stu.campus.edu.cn', 3, 1, NOW(), NOW()),
-- 学生 (用户名: wangwu, 密码: admin123)
(6, 'wangwu', 'admin123', '王五', '13800000006', 'wangwu@stu.campus.edu.cn', 3, 1, NOW(), NOW()),
-- 学生 (用户名: zhaoliu, 密码: admin123)
(7, 'zhaoliu', 'admin123', '赵六', '13800000007', 'zhaoliu@stu.campus.edu.cn', 3, 1, NOW(), NOW()),
-- 学生 (用户名: student05, 密码: admin123)
(8, 'student05', 'admin123', '钱七', '13800000008', 'qianqi@stu.campus.edu.cn', 3, 1, NOW(), NOW());

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
INSERT INTO campus_facility (id, facility_name, facility_type, description, location, longitude, latitude, images, status, deleted, create_time, update_time) VALUES
-- 餐厅 (类型1)
(1, '第一学生餐厅', 1, '位于学校南门，主要提供快餐服务，菜品种类丰富，价格实惠。', '南门东侧100米', 116.397428, 39.90923, '["https://picsum.photos/800/600?random=1","https://picsum.photos/800/600?random=2"]', 1, 0, NOW(), NOW()),
(2, '第二学生餐厅', 1, '位于学校中心区域，以地方特色菜为主，环境优雅。', '学校中心广场北侧', 116.398000, 39.910000, '["https://picsum.photos/800/600?random=3"]', 1, 0, NOW(), NOW()),
(3, '清真餐厅', 1, '专门提供清真美食，食材新鲜，口味正宗。', '东门附近', 116.399500, 39.908500, '["https://picsum.photos/800/600?random=4"]', 1, 0, NOW(), NOW()),
-- 运动场 (类型2)
(4, '东区运动场', 2, '包含篮球场、足球场、羽毛球场等设施，是师生锻炼的首选之地。', '学校东区', 116.398500, 39.911000, '["https://picsum.photos/800/600?random=5"]', 1, 0, NOW(), NOW()),
(5, '体育馆', 2, '室内体育馆，设有篮球场、羽毛球场、乒乓球室等。', '学校北门', 116.396000, 39.910500, '["https://picsum.photos/800/600?random=6"]', 1, 0, NOW(), NOW()),
(6, '田径场', 2, '标准400米跑道，天然草坪足球场，适合跑步和足球运动。', '学校西侧', 116.394500, 39.909000, '["https://picsum.photos/800/600?random=7"]', 1, 0, NOW(), NOW()),
-- 教学楼 (类型3)
(7, '博学楼', 3, '学校主教学楼，设施齐全，教室宽敞明亮。', '学校中轴线', 116.397800, 39.909500, '["https://picsum.photos/800/600?random=8"]', 1, 0, NOW(), NOW()),
(8, '致远楼', 3, '主要用于是实验教学，配备先进实验设备。', '博学楼东侧', 116.398200, 39.909700, '["https://picsum.photos/800/600?random=9"]', 1, 0, NOW(), NOW()),
(9, '图书馆', 3, '学校图书馆，藏书丰富，学习环境舒适。', '学校中心', 116.397600, 39.909300, '["https://picsum.photos/800/600?random=10"]', 1, 0, NOW(), NOW()),
-- 宿舍 (类型4)
(10, '松园1号楼', 4, '男生宿舍楼，环境优美，设施完善。', '学校东区松园', 116.399000, 39.911500, '["https://picsum.photos/800/600?random=11"]', 1, 0, NOW(), NOW()),
(11, '松园2号楼', 4, '男生宿舍楼，靠近食堂，生活便利。', '学校东区松园', 116.399200, 39.911300, '["https://picsum.photos/800/600?random=12"]', 1, 0, NOW(), NOW()),
(12, '竹园1号楼', 4, '女生宿舍楼，安全安静，适合学习。', '学校北区竹园', 116.396500, 39.912000, '["https://picsum.photos/800/600?random=13"]', 1, 0, NOW(), NOW()),
(13, '竹园2号楼', 4, '女生宿舍楼，距离图书馆近。', '学校北区竹园', 116.396700, 39.911800, '["https://picsum.photos/800/600?random=14"]', 1, 0, NOW(), NOW());

-- =============================================
-- 设施评价数据
-- =============================================
INSERT INTO facility_review (id, facility_id, user_id, score, content, images, create_time) VALUES
-- 餐厅评价
(1, 1, 4, 5, '第一学生餐厅的味道非常正宗，菜品丰富，价格实惠！', NULL, NOW()),
(2, 1, 5, 4, '味道不错，就是人有点多，排队时间较长。', NULL, NOW()),
(3, 1, 6, 5, '麻辣烫超级好吃，食材新鲜！', '["https://picsum.photos/400/300?random=30"]', NOW()),
(4, 1, 7, 4, '面条做得不错，分量足，价格实惠。', NULL, NOW()),
(5, 2, 4, 4, '粤式烧腊饭很好吃，环境也不错。', NULL, NOW()),
-- 运动场评价
(6, 4, 5, 5, '东区运动场设施很好，地面平整，灯光充足。', NULL, NOW()),
(7, 4, 7, 4, '羽毛球场不错，就是有时候人多需要排队。', NULL, NOW()),
(8, 5, 4, 5, '室内篮球馆环境很好，地板质量不错。', NULL, NOW()),
-- 教学楼评价
(9, 7, 5, 5, '博学楼很宽敞，座位舒适，投影清晰。', NULL, NOW()),
(10, 7, 6, 4, '多媒体教室设备齐全，录播功能很实用。', NULL, NOW());

-- =============================================
-- 第四部分：第四阶段 - 校园地图导航模块数据
-- =============================================

-- =============================================
-- 地图配置数据
-- =============================================
INSERT INTO map_config (id, config_key, config_value, description, create_time, update_time) VALUES
(1, 'map_center_longitude', '116.397428', '地图中心经度', NOW(), NOW()),
(2, 'map_center_latitude', '39.909500', '地图中心纬度', NOW(), NOW()),
(3, 'map_zoom_level', '16', '默认缩放级别(1-20)', NOW(), NOW()),
(4, 'map_boundary', '{"northEast":{"longitude":116.41,"latitude":39.92},"southWest":{"longitude":116.38,"latitude":39.89}}', '地图边界范围', NOW(), NOW());

-- =============================================
-- 地图标记数据
-- =============================================
INSERT INTO map_marker (id, facility_id, icon_url, description, sort, status, deleted, create_time, update_time) VALUES
-- 餐厅标记 (按设施类型排序)
(1, 1, NULL, '第一学生餐厅，提供多种餐饮选择', 1, 1, 0, NOW(), NOW()),
(2, 2, NULL, '第二学生餐厅，地方特色菜为主', 2, 1, 0, NOW(), NOW()),
(3, 3, NULL, '清真餐厅，提供清真美食', 3, 1, 0, NOW(), NOW()),
-- 运动场标记
(4, 4, NULL, '东区运动场，篮球场、足球场、羽毛球场', 4, 1, 0, NOW(), NOW()),
(5, 5, NULL, '体育馆，室内运动场所', 5, 1, 0, NOW(), NOW()),
(6, 6, NULL, '田径场，400米跑道和足球场', 6, 1, 0, NOW(), NOW()),
-- 教学楼标记
(7, 7, NULL, '博学楼，主教学楼', 7, 1, 0, NOW(), NOW()),
(8, 8, NULL, '致远楼，实验教学楼', 8, 1, 0, NOW(), NOW()),
(9, 9, NULL, '图书馆，学习中心', 9, 1, 0, NOW(), NOW()),
-- 宿舍标记
(10, 10, NULL, '松园1号楼，男生宿舍', 10, 1, 0, NOW(), NOW()),
(11, 11, NULL, '松园2号楼，男生宿舍', 11, 1, 0, NOW(), NOW()),
(12, 12, NULL, '竹园1号楼，女生宿舍', 12, 1, 0, NOW(), NOW()),
(13, 13, NULL, '竹园2号楼，女生宿舍', 13, 1, 0, NOW(), NOW());

-- =============================================
-- 导航记录数据
-- =============================================
INSERT INTO navigation_log (id, user_id, from_longitude, from_latitude, to_marker_id, distance, duration, status, arrive_time, create_time) VALUES
(1, 4, 116.397428, 39.909230, 1, 150.50, 120, 2, NOW(), NOW()),
(2, 4, 116.397428, 39.909230, 7, 200.00, 180, 2, NOW(), NOW()),
(3, 5, 116.399000, 39.911500, 4, 300.00, 240, 2, NOW(), NOW()),
(4, 6, 116.396500, 39.912000, 9, 500.00, 400, 2, NOW(), NOW()),
(5, 7, 116.396000, 39.910500, 5, 100.00, 60, 1, NULL, NOW());

-- =============================================
-- 收藏目的地数据
-- =============================================
INSERT INTO favorite_destination (id, user_id, marker_id, marker_name, longitude, latitude, facility_type, remark, create_time) VALUES
(1, 4, 1, '第一学生餐厅', 116.397428, 39.909230, 1, '常去吃饭', NOW()),
(2, 4, 7, '博学楼', 116.397800, 39.909500, 3, '上课地点', NOW()),
(3, 4, 9, '图书馆', 116.397600, 39.909300, 3, '自习', NOW()),
(4, 5, 2, '第二学生餐厅', 116.398000, 39.910000, 1, '喜欢吃粤菜', NOW()),
(5, 5, 4, '东区运动场', 116.398500, 39.911000, 2, '打篮球', NOW()),
(6, 6, 3, '清真餐厅', 116.399500, 39.908500, 1, '清真美食', NOW()),
(7, 6, 12, '竹园1号楼', 116.396500, 39.912000, 4, '宿舍', NOW()),
(8, 7, 6, '田径场', 116.394500, 39.909000, 2, '跑步', NOW()),
(9, 7, 10, '松园1号楼', 116.399000, 39.911500, 4, '宿舍', NOW()),
(10, 8, 9, '图书馆', 116.397600, 39.909300, 3, '学习', NOW());
