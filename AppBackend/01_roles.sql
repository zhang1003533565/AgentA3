-- 第一段：角色导入 SQL（可重复执行）
INSERT INTO sys_role (id, name) VALUES
(1, 'ADMIN'),
(2, 'TEACHER'),
(3, 'STUDENT'),
(4, 'MERCHANT')
ON DUPLICATE KEY UPDATE
name = VALUES(name);
