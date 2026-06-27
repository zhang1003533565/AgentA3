# 试卷题型 JSON 规范

本文档用于统一试卷、题库、智能体出题结果的数据格式。后续所有出题智能体建议只输出合法 JSON，不输出 Markdown、解释性文字或代码块。

## 1. 设计目标

- 数据库好落表：通用字段放主表，题型差异放 JSON 字段。
- 前端好渲染：所有题目都有统一外壳，不同题型只读取 `body`、`answer`、`scoring`。
- 智能体好遵守：每个题型都有明确的输出结构、答案结构和评分结构。
- 后续好扩展：新增题型时不破坏已有数据，只扩展 `type` 和题型专属 JSON。

## 2. 题型枚举

| type | 中文名 | 说明 |
| --- | --- | --- |
| `single_choice` | 单选题 | 多个选项，只有一个正确答案 |
| `multiple_choice` | 多选题 | 多个选项，存在多个正确答案 |
| `true_false` | 判断题 | 判断陈述是否正确 |
| `fill_blank` | 填空题 | 一个或多个空，每个空有标准答案或同义答案 |
| `short_answer` | 简答题 | 按答案要点评分 |
| `essay` | 论述题/作文题 | 长文本作答，按评分维度评分 |
| `material_analysis` | 材料分析题/阅读理解题 | 一段材料下面包含多个子题 |
| `calculation` | 计算题/解答题 | 包含计算过程、步骤和最终结果 |
| `proof` | 证明题 | 包含证明目标、证明步骤和结论 |
| `programming` | 编程题 | 包含输入输出、约束、样例、测试用例 |
| `operation` | 操作题/实践题 | 要求完成某个操作、实验或上传成果 |
| `matching` | 匹配题/连线题 | 左右两组内容进行匹配 |
| `ordering` | 排序题 | 将若干项目按要求排序 |
| `cloze` | 完形填空/选词填空 | 一段文本中多个空，常带候选词或选项 |

第一版数据库和智能体建议优先支持：`single_choice`、`multiple_choice`、`true_false`、`fill_blank`、`short_answer`、`material_analysis`、`calculation`、`programming`。

## 3. 统一题目结构

所有题型都使用下面的统一外壳。

```json
{
  "id": "Q1",
  "type": "single_choice",
  "stem": "题干文本",
  "score": 2,
  "difficulty": "easy",
  "knowledgePoints": ["知识点1", "知识点2"],
  "tags": ["章节", "考点"],
  "body": {},
  "answer": {},
  "analysis": "解析文本",
  "scoring": {
    "mode": "exact",
    "rubrics": []
  },
  "sourceBasis": ["生成依据或教材原文摘要"]
}
```

### 3.1 通用字段说明

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | string | 是 | 题目临时 ID，可用 `Q1`、`SC1`、UUID |
| `type` | string | 是 | 题型枚举 |
| `stem` | string | 是 | 题干，材料题的子题也要有自己的题干 |
| `score` | number | 是 | 题目总分 |
| `difficulty` | string | 是 | `easy`、`medium`、`hard` |
| `knowledgePoints` | string[] | 否 | 关联知识点 |
| `tags` | string[] | 否 | 章节、能力层级、用途等标签 |
| `body` | object | 是 | 题型专属内容，如选项、空位、材料、样例 |
| `answer` | object | 是 | 标准答案 |
| `analysis` | string | 否 | 解析 |
| `scoring` | object | 是 | 评分方式 |
| `sourceBasis` | string[] | 否 | 智能体生成依据，便于防幻觉和追溯 |

### 3.2 difficulty 枚举

```json
["easy", "medium", "hard"]
```

### 3.3 scoring.mode 枚举

| mode | 说明 |
| --- | --- |
| `exact` | 精确匹配，常用于选择、判断、排序 |
| `blank` | 填空判分，支持同义答案 |
| `rubric` | 按评分点给分，常用于简答、论述、材料分析 |
| `step` | 按步骤给分，常用于计算、证明 |
| `program` | 编程题按测试用例或规则评分 |
| `manual` | 需要人工评分，系统只保存参考答案和评分建议 |

## 4. 智能体输出总格式

单个题型智能体和组卷智能体都建议输出同一个顶层结构。

```json
{
  "questions": [],
  "missingInfo": []
}
```

字段说明：

- `questions`：题目数组，每个元素必须符合统一题目结构。
- `missingInfo`：生成失败或资料不足时说明缺什么；资料足够时返回空数组。

规则：

- 只输出合法 JSON。
- 不要输出 Markdown。
- 不要输出代码块标记。
- 不要把答案藏在解析里，答案必须放入 `answer`。
- `score` 必须等于 `scoring.rubrics` 或步骤分之和；客观题可以没有 rubrics。
- 如果依据不足，返回 `"questions": []`，并在 `missingInfo` 中说明原因。

## 5. 各题型 JSON 示例

### 5.1 单选题 single_choice

```json
{
  "id": "SC1",
  "type": "single_choice",
  "stem": "栈的特点是以下哪一项？",
  "score": 2,
  "difficulty": "easy",
  "knowledgePoints": ["栈"],
  "tags": ["数据结构"],
  "body": {
    "options": [
      { "key": "A", "text": "先进先出" },
      { "key": "B", "text": "后进先出" },
      { "key": "C", "text": "随机访问" },
      { "key": "D", "text": "按关键字排序" }
    ],
    "shuffleOptions": false
  },
  "answer": {
    "correctOption": "B"
  },
  "analysis": "栈只允许在栈顶进行插入和删除，遵循后进先出。",
  "scoring": {
    "mode": "exact",
    "rubrics": []
  },
  "sourceBasis": ["栈遵循后进先出。"]
}
```

### 5.2 多选题 multiple_choice

```json
{
  "id": "MC1",
  "type": "multiple_choice",
  "stem": "关于队列，下列说法正确的是哪些？",
  "score": 4,
  "difficulty": "medium",
  "knowledgePoints": ["队列"],
  "tags": ["数据结构"],
  "body": {
    "options": [
      { "key": "A", "text": "队列通常在队尾插入元素" },
      { "key": "B", "text": "队列通常在队头删除元素" },
      { "key": "C", "text": "队列一定支持随机访问" },
      { "key": "D", "text": "队列遵循先进先出" }
    ],
    "shuffleOptions": false,
    "partialCredit": true
  },
  "answer": {
    "correctOptions": ["A", "B", "D"]
  },
  "analysis": "队列是先进先出的线性结构，常见操作是队尾入队、队头出队。",
  "scoring": {
    "mode": "exact",
    "rubrics": [
      { "criterion": "全选正确", "score": 4 },
      { "criterion": "少选且无错选，按正确项比例给分", "score": 2 },
      { "criterion": "出现错选", "score": 0 }
    ]
  },
  "sourceBasis": ["队列只允许在队尾插入、队头删除。"]
}
```

### 5.3 判断题 true_false

```json
{
  "id": "TF1",
  "type": "true_false",
  "stem": "循环队列可以复用数组空间。",
  "score": 2,
  "difficulty": "easy",
  "knowledgePoints": ["循环队列"],
  "tags": ["数据结构"],
  "body": {
    "statement": "循环队列可以复用数组空间。"
  },
  "answer": {
    "correct": true
  },
  "analysis": "循环队列通过取模方式复用数组空间。",
  "scoring": {
    "mode": "exact",
    "rubrics": []
  },
  "sourceBasis": ["循环队列可复用数组空间。"]
}
```

### 5.4 填空题 fill_blank

```json
{
  "id": "FB1",
  "type": "fill_blank",
  "stem": "栈遵循____原则，队列遵循____原则。",
  "score": 4,
  "difficulty": "easy",
  "knowledgePoints": ["栈", "队列"],
  "tags": ["数据结构"],
  "body": {
    "text": "栈遵循{{blank_1}}原则，队列遵循{{blank_2}}原则。",
    "blanks": [
      { "id": "blank_1", "index": 1, "score": 2 },
      { "id": "blank_2", "index": 2, "score": 2 }
    ]
  },
  "answer": {
    "blanks": [
      { "id": "blank_1", "answers": ["后进先出", "LIFO"] },
      { "id": "blank_2", "answers": ["先进先出", "FIFO"] }
    ],
    "caseSensitive": false
  },
  "analysis": "栈是 LIFO，队列是 FIFO。",
  "scoring": {
    "mode": "blank",
    "rubrics": [
      { "criterion": "blank_1 正确", "score": 2 },
      { "criterion": "blank_2 正确", "score": 2 }
    ]
  },
  "sourceBasis": ["栈是后进先出，队列是先进先出。"]
}
```

### 5.5 简答题 short_answer

```json
{
  "id": "SA1",
  "type": "short_answer",
  "stem": "简述栈和队列的主要区别。",
  "score": 8,
  "difficulty": "medium",
  "knowledgePoints": ["栈", "队列"],
  "tags": ["数据结构"],
  "body": {
    "answerLengthHint": "80-150字"
  },
  "answer": {
    "referenceAnswer": "栈和队列都是操作受限的线性表。栈只允许在栈顶插入和删除，遵循后进先出；队列通常在队尾插入、队头删除，遵循先进先出。",
    "answerPoints": [
      "说明二者都是操作受限的线性表",
      "说明栈的操作位置和后进先出特点",
      "说明队列的操作位置和先进先出特点"
    ]
  },
  "analysis": "答题重点在操作位置和数据进出顺序。",
  "scoring": {
    "mode": "rubric",
    "rubrics": [
      { "criterion": "指出二者都是操作受限的线性表", "score": 2 },
      { "criterion": "说明栈的后进先出", "score": 3 },
      { "criterion": "说明队列的先进先出", "score": 3 }
    ]
  },
  "sourceBasis": ["栈和队列都是操作受限的线性表。"]
}
```

### 5.6 论述题 essay

```json
{
  "id": "ES1",
  "type": "essay",
  "stem": "结合实际场景，论述队列在任务调度中的作用。",
  "score": 15,
  "difficulty": "hard",
  "knowledgePoints": ["队列", "任务调度"],
  "tags": ["综合应用"],
  "body": {
    "answerLengthHint": "300-500字",
    "requirements": [
      "说明队列的先进先出特点",
      "结合至少一个任务调度场景",
      "分析使用队列的优势"
    ]
  },
  "answer": {
    "referenceAnswer": "可从任务排队、请求处理、打印队列等场景展开，说明队列按照到达顺序组织任务，有助于保证处理顺序和系统公平性。",
    "keyPoints": [
      "解释先进先出",
      "给出合理场景",
      "说明顺序性、公平性或缓冲作用"
    ]
  },
  "analysis": "论述题不要求答案完全一致，但必须围绕队列特性和应用场景展开。",
  "scoring": {
    "mode": "rubric",
    "rubrics": [
      { "criterion": "概念准确", "score": 5 },
      { "criterion": "场景合理", "score": 5 },
      { "criterion": "分析完整", "score": 5 }
    ]
  },
  "sourceBasis": ["队列常用于任务排队。"]
}
```

### 5.7 材料分析题 material_analysis

材料题本身可以作为一道大题，子题放在 `body.subQuestions` 中。每个子题仍然使用统一题目结构。

```json
{
  "id": "MA1",
  "type": "material_analysis",
  "stem": "阅读材料并回答问题。",
  "score": 12,
  "difficulty": "medium",
  "knowledgePoints": ["栈", "队列"],
  "tags": ["综合题"],
  "body": {
    "material": "某系统需要处理用户请求，请求按到达顺序排队；同时系统需要检查表达式中的括号是否匹配。",
    "subQuestions": [
      {
        "id": "MA1-1",
        "type": "single_choice",
        "stem": "处理用户请求更适合使用哪种结构？",
        "score": 3,
        "difficulty": "easy",
        "knowledgePoints": ["队列"],
        "tags": ["材料题"],
        "body": {
          "options": [
            { "key": "A", "text": "栈" },
            { "key": "B", "text": "队列" },
            { "key": "C", "text": "树" },
            { "key": "D", "text": "图" }
          ],
          "shuffleOptions": false
        },
        "answer": {
          "correctOption": "B"
        },
        "analysis": "请求按到达顺序处理，符合队列先进先出。",
        "scoring": {
          "mode": "exact",
          "rubrics": []
        },
        "sourceBasis": ["请求按到达顺序排队。"]
      },
      {
        "id": "MA1-2",
        "type": "short_answer",
        "stem": "为什么括号匹配常使用栈？",
        "score": 9,
        "difficulty": "medium",
        "knowledgePoints": ["栈"],
        "tags": ["材料题"],
        "body": {
          "answerLengthHint": "60-120字"
        },
        "answer": {
          "referenceAnswer": "括号匹配需要最近出现的左括号优先与当前右括号匹配，符合栈后进先出的特点。",
          "answerPoints": [
            "说明左括号入栈",
            "说明右括号与栈顶匹配",
            "说明后进先出适合最近匹配"
          ]
        },
        "analysis": "括号匹配体现了栈的后进先出。",
        "scoring": {
          "mode": "rubric",
          "rubrics": [
            { "criterion": "说明左括号入栈", "score": 3 },
            { "criterion": "说明右括号匹配栈顶", "score": 3 },
            { "criterion": "解释后进先出原因", "score": 3 }
          ]
        },
        "sourceBasis": ["栈常用于括号匹配。"]
      }
    ]
  },
  "answer": {
    "subQuestionAnswers": ["MA1-1", "MA1-2"]
  },
  "analysis": "材料题总解析可概括材料中的结构选择。",
  "scoring": {
    "mode": "rubric",
    "rubrics": [
      { "criterion": "子题 MA1-1", "score": 3 },
      { "criterion": "子题 MA1-2", "score": 9 }
    ]
  },
  "sourceBasis": ["材料明确给出请求排队和括号匹配两个场景。"]
}
```

### 5.8 计算题 calculation

```json
{
  "id": "CA1",
  "type": "calculation",
  "stem": "设循环队列容量为 6，front=2，rear=5，执行一次入队后 rear 的值是多少？",
  "score": 6,
  "difficulty": "medium",
  "knowledgePoints": ["循环队列"],
  "tags": ["计算题"],
  "body": {
    "given": ["容量为 6", "front=2", "rear=5"],
    "requirements": ["计算执行一次入队后的 rear 值"]
  },
  "answer": {
    "finalAnswer": "0",
    "steps": [
      "循环队列 rear 更新公式为 (rear + 1) % capacity",
      "代入 rear=5，capacity=6",
      "(5 + 1) % 6 = 0"
    ]
  },
  "analysis": "循环队列通过取模实现下标回绕。",
  "scoring": {
    "mode": "step",
    "rubrics": [
      { "criterion": "写出更新公式", "score": 2 },
      { "criterion": "正确代入数据", "score": 2 },
      { "criterion": "结果正确", "score": 2 }
    ]
  },
  "sourceBasis": ["循环队列队满条件和下标更新使用取模。"]
}
```

### 5.9 证明题 proof

```json
{
  "id": "PR1",
  "type": "proof",
  "stem": "证明：若栈 S 按序压入 1、2、3，则出栈序列 3、2、1 是可能的。",
  "score": 8,
  "difficulty": "medium",
  "knowledgePoints": ["栈", "出栈序列"],
  "tags": ["证明题"],
  "body": {
    "proposition": "出栈序列 3、2、1 是可能的",
    "conditions": ["按序压入 1、2、3"]
  },
  "answer": {
    "proofSteps": [
      "依次压入 1、2、3",
      "此时栈顶为 3，弹出得到 3",
      "继续弹出得到 2",
      "继续弹出得到 1",
      "因此出栈序列 3、2、1 是可能的"
    ],
    "conclusion": "命题成立"
  },
  "analysis": "证明关键是构造一组合法的压栈和出栈操作。",
  "scoring": {
    "mode": "step",
    "rubrics": [
      { "criterion": "说明合法压栈过程", "score": 3 },
      { "criterion": "说明合法出栈过程", "score": 3 },
      { "criterion": "结论明确", "score": 2 }
    ]
  },
  "sourceBasis": ["栈遵循后进先出。"]
}
```

### 5.10 编程题 programming

```json
{
  "id": "PG1",
  "type": "programming",
  "stem": "编写程序判断括号字符串是否匹配。",
  "score": 20,
  "difficulty": "hard",
  "knowledgePoints": ["栈", "括号匹配"],
  "tags": ["编程题"],
  "body": {
    "title": "括号匹配",
    "description": "给定只包含小括号、中括号和大括号的字符串，判断括号是否正确匹配。",
    "language": "未指定",
    "inputFormat": "一行字符串 s。",
    "outputFormat": "若匹配输出 true，否则输出 false。",
    "constraints": ["1 <= s.length <= 10000"],
    "examples": [
      {
        "input": "{[()]}",
        "output": "true",
        "explanation": "所有括号均正确匹配。"
      }
    ]
  },
  "answer": {
    "solutionOutline": [
      "遇到左括号入栈",
      "遇到右括号时检查栈顶是否匹配",
      "扫描结束后栈为空则匹配"
    ],
    "referenceSolution": "",
    "testCases": [
      { "input": "{[()]}", "expectedOutput": "true", "hidden": false },
      { "input": "([)]", "expectedOutput": "false", "hidden": false },
      { "input": "(((", "expectedOutput": "false", "hidden": true }
    ]
  },
  "analysis": "该题考查栈的后进先出特点。",
  "scoring": {
    "mode": "program",
    "rubrics": [
      { "criterion": "算法思路正确", "score": 6 },
      { "criterion": "边界情况处理正确", "score": 6 },
      { "criterion": "通过测试用例", "score": 8 }
    ]
  },
  "sourceBasis": ["括号匹配算法常使用栈。"]
}
```

### 5.11 操作题 operation

```json
{
  "id": "OP1",
  "type": "operation",
  "stem": "在数据库管理工具中创建一张学生表，并截图提交。",
  "score": 10,
  "difficulty": "medium",
  "knowledgePoints": ["数据库表设计"],
  "tags": ["实践题"],
  "body": {
    "task": "创建 student 表",
    "requirements": [
      "包含 id、name、student_no、created_at 字段",
      "id 为主键",
      "student_no 唯一"
    ],
    "submissionType": "image",
    "allowedFileTypes": ["png", "jpg", "jpeg"]
  },
  "answer": {
    "expectedResult": "截图中能看到 student 表及指定字段、主键和唯一约束。"
  },
  "analysis": "操作题重点检查结果是否满足要求。",
  "scoring": {
    "mode": "manual",
    "rubrics": [
      { "criterion": "字段完整", "score": 4 },
      { "criterion": "主键正确", "score": 3 },
      { "criterion": "唯一约束正确", "score": 3 }
    ]
  },
  "sourceBasis": []
}
```

### 5.12 匹配题 matching

```json
{
  "id": "MT1",
  "type": "matching",
  "stem": "将数据结构与其特点进行匹配。",
  "score": 6,
  "difficulty": "easy",
  "knowledgePoints": ["栈", "队列"],
  "tags": ["匹配题"],
  "body": {
    "leftItems": [
      { "key": "L1", "text": "栈" },
      { "key": "L2", "text": "队列" }
    ],
    "rightItems": [
      { "key": "R1", "text": "先进先出" },
      { "key": "R2", "text": "后进先出" }
    ]
  },
  "answer": {
    "pairs": [
      { "left": "L1", "right": "R2" },
      { "left": "L2", "right": "R1" }
    ]
  },
  "analysis": "栈对应后进先出，队列对应先进先出。",
  "scoring": {
    "mode": "exact",
    "rubrics": [
      { "criterion": "每组匹配正确", "score": 3 }
    ]
  },
  "sourceBasis": ["栈是后进先出，队列是先进先出。"]
}
```

### 5.13 排序题 ordering

```json
{
  "id": "OR1",
  "type": "ordering",
  "stem": "将顺序栈入栈操作步骤排序。",
  "score": 6,
  "difficulty": "medium",
  "knowledgePoints": ["顺序栈"],
  "tags": ["排序题"],
  "body": {
    "items": [
      { "key": "A", "text": "判断栈是否已满" },
      { "key": "B", "text": "top 加 1" },
      { "key": "C", "text": "将新元素放入 top 所指位置" }
    ]
  },
  "answer": {
    "orderedKeys": ["A", "B", "C"]
  },
  "analysis": "入栈前先判满，再移动栈顶指针，最后写入元素。",
  "scoring": {
    "mode": "exact",
    "rubrics": [
      { "criterion": "顺序完全正确", "score": 6 }
    ]
  },
  "sourceBasis": ["顺序栈入栈需要先判断容量，再更新 top。"]
}
```

### 5.14 完形填空 cloze

```json
{
  "id": "CZ1",
  "type": "cloze",
  "stem": "阅读短文，选择合适内容填空。",
  "score": 8,
  "difficulty": "medium",
  "knowledgePoints": ["栈", "队列"],
  "tags": ["完形填空"],
  "body": {
    "text": "栈是一种{{blank_1}}的线性表，队列是一种{{blank_2}}的线性表。",
    "options": [
      { "key": "A", "text": "后进先出" },
      { "key": "B", "text": "先进先出" },
      { "key": "C", "text": "随机访问" },
      { "key": "D", "text": "按值排序" }
    ],
    "blanks": [
      { "id": "blank_1", "index": 1, "score": 4 },
      { "id": "blank_2", "index": 2, "score": 4 }
    ]
  },
  "answer": {
    "blanks": [
      { "id": "blank_1", "correctOption": "A" },
      { "id": "blank_2", "correctOption": "B" }
    ]
  },
  "analysis": "栈对应后进先出，队列对应先进先出。",
  "scoring": {
    "mode": "exact",
    "rubrics": [
      { "criterion": "每空正确", "score": 4 }
    ]
  },
  "sourceBasis": ["栈是后进先出，队列是先进先出。"]
}
```

## 6. 试卷 JSON 结构

如果要一次生成整张试卷，建议使用下面结构。

```json
{
  "paper": {
    "id": "PAPER1",
    "title": "数据结构阶段测试",
    "subject": "数据结构",
    "grade": "大学",
    "durationMinutes": 90,
    "totalScore": 100,
    "difficulty": "medium",
    "description": "覆盖栈、队列等基础知识。"
  },
  "sections": [
    {
      "id": "S1",
      "title": "一、单选题",
      "questionType": "single_choice",
      "scorePerQuestion": 2,
      "questions": []
    },
    {
      "id": "S2",
      "title": "二、简答题",
      "questionType": "short_answer",
      "scorePerQuestion": 8,
      "questions": []
    }
  ],
  "missingInfo": []
}
```

规则：

- `paper.totalScore` 应等于所有 section 下题目分值总和。
- `section.questionType` 可以是单一题型；综合题 section 可使用 `mixed`。
- `questions` 内仍然使用统一题目结构。

## 7. 数据库落表建议

推荐“主字段 + JSON 字段”混合设计，不建议为每种题型单独建一张大表。

### 7.1 题库表 exam_question

| 字段 | 建议类型 | 说明 |
| --- | --- | --- |
| `id` | bigint / uuid | 主键 |
| `type` | varchar | 题型，如 `single_choice` |
| `stem` | text | 题干 |
| `score` | decimal | 默认分值 |
| `difficulty` | varchar | 难度 |
| `knowledge_points_json` | json | 知识点数组 |
| `tags_json` | json | 标签数组 |
| `body_json` | json | 题型专属内容 |
| `answer_json` | json | 标准答案 |
| `analysis` | text | 解析 |
| `scoring_json` | json | 评分规则 |
| `source_basis_json` | json | 生成依据 |
| `created_by` | bigint | 创建人 |
| `created_at` | datetime | 创建时间 |
| `updated_at` | datetime | 更新时间 |

### 7.2 试卷表 exam_paper

| 字段 | 建议类型 | 说明 |
| --- | --- | --- |
| `id` | bigint / uuid | 主键 |
| `title` | varchar | 试卷名称 |
| `subject` | varchar | 学科 |
| `grade` | varchar | 年级 |
| `duration_minutes` | int | 考试时长 |
| `total_score` | decimal | 总分 |
| `difficulty` | varchar | 整体难度 |
| `description` | text | 说明 |
| `created_by` | bigint | 创建人 |
| `created_at` | datetime | 创建时间 |

### 7.3 试卷题目关联表 exam_paper_question

| 字段 | 建议类型 | 说明 |
| --- | --- | --- |
| `id` | bigint / uuid | 主键 |
| `paper_id` | bigint / uuid | 试卷 ID |
| `section_id` | varchar | 大题 ID，如 `S1` |
| `section_title` | varchar | 大题标题 |
| `question_id` | bigint / uuid | 题库题目 ID |
| `question_snapshot_json` | json | 题目快照，防止题库变更影响历史试卷 |
| `sort_order` | int | 排序 |
| `score` | decimal | 本试卷中的分值 |

说明：

- 题库题目可复用，所以试卷关联表要存 `question_snapshot_json`。
- 选项可以直接放在 `body_json.options`，除非你需要频繁单独统计选项，否则不用单独建选项表。
- 材料题子题可放在 `body_json.subQuestions`；如果后续要对子题独立统计，再拆子题表。

## 8. 智能体提示词输出要求模板

以后给出题智能体加提示词时，可以直接追加以下约束：

```text
你必须只输出合法 JSON，不要输出 Markdown、代码块标记或额外解释。

顶层结构必须是：
{
  "questions": [],
  "missingInfo": []
}

每道题必须包含：
- id
- type
- stem
- score
- difficulty
- knowledgePoints
- tags
- body
- answer
- analysis
- scoring
- sourceBasis

difficulty 只能是 easy、medium、hard。
type 必须使用系统题型枚举，例如 single_choice、multiple_choice、true_false、fill_blank、short_answer、material_analysis、calculation、programming。
如果资料不足，不要编造题目，返回 "questions": []，并在 missingInfo 中说明缺少哪些资料。
答案必须放在 answer 字段，解析必须放在 analysis 字段，评分规则必须放在 scoring 字段。
```

## 9. 推荐的第一版智能体题型覆盖

结合当前系统已有教材出题智能体，第一版建议优先统一这些智能体输出：

| 智能体 | type |
| --- | --- |
| `textbook_question_single_choice_agent` | `single_choice` |
| `textbook_question_multiple_choice_agent` | `multiple_choice` |
| `textbook_question_true_false_agent` | `true_false` |
| `textbook_question_fill_blank_agent` | `fill_blank` |
| `textbook_question_short_answer_agent` | `short_answer` |
| `textbook_question_calculation_agent` | `calculation` |
| `textbook_question_programming_agent` | `programming` |

后续再扩展：

- `essay`
- `material_analysis`
- `proof`
- `operation`
- `matching`
- `ordering`
- `cloze`

## 10. 已接入接口

### 10.1 AI Server 校验/审查接口

AI Server 已提供两种模式：

| 接口 | 说明 |
| --- | --- |
| `POST /internal/rag/question-bank/review` | 审查模式，返回 `valid`、`issues`、`warnings`，不通过也会返回问题清单 |
| `POST /internal/rag/question-bank/validate` | 校验模式，不符合规范直接返回错误 |

请求结构：

```json
{
  "payload": {
    "questions": [],
    "missingInfo": []
  },
  "expectedType": "single_choice"
}
```

### 10.2 Java 后端题库接口

Java 后端已提供题库导入和查询接口：

| 接口 | 说明 |
| --- | --- |
| `POST /api/exam/questions/review` | 审查题库 JSON，不落库 |
| `POST /api/exam/questions/validate` | 校验题库 JSON，不通过直接报错 |
| `POST /api/exam/questions/import` | 先校验，通过后导入 `exam_question` 表 |
| `GET /api/exam/questions` | 分页查询题库，支持 `type`、`difficulty`、`keyword` |
| `GET /api/exam/questions/{id}` | 查询单题详情 |

导入请求可以直接使用智能体返回结构，并额外带来源信息：

```json
{
  "sourceAgent": "textbook_question_single_choice_agent",
  "sourceTitle": "数据结构栈与队列",
  "questions": [],
  "missingInfo": []
}
```

导入规则：

- 导入前必须通过 Java 侧结构校验。
- 如果 `expectedType=single_choice`，所有题目的 `type` 都必须是 `single_choice`。
- 不合格题目不会写入数据库。
- 题目原始 JSON 会保存为快照，方便以后组卷和追溯。
