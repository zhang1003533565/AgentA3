-- 会议个人任务表初始化 SQL
-- 用于保存会后 AI 识别的个人任务分工

SET NAMES utf8mb4;

-- 会议个人任务表
CREATE TABLE IF NOT EXISTS meeting_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '任务唯一 ID',
    meeting_session_id BIGINT NOT NULL COMMENT '所属会议 ID',
    assignee_id BIGINT NOT NULL COMMENT '任务负责人用户 ID',
    assignee_name VARCHAR(80) NOT NULL COMMENT '任务负责人名称快照（创建时）',
    title VARCHAR(255) NOT NULL COMMENT '任务标题',
    description TEXT COMMENT '任务详细描述',
    deadline DATETIME COMMENT '截止时间（未明确为 NULL）',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '任务状态：PENDING-待完成 / COMPLETED-已完成',
    evidence TEXT NOT NULL COMMENT '任务依据（原始发言记录）',
    completed_at DATETIME COMMENT '完成时间（未完成为 NULL）',
    completed_by BIGINT COMMENT '完成确认人 ID（谁确认完成了这个任务）',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    
    INDEX idx_meeting_session_id (meeting_session_id),
    INDEX idx_assignee_id (assignee_id),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time),
    -- 注意：不在数据库中做 UNIQUE 约束，由业务层判断重复
    
    FOREIGN KEY (meeting_session_id) REFERENCES meeting_session(id) ON DELETE CASCADE,
    -- assignee_id 引用 sys_user 表时需要手动建立关联（如果系统中有用户表）
    -- FOREIGN KEY (assignee_id) REFERENCES sys_user(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会议个人任务表';

-- 清空旧测试数据（避免重复插入报错）
DELETE FROM meeting_task WHERE meeting_session_id IN (SELECT id FROM meeting_session WHERE session_id LIKE 'sess%');
DELETE FROM meeting_task WHERE meeting_session_id IN (SELECT id FROM meeting_session WHERE room_code IS NOT NULL);

-- 插入测试任务（假设存在 user_id = 1 的用户）
INSERT INTO meeting_task (
    meeting_session_id, 
    assignee_id, 
    assignee_name, 
    title, 
    description, 
    deadline, 
    status, 
    evidence, 
    completed_at, 
    completed_by, 
    create_time, 
    update_time
)
SELECT 
    id, 
    1, 
    '张三', 
    '进行机器学习模型的算法训练', 
    '需要在下次汇报前完成模型训练并生成结果', 
    DATE_ADD(NOW(), INTERVAL 7 DAY), 
    'PENDING', 
    '我负责项目相关工作的第一个任务，该任务涉及机器学习相关工作的分配。',
    NULL, 
    NULL, 
    NOW(), 
    NOW()
FROM meeting_session WHERE session_id = 'sess003';

INSERT INTO meeting_task (
    meeting_session_id, 
    assignee_id, 
    assignee_name, 
    title, 
    description, 
    deadline, 
    status, 
    evidence, 
    completed_at, 
    completed_by, 
    create_time, 
    update_time
)
SELECT 
    id, 
    1, 
    '张三', 
    '整理测试数据', 
    '准备下一阶段的测试数据集', 
    NULL, 
    'COMPLETED', 
    '我负责整理测试数据。',
    NOW(), 
    1, 
    NOW(), 
    NOW()
FROM meeting_session WHERE session_id = 'sess003';
