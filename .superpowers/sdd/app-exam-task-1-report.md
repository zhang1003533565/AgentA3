# App 在线答题计划 Task 1 实施报告

## 状态

已完成管理员试卷发布状态机；未实现 App 答题能力。

## 实现

- `ExamPaper` 增加默认未发布的 `published` 与可空 `publishTime`。
- `PaperVO` 在创建、列表、详情及发布操作返回中暴露发布状态与时间。
- 新增 `publish` / `unpublish` 服务事务与 `POST /api/exam/papers/{id}/publish|unpublish`。
- 控制器在调用服务前要求已登录且角色为 `ADMIN`。
- 服务仅允许有效试卷的创建者操作；发布前拒绝空试卷与非正考试时长。
- 重复发布保留首次发布时间且不重复写库；重复取消发布不重复写库。

## TDD 证据

RED：先扩展 `ExamPaperServiceImplTest` 与 `ExamPaperControllerTest`，指定测试因缺少发布接口、实体字段和 repository 查询方法而编译失败。

GREEN：

```text
mvn -DargLine=-javaagent:/Users/zzs/.m2/repository/org/mockito/mockito-core/5.20.0/mockito-core-5.20.0.jar \
  -Dtest=ExamPaperServiceImplTest,ExamPaperControllerTest test
Tests run: 43, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

本机 JDK 21 禁止 Mockito 自附加，因此验证命令显式加载与项目依赖版本一致的 Mockito agent；不加 agent 时所有测试均在 Mockito 初始化阶段失败，未进入业务断言。

## 关注点

- 本任务按简报限定的 Java 文件实现字段映射，未新增独立数据库迁移脚本；非自动建表环境需由后续部署迁移补齐 `published`、`publish_time` 列。
- 当前幂等保证基于事务内状态判断；若后续要求高并发下严格串行发布，可为 repository 查询增加悲观锁或版本字段。
