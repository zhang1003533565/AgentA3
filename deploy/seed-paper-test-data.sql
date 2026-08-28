-- AgentA3 试卷生成联调数据。
-- 幂等设计：按稳定名称和题目内容判断，重复执行不会产生重复数据。
-- 测试用户：zzs（user_id = 4）。

SET NAMES utf8mb4;
START TRANSACTION;

INSERT INTO question_bank
    (name, subject_id, visibility, owner_id, description, bank_type, create_time, update_time)
SELECT 'A3测试·Python基础公共题库', 1, 'public', NULL,
       '覆盖Python基础语法与常用数据结构，用于公共题库组卷测试。', 'final_review', NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM question_bank WHERE name = 'A3测试·Python基础公共题库' AND visibility = 'public'
);

INSERT INTO question_bank
    (name, subject_id, visibility, owner_id, description, bank_type, create_time, update_time)
SELECT 'A3测试·数据结构公共题库', 5, 'public', NULL,
       '覆盖栈、队列、查找、树和链表，用于多题型试卷预览。', 'chapter_practice', NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM question_bank WHERE name = 'A3测试·数据结构公共题库' AND visibility = 'public'
);

INSERT INTO question_bank
    (name, subject_id, visibility, owner_id, description, bank_type, create_time, update_time)
SELECT 'A3测试·我的Python错题集', 1, 'private', 4,
       'zzs的私有Python练习题，用于验证私有题库权限与组卷。', 'wrong_questions', NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM question_bank WHERE name = 'A3测试·我的Python错题集' AND visibility = 'private' AND owner_id = 4
);

INSERT INTO question_bank
    (name, subject_id, visibility, owner_id, description, bank_type, create_time, update_time)
SELECT 'A3测试·我的数据库练习', 3, 'private', 4,
       'zzs的私有数据库基础题，用于验证收藏和混合组卷。', 'custom', NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM question_bank WHERE name = 'A3测试·我的数据库练习' AND visibility = 'private' AND owner_id = 4
);

SET @public_python_bank = (
    SELECT id FROM question_bank
    WHERE name = 'A3测试·Python基础公共题库' AND visibility = 'public'
    ORDER BY id LIMIT 1
);
SET @public_ds_bank = (
    SELECT id FROM question_bank
    WHERE name = 'A3测试·数据结构公共题库' AND visibility = 'public'
    ORDER BY id LIMIT 1
);
SET @private_python_bank = (
    SELECT id FROM question_bank
    WHERE name = 'A3测试·我的Python错题集' AND visibility = 'private' AND owner_id = 4
    ORDER BY id LIMIT 1
);
SET @private_db_bank = (
    SELECT id FROM question_bank
    WHERE name = 'A3测试·我的数据库练习' AND visibility = 'private' AND owner_id = 4
    ORDER BY id LIMIT 1
);

-- 公共题库：Python基础（6题，覆盖全部主要题型）。
INSERT INTO question
    (bank_id, subject_id, subject, chapter, knowledge_point, question_type, difficulty,
     content, options, answer, analysis, creator_id, create_time, update_time)
SELECT @public_python_bank, 1, 'Python程序设计', '基础语法', '函数定义', '单选题', '简单',
       'Python中用于定义普通函数的关键字是？', '["function","def","func","lambda"]', 'B',
       'def用于定义普通函数；lambda用于创建匿名函数。', 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM question WHERE bank_id = @public_python_bank AND content = 'Python中用于定义普通函数的关键字是？');

INSERT INTO question
    (bank_id, subject_id, subject, chapter, knowledge_point, question_type, difficulty,
     content, options, answer, analysis, creator_id, create_time, update_time)
SELECT @public_python_bank, 1, 'Python程序设计', '数据类型', '不可变对象', '多选题', '中等',
       '下列哪些属于Python不可变数据类型？', '["列表","元组","字符串","字典"]', 'B、C',
       '元组和字符串创建后不能原地修改；列表和字典属于可变对象。', 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM question WHERE bank_id = @public_python_bank AND content = '下列哪些属于Python不可变数据类型？');

INSERT INTO question
    (bank_id, subject_id, subject, chapter, knowledge_point, question_type, difficulty,
     content, options, answer, analysis, creator_id, create_time, update_time)
SELECT @public_python_bank, 1, 'Python程序设计', '容器类型', '列表', '判断题', '简单',
       'Python列表是可变对象，可以在原列表上追加或删除元素。', NULL, '正确',
       'append、extend、remove等操作都会修改原列表。', 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM question WHERE bank_id = @public_python_bank AND content = 'Python列表是可变对象，可以在原列表上追加或删除元素。');

INSERT INTO question
    (bank_id, subject_id, subject, chapter, knowledge_point, question_type, difficulty,
     content, options, answer, analysis, creator_id, create_time, update_time)
SELECT @public_python_bank, 1, 'Python程序设计', '内置函数', 'len函数', '填空题', '简单',
       '表达式len([10, 20, 30])的计算结果为____。', NULL, '3',
       '列表中包含三个元素，因此长度为3。', 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM question WHERE bank_id = @public_python_bank AND content = '表达式len([10, 20, 30])的计算结果为____。');

INSERT INTO question
    (bank_id, subject_id, subject, chapter, knowledge_point, question_type, difficulty,
     content, options, answer, analysis, creator_id, create_time, update_time)
SELECT @public_python_bank, 1, 'Python程序设计', '数据类型', '列表与元组', '简答题', '中等',
       '简述Python列表与元组的主要区别，并各举一个适用场景。', NULL,
       '列表可变，适合保存需要增删改的数据；元组不可变，适合表达固定记录或作为字典键。',
       '答案应说明可变性差异，并给出合理使用场景。', 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM question WHERE bank_id = @public_python_bank AND content = '简述Python列表与元组的主要区别，并各举一个适用场景。');

INSERT INTO question
    (bank_id, subject_id, subject, chapter, knowledge_point, question_type, difficulty,
     content, options, answer, analysis, creator_id, create_time, update_time)
SELECT @public_python_bank, 1, 'Python程序设计', '函数与字符串', '回文判断', '编程题', '困难',
       '编写函数is_palindrome(text)，忽略大小写判断字符串是否为回文，返回布尔值。', NULL,
       'def is_palindrome(text):\n    value = text.lower()\n    return value == value[::-1]',
       '将字符串统一为小写后与其反转结果比较。', 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM question WHERE bank_id = @public_python_bank AND content = '编写函数is_palindrome(text)，忽略大小写判断字符串是否为回文，返回布尔值。');

-- 公共题库：数据结构（6题）。
INSERT INTO question
    (bank_id, subject_id, subject, chapter, knowledge_point, question_type, difficulty,
     content, options, answer, analysis, creator_id, create_time, update_time)
SELECT @public_ds_bank, 5, '数据结构', '线性结构', '队列', '单选题', '简单',
       '队列通常遵循哪一种元素访问原则？', '["先进先出","后进先出","随机访问","按关键字访问"]', 'A',
       '队列从队尾入队、队头出队，遵循FIFO原则。', 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM question WHERE bank_id = @public_ds_bank AND content = '队列通常遵循哪一种元素访问原则？');

INSERT INTO question
    (bank_id, subject_id, subject, chapter, knowledge_point, question_type, difficulty,
     content, options, answer, analysis, creator_id, create_time, update_time)
SELECT @public_ds_bank, 5, '数据结构', '树', '二叉搜索树', '多选题', '中等',
       '关于二叉搜索树，下列说法正确的有？', '["左子树键值通常小于根节点","右子树键值通常大于根节点","中序遍历结果有序","所有节点都必须有两个孩子"]', 'A、B、C',
       '二叉搜索树不要求每个节点都有两个孩子。', 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM question WHERE bank_id = @public_ds_bank AND content = '关于二叉搜索树，下列说法正确的有？');

INSERT INTO question
    (bank_id, subject_id, subject, chapter, knowledge_point, question_type, difficulty,
     content, options, answer, analysis, creator_id, create_time, update_time)
SELECT @public_ds_bank, 5, '数据结构', '递归', '运行时栈', '判断题', '中等',
       '递归调用通常会借助运行时栈保存每一层调用的局部状态。', NULL, '正确',
       '每次函数调用都会形成新的栈帧，返回时按相反顺序出栈。', 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM question WHERE bank_id = @public_ds_bank AND content = '递归调用通常会借助运行时栈保存每一层调用的局部状态。');

INSERT INTO question
    (bank_id, subject_id, subject, chapter, knowledge_point, question_type, difficulty,
     content, options, answer, analysis, creator_id, create_time, update_time)
SELECT @public_ds_bank, 5, '数据结构', '查找', '二分查找', '填空题', '中等',
       '在有序数组中，二分查找的平均时间复杂度为____。', NULL, 'O(log n)',
       '每次比较都将搜索区间缩小约一半。', 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM question WHERE bank_id = @public_ds_bank AND content = '在有序数组中，二分查找的平均时间复杂度为____。');

INSERT INTO question
    (bank_id, subject_id, subject, chapter, knowledge_point, question_type, difficulty,
     content, options, answer, analysis, creator_id, create_time, update_time)
SELECT @public_ds_bank, 5, '数据结构', '散列表', '哈希冲突', '简答题', '中等',
       '什么是哈希冲突？请写出两种常见的冲突解决方法。', NULL,
       '不同关键字映射到同一哈希地址称为哈希冲突；常见方法有开放定址法和链地址法。',
       '答案包含冲突定义以及任意两种合理解决方法即可。', 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM question WHERE bank_id = @public_ds_bank AND content = '什么是哈希冲突？请写出两种常见的冲突解决方法。');

INSERT INTO question
    (bank_id, subject_id, subject, chapter, knowledge_point, question_type, difficulty,
     content, options, answer, analysis, creator_id, create_time, update_time)
SELECT @public_ds_bank, 5, '数据结构', '链表', '单链表反转', '编程题', '困难',
       '编写伪代码或程序，将一个单链表原地反转并返回新的头节点。', NULL,
       'prev = null\ncur = head\nwhile cur != null:\n    next = cur.next\n    cur.next = prev\n    prev = cur\n    cur = next\nreturn prev',
       '遍历过程中保存后继节点，并逐个反转next指针。', 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM question WHERE bank_id = @public_ds_bank AND content = '编写伪代码或程序，将一个单链表原地反转并返回新的头节点。');

-- 私有题库：zzs的Python错题集（4题）。
INSERT INTO question
    (bank_id, subject_id, subject, chapter, knowledge_point, question_type, difficulty,
     content, options, answer, analysis, creator_id, create_time, update_time)
SELECT @private_python_bank, 1, 'Python程序设计', '序列', 'range函数', '单选题', '简单',
       'list(range(1, 5))包含多少个整数？', '["3","4","5","6"]', 'B',
       'range左闭右开，结果为1、2、3、4，共4个整数。', 4, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM question WHERE bank_id = @private_python_bank AND content = 'list(range(1, 5))包含多少个整数？');

INSERT INTO question
    (bank_id, subject_id, subject, chapter, knowledge_point, question_type, difficulty,
     content, options, answer, analysis, creator_id, create_time, update_time)
SELECT @private_python_bank, 1, 'Python程序设计', '字典', '字典方法', '多选题', '中等',
       '下列哪些是Python字典对象的常用方法？', '["keys","values","items","append"]', 'A、B、C',
       'append是列表方法，字典常用keys、values和items访问视图。', 4, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM question WHERE bank_id = @private_python_bank AND content = '下列哪些是Python字典对象的常用方法？');

INSERT INTO question
    (bank_id, subject_id, subject, chapter, knowledge_point, question_type, difficulty,
     content, options, answer, analysis, creator_id, create_time, update_time)
SELECT @private_python_bank, 1, 'Python程序设计', '推导式', '列表推导式', '填空题', '中等',
       '表达式[x * x for x in range(3)]的结果为____。', NULL, '[0, 1, 4]',
       'range(3)产生0、1、2，分别平方得到0、1、4。', 4, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM question WHERE bank_id = @private_python_bank AND content = '表达式[x * x for x in range(3)]的结果为____。');

INSERT INTO question
    (bank_id, subject_id, subject, chapter, knowledge_point, question_type, difficulty,
     content, options, answer, analysis, creator_id, create_time, update_time)
SELECT @private_python_bank, 1, 'Python程序设计', '综合应用', '词频统计', '编程题', '困难',
       '编写函数word_count(words)，返回每个字符串在列表中出现次数的字典。', NULL,
       'def word_count(words):\n    result = {}\n    for word in words:\n        result[word] = result.get(word, 0) + 1\n    return result',
       '使用字典get方法读取已有计数，不存在时从0开始。', 4, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM question WHERE bank_id = @private_python_bank AND content = '编写函数word_count(words)，返回每个字符串在列表中出现次数的字典。');

-- 私有题库：zzs的数据库练习（4题）。
INSERT INTO question
    (bank_id, subject_id, subject, chapter, knowledge_point, question_type, difficulty,
     content, options, answer, analysis, creator_id, create_time, update_time)
SELECT @private_db_bank, 3, '数据库', 'SQL基础', '聚合查询', '单选题', '简单',
       'SQL中用于统计结果行数的聚合函数是？', '["SUM","COUNT","AVG","MAX"]', 'B',
       'COUNT用于统计行数或非NULL值数量。', 4, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM question WHERE bank_id = @private_db_bank AND content = 'SQL中用于统计结果行数的聚合函数是？');

INSERT INTO question
    (bank_id, subject_id, subject, chapter, knowledge_point, question_type, difficulty,
     content, options, answer, analysis, creator_id, create_time, update_time)
SELECT @private_db_bank, 3, '数据库', '事务', 'ACID特性', '多选题', '中等',
       '关系数据库事务的ACID特性包括哪些？', '["原子性","一致性","隔离性","持久性"]', 'A、B、C、D',
       'ACID分别代表Atomicity、Consistency、Isolation、Durability。', 4, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM question WHERE bank_id = @private_db_bank AND content = '关系数据库事务的ACID特性包括哪些？');

INSERT INTO question
    (bank_id, subject_id, subject, chapter, knowledge_point, question_type, difficulty,
     content, options, answer, analysis, creator_id, create_time, update_time)
SELECT @private_db_bank, 3, '数据库', '关系模型', '主键约束', '判断题', '简单',
       '关系表的主键列可以包含NULL值。', NULL, '错误',
       '主键必须唯一且非空。', 4, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM question WHERE bank_id = @private_db_bank AND content = '关系表的主键列可以包含NULL值。');

INSERT INTO question
    (bank_id, subject_id, subject, chapter, knowledge_point, question_type, difficulty,
     content, options, answer, analysis, creator_id, create_time, update_time)
SELECT @private_db_bank, 3, '数据库', '索引', '索引设计', '简答题', '困难',
       '简述数据库索引对查询和写入性能的主要影响。', NULL,
       '索引通常减少查询扫描量并加快检索，但会占用额外空间，且插入、更新、删除时需要维护索引，因此可能降低写入性能。',
       '需要同时说明查询收益以及空间和写入维护成本。', 4, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM question WHERE bank_id = @private_db_bank AND content = '简述数据库索引对查询和写入性能的主要影响。');

-- 私有题库数量由question_bank_item统计，因此为每道私有题建立真实关联。
INSERT INTO question_bank_item (bank_id, question_id, added_by, create_time)
SELECT q.bank_id, q.id, 4, NOW()
FROM question q
WHERE q.bank_id IN (@private_python_bank, @private_db_bank)
  AND NOT EXISTS (
      SELECT 1 FROM question_bank_item item
      WHERE item.bank_id = q.bank_id AND item.question_id = q.id
  );

-- zzs收藏夹：同时收藏公共题和自己的私有题，验证权限及混合组卷。
INSERT INTO question_favorite (user_id, question_id, create_time)
SELECT 4, q.id, NOW()
FROM question q
WHERE q.content IN (
    'Python中用于定义普通函数的关键字是？',
    '简述Python列表与元组的主要区别，并各举一个适用场景。',
    '队列通常遵循哪一种元素访问原则？',
    '什么是哈希冲突？请写出两种常见的冲突解决方法。',
    '表达式[x * x for x in range(3)]的结果为____。',
    '关系数据库事务的ACID特性包括哪些？'
)
AND NOT EXISTS (
    SELECT 1 FROM question_favorite favorite
    WHERE favorite.user_id = 4 AND favorite.question_id = q.id
);

COMMIT;
