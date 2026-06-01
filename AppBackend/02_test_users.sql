-- 第二段：测试账号 SQL（每个角色 1 个，可重复执行）
-- 统一测试密码：admin123（与项目现有 data.sql 一致为明文）
INSERT INTO sys_user (
  id, username, password, real_name, phone, email, role_id, status,
  create_time, update_time, jwx_password, jwx_student_id, semester_start, share_code
) VALUES
(101, 'test_admin',    'admin123', '测试管理员', '13800000101', 'test_admin@campus.edu.cn',    1, 1, NOW(), NOW(), '313', 'TADMIN101',  '2026-02-24', 'SCHTEST101'),
(102, 'test_teacher',  'admin123', '测试教师',   '13800000102', 'test_teacher@campus.edu.cn',  2, 1, NOW(), NOW(), '313', 'TTEACH102',  '2026-02-24', 'SCHTEST102'),
(103, 'test_student',  'admin123', '测试学生',   '13800000103', 'test_student@campus.edu.cn',  3, 1, NOW(), NOW(), '313', 'TSTU103',    '2026-02-24', 'SCHTEST103'),
(104, 'test_merchant', 'admin123', '测试商家',   '13800000104', 'test_merchant@campus.edu.cn', 4, 1, NOW(), NOW(), '313', 'TMER104',    '2026-02-24', 'SCHTEST104')
ON DUPLICATE KEY UPDATE
username       = VALUES(username),
password       = VALUES(password),
real_name      = VALUES(real_name),
phone          = VALUES(phone),
email          = VALUES(email),
role_id        = VALUES(role_id),
status         = VALUES(status),
update_time    = NOW(),
jwx_password   = VALUES(jwx_password),
jwx_student_id = VALUES(jwx_student_id),
semester_start = VALUES(semester_start),
share_code     = VALUES(share_code);
