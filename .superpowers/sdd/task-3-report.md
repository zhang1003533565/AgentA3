# Task 3 报告：Python 掌握度与动态学习路径持久化

## 结果

已在指定 worktree 内完成 Task 3，范围仅包含学习掌握度、学习路径、路径项的实体、仓库、DTO、服务与测试。未创建控制器，未修改考试、校园路由、`UserProfileEvidence` 或其他画像/考试体系。

## 交付内容

- `LearningKnowledgeMastery`：以 `(user_id, course_key, knowledge_point_key)` 唯一约束保存最近答题 ID、规范化历史答题收据 JSON、答题/正误计数、分数、置信度、掌握状态、复习时间，并使用 `@Version Long version` 做乐观并发控制。
- `LearningPath`：保存用户课程目标、单调递增版本、状态、画像/掌握度摘要、来源消息和重规划时间；`(user_id, course_key, version_no)` 唯一。
- `LearningPathItem`：保存节点顺序、知识点、目标掌握度、资源类型/ID JSON、学习/交付状态和时间信息；路径内 `itemKey` 与序号均唯一。
- `LearningStatuses`：严格使用 brief 指定的 PATH、ITEM、MASTERY 状态集合，并在实体持久化回调中拒绝非法状态。
- `LearningPathServiceImpl`：实现首页/当前路径读取、事务内 active 路径归档与版本替换、资源互动、assessment 幂等更新及 DTO 映射。
- assessment 写入前裁剪 `knowledgePointKey`，并在同一掌握度行中用升序、去重、无多余空白的 JSON 收据集合覆盖任意历史 attempt 重放；兼容仅有 `lastAttemptId` 的旧行。
- 路径替换与 assessment 更新均在事务开始写业务数据前锁定稳定的 `sys_user` 行，首次创建也能串行化；同用户并发路径替换保持连续版本且只有一个 active 路径。
- 资源 ID 写入前执行去空、裁剪、去重、排序并序列化为无多余空白的规范 JSON。
- 资源互动只接受 `view/open/complete/dismiss`；查询通过用户、课程和 active 路径联结并加写锁；`complete` 完成节点，`dismiss` 只改变交付状态。
- 掌握度严格使用 brief 的难度权重公式；状态阈值为 `<60 weak`、`60–79.99 learning`、`>=80 且至少 3 次 mastered`、其余 `new`；复习间隔分别为 1/3/7 天。

## TDD 证据

### 预期 RED 1：持久化类型尚不存在

先创建 `LearningPathServiceImplTest` 的路径版本归档和 assessment 重复提交测试，再运行：

```text
cd AppBackend && mvn -q -Dtest=LearningPathServiceImplTest test
```

结果为退出码 1，测试编译明确报告 `LearningPathDTO`、三实体、三仓库和 `LearningPathServiceImpl` 不存在，失败原因与 brief 的预期 RED 一致。

### 预期 RED 2：互动所有权查询尚不存在

在第一批最小实现后，先补首页、规范 JSON、重复节点、四种互动、状态阈值和复习间隔测试，再运行同一命令。

结果为退出码 1，测试编译只报告 `LearningPathItemRepository.findOwnedActiveByIdForUpdate` 不存在；该失败准确锁定下一步需要的 active 路径所有权写锁查询。

### 环境错误（不计为业务 RED）

补齐实现后的首次执行中，13 条测试均在 `setUp` 前因 Mockito/Byte Buddy 无法在当前受限 JDK 中自附加 agent 而报错；没有进入任何业务断言。仓库现有 `SystemConfigServiceImplTest` 已采用 JDK 动态代理规避同一限制，因此仅将本测试替身改为内存状态 + JDK Proxy，没有修改 POM、生产逻辑或测试断言。替换后 brief 原命令可直接退出 0。

### 自审回归 RED

自审时增加“最新历史版本已 completed、较旧版本仍 active”的回归用例。修复前该用例以 `expected archived but was active` 失败；随后将“最高版本号来源”与“当前 active 行归档”拆开，单例与完整套件均转为 GREEN。

### 评审修复 RED：历史重放、键规范化与 dismiss 时间

先增加 `A -> B -> replay A`、带空白知识点键以及已有 `deliveredAt` 的 dismiss 回归，再运行目标方法。结果为 3 个断言失败：历史 A 被重复计数、空白键创建第二行、dismiss 覆盖交付时间。实现规范收据集合、统一裁剪键和 dismiss 仅更新状态后，同一目标集退出码 0。

### 评审修复 RED：真实持久化并发边界

先增加基于 H2 的最小 `@DataJpaTest`，使用真实 Spring 事务与 Repository 并发执行同一 assessment 及三次路径替换。加锁前稳定复现掌握度唯一约束冲突和路径版本唯一约束冲突；随后在 `UserRepository.findByIdForUpdate` 增加悲观写锁，并让两条写链路先锁用户行。修复后同一测试验证：

- 相同 attempt 并发提交只累计一次，收据为 `[301]`；
- 三次并发路径替换产生版本 `[1,2,3]`，数据库中只有一个 active；
- 收据以 `[199,200]` 规范持久化，带空白键不会产生重复掌握度行。

## 最终验证

```text
cd AppBackend && mvn -q -Dtest=LearningPathServiceImplTest,LearningPathPersistenceTest test
```

新鲜结果：退出码 0；单元测试 `tests=16`、H2 持久化/并发测试 `tests=3`，合计 19 项，`failures=0, errors=0, skipped=0`。

覆盖内容：路径版本递增与归档、非最新 active 行归档、并发连续版本与唯一 active、assessment 相邻重复/历史重放/并发重复幂等、收据规范持久化、知识点键裁剪、精确权重与阈值、1/3/7 天复习间隔、状态集合、规范资源 JSON、重复 itemKey 拒绝、首页聚合、互动白名单、节点完成与 dismiss 仅改交付状态且保留时间、用户/active 路径所有权查询。

## 自审结论与已知边界

- 自审结论：无阻断级发现；新增文件未包含密钥、控制器、考试变更或校园路由变更。
- assessment 幂等收据与掌握度保存在同一行，没有新增表或第二套考试体系；收据 JSON 会随历史 attempt 数量增长，当前未设置裁剪策略以免破坏精确幂等。
- 已用 H2/MySQL 模式验证真实 JPA 持久化、悲观锁和并发事务；未连接真实 MySQL 验证其锁等待行为，也未覆盖后续考试反馈集成。
- `confidence` 已按模型持久化并以确定性默认值初始化；brief 未给出置信度更新公式，因此本任务没有自行发明计算规则。
