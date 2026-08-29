-- 校园论坛 + 校园优惠 演示数据
-- 适配用户：admin=101, wangli=102, zzs=103
-- 可重复执行：先清理 demo 区间再插入
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- 0. 商家（校园优惠页依赖 merchant + discount_activity）
-- ============================================================
DELETE FROM discount_favorite WHERE activity_id BETWEEN 9101 AND 9110;
DELETE FROM discount_claim WHERE activity_id BETWEEN 9101 AND 9110;
DELETE FROM discount_activity WHERE id BETWEEN 9101 AND 9110;

INSERT INTO merchant_category (id, category_name, category_icon, sort, status, create_time) VALUES
    (9001, '餐厅美食', NULL, 1, 1, NOW()),
    (9002, '饮品甜点', NULL, 2, 1, NOW()),
    (9003, '超市便利', NULL, 3, 1, NOW()),
    (9004, '打印复印', NULL, 4, 1, NOW())
ON DUPLICATE KEY UPDATE
    category_name = VALUES(category_name),
    sort = VALUES(sort),
    status = VALUES(status);

INSERT INTO merchant
    (id, merchant_name, category_id, description, logo, address, contact_name, contact_phone, business_hours, user_id, status, view_count, create_time, update_time)
VALUES
    (9001, '学府餐厅', 9001, '学一食堂主力合作商家，提供学生套餐与特价午餐。', 'https://picsum.photos/seed/merchant-9001/200/200', '学一食堂一楼', '李老板', '13812345601', '07:00-21:00', 103, 1, 320, NOW(), NOW()),
    (9002, '书香咖啡', 9002, '图书馆一楼咖啡轻食，适合自习小憩。', 'https://picsum.photos/seed/merchant-9002/200/200', '图书馆一层', '王老板', '13812345602', '08:00-22:00', 102, 1, 210, NOW(), NOW()),
    (9003, '校园便利超市', 9003, '日用品、零食、文具一应俱全。', 'https://picsum.photos/seed/merchant-9003/200/200', '南门内 50 米', '张老板', '13812345603', '07:00-23:00', 101, 1, 480, NOW(), NOW()),
    (9004, '快印图文店', 9004, '打印复印、装订、证件照服务。', 'https://picsum.photos/seed/merchant-9004/200/200', '明德楼地下一层', '赵老板', '13812345604', '08:00-20:00', 101, 1, 156, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    merchant_name = VALUES(merchant_name),
    category_id = VALUES(category_id),
    description = VALUES(description),
    logo = VALUES(logo),
    address = VALUES(address),
    business_hours = VALUES(business_hours),
    status = VALUES(status),
    update_time = NOW();

INSERT INTO discount_activity
    (id, merchant_id, title, description, cover_image, images, start_time, end_time, use_rules, total_count, remain_count, status, create_time)
VALUES
    (9101, 9001, '午餐特价套餐 8 折', '周一至周五 11:00-13:00，两荤一素套餐享 8 折，限量供应。', 'https://picsum.photos/seed/discount-9101/800/500', '["https://picsum.photos/seed/discount-9101a/800/500"]', '2026-03-01 00:00:00', '2026-12-31 23:59:59', '1. 仅限堂食\n2. 不可与其他优惠叠加\n3. 出示领取码给收银员', 200, 136, 1, NOW()),
    (9102, 9001, '新生首单满 30 减 8', '开学季专享，首次消费满 30 元立减 8 元。', 'https://picsum.photos/seed/discount-9102/800/500', '["https://picsum.photos/seed/discount-9102a/800/500"]', '2026-03-01 00:00:00', '2026-12-31 23:59:59', '1. 每人限领 1 次\n2. 需在校内支付', 500, 412, 1, NOW()),
    (9103, 9002, '自习咖啡第二杯半价', '凭学生证领取，美式/拿铁适用。', 'https://picsum.photos/seed/discount-9103/800/500', '["https://picsum.photos/seed/discount-9103a/800/500"]', '2026-03-01 00:00:00', '2026-12-31 23:59:59', '1. 第二杯须同等或更低价位\n2. 不可拆分使用', 300, 188, 1, NOW()),
    (9104, 9002, '周末贝果套餐 15 元', '贝果 + 美式组合，周末全天可用。', 'https://picsum.photos/seed/discount-9104/800/500', '["https://picsum.photos/seed/discount-9104a/800/500"]', '2026-03-01 00:00:00', '2026-12-31 23:59:59', '1. 周六日可用\n2. 每日限量 50 份', 150, 73, 1, NOW()),
    (9105, 9003, '日用品满 50 减 10', '洗护、纸巾、清洁用品满 50 减 10。', 'https://picsum.photos/seed/discount-9105/800/500', '["https://picsum.photos/seed/discount-9105a/800/500"]', '2026-03-01 00:00:00', '2026-12-31 23:59:59', '1. 烟酒除外\n2. 结账前出示优惠码', 400, 265, 1, NOW()),
    (9106, 9003, '深夜泡面加蛋 1 元', '22:00 后购买指定泡面可加蛋 1 元。', 'https://picsum.photos/seed/discount-9106/800/500', '["https://picsum.photos/seed/discount-9106a/800/500"]', '2026-03-01 00:00:00', '2026-12-31 23:59:59', '1. 限 22:00-23:30\n2. 每日限 30 份', 120, 54, 1, NOW()),
    (9107, 9004, '打印满 10 页送 5 页', '黑白打印 A4，适合课程资料批量打印。', 'https://picsum.photos/seed/discount-9107/800/500', '["https://picsum.photos/seed/discount-9107a/800/500"]', '2026-03-01 00:00:00', '2026-12-31 23:59:59', '1. 仅限黑白 A4\n2. 不可与其他活动叠加', 250, 201, 1, NOW()),
    (9108, 9004, '证件照精修 9.9 元', '标准证件照拍摄 + 基础修图，需提前预约。', 'https://picsum.photos/seed/discount-9108/800/500', '["https://picsum.photos/seed/discount-9108a/800/500"]', '2026-03-01 00:00:00', '2026-12-31 23:59:59', '1. 需提前 1 天预约\n2. 每人限 1 次', 80, 31, 1, NOW());

-- ============================================================
-- 1. 校园论坛：话题 / 帖子 / 评论
-- ============================================================
DELETE FROM forum_comment WHERE id BETWEEN 9101 AND 9130;
DELETE FROM forum_like WHERE target_type = 'POST' AND target_id BETWEEN 9101 AND 9120;
DELETE FROM forum_favorite WHERE post_id BETWEEN 9101 AND 9120;
DELETE FROM forum_post WHERE id BETWEEN 9101 AND 9120;
DELETE FROM forum_topic WHERE id BETWEEN 9101 AND 9108;

INSERT INTO forum_topic
    (id, topic_name, topic_icon, description, post_count, is_hot, status, create_time)
VALUES
    (9101, '考研经验', NULL, '分享考研复习计划、资料与心态调整。', 3, 1, 'ACTIVE', NOW()),
    (9102, '实习求职', NULL, '简历、面试、实习内推信息交流。', 2, 1, 'ACTIVE', NOW()),
    (9103, '课程互助', NULL, '作业讨论、课程推荐、学习资源分享。', 2, 0, 'ACTIVE', NOW()),
    (9104, '校园生活', NULL, '宿舍、社团、日常琐事与趣事。', 2, 1, 'ACTIVE', NOW()),
    (9105, '美食推荐', NULL, '食堂档口、周边餐厅与外卖测评。', 2, 1, 'ACTIVE', NOW()),
    (9106, '运动健身', NULL, '跑步、球类、健身房打卡。', 1, 0, 'ACTIVE', NOW()),
    (9107, '二手交流', NULL, '闲置转让、求购与交易经验。', 1, 0, 'ACTIVE', NOW()),
    (9108, '吐槽树洞', NULL, '匿名倾诉、轻松吐槽。', 1, 0, 'ACTIVE', NOW());

INSERT INTO forum_post
    (id, user_id, title, content, images, topic_id, view_count, like_count, comment_count, status, pin_order, highlighted, create_time, update_time)
VALUES
    (9101, 103, '计算机考研 408 怎么分配复习时间？', '目前大三，准备考 408。数据结构已经过完一遍，操作系统和计网还没开始。想问问大家每天大概学多久比较合适？要不要先刷题再看书？', NULL, 9101, 186, 24, 3, 'PUBLISHED', 1, 1, DATE_SUB(NOW(), INTERVAL 2 DAY), NOW()),
    (9102, 101, '分享一份我整理的英语作文模板', '整理了小作文和大作文常用句型，适合冲刺阶段直接背诵。需要的话可以评论区留邮箱，我发 PDF。', '["https://picsum.photos/seed/forum-9102/800/600"]', 9101, 142, 31, 2, 'PUBLISHED', 0, 1, DATE_SUB(NOW(), INTERVAL 3 DAY), NOW()),
    (9103, 102, '跨专业考研值得吗？', '本科工商管理，想转计算机。身边有人劝退，也有人支持。有没有过来人聊聊真实感受？', NULL, 9101, 98, 12, 1, 'PUBLISHED', 0, 0, DATE_SUB(NOW(), INTERVAL 5 DAY), NOW()),
    (9104, 103, '字节跳动日常实习面经（已 OC）', '共三轮：技术面 + 技术面 + HR。重点问了项目经历和手撕链表题。建议提前准备 STAR 法则讲项目。', NULL, 9102, 256, 45, 2, 'PUBLISHED', 0, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), NOW()),
    (9105, 101, '春招简历怎么写项目经历？', '没有大厂实习，只有课程项目和校园比赛，简历会不会太单薄？求模板或修改建议。', '["https://picsum.photos/seed/forum-9105/800/600","https://picsum.photos/seed/forum-9105b/800/600"]', 9102, 167, 19, 1, 'PUBLISHED', 0, 0, DATE_SUB(NOW(), INTERVAL 4 DAY), NOW()),
    (9106, 102, '数据结构这门课有推荐网课吗？', '老师讲得比较快，想找个补充视频。最好是中文、例子多一点的。', NULL, 9103, 88, 8, 1, 'PUBLISHED', 0, 0, DATE_SUB(NOW(), INTERVAL 6 DAY), NOW()),
    (9107, 103, 'Python 期末大作业求助', '要做一个小型 Web 项目，Vue + Spring Boot。有没有类似的开源参考？', NULL, 9103, 73, 6, 0, 'PUBLISHED', 0, 0, DATE_SUB(NOW(), INTERVAL 2 DAY), NOW()),
    (9108, 101, '宿舍断网之后大家都怎么学习？', '最近晚上 11 点断网，手机热点流量撑不住。有没有离线学习资料推荐？', NULL, 9104, 134, 22, 2, 'PUBLISHED', 0, 0, DATE_SUB(NOW(), INTERVAL 1 DAY), NOW()),
    (9109, 102, '社团招新值得参加吗？', '大一加了两个社团，感觉时间被占满。过来人建议保留几个？', NULL, 9104, 91, 14, 0, 'PUBLISHED', 0, 0, DATE_SUB(NOW(), INTERVAL 7 DAY), NOW()),
    (9110, 103, '学一食堂麻辣烫测评', '西侧麻辣烫档口，麻酱给得很足，自选食材新鲜。人均 18 左右，推荐午餐去。', '["https://picsum.photos/seed/forum-9110/800/600"]', 9105, 203, 38, 2, 'PUBLISHED', 0, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), NOW()),
    (9111, 101, '图书馆咖啡角新出的贝果不错', '书香咖啡周末套餐 15 元，贝果外脆内软，配美式刚好。适合复习中途补充能量。', '["https://picsum.photos/seed/forum-9111/800/600"]', 9105, 119, 17, 1, 'PUBLISHED', 0, 0, DATE_SUB(NOW(), INTERVAL 3 DAY), NOW()),
    (9112, 102, '东区篮球场晚上几点人最少？', '想练投篮，但高峰期要排队。有没有固定时间段比较空？', NULL, 9106, 67, 9, 0, 'PUBLISHED', 0, 0, DATE_SUB(NOW(), INTERVAL 4 DAY), NOW()),
    (9113, 103, '出 iPad 平板的交易经验', '在市集出掉了闲置 iPad，建议当面验机、保留聊天记录。校区内交易比较安心。', NULL, 9107, 84, 11, 0, 'PUBLISHED', 0, 0, DATE_SUB(NOW(), INTERVAL 5 DAY), NOW()),
    (9114, 101, '为什么早八永远起不来？', '设了三个闹钟还是迟到。有没有有效的早起办法，树洞一下。', NULL, 9108, 156, 28, 1, 'PUBLISHED', 0, 0, DATE_SUB(NOW(), INTERVAL 2 DAY), NOW());

INSERT INTO forum_comment
    (id, post_id, user_id, parent_id, reply_to_id, content, images, like_count, status, create_time, update_time)
VALUES
    (9101, 9101, 101, NULL, NULL, '建议 408 按数据结构 → 计组 → 操作系统 → 计网顺序，每天 4-6 小时有效学习即可。', NULL, 5, 'NORMAL', DATE_SUB(NOW(), INTERVAL 1 DAY), NOW()),
    (9102, 9101, 102, NULL, NULL, '我去年是先看视频再刷题，错题本很重要。', NULL, 3, 'NORMAL', DATE_SUB(NOW(), INTERVAL 20 HOUR), NOW()),
    (9103, 9101, 103, 9102, 102, '谢谢，我也准备开始刷题了。', NULL, 1, 'NORMAL', DATE_SUB(NOW(), INTERVAL 18 HOUR), NOW()),
    (9104, 9102, 103, NULL, NULL, '求 PDF，邮箱 demo@campus.edu', NULL, 2, 'NORMAL', DATE_SUB(NOW(), INTERVAL 2 DAY), NOW()),
    (9105, 9102, 101, 9104, 103, '已发，注意查收垃圾箱。', NULL, 1, 'NORMAL', DATE_SUB(NOW(), INTERVAL 1 DAY), NOW()),
    (9106, 9104, 101, NULL, NULL, '恭喜 OC！请问技术面大概多少分钟？', NULL, 4, 'NORMAL', DATE_SUB(NOW(), INTERVAL 12 HOUR), NOW()),
    (9107, 9104, 103, 9106, 101, '单面 45-60 分钟，会有追问。', NULL, 2, 'NORMAL', DATE_SUB(NOW(), INTERVAL 10 HOUR), NOW()),
    (9108, 9105, 102, NULL, NULL, '可以把课程项目写完整：背景、职责、技术栈、结果，比堆名词有用。', NULL, 3, 'NORMAL', DATE_SUB(NOW(), INTERVAL 3 DAY), NOW()),
    (9109, 9106, 103, NULL, NULL, '推荐 B 站王道的数据结构，配合教材看。', NULL, 2, 'NORMAL', DATE_SUB(NOW(), INTERVAL 5 DAY), NOW()),
    (9110, 9108, 102, NULL, NULL, '下载离线视频 + PDF，或者提前缓存网课。', NULL, 1, 'NORMAL', DATE_SUB(NOW(), INTERVAL 20 HOUR), NOW()),
    (9111, 9108, 101, 9110, 102, '有道理，我试试提前缓存。', NULL, 0, 'NORMAL', DATE_SUB(NOW(), INTERVAL 18 HOUR), NOW()),
    (9112, 9110, 101, NULL, NULL, '同意，麻酱是灵魂。', NULL, 2, 'NORMAL', DATE_SUB(NOW(), INTERVAL 1 DAY), NOW()),
    (9113, 9110, 102, NULL, NULL, '晚上去可能要排队，建议 11 点前。', NULL, 1, 'NORMAL', DATE_SUB(NOW(), INTERVAL 22 HOUR), NOW()),
    (9114, 9111, 103, NULL, NULL, '周末套餐确实划算，已收藏优惠。', NULL, 1, 'NORMAL', DATE_SUB(NOW(), INTERVAL 2 DAY), NOW()),
    (9115, 9114, 102, NULL, NULL, '把闹钟放远离床的地方，亲测有效。', NULL, 4, 'NORMAL', DATE_SUB(NOW(), INTERVAL 1 DAY), NOW());

SET FOREIGN_KEY_CHECKS = 1;
