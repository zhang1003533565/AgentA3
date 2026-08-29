-- 校园服务 AI 工具测试数据（课表/活动/会议/食堂/设施/二手）
-- 适配当前库用户：admin=101, wangli=102, zzs=103
-- 可重复执行：先清理 demo 区间再插入
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- 0. 用户学期配置（课表周次计算依赖）
-- ============================================================
UPDATE sys_user
SET semester_start = '2026-02-24', update_time = NOW()
WHERE id IN (101, 102, 103);

DELETE FROM schedule_period_setting WHERE user_id IN (101, 103);
DELETE FROM schedule_semester_setting WHERE user_id IN (101, 103);

INSERT INTO schedule_semester_setting
    (user_id, academic_year, semester_term, semester_code, semester_start, selected_flag, create_time, update_time)
VALUES
    (101, '2025-2026', 2, '12', '2026-02-24', 1, NOW(), NOW()),
    (103, '2025-2026', 2, '12', '2026-02-24', 1, NOW(), NOW());

INSERT INTO schedule_period_setting (user_id, period_index, start_time, end_time, create_time, update_time) VALUES
    (101, 1, '08:00:00', '08:45:00', NOW(), NOW()),
    (101, 2, '08:55:00', '09:40:00', NOW(), NOW()),
    (101, 3, '10:00:00', '10:45:00', NOW(), NOW()),
    (101, 4, '10:55:00', '11:40:00', NOW(), NOW()),
    (101, 5, '14:00:00', '14:45:00', NOW(), NOW()),
    (101, 6, '14:55:00', '15:40:00', NOW(), NOW()),
    (101, 7, '16:00:00', '16:45:00', NOW(), NOW()),
    (101, 8, '16:55:00', '17:40:00', NOW(), NOW()),
    (103, 1, '08:00:00', '08:45:00', NOW(), NOW()),
    (103, 2, '08:55:00', '09:40:00', NOW(), NOW()),
    (103, 3, '10:00:00', '10:45:00', NOW(), NOW()),
    (103, 4, '10:55:00', '11:40:00', NOW(), NOW()),
    (103, 5, '14:00:00', '14:45:00', NOW(), NOW()),
    (103, 6, '14:55:00', '15:40:00', NOW(), NOW()),
    (103, 7, '16:00:00', '16:45:00', NOW(), NOW()),
    (103, 8, '16:55:00', '17:40:00', NOW(), NOW());

-- ============================================================
-- 1. 校园设施（java_facility_api / java_canteen_api）
-- ============================================================
DELETE FROM facility_review WHERE id BETWEEN 9001 AND 9010;
DELETE FROM canteen_stall WHERE id BETWEEN 9001 AND 9013;
DELETE FROM campus_facility WHERE id BETWEEN 9001 AND 9012;

INSERT INTO campus_facility
    (id, facility_name, facility_type, status, description, location, longitude, latitude, images, create_time, update_time)
VALUES
    (9001, '学一食堂', 1, 1, '朝阳校区主食堂，早餐与午餐人气最高。', '朝阳校区东区', 114.899741, 40.755538, '["https://picsum.photos/seed/canteen-1/800/600"]', NOW(), NOW()),
    (9002, '学二食堂', 1, 1, '靠近图书馆，适合课后就餐。', '朝阳校区中区', 114.899888, 40.756985, '["https://picsum.photos/seed/canteen-2/800/600"]', NOW(), NOW()),
    (9003, '学三食堂', 1, 1, '清真与风味档口较多。', '朝阳校区西区', 114.899948, 40.754134, '["https://picsum.photos/seed/canteen-3/800/600"]', NOW(), NOW()),
    (9004, '东区运动场', 2, 1, '综合运动场地，晚间开放。', '朝阳校区东区', 114.899512, 40.758585, '["https://picsum.photos/seed/sports-4/800/600"]', NOW(), NOW()),
    (9005, '篮球场', 2, 1, '室外篮球场，可预约。', '朝阳校区', 114.896483, 40.752178, '["https://picsum.photos/seed/sports-5/800/600"]', NOW(), NOW()),
    (9006, '明德楼', 3, 1, '计算机学院主要上课教学楼。', '朝阳校区', 114.898999, 40.757583, '["https://picsum.photos/seed/teaching-8/800/600"]', NOW(), NOW()),
    (9007, '图书馆', 3, 1, '自习与借阅中心，一层有咖啡角。', '朝阳校区', 114.897021, 40.755812, '["https://picsum.photos/seed/library-10/800/600"]', NOW(), NOW()),
    (9008, '综合服务楼', 3, 1, '校园卡、打印、快递综合服务。', '朝阳校区', 114.898965, 40.756936, '["https://picsum.photos/seed/service-12/800/600"]', NOW(), NOW());

INSERT INTO facility_review (id, facility_id, user_id, score, content, create_time) VALUES
    (9001, 9001, 103, 5, '学一食堂麻辣烫和拉面都很好吃，价格实惠。', NOW()),
    (9002, 9007, 101, 5, '图书馆环境安静，插座充足，适合自习。', NOW()),
    (9003, 9006, 103, 4, '明德楼教室多媒体设备齐全。', NOW());

-- ============================================================
-- 2. 商家 / 档口 / 菜品 / 优惠券（java_canteen_api）
-- ============================================================
DELETE FROM promotion_coupon WHERE id BETWEEN 9001 AND 9010;
DELETE FROM dish WHERE id BETWEEN 9001 AND 9020;
DELETE FROM merchant WHERE id BETWEEN 9001 AND 9004;
DELETE FROM merchant_category WHERE id BETWEEN 9001 AND 9004;

INSERT INTO merchant_category (id, category_name, category_icon, sort, status, create_time) VALUES
    (9001, '餐厅美食', NULL, 1, 1, NOW()),
    (9002, '饮品甜点', NULL, 2, 1, NOW()),
    (9003, '超市便利', NULL, 3, 1, NOW()),
    (9004, '打印复印', NULL, 4, 1, NOW());

INSERT INTO merchant
    (id, merchant_name, category_id, description, logo, address, contact_name, contact_phone, business_hours, user_id, status, create_time, update_time)
VALUES
    (9001, '学府餐厅', 9001, '学一食堂主力合作商家。', NULL, '学一食堂一楼', '李老板', '13812345601', '07:00-21:00', 103, 1, NOW(), NOW()),
    (9002, '书香咖啡', 9002, '图书馆一楼咖啡轻食。', NULL, '图书馆一层', '王老板', '13812345602', '08:00-22:00', 104, 1, NOW(), NOW()),
    (9003, '校园便利超市', 9003, '日用品与零食齐全。', NULL, '南门内 50 米', '张老板', '13812345603', '07:00-23:00', 105, 1, NOW(), NOW()),
    (9004, '快印图文店', 9004, '打印复印装订服务。', NULL, '明德楼地下一层', '赵老板', '13812345604', '08:00-20:00', 101, 1, NOW(), NOW());

INSERT INTO canteen_stall
    (id, stall_name, restaurant_id, floor, category, location, score, review_count, recommend_rate, avg_price, business_hours, description, status, sort, create_time, update_time)
VALUES
    (9001, '早餐包子铺', 9001, '1F', '早餐', '学一食堂东侧', 4.50, 120, 92, 12.00, '06:30-09:30', '鲜肉包、豆浆、油条。', 1, 1, NOW(), NOW()),
    (9002, '麻辣烫档口', 9001, '2F', '面食', '学一食堂西侧', 4.30, 86, 85, 18.00, '10:30-20:30', '自选麻辣烫，麻酱香浓。', 1, 2, NOW(), NOW()),
    (9003, '兰州拉面', 9001, '1F', '面食', '学一食堂南门', 4.60, 95, 90, 14.00, '07:00-20:30', '手工拉面，汤鲜面劲。', 1, 3, NOW(), NOW()),
    (9004, '自选快餐', 9002, '1F', '米饭', '学二食堂大厅', 4.40, 210, 82, 13.00, '10:30-19:00', '两荤两素实惠套餐。', 1, 1, NOW(), NOW()),
    (9005, '奶茶饮品站', 9002, '2F', '饮品', '学二食堂北侧', 4.55, 180, 88, 8.00, '09:00-21:00', '珍珠奶茶、柠檬茶。', 1, 2, NOW(), NOW()),
    (9006, '清真牛肉面', 9003, '1F', '面食', '学三食堂大厅', 4.70, 66, 95, 16.00, '07:00-20:00', '清真认证，牛肉新鲜。', 1, 1, NOW(), NOW());

INSERT INTO dish (id, name, stall_id, price, category, rating, sold_count, is_available, taste, description, create_time, update_time) VALUES
    (9001, '鲜肉包', 9001, 2.50, '包子', 4.8, 1200, 1, '咸鲜', '皮薄馅大，汁多味美。', NOW(), NOW()),
    (9002, '豆浆', 9001, 1.50, '饮品', 4.6, 950, 1, '微甜', '现磨豆浆。', NOW(), NOW()),
    (9003, '麻辣烫自选', 9002, 18.00, '麻辣烫', 4.5, 890, 1, '麻辣', '多种食材自选。', NOW(), NOW()),
    (9004, '牛肉拉面', 9003, 14.00, '拉面', 4.7, 980, 1, '清淡', '手工拉面，汤鲜面劲。', NOW(), NOW()),
    (9005, '两荤两素套餐', 9004, 13.00, '套餐', 4.4, 720, 1, '家常', '经济实惠。', NOW(), NOW()),
    (9006, '珍珠奶茶', 9005, 8.00, '奶茶', 4.7, 1100, 1, '微甜', '经典珍珠奶茶。', NOW(), NOW()),
    (9007, '清真牛肉面', 9006, 16.00, '拉面', 4.8, 560, 1, '清淡', '汤底醇厚。', NOW(), NOW());

INSERT INTO promotion_coupon
    (id, coupon_name, category, merchant_id, stall_id, facility_id, total_quantity, start_date, end_date, tag_type, pickup_location, description, status, sort_order, is_banner, create_time, update_time)
VALUES
    (9001, '学一食堂满减券', 'coupon', 9001, NULL, 9001, 500, '2026-03-01', '2026-12-31', 'hot', '学一食堂一楼服务台', '堂食满 30 减 5。', 1, 1, 1, NOW(), NOW()),
    (9002, '早餐专享券', 'coupon', 9001, 9001, 9001, 300, '2026-03-01', '2026-12-31', 'new', '早餐包子铺窗口', '早餐时段可用。', 1, 2, 1, NOW(), NOW()),
    (9003, '麻辣烫专享券', 'coupon', 9001, 9002, 9001, 200, '2026-03-15', '2026-12-31', 'recommend', '麻辣烫档口收银台', '麻辣烫档口专用。', 1, 3, 0, NOW(), NOW()),
    (9004, '奶茶专享券', 'coupon', 9002, 9005, 9002, 300, '2026-03-15', '2026-09-30', 'new', '学二食堂奶茶站', '指定饮品可用。', 1, 4, 0, NOW(), NOW());

-- ============================================================
-- 3. 校园活动（java_activity_api）
-- ============================================================
DELETE FROM activity_notice WHERE activity_id BETWEEN 9001 AND 9008;
DELETE FROM activity WHERE id BETWEEN 9001 AND 9008;

INSERT INTO activity_category (id, category_name, sort, status, create_time) VALUES
    (9001, '学术讲座', 1, 1, NOW()),
    (9002, '体育活动', 2, 1, NOW()),
    (9003, '社团活动', 3, 1, NOW()),
    (9004, '志愿活动', 4, 1, NOW())
ON DUPLICATE KEY UPDATE category_name = VALUES(category_name), status = 1;

INSERT INTO activity
    (id, title, cover_image, category_id, organizer_id, organizer_name, content, location, max_people, current_people,
     start_time, end_time, signup_start_time, signup_end_time, sign_in_start_time, sign_in_end_time,
     status, sign_in_type, sign_in_open, requires_audit, cancel_requires_audit, score, contact_name, contact_phone, create_time)
VALUES
    (9001, 'AI 学习工作坊', 'https://picsum.photos/800/600?random=101', 9001, 101, '张老师',
     '面向全校同学的 AI 学习工作坊，讲解常见工具和实践方法。', '图书馆报告厅', 120, 46,
     '2026-09-05 14:00:00', '2026-09-05 17:00:00', '2026-08-25 09:00:00', '2026-09-04 18:00:00',
     '2026-09-05 13:40:00', '2026-09-05 15:30:00', 'PUBLISHED', 1, 0, 1, 1, 1.5, '张老师', '13800138000', NOW()),
    (9002, '秋季篮球联赛', 'https://picsum.photos/800/600?random=102', 9002, 102, '李老师',
     '秋季篮球联赛热身赛，欢迎观赛。', '东区篮球场', 240, 88,
     '2026-09-06 13:00:00', '2026-09-06 18:00:00', '2026-08-20 08:00:00', '2026-09-05 18:00:00',
     '2026-09-06 12:45:00', '2026-09-06 15:00:00', 'PUBLISHED', 1, 0, 0, 0, 1.0, '李老师', '13800138001', NOW()),
    (9003, '社团开放日', 'https://picsum.photos/800/600?random=103', 9003, 101, '张老师',
     '各大社团集中展示招新内容。', '图书馆前广场', 300, 132,
     '2026-09-10 10:00:00', '2026-09-10 16:30:00', '2026-08-28 09:00:00', '2026-09-09 20:00:00',
     '2026-09-10 09:30:00', '2026-09-10 12:30:00', 'PUBLISHED', 1, 0, 0, 0, 0.5, '张老师', '13800138000', NOW()),
    (9004, '心理健康沙龙', 'https://picsum.photos/800/600?random=106', 9001, 102, '李老师',
     '压力管理与情绪疏导交流分享。', '大学生活动中心 201', 90, 21,
     '2026-09-12 15:00:00', '2026-09-12 17:00:00', '2026-08-29 08:00:00', '2026-09-11 18:00:00',
     '2026-09-12 14:40:00', '2026-09-12 16:20:00', 'PUBLISHED', 1, 0, 1, 1, 0.5, '李老师', '13800138001', NOW()),
    (9005, '校园环保志愿行动', 'https://picsum.photos/800/600?random=104', 9004, 101, '张老师',
     '组织志愿者进行校园清洁与垃圾分类宣传。', '校园主干道', 80, 35,
     '2026-09-15 09:00:00', '2026-09-15 12:00:00', '2026-08-29 08:00:00', '2026-09-14 18:00:00',
     '2026-09-15 08:30:00', '2026-09-15 10:30:00', 'PUBLISHED', 1, 0, 1, 0, 1.0, '张老师', '13800138000', NOW());

INSERT INTO activity_notice (id, activity_id, title, content, publisher_id, publisher_name, status, publish_time, create_time, update_time) VALUES
    (9001, 9001, 'AI 工作坊报名提醒', 'AI 学习工作坊名额有限，请尽快通过系统报名。', 101, '张老师', 'PUBLISHED', NOW(), NOW(), NOW()),
    (9002, 9003, '社团开放日预告', '社团开放日将于下周举行，欢迎新生参加。', 101, '张老师', 'PUBLISHED', NOW(), NOW(), NOW());

-- ============================================================
-- 4. 课表（java_schedule_api）— admin(101) 与 zzs(103)
-- ============================================================
DELETE FROM course_schedule WHERE user_id IN (101, 103);

INSERT INTO course_schedule
    (user_id, student_id, academic_year, semester_term, semester_code, course_name, week_range, class_sessions, weekday,
     location, campus, teacher_name, class_code, assessment_type, theory_hours, lab_hours, weekly_hours, total_hours, credit, create_time, update_time)
VALUES
    -- admin 课表
    (101, '2026001001', '2025-2026', 2, '12', '软件工程', '1-16 周', '1-2 节', 1, '明德楼 303', '朝阳校区', '李耀辉', 'SE-01', '考试', 32, 0, 3, 32, 2.0, NOW(), NOW()),
    (101, '2026001001', '2025-2026', 2, '12', '深度学习', '1-16 周', '3-4 节', 1, '明德楼 505', '朝阳校区', '赵明瞻', 'DL-01', '考查', 18, 14, 2, 18, 2.0, NOW(), NOW()),
    (101, '2026001001', '2025-2026', 2, '12', 'Python 程序设计', '1-16 周', '1-2 节', 2, '明德楼 503', '朝阳校区', '范晶晶', 'PY-01', '考查', 20, 12, 2, 20, 2.0, NOW(), NOW()),
    (101, '2026001001', '2025-2026', 2, '12', '网络编程', '1-16 周', '3-4 节', 2, '图书馆机房 3', '朝阳校区', '付江龙', 'NP-01', '考试', 28, 12, 3, 28, 2.5, NOW(), NOW()),
    (101, '2026001001', '2025-2026', 2, '12', 'Linux 系统', '1-16 周', '5-6 节', 3, '明德楼 403', '朝阳校区', '庞慧', 'LX-01', '考查', 24, 8, 3, 24, 2.0, NOW(), NOW()),
    (101, '2026001001', '2025-2026', 2, '12', '形势与政策', '1-16 周', '7-8 节', 4, '明德楼阶梯 110', '朝阳校区', '樊智华', 'POL-01', '考查', 8, 0, 2, 8, 0.3, NOW(), NOW()),
    (101, '2026001001', '2025-2026', 2, '12', '创新创业实践', '1-16 周', '3-4 节', 5, 'A414', '朝阳校区', '王老师', 'INN-01', '考查', 16, 16, 4, 16, 1.0, NOW(), NOW()),
    (101, '2026001001', '2025-2026', 2, '12', '体育（羽毛球）', '1-16 周', '5-6 节', 6, '东区运动场', '朝阳校区', '刘教练', 'PE-01', '考查', 0, 32, 2, 32, 1.0, NOW(), NOW()),
    -- zzs 课表
    (103, '2026003001', '2025-2026', 2, '12', '数据结构', '1-16 周', '1-2 节', 1, '明德楼 407', '朝阳校区', '陈老师', 'DS-01', '考试', 32, 8, 4, 32, 3.0, NOW(), NOW()),
    (103, '2026003001', '2025-2026', 2, '12', '操作系统', '1-16 周', '3-4 节', 1, '明德楼 505', '朝阳校区', '周老师', 'OS-01', '考试', 28, 12, 3, 28, 2.5, NOW(), NOW()),
    (103, '2026003001', '2025-2026', 2, '12', '数据库原理', '1-16 周', '1-2 节', 3, '明德楼 303', '朝阳校区', '孙老师', 'DB-01', '考试', 32, 8, 4, 32, 3.0, NOW(), NOW()),
    (103, '2026003001', '2025-2026', 2, '12', 'Web 开发', '1-16 周', '5-6 节', 3, '图书馆机房 4', '朝阳校区', '林老师', 'WEB-01', '考查', 16, 16, 4, 16, 2.0, NOW(), NOW()),
    (103, '2026003001', '2025-2026', 2, '12', '大学英语', '1-16 周', '3-4 节', 4, '崇德楼 201', '朝阳校区', 'Ms. Li', 'ENG-01', '考试', 32, 0, 2, 32, 2.0, NOW(), NOW()),
    (103, '2026003001', '2025-2026', 2, '12', '概率论', '1-16 周', '1-2 节', 5, '明德楼 113', '朝阳校区', '马老师', 'PROB-01', '考试', 32, 0, 3, 32, 2.5, NOW(), NOW());

-- ============================================================
-- 5. 二手物品（java_secondhand_api）
-- ============================================================
DELETE FROM secondhand_favorite WHERE item_id BETWEEN 9001 AND 9010;
DELETE FROM secondhand_item WHERE id BETWEEN 9001 AND 9010;

INSERT INTO secondhand_item
    (id, user_id, category_id, title, description, images, price, original_price, `condition`, location, view_count, favorite_count, status, create_time, update_time)
VALUES
    (9001, 103, 1, 'iPad Air 4 256G WiFi 版', '2023 年购入，屏幕无划痕，带原装充电器和保护套。', '["https://picsum.photos/800/600?random=201"]', 2800.00, 4999.00, 2, '图书馆门口', 256, 18, 2, NOW(), NOW()),
    (9002, 103, 1, 'AirPods Pro 2 代', '使用两个月，配件齐全，几乎全新。', '["https://picsum.photos/800/600?random=202"]', 1200.00, 1899.00, 2, '松园 1 号楼', 189, 12, 2, NOW(), NOW()),
    (9003, 104, 1, '小米 12 手机 8+256G', '无维修无进水，拍照清晰。', '["https://picsum.photos/800/600?random=203"]', 1500.00, 3699.00, 3, '学校南门', 320, 25, 2, NOW(), NOW()),
    (9004, 104, 2, '考研数学全套资料', '高数线代概率教材及真题集，笔记较少。', '["https://picsum.photos/800/600?random=204"]', 120.00, 350.00, 3, '博学楼', 480, 32, 2, NOW(), NOW()),
    (9005, 105, 2, 'Python 编程从入门到实践', '保存完好，有少量学习笔记。', '["https://picsum.photos/800/600?random=205"]', 35.00, 84.00, 2, '图书馆', 210, 15, 2, NOW(), NOW()),
    (9006, 103, 3, 'Nike Air Force 1 白色 42 码', '穿了五六次，清洗后几乎无痕迹。', '["https://picsum.photos/800/600?random=206"]', 380.00, 799.00, 2, '中心广场', 560, 40, 2, NOW(), NOW()),
    (9007, 105, 4, '美的落地风扇', '去年夏天买的，拆卸方便。', '["https://picsum.photos/800/600?random=207"]', 120.00, 299.00, 2, '松园 2 号楼', 88, 5, 2, NOW(), NOW()),
    (9008, 104, 5, '威尔逊羽毛球拍', '单拍，磅数 22 磅，进攻型。', '["https://picsum.photos/800/600?random=208"]', 150.00, 380.00, 3, '东区运动场', 75, 4, 2, NOW(), NOW());

-- ============================================================
-- 6. 会议（java_meeting_api）— admin(101) 与 zzs(103)
-- ============================================================
DELETE FROM meeting_record WHERE meeting_session_id IN (SELECT id FROM meeting_session WHERE session_id LIKE 'demo-%');
DELETE FROM meeting_participant WHERE meeting_session_id IN (SELECT id FROM meeting_session WHERE session_id LIKE 'demo-%');
DELETE FROM meeting_agent_result WHERE meeting_session_id IN (SELECT id FROM meeting_session WHERE session_id LIKE 'demo-%');
DELETE FROM meeting_session WHERE session_id LIKE 'demo-%';

INSERT INTO meeting_session
    (session_id, room_code, user_id, title, meeting_type, status, scheduled_start_time, expected_duration_minutes, start_time, end_time, last_note, record_count, create_time, update_time)
VALUES
    ('demo-001', 'A1B2C3', 101, '项目进度同步会', 'quick', 'active', NOW(), 60, NOW(), NULL, '讨论本周开发进度与联调计划', 2, NOW(), NOW()),
    ('demo-002', 'D4E5F6', 101, '需求评审会', 'reserved', 'idle', DATE_ADD(NOW(), INTERVAL 4 HOUR), 90, NULL, NULL, NULL, 0, NOW(), NOW()),
    ('demo-003', 'G7H8I9', 101, '项目复盘会', 'reserved', 'ended', DATE_SUB(NOW(), INTERVAL 2 DAY), 60, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY) + INTERVAL 1 HOUR, '复盘联调问题与改进项', 3, DATE_SUB(NOW(), INTERVAL 2 DAY), NOW()),
    ('demo-004', 'J1K2L3', 103, '小组作业讨论', 'quick', 'active', NOW(), 45, NOW(), NULL, '讨论数据库课程设计', 1, NOW(), NOW()),
    ('demo-005', 'M4N5O6', 103, '导师见面预约', 'reserved', 'idle', DATE_ADD(NOW(), INTERVAL 1 DAY), 30, NULL, NULL, NULL, 0, NOW(), NOW());

INSERT INTO meeting_record (meeting_session_id, source, content, create_time)
SELECT id, 'manual', '确认本周联调范围：课表、活动、食堂、二手四个模块。', NOW()
FROM meeting_session WHERE session_id = 'demo-001';

INSERT INTO meeting_record (meeting_session_id, source, content, create_time)
SELECT id, 'manual', '约定明天下午再测会议与设施查询。', NOW()
FROM meeting_session WHERE session_id = 'demo-001';

INSERT INTO meeting_record (meeting_session_id, source, content, create_time)
SELECT id, 'manual', '数据库 ER 图初稿已完成，待评审。', NOW()
FROM meeting_session WHERE session_id = 'demo-004';

SET FOREIGN_KEY_CHECKS = 1;
