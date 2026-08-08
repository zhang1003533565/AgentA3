# Frontend 缺失功能实施结果

## 已完成

### Python 个性化学习

- `/learning`：个性化学习工作台
- `/learning/knowledge-graph`：知识图谱与知识点详情
- `/learning/resources`：专项资源流式生成与智能体执行链路
- 学习路径开始、完成和重新规划
- 精准推荐入口

### 校园二手市集

- `/marketplace`：商品列表、分类、搜索、发布、收藏、我的发布
- 商品详情、预订和联系卖家
- 浏览记录
- 交易记录及确认、完成、取消操作
- 交易通知
- `/marketplace/chat`：真实买卖双方会话

### 校园论坛

- `/forum`：话题筛选、帖子流、热门帖子和发布
- `/forum/posts/:postId`：帖子详情、点赞、收藏、评论和关注
- `/forum/users/:userId`：论坛用户主页、帖子、关注关系和点赞证据

### 消息中心

- `/messages`：真实分类消息、未读数量、单条已读、分类已读和全部已读
- `/mine/messages`：复用同一真实消息中心

### AI 工具

- `/ai-studio/writing`：真实 AI 写作
- `/ai-studio/image`：真实文生图任务及轮询
- `/ai-studio/exam`：试卷资源生成
- `/ai-studio/presentation`：PPT 资源生成
- `/ai-studio/mind_map`：思维导图资源生成
- `/ai-studio/architecture`：架构图资源生成
- `/ai-studio/flowchart`：流程图资源生成
- `/profile-radar`：真实个人画像雷达

### 会议

- `/meetings`：会议列表、快速会议、预约、加入
- 会议详情、开始、结束、智能整理和删除

### 课表

- `/mine/schedule`：真实周课表、周次切换、教务导入、分享码复制
- `/mine/schedule-settings`：教务账号、当前学期、学期管理和节次时间
- 原来的教务账号、学期管理、节次时间路由已统一跳转到真实设置工作台

### 校园服务

- `/campus-services?tab=notices`：公告列表和详情
- `/campus-services?tab=promotions`：优惠列表和详情
- `/campus-services?tab=facilities`：校园设施列表和详情
- `/campus-services?tab=dining`：餐饮档口入口

### 活动和账户

- `/activities/publish`：教师或管理员创建并发布活动
- `/activities/:activityId/sign-in`：学生查询真实签到状态并签到
- `/mine/account-settings`：修改密码和更新头像地址

## 没有复制的 APP 演示数据

以下 APP 页面本身没有真实后端闭环，因此网页版没有复制其中的硬编码数据：

- 教务成绩页：APP 直接写死 GPA 和课程成绩，后端没有教务成绩查询接口。
- 社区活动发布页：APP 使用 `setTimeout` 模拟成功；网页版改为真实活动草稿和发布接口，且受教师/管理员权限控制。
- 网页旧课表、学期和节次页面：原来使用本地常量和 TODO；现已替换为真实后端接口。
- 宿舍选择：当前没有保存学生宿舍选择的后端接口，不生成虚假选择状态。

## 验证

- `npm.cmd run build`
- Vite 生产构建通过
- 路由组件和 API 模块均被构建器成功解析
