-- 初始化角色数据（表由 JPA ddl-auto 创建，此处仅插入初始数据）
-- 使用 INSERT IGNORE 避免重复启动时报唯一约束错误
INSERT IGNORE INTO sys_role (name) VALUES
    ('ADMIN'),
    ('TEACHER'),
    ('STUDENT');
