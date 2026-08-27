-- 学习计划结构化拆解：Goal / Task 建表脚本
-- 项目默认由 Hibernate ddl-auto=update 自动建表；此文件用于数据库评审或手工初始化。

CREATE TABLE IF NOT EXISTS study_goal (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '所属用户ID',
    title VARCHAR(120) NOT NULL COMMENT '目标名称',
    description TEXT NULL COMMENT '目标描述',
    start_date DATE NULL COMMENT '排程开始日期',
    target_date DATE NULL COMMENT '目标完成日期',
    progress INT NOT NULL DEFAULT 0 COMMENT '完成百分比 0-100，自动计算',
    status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '状态 pending/in_progress/completed',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_study_goal_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习目标表';

CREATE TABLE IF NOT EXISTS study_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    goal_id BIGINT NOT NULL COMMENT '关联的Goal ID',
    task_name VARCHAR(120) NOT NULL COMMENT '任务名称',
    stage VARCHAR(60) NULL COMMENT '所属阶段',
    estimated_days INT NOT NULL DEFAULT 1 COMMENT '预计天数',
    planned_start_date DATE NULL COMMENT '计划开始日期',
    planned_end_date DATE NULL COMMENT '计划结束日期',
    priority VARCHAR(10) NOT NULL DEFAULT '中' COMMENT '优先级 高/中/低',
    order_num INT NOT NULL DEFAULT 0 COMMENT '排序序号，从1开始',
    status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '任务状态 pending/completed',
    is_completed TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已完成（前端勾选）',
    progress_percent INT NOT NULL DEFAULT 0 COMMENT '任务完成百分比 0-100',
    description TEXT NULL COMMENT '补充说明',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_study_task_goal (goal_id),
    KEY idx_study_task_goal_completed (goal_id, is_completed),
    CONSTRAINT fk_study_task_goal FOREIGN KEY (goal_id) REFERENCES study_goal (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习任务表';
