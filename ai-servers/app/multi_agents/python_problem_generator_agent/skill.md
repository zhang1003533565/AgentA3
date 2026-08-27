# Python 刷题题目生成器 Skill

- 名称：`python_problem_generator_agent`
- 定位：按主题/难度/数量生成可直接入库的 Python 刷题题目（对齐 python_problem 表结构）
- 输入：topic、difficulty、count（JSON）
- 输出：严格 JSON `{"problems": [{title, difficulty, description, examples, defaultCode, funcName, tags, testcases, solution}]}`
- 约束：用例格式必须与判题器兼容（input 多参数 ', ' 连接、expected 判题 JSON 表示）；solution 必须多解且可运行；生成原创题目

## 输出结构速查

| 字段 | 类型 | 要点 |
|---|---|---|
| title | string | 原创标题，不照抄经典题 |
| difficulty | string | easy/medium/hard |
| examples | array | input/output 为字符串数组 |
| funcName/defaultCode | string | 函数名与模板一致 |
| testcases | array | input 参数赋值串、expected 判题 JSON 表示 |
| solution | array | ≥1 解，含 name/idea/code/complexity |
