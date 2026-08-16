# App Exam Task 2 修复报告

## 原实现

- `ExamPaperAttemptRepository` 通过 `findByPaperIdAndUserIdAndStatus` 接受任意状态并返回 `Optional`，调用方可能把已提交记录误当作唯一活动答题。
- 历史查询返回该试卷与用户的所有答题，并按 `attemptNo` 排序；完成次数直接统计所有状态。
- `exam_paper_attempt_answer` 同时声明 `(attempt_id, paper_question_id)` 唯一索引与 `attempt_id` 单列索引，后者是唯一索引的冗余左前缀索引。
- 持久化契约只验证 `@PrePersist` 对 `activeMarker` 的处理，没有覆盖状态更新时释放与重新占用活动标记。

## 修复

- 活动答题改为 `findByPaperIdAndUserIdAndActiveMarker(paperId, userId, 1)`，不再允许用任意状态表达活动记录。
- 新增语义明确的 `findHistoryByPaperIdAndUserId`，查询仅包含 `SUBMITTED`、`AUTO_SUBMITTED`，并按 `submittedAt desc` 排序。
- 新增 `countCompletedByPaperIdAndUserId`，仅统计 `SUBMITTED`、`AUTO_SUBMITTED`。
- 删除 answer 表的 `attempt_id` 单列索引，保留 `(attempt_id, paper_question_id)` 唯一约束。
- 扩展 `AppExamPersistenceContractTest`：锁定 repository 方法签名与查询语义，并验证 `@PreUpdate` 在 `IN_PROGRESS -> SUBMITTED/AUTO_SUBMITTED` 时清空 `activeMarker`，回到 `IN_PROGRESS` 时恢复为 `1`。

## 测试

- RED：`mvn -Dtest=AppExamPersistenceContractTest test`，预期失败于缺失活动查询和仍存在的冗余索引（1 failure、1 error）。
- GREEN：`mvn -Dtest=AppExamPersistenceContractTest test`，6 tests，0 failures/errors。
