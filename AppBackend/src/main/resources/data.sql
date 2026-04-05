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

-- 校园设施表
CREATE TABLE IF NOT EXISTS campus_facility (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '设施ID',
    facility_name VARCHAR(100) NOT NULL COMMENT '设施名称',
    facility_type INT NOT NULL COMMENT '设施类型: 1-餐厅 2-运动场 3-教学楼 4-宿舍',
    status INT NOT NULL DEFAULT 1 COMMENT '设施状态: 1-正常/开放 2-维护中 3-关闭/不可用',
    description TEXT COMMENT '设施描述',
    location VARCHAR(200) COMMENT '位置描述',
    longitude DECIMAL(10,7) COMMENT '经度',
    latitude DECIMAL(10,7) COMMENT '纬度',
    images TEXT COMMENT '图片列表(JSON数组)',
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
(3, 'STUDENT'),
(4, 'MERCHANT');

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
(8, 'student05', 'admin123', '钱七', '13800000008', 'qianqi@stu.campus.edu.cn', 3, 1, NOW(), NOW()),
-- 商家用户 (用户名: merchant01~04, 密码: admin123, role_id=4)
(9, 'merchant01', 'admin123', '学府餐厅老板', '13812345601', 'lishilaoban@campus.edu.cn', 4, 1, NOW(), NOW()),
(10, 'merchant02', 'admin123', '书香咖啡老板', '13812345602', 'wanglaoban@campus.edu.cn', 4, 1, NOW(), NOW()),
(11, 'merchant03', 'admin123', '校园超市老板', '13812345603', 'zhanglaoban@campus.edu.cn', 4, 1, NOW(), NOW()),
(12, 'merchant04', 'admin123', '快印图文老板', '13812345604', 'zhaolaoban@campus.edu.cn', 4, 1, NOW(), NOW());

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
INSERT INTO campus_facility (id, facility_name, facility_type, status, description, location, longitude, latitude, images, create_time, update_time) VALUES
-- 餐厅 (类型1，状态1=正常/开放)
(1, '第一学生餐厅', 1, 1, '位于学校南门，主要提供快餐服务，菜品种类丰富，价格实惠。', '南门东侧100米', 116.397428, 39.90923, '["https://picsum.photos/800/600?random=1","https://picsum.photos/800/600?random=2"]', NOW(), NOW()),
(2, '第二学生餐厅', 1, 1, '位于学校中心区域，以地方特色菜为主，环境优雅。', '学校中心广场北侧', 116.398000, 39.910000, '["https://picsum.photos/800/600?random=3"]', NOW(), NOW()),
(3, '清真餐厅', 1, 1, '专门提供清真美食，食材新鲜，口味正宗。', '东门附近', 116.399500, 39.908500, '["https://picsum.photos/800/600?random=4"]', NOW(), NOW()),
-- 运动场 (类型2，状态: 1=正常 2=维护中 3=关闭)
(4, '东区运动场', 2, 1, '包含篮球场、足球场、羽毛球场等设施，是师生锻炼的首选之地。', '学校东区', 116.398500, 39.911000, '["https://picsum.photos/800/600?random=5"]', NOW(), NOW()),
(5, '体育馆', 2, 2, '室内体育馆，设有篮球场、羽毛球场、乒乓球室等。', '学校北门', 116.396000, 39.910500, '["https://picsum.photos/800/600?random=6"]', NOW(), NOW()),
(6, '田径场', 2, 1, '标准400米跑道，天然草坪足球场，适合跑步和足球运动。', '学校西侧', 116.394500, 39.909000, '["https://picsum.photos/800/600?random=7"]', NOW(), NOW()),
-- 教学楼 (类型3，状态: 1=正常 2=维护中 3=关闭)
(7, '博学楼', 3, 1, '学校主教学楼，设施齐全，教室宽敞明亮。', '学校中轴线', 116.397800, 39.909500, '["https://picsum.photos/800/600?random=8"]', NOW(), NOW()),
(8, '致远楼', 3, 3, '主要用于实验教学，配备先进实验设备。', '博学楼东侧', 116.398200, 39.909700, '["https://picsum.photos/800/600?random=9"]', NOW(), NOW()),
(9, '图书馆', 3, 1, '学校图书馆，藏书丰富，学习环境舒适。', '学校中心', 116.397600, 39.909300, '["https://picsum.photos/800/600?random=10"]', NOW(), NOW()),
-- 宿舍 (类型4)
(10, '松园1号楼', 4, 1, '男生宿舍楼，环境优美，设施完善。', '学校东区松园', 116.399000, 39.911500, '["https://picsum.photos/800/600?random=11"]', NOW(), NOW()),
(11, '松园2号楼', 4, 1, '男生宿舍楼，靠近食堂，生活便利。', '学校东区松园', 116.399200, 39.911300, '["https://picsum.photos/800/600?random=12"]', NOW(), NOW()),
(12, '竹园1号楼', 4, 1, '女生宿舍楼，安全安静，适合学习。', '学校北区竹园', 116.396500, 39.912000, '["https://picsum.photos/800/600?random=13"]', NOW(), NOW()),
(13, '竹园2号楼', 4, 1, '女生宿舍楼，距离图书馆近。', '学校北区竹园', 116.396700, 39.911800, '["https://picsum.photos/800/600?random=14"]', NOW(), NOW());

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
INSERT INTO map_marker (id, facility_id, icon_url, sort, create_time, update_time) VALUES
-- 餐厅标记 (按设施类型排序)
(1, 1, NULL, 1, NOW(), NOW()),
(2, 2, NULL, 2, NOW(), NOW()),
(3, 3, NULL, 3, NOW(), NOW()),
-- 运动场标记
(4, 4, NULL, 4, NOW(), NOW()),
(5, 5, NULL, 5, NOW(), NOW()),
(6, 6, NULL, 6, NOW(), NOW()),
-- 教学楼标记
(7, 7, NULL, 7, NOW(), NOW()),
(8, 8, NULL, 8, NOW(), NOW()),
(9, 9, NULL, 9, NOW(), NOW()),
-- 宿舍标记
(10, 10, NULL, 10, NOW(), NOW()),
(11, 11, NULL, 11, NOW(), NOW()),
(12, 12, NULL, 12, NOW(), NOW()),
(13, 13, NULL, 13, NOW(), NOW());

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
(9, 7, 10, '松园1号楼', 116.399000, 39.911500, 4, '宿舍', NOW());
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
    view_count INT DEFAULT 0 COMMENT '浏览量',
    favorite_count INT DEFAULT 0 COMMENT '收藏数',
    status INT NOT NULL DEFAULT 2 COMMENT '状态: 2-在售 3-已售出 4-已下架',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    FOREIGN KEY (user_id) REFERENCES sys_user(id),
    FOREIGN KEY (category_id) REFERENCES secondhand_category(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='二手物品表';

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
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '活动状态: DRAFT-草稿, PUBLISHED-已发布, REJECTED-已驳回, CANCELLED-已取消, COMPLETED-已完成',
    sign_in_type INT DEFAULT 1 COMMENT '签到类型: 1-现场签到, 2-二维码签到',
    sign_in_open BOOLEAN DEFAULT FALSE COMMENT '签到是否开启',
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
INSERT INTO activity (id, title, cover_image, category_id, organizer_id, organizer_name, content, location, max_people, current_people, start_time, end_time, signup_start_time, signup_end_time, status, sign_in_type, sign_in_open, score, contact_name, contact_phone, create_time) VALUES
(1, 'AI 学习工作坊', 'https://picsum.photos/800/600?random=101', 1, 2, '张老师', '面向全校同学的 AI 学习工作坊，讲解常见工具和实践方法。', '图书馆报告厅', 120, 46, '2026-04-12 14:00:00', '2026-04-12 17:00:00', '2026-04-01 09:00:00', '2026-04-11 18:00:00', 'PUBLISHED', 1, false, 1.5, '张老师', '13800138000', NOW()),
(2, '春季篮球联赛', 'https://picsum.photos/800/600?random=102', 2, 3, '李老师', '春季篮球联赛正在进行，欢迎同学们到场观赛与加油。', '篮球场', 240, 168, '2026-04-06 13:00:00', '2026-04-06 18:00:00', '2026-03-20 08:00:00', '2026-04-05 18:00:00', 'PUBLISHED', 1, false, 1.0, '李老师', '13800138001', NOW()),
(3, '社团开放日', 'https://picsum.photos/800/600?random=103', 3, 2, '张老师', '各大社团集中展示招新内容，现场可体验互动项目并咨询报名。', '图书馆前广场', 300, 132, '2026-04-10 10:00:00', '2026-04-10 16:30:00', '2026-04-02 09:00:00', '2026-04-09 20:00:00', 'PUBLISHED', 1, false, 0.5, '张老师', '13800138000', NOW()),
(4, '校园环保行动', 'https://picsum.photos/800/600?random=104', 4, 3, '李老师', '组织志愿者进行校园清洁与垃圾分类宣传，活动已经顺利结束。', '校园主干道', 80, 63, '2026-04-03 09:00:00', '2026-04-03 12:00:00', '2026-03-25 08:00:00', '2026-04-02 18:00:00', 'COMPLETED', 1, false, 1.0, '李老师', '13800138001', NOW()),
(5, '新生融入分享会', 'https://picsum.photos/800/600?random=105', 5, 2, '张老师', '邀请优秀学长学姐分享学习与生活经验，帮助新生快速适应校园。', '博学楼报告厅', 180, 72, '2026-04-08 19:00:00', '2026-04-08 21:00:00', '2026-04-01 10:00:00', '2026-04-07 18:00:00', 'PUBLISHED', 1, false, 0.5, '张老师', '13800138000', NOW()),
(6, '心理健康沙龙', 'https://picsum.photos/800/600?random=106', 1, 3, '李老师', '围绕压力管理与情绪疏导开展交流分享，适合同学们报名参加。', '大学生活动中心 201', 90, 21, '2026-04-15 15:00:00', '2026-04-15 17:00:00', '2026-04-04 08:00:00', '2026-04-14 18:00:00', 'PUBLISHED', 1, false, 0.5, '李老师', '13800138001', NOW());

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
INSERT INTO forum_topic (id, topic_name, post_count, is_hot, status, create_time) VALUES
(1, '校园生活', 0, 1, 'ACTIVE', NOW()),
(2, '学习交流', 0, 1, 'ACTIVE', NOW()),
(3, '求职招聘', 0, 1, 'ACTIVE', NOW()),
(4, '二手交易', 0, 1, 'ACTIVE', NOW()),
(5, '情感树洞', 0, 1, 'ACTIVE', NOW()),
(6, '美食探店', 0, 1, 'ACTIVE', NOW()),
(7, '求助问答', 0, 0, 'ACTIVE', NOW()),
(8, '失物招领', 0, 0, 'ACTIVE', NOW())
ON DUPLICATE KEY UPDATE topic_name = VALUES(topic_name), status = 'ACTIVE';

-- =============================================
-- 优惠活动数据
