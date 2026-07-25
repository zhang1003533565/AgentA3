# 校园论坛管理页面优化方案

## 一、现状分析

### 1.1 现有页面结构

| 页面 | 路由 | 文件路径 | 状态 |
|------|------|----------|------|
| 帖子管理 | `/forum/post` | `AppWeb/src/pages/forum/PostManage/` | ✅ 基础CRUD |
| 评论管理 | `/forum/comment` | `AppWeb/src/pages/forum/CommentManage/` | ⚠️ 需帖子ID |
| 话题管理 | `/forum/topic` | `AppWeb/src/pages/forum/TopicManage/` | ✅ 基础CRUD |
| 举报处理 | 未注册路由 | `AppWeb/src/pages/forum/ReportManage/` | ❌ 前端存在但未接入路由 |

### 1.2 后端 API 覆盖分析

| 模块 | 已有接口 | 缺失接口 |
|------|----------|----------|
| 帖子 | 增删查、分页列表、热门、批量删除(service层) | ❌ 批量删除 Controller 端点<br>❌ 置顶/加精接口<br>❌ 隐藏/恢复接口<br>❌ 管理端专用列表接口 |
| 评论 | 增删查、管理端列表、批量删除(service层) | ❌ 批量删除 Controller 端点<br>❌ 管理端专用列表接口（已有`/admin/list`但前端未用分页） |
| 话题 | 增删改查、热门话题 | ✅ 基本齐全 |
| 举报 | 列表、详情、处理、统计、审计日志 | ✅ 基本齐全 |

### 1.3 前端问题分析

1. **帖子管理**：查看详情用 `Modal.info` 内联渲染，体验一般；缺少批量操作；缺少置顶/隐藏功能
2. **评论管理**：搜索栏只有关键词+状态，缺少"所属帖子"筛选；查看详情用 Modal 内联渲染
3. **话题管理**：前端用客户端过滤而非后端分页；`isHot` 和 `status` 字段类型不一致（前端用 Boolean，后端用 Integer/String）
4. **举报管理**：页面文件存在但**未注册到路由**，侧边栏也未配置入口
5. **统一问题**：所有页面缺少数据看板/统计卡片

---

## 二、优化方案

### 2.1 后端新增接口

#### 2.1.1 帖子管理增强

**新增 Controller 端点（PostController.java）**

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 批量删除 | DELETE | `/api/forum/posts/batch` | 传入 ID 数组，批量删除帖子 |
| 置顶/取消 | PUT | `/api/forum/posts/{id}/pin` | 切换置顶状态 |
| 加精/取消 | PUT | `/api/forum/posts/{id}/highlight` | 切换加精状态 |
| 隐藏/恢复 | PUT | `/api/forum/posts/{id}/hidden` | 切换隐藏状态 |
| 管理端列表 | GET | `/api/forum/posts/admin/list` | 管理员查看所有状态帖子 |

**Service 层（PostService.java / PostServiceImpl.java）**

```java
// 已有但未暴露到 Controller
void batchDeletePosts(List<Long> ids);
```

需要新增：
```java
void pinPost(Long id);
void highlightPost(Long id);
void toggleHidden(Long id);
PageResponse<PostListItem> getAdminPostList(Integer pageNum, Integer pageSize, String keyword, String status, String sortBy, Long topicId);
```

#### 2.1.2 评论管理增强

**新增 Controller 端点（CommentController.java）**

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 批量删除 | DELETE | `/api/forum/comments/admin/batch` | 传入 ID 数组，批量删除评论 |
| 管理端列表 | GET | `/api/forum/comments/admin/list` | 已有但需确认分页支持 |

**Service 层（CommentService.java）**

```java
// 已有但未暴露到 Controller
void batchDeleteComments(List<Long> ids);
```

#### 2.1.3 举报管理增强

举报管理后端接口已齐全，前端页面也已写好，主要问题是**未注册路由**。

### 2.2 前端页面优化

#### 2.2.1 帖子管理（PostManage.jsx）

**优化项：**

1. **增加顶部统计卡片**：显示帖子总数、今日新增、待审核、已隐藏
2. **增加批量操作栏**：勾选多条记录后可批量删除
3. **增加快捷操作列**：置顶、加精、隐藏（需后端接口支持）
4. **搜索栏增强**：增加"创建时间范围"筛选、"话题"下拉筛选
5. **查看详情优化**：改用抽屉（Drawer）替代 Modal，支持全屏查看富文本内容
6. **排序功能**：支持按浏览量、点赞数、发布时间排序

#### 2.2.2 评论管理（CommentManage.jsx）

**优化项：**

1. **增加顶部统计卡片**：评论总数、今日新增、待处理举报
2. **搜索栏增强**：增加"所属帖子ID"输入框、"评论者"筛选
3. **批量操作**：支持批量删除选中评论
4. **查看详情优化**：改用抽屉（Drawer），显示评论上下文（所属帖子标题+链接）
5. **回复链展示**：如果有回复关系，显示引用上下文

#### 2.2.3 话题管理（TopicManage.jsx）

**优化项：**

1. **改用后端分页**：当前用前端 `filter` 过滤，改为使用后端分页 API
2. **搜索栏增强**：增加"状态"筛选（启用/禁用）、"热门"筛选
3. **排序功能**：支持按帖子数排序
4. **批量操作**：支持批量启用/禁用/设为热门
5. **表单修复**：修复 `isHot` 和 `status` 字段类型不一致问题

#### 2.2.4 举报管理（ReportManage.jsx）— 注册路由

**优化项：**

1. **注册路由**：在 `App.jsx` 中添加 `/forum/report` 路由
2. **侧边栏入口**：在 `portalData.js` 的"校园论坛"分组下添加"举报处理"菜单项
3. **增加统计卡片**：顶部显示举报总数、待处理、已处理、已忽略
4. **快捷操作**：待处理举报可直接在列表中操作（忽略/删除内容）

#### 2.2.5 新增：论坛数据看板

在帖子管理页面顶部增加一个**论坛数据看板**组件，展示：

- 帖子总数 / 今日新增 / 本周新增
- 评论总数 / 今日新增
- 话题总数 / 热门话题数
- 待处理举报数
- 近7天发帖趋势（折线图）
- 热门话题 TOP5（排行榜）

---

## 三、实施步骤

### Phase 1：后端接口补充（优先）

1. 在 `PostController.java` 中新增批量删除、置顶、加精、隐藏接口
2. 在 `PostServiceImpl.java` 中实现 `pinPost`、`highlightPost`、`toggleHidden`、`getAdminPostList`
3. 在 `CommentController.java` 中新增批量删除接口
4. 在 `TopicController.java` 中确认分页接口支持 status/isHot 筛选

### Phase 2：前端路由注册

1. 在 `portalData.js` 中为举报管理添加侧边栏入口
2. 在 `App.jsx` 中注册 `/forum/report` 路由

### Phase 3：页面功能增强

1. 帖子管理：统计卡片 + 批量操作 + 快捷操作列 + 搜索增强 + 详情抽屉
2. 评论管理：统计卡片 + 批量操作 + 搜索增强 + 详情抽屉
3. 话题管理：后端分页 + 搜索增强 + 批量操作
4. 举报管理：确认功能正常 + 统计卡片

### Phase 4：数据看板

1. 新建 `ForumDashboard.jsx` 组件
2. 后端新增 `/api/forum/dashboard` 聚合统计接口
3. 在帖子管理页面嵌入看板

---

## 四、技术细节

### 4.1 后端批量删除接口设计

```java
// PostController.java
@DeleteMapping("/batch")
public Result<Void> batchDeletePosts(
        @RequestBody List<Long> ids,
        HttpServletRequest request) {
    checkAdmin(request);
    postService.batchDeletePosts(ids);
    return Result.success("批量删除成功", null);
}

// PostService.java
void batchDeletePosts(List<Long> ids);

// PostServiceImpl.java
@Override
public void batchDeletePosts(List<Long> ids) {
    List<ForumPost> posts = postRepository.findAllById(ids);
    posts.forEach(post -> post.setStatus("DELETED"));
    postRepository.saveAll(posts);
}
```

### 4.2 前端批量操作组件模式

```jsx
<Table
  rowSelection={{ selectedRowKeys, onChange: setSelectedRowKeys }}
  // ...
/>

{selectedRowKeys.length > 0 && (
  <div className="batch-actions">
    <Popconfirm title={`确定删除 ${selectedRowKeys.length} 条记录？`} onConfirm={handleBatchDelete}>
      <Button type="primary" danger>批量删除</Button>
    </Popconfirm>
  </div>
)}
```

### 4.3 前端分页请求适配

当前前端 `getPostList` 传参使用 `pageNum/pageSize`，后端 Spring Boot 用 `@RequestParam` 接收，需确保一致。

---

## 五、优先级排序

| 优先级 | 任务 | 工作量 | 影响 |
|--------|------|--------|------|
| P0 | 举报管理注册路由 | 小 | 修复缺失功能 |
| P0 | 帖子/评论批量删除接口 | 中 | 提升管理效率 |
| P1 | 帖子管理统计卡片+批量操作 | 中 | 核心体验提升 |
| P1 | 评论管理统计卡片+批量操作 | 中 | 核心体验提升 |
| P1 | 话题管理后端分页改造 | 中 | 修复功能缺陷 |
| P2 | 帖子置顶/加精/隐藏 | 中 | 内容丰富 |
| P2 | 举报管理统计卡片 | 小 | 体验提升 |
| P3 | 论坛数据看板 | 大 | 展示亮点 |
