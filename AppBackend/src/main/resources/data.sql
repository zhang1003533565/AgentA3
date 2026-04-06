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
INSERT INTO sys_user (id, username, password, real_name, phone, email, role_id, status, create_time, update_time,jwx_password,jwx_student_id,semester_start,share_code) VALUES
-- 管理员 (用户名: admin, 密码: admin123)
(1, 'admin', 'admin123', '系统管理员', '13800000001', 'admin@campus.edu.cn', 1, 1, NOW(), NOW(),'313','32132313','2026-02-24','SCH000001'),
-- 教师 (用户名: fjj, 密码: admin123)
(2, 'fjj', 'admin123', '张老师', '13800000002', 'zhanglaoshi@campus.edu.cn', 2, 1, NOW(), NOW(),'313','32132313','2026-02-24','SCH000002'),
-- 教师 (用户名: fjj2, 密码: admin123)
(3, 'fjj2', 'admin123', '李老师', '13800000003', 'lilaoshi@campus.edu.cn', 2, 1, NOW(), NOW(),'313','32132313','2026-02-24','SCH000003'),
-- 学生 (用户名: zzs, 密码: admin123)
(4, 'zzs', 'admin123', '张三', '13800000004', 'zhangsan@stu.campus.edu.cn', 3, 1, NOW(), NOW(),'313','32132313','2026-02-24','SCH000004'),
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
(12, 'merchant04', 'admin123', '快印图文老板', '13812345604', 'zhaolaoban@campus.edu.cn', 4, 1, NOW(), NOW(),'313','32132313','2026-02-24','SCH000012');
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
    publisher_id BIGINT COMMENT '发布者 ID',
    publisher_name VARCHAR(50) COMMENT '发布者名称',
    publish_time DATETIME COMMENT '发布时间',
    enabled TINYINT DEFAULT 1 COMMENT '是否启用：0-禁用，1-启用' NOT NULL,
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公告栏表';

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
INSERT IGNORE INTO forum_topic (id, topic_name, post_count, is_hot, status, create_time) VALUES
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
-- 第一学生餐厅 (restaurant_id=1) 的档口
(1, '早餐包子铺', 1, '1F', '早餐', '食堂东侧', 4.8, 527, 88, 12.0, '06:30-09:30', 'https://picsum.photos/seed/stall001/400/300', '主营鲜肉包、菜包、豆浆、油条等早餐，现包现蒸，新鲜美味。', 1, 1, NOW(), NOW()),
(2, '麻辣烫档口', 1, '3F', '面食', '食堂西侧', 4.5, 508, 82, 18.0, '10:30-20:30', 'https://picsum.photos/seed/stall002/400/300', '自选麻辣烫，多种食材可选，麻酱香浓，口味正宗。', 1, 2, NOW(), NOW()),
(3, '石锅拌饭', 1, '2F', '米饭', '食堂中部', 4.3, 431, 78, 16.0, '10:30-20:00', 'https://picsum.photos/seed/stall003/400/300', '韩式石锅拌饭，锅巴香脆，牛肉嫩滑，酱料正宗。', 1, 3, NOW(), NOW()),
(4, '黄焖鸡米饭', 1, '2F', '米饭', '食堂北侧', 4.0, 289, 71, 15.0, '10:30-20:00', 'https://picsum.photos/seed/stall004/400/300', '经典黄焖鸡，鸡肉嫩滑，汤汁浓郁，拌饭绝佳。', 1, 4, NOW(), NOW()),
(5, '兰州拉面', 1, '1F', '面食', '食堂南门', 4.6, 612, 85, 14.0, '07:00-20:30', 'https://picsum.photos/seed/stall005/400/300', '手工拉面，汤鲜面劲道，牛肉片足，正宗西北风味。', 1, 5, NOW(), NOW()),
(6, '沙县小吃', 1, '2F', '小吃', '食堂东侧', 4.2, 345, 76, 10.0, '08:00-20:00', 'https://picsum.photos/seed/stall006/400/300', '拌面、扁肉、蒸饺、炖罐等经典沙县美食。', 1, 6, NOW(), NOW()),

-- 第二学生餐厅 (restaurant_id=2) 的档口
(7, '自选快餐', 2, '1F', '米饭', '食堂大厅', 4.4, 478, 80, 13.0, '10:30-19:00', 'https://picsum.photos/seed/stall007/400/300', '多种菜品自选，两荤两素搭配，价格实惠。', 1, 1, NOW(), NOW()),
(8, '奶茶饮品站', 2, '2F', '饮品', '食堂北侧', 4.7, 720, 90, 8.0, '09:00-21:00', 'https://picsum.photos/seed/stall008/400/300', '珍珠奶茶、柠檬茶、芒果冰沙等各式饮品。', 1, 2, NOW(), NOW()),
(9, '煎饼果子', 2, '1F', '早餐', '食堂东门', 4.5, 390, 86, 9.0, '06:30-09:30', 'https://picsum.photos/seed/stall009/400/300', '正宗天津煎饼果子，薄脆加蛋加肠，料足味美。', 1, 3, NOW(), NOW()),
(10, '重庆小面', 2, '3F', '面食', '食堂西侧', 4.4, 456, 83, 15.0, '10:30-20:30', 'https://picsum.photos/seed/stall010/400/300', '麻辣鲜香，正宗重庆风味，豌杂面、酸辣粉可选。', 1, 4, NOW(), NOW()),

-- 清真餐厅 (restaurant_id=3) 的档口
(11, '清真牛肉面', 3, '1F', '面食', '食堂大厅', 4.6, 280, 87, 16.0, '07:00-20:00', 'https://picsum.photos/seed/stall011/400/300', '清真认证，牛肉新鲜，汤底醇厚，面条筋道。', 1, 1, NOW(), NOW()),
(12, '新疆大盘鸡', 3, '1F', '米饭', '食堂北侧', 4.5, 195, 84, 22.0, '10:30-20:00', 'https://picsum.photos/seed/stall012/400/300', '正宗新疆风味，鸡肉鲜嫩，土豆软糯，配皮带面。', 1, 2, NOW(), NOW()),
(13, '烤羊肉串', 3, '1F', '小吃', '食堂东门', 4.7, 320, 89, 5.0, '11:00-21:00', 'https://picsum.photos/seed/stall013/400/300', '现烤羊肉串，外焦里嫩，孜然香气扑鼻。', 1, 3, NOW(), NOW());

-- =============================================
-- 菜品表
-- =============================================
DROP TABLE IF EXISTS dish_review;
DROP TABLE IF EXISTS dish;
CREATE TABLE IF NOT EXISTS dish (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '菜品 ID',
    name VARCHAR(100) NOT NULL COMMENT '菜品名称',
    stall_id BIGINT NOT NULL COMMENT '所属档口 ID',
    price DECIMAL(10,2) NOT NULL COMMENT '菜品价格',
    category VARCHAR(50) COMMENT '菜品分类',
    image_url VARCHAR(255) COMMENT '菜品图片 URL',
    rating DECIMAL(3,2) DEFAULT 0 COMMENT '菜品评分 (0-5)',
    sold_count INT DEFAULT 0 COMMENT '销量',
    is_available TINYINT(1) DEFAULT 1 COMMENT '是否可用：1-可售 0-停售',
    taste VARCHAR(100) COMMENT '口味类型',
    description TEXT COMMENT '菜品描述',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    FOREIGN KEY (stall_id) REFERENCES canteen_stall(id)
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
(1, 1, 1001, 1, 5.00, '鲜肉包真的很好吃，皮薄馅大，汁水很足！每天早上都要来两个。', 'https://picsum.photos/seed/review001/200/200', 0, 12, 3, 1, NOW(), NOW()),
(2, 1, 1002, 1, 4.50, '味道不错，就是有时候要排队很久。', NULL, 0, 5, 1, 1, NOW(), NOW()),
(3, 1, 1003, 1, 5.00, '早餐首选，包子新鲜热乎，老板人也好。', NULL, 0, 8, 0, 1, NOW(), NOW()),

-- 菜包 (dish_id=2) 的评价
(4, 2, 1004, 1, 4.00, '青菜馅很新鲜，清淡健康，适合不喜欢油腻的人。', NULL, 0, 3, 0, 1, NOW(), NOW()),
(5, 2, 1005, 1, 4.50, '菜包味道好，馅料足，价格实惠。', NULL, 0, 6, 2, 1, NOW(), NOW()),

-- 豆浆 (dish_id=3) 的评价
(6, 3, 1006, 1, 5.00, '现磨豆浆，香浓可口，配包子完美！', NULL, 0, 10, 1, 1, NOW(), NOW()),
(7, 3, 1007, 1, 4.00, '豆浆味道纯正，就是有时候有点烫。', NULL, 0, 2, 0, 1, NOW(), NOW()),

-- 油条 (dish_id=4) 的评价
(8, 4, 1008, 1, 4.50, '油条炸得酥脆，配豆浆很好吃。', 'https://picsum.photos/seed/review002/200/200', 0, 7, 0, 1, NOW(), NOW()),
(9, 4, 1009, 1, 3.50, '有时候偏软，早点去会更好。', NULL, 0, 4, 1, 1, NOW(), NOW()),

-- 麻辣烫自选 (dish_id=5) 的评价
(10, 5, 1010, 2, 4.50, '麻辣烫味道很好，可以自选食材，丰俭由人。', 'https://picsum.photos/seed/review003/200/200', 0, 15, 5, 1, NOW(), NOW()),
(11, 5, 1011, 2, 4.00, '麻酱香浓，食材新鲜，就是价格稍微有点贵。', NULL, 0, 6, 2, 1, NOW(), NOW()),
(12, 5, 1012, 2, 5.00, '超级好吃！每次都要加好多菜，老板人也很好。', NULL, 0, 9, 0, 1, NOW(), NOW()),

-- 牛肉石锅拌饭 (dish_id=7) 的评价
(13, 7, 1013, 3, 4.50, '石锅拌饭很正宗，锅巴香脆，牛肉嫩滑。', 'https://picsum.photos/seed/review004/200/200', 0, 11, 3, 1, NOW(), NOW()),
(14, 7, 1014, 3, 4.00, '味道不错，就是量有点少。', NULL, 0, 4, 1, 1, NOW(), NOW()),

-- 黄焖鸡米饭 (dish_id=10) 的评价
(15, 10, 1015, 4, 4.50, '黄焖鸡味道很好，汤汁浓郁，拌饭一绝！', NULL, 0, 18, 4, 1, NOW(), NOW()),
(16, 10, 1016, 4, 4.00, '鸡肉嫩滑，土豆软糯，就是有时候等得有点久。', NULL, 0, 5, 0, 1, NOW(), NOW()),
(17, 10, 1017, 4, 5.00, '超级好吃，每次必点！', 'https://picsum.photos/seed/review005/200/200', 0, 12, 2, 1, NOW(), NOW()),

-- 牛肉拉面 (dish_id=13) 的评价
(18, 13, 1018, 5, 5.00, '兰州拉面很正宗，汤鲜面劲道，牛肉片足。', NULL, 0, 20, 6, 1, NOW(), NOW()),
(19, 13, 1019, 5, 4.50, '味道好，量大实惠，推荐！', NULL, 0, 8, 1, 1, NOW(), NOW()),

-- 珍珠奶茶 (dish_id=22) 的评价
(20, 22, 1020, 8, 4.50, '珍珠奶茶很好喝，珍珠 Q 弹，甜度适中。', 'https://picsum.photos/seed/review006/200/200', 0, 14, 3, 1, NOW(), NOW()),
(21, 22, 1021, 8, 5.00, '最爱这家的奶茶，比外面卖的好喝多了！', NULL, 0, 9, 0, 1, NOW(), NOW()),
(22, 22, 1022, 8, 4.00, '味道不错，就是有时候要等很久。', NULL, 0, 3, 1, 1, NOW(), NOW()),

-- 烤羊肉串 (dish_id=37) 的评价
(23, 37, 1023, 13, 5.00, '羊肉串很正宗，外焦里嫩，孜然味十足！', 'https://picsum.photos/seed/review007/200/200', 0, 25, 8, 1, NOW(), NOW()),
(24, 37, 1024, 13, 4.50, '新鲜现烤，肉质好，价格实惠。', NULL, 0, 11, 2, 1, NOW(), NOW()),
(25, 37, 1025, 13, 5.00, '超级好吃！每次都要来几串。', NULL, 0, 16, 0, 1, NOW(), NOW());

-- =============================================
-- 优惠活动数据
