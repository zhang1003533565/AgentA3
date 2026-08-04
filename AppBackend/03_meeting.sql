-- 会议模块初始化 SQL
-- 注意：meeting_session 的 user_id 需要与当前登录账号的 users.id 一致，否则小程序里看不到
-- 导入前请先执行：SELECT id FROM users WHERE username='你的登录账号'; 然后把下面的 @USER_ID 替换为实际值
-- 或者导入后在 Adminer 里执行：UPDATE meeting_session SET user_id = 你的用户ID;

SET NAMES utf8mb4;

-- 会议主表
CREATE TABLE IF NOT EXISTS meeting_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    session_id VARCHAR(80) NOT NULL COMMENT '会议会话ID',
    room_code VARCHAR(12) COMMENT '可分享会议号',
    user_id BIGINT NOT NULL COMMENT '创建用户ID',
    title VARCHAR(120) NOT NULL COMMENT '会议标题',
    meeting_type VARCHAR(20) NOT NULL DEFAULT 'quick' COMMENT '会议类型：quick-快速会议/reserved-预约会议',
    status VARCHAR(20) NOT NULL DEFAULT 'idle' COMMENT '会议状态：idle-未开始/active-进行中/paused-暂停/ended-已结束',
    scheduled_start_time DATETIME COMMENT '预约开始时间',
    start_time DATETIME COMMENT '实际开始时间',
    end_time DATETIME COMMENT '结束时间',
    last_note VARCHAR(500) COMMENT '最后一段会议记录摘要',
    record_count INT DEFAULT 0 COMMENT '会议记录数量',
    result_count INT DEFAULT 0 COMMENT '智能体结果数量',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    UNIQUE KEY uk_user_session (user_id, session_id),
    UNIQUE KEY uk_room_code (room_code),
    KEY idx_user_id (user_id),
    KEY idx_status (status),
    KEY idx_meeting_type (meeting_type),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会议会话表';

-- 会议参与成员表
CREATE TABLE IF NOT EXISTS meeting_participant (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    meeting_session_id BIGINT NOT NULL COMMENT '会议主键ID',
    name VARCHAR(80) NOT NULL COMMENT '成员名称',
    sort_order INT DEFAULT 0 COMMENT '排序',
    create_time DATETIME COMMENT '创建时间',
    KEY idx_meeting_session_id (meeting_session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会议参与成员表';

-- 会议记录表
CREATE TABLE IF NOT EXISTS meeting_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    meeting_session_id BIGINT NOT NULL COMMENT '会议主键ID',
    source VARCHAR(40) NOT NULL DEFAULT 'manual' COMMENT '记录来源：manual-人工录入/transcription-语音转写',
    content TEXT NOT NULL COMMENT '会议记录内容',
    create_time DATETIME COMMENT '创建时间',
    KEY idx_meeting_session_id (meeting_session_id),
    KEY idx_source (source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会议记录表';

-- 会议智能体结果表
CREATE TABLE IF NOT EXISTS meeting_agent_result (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    meeting_session_id BIGINT NOT NULL COMMENT '会议主键ID',
    agent_name VARCHAR(80) NOT NULL COMMENT '会议智能体名称',
    answer_type VARCHAR(40) COMMENT '回答类型',
    answer TEXT NOT NULL COMMENT '智能体输出内容',
    create_time DATETIME COMMENT '创建时间',
    KEY idx_meeting_session_id (meeting_session_id),
    KEY idx_agent_name (agent_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会议智能体结果表';

-- 清空旧测试数据（避免重复插入报错）
DELETE FROM meeting_record WHERE meeting_session_id IN (SELECT id FROM meeting_session WHERE session_id LIKE 'sess%');
DELETE FROM meeting_participant WHERE meeting_session_id IN (SELECT id FROM meeting_session WHERE session_id LIKE 'sess%');
DELETE FROM meeting_agent_result WHERE meeting_session_id IN (SELECT id FROM meeting_session WHERE session_id LIKE 'sess%');
DELETE FROM meeting_session WHERE session_id LIKE 'sess%';

-- 插入测试会议（默认 user_id = 1，导入后请根据实际登录用户修改）
INSERT INTO meeting_session (session_id, room_code, user_id, title, meeting_type, status, scheduled_start_time, start_time, end_time, create_time, update_time) VALUES
('sess001', '3LMCS5', 1, '项目进度同步会', 'quick', 'active', NOW(), NOW(), NULL, NOW(), NOW()),
('sess002', 'DKCBUX', 1, '需求评审会', 'reserved', 'idle', DATE_ADD(NOW(), INTERVAL 4 HOUR), NULL, NULL, NOW(), NOW()),
('sess003', 'KQ8M2P', 1, '项目复盘会', 'reserved', 'ended', DATE_SUB(NOW(), INTERVAL 2 HOUR), DATE_SUB(NOW(), INTERVAL 2 HOUR), DATE_SUB(NOW(), INTERVAL 1 HOUR), DATE_SUB(NOW(), INTERVAL 3 HOUR), NOW()),
('sess004', 'V7N4JD', 1, '周例会', 'reserved', 'ended', DATE_SUB(NOW(), INTERVAL 6 HOUR), DATE_SUB(NOW(), INTERVAL 6 HOUR), DATE_SUB(NOW(), INTERVAL 5 HOUR), DATE_SUB(NOW(), INTERVAL 8 HOUR), NOW());

-- 插入会议记录
INSERT INTO meeting_record (meeting_session_id, source, content, create_time)
SELECT id, 'manual', '确认本周迭代目标', NOW() FROM meeting_session WHERE session_id = 'sess001';

INSERT INTO meeting_record (meeting_session_id, source, content, create_time)
SELECT id, 'transcription', '张三：后端接口预计周三完成', NOW() FROM meeting_session WHERE session_id = 'sess001';

-- 插入参与人
INSERT INTO meeting_participant (meeting_session_id, name, sort_order, create_time)
SELECT id, '张三', 0, NOW() FROM meeting_session WHERE session_id = 'sess001';

INSERT INTO meeting_participant (meeting_session_id, name, sort_order, create_time)
SELECT id, '李四', 1, NOW() FROM meeting_session WHERE session_id = 'sess001';
