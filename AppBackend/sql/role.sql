-- 角色表
CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '角色ID',
    name VARCHAR(20) NOT NULL UNIQUE COMMENT '角色名称'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

-- 初始化角色数据
INSERT INTO sys_role (name) VALUES
    ('ADMIN'),
    ('TEACHER'),
    ('STUDENT');
