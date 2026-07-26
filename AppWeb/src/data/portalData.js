import { QUESTION_BANK_ROUTES } from '../pages/questionBank/questionBankRoutes.js'

export const portalGroups = [
  {
    label: '总览',
    items: [
      { path: '/home', label: '管理驾驶舱', icon: 'dashboard' },
      { path: '/user/manage', label: '用户与角色', icon: 'team', pageKey: 'user-manage' },
    ],
  },
  {
    label: '校园活动',
    items: [
      { path: '/activity/manage', label: '活动管理', icon: 'calendar', pageKey: 'activity-center' },
      { path: '/category/manage', label: '分类管理', icon: 'tags', pageKey: 'activity-category' },
    ],
  },
  {
    label: '校园论坛',
    items: [
      { path: '/forum/post', label: '帖子管理', icon: 'message', pageKey: 'forum-post' },
      { path: '/forum/comment', label: '评论管理', icon: 'comment', pageKey: 'forum-comment' },
      { path: '/forum/topic', label: '话题管理', icon: 'tag', pageKey: 'forum-topic' },
      { path: '/forum/report', label: '举报处理', icon: 'flag', pageKey: 'forum-report' },
    ],
  },
  {
    label: '校园设施',
    items: [
      { path: '/facility/canteen', label: '食堂管理', icon: 'shop' },
      { path: '/facility/restaurant', label: '档口管理', icon: 'shop', pageKey: 'facility-restaurant', hidden: true },
      { path: '/facility/stall-dish', label: '档口菜品管理', icon: 'shop', pageKey: 'facility-stall-dish', hidden: true },
      { path: '/facility/sports', label: '运动场设置', icon: 'thunder', pageKey: 'facility-sports' },
      { path: '/facility/teaching', label: '教学楼设置', icon: 'bank', pageKey: 'facility-teaching' },
      { path: '/facility/dormitory', label: '宿舍设置', icon: 'home', pageKey: 'facility-dormitory' },
      { path: '/facility/marker', label: '标点管理', icon: 'pushpin', pageKey: 'facility-marker' },
      { path: '/facility/analytics', label: '设施统计', icon: 'bar-chart', pageKey: 'facility-analytics' },
      { path: '/facility/nav-analytics', label: '导航统计', icon: 'line-chart', pageKey: 'map-analytics' },
    ],
  },
  {
    label: '旧物交易',
    items: [
      { path: '/market/item', label: '物品管理', icon: 'shopping', pageKey: 'market-item' },
      { path: '/market/category', label: '分类管理', icon: 'appstore', pageKey: 'market-category' },
    ],
  },
  {
    label: '校园特惠',
    items: [
      { path: '/discount/merchant', label: '商家管理', icon: 'shop', pageKey: 'discount-merchant' },
      { path: '/discount/activity', label: '优惠活动', icon: 'gift', pageKey: 'discount-activity' },
      { path: '/discount/category', label: '分类管理', icon: 'tags', pageKey: 'discount-category' },
      { path: '/discount/analytics', label: '特惠统计', icon: 'fund', pageKey: 'discount-analytics' },
    ],
  },
  {
    label: '会议模块',
    items: [
      { path: '/meeting/history', label: '会议历史', icon: 'video-camera', pageKey: 'meeting-history' },
      { path: '/meeting/voice-model', label: '语音模型配置', icon: 'audio', pageKey: 'voice-model-config' },
    ],
  },
  {
    label: '题库管理',
    items: [
      { path: QUESTION_BANK_ROUTES.questions, label: '题库', icon: 'appstore' },
      { path: QUESTION_BANK_ROUTES.generate, label: '题库生成', icon: 'robot' },
      { path: QUESTION_BANK_ROUTES.createPaper, label: '试卷生成', icon: 'file-text' },
      { path: QUESTION_BANK_ROUTES.paperHistory, label: '生成的试卷', icon: 'file-search' },
    ],
  },
  {
    label: 'AI 模块',
    items: [
      { path: '/ai/model', label: '模型配置', icon: 'robot', pageKey: 'system-config' },
      { path: '/ai/agent-settings', label: '智能体设置', icon: 'setting' },
      { path: '/ai/rag/agents', label: '智能体测试', icon: 'robot' },
      { path: '/ai/agent-cache', label: '缓存监控', icon: 'line-chart' },
      { path: '/ai/knowledge', label: '知识库管理', icon: 'file-search' },
      { path: '/admin/knowledge-chat', label: '知识库聊天', icon: 'message' },
      { path: '/ai/profile-rules', label: '画像规则', icon: 'pie-chart' },
    ],
  },
]

export const navigationSections = portalGroups

export const moduleCards = [
  { title: '校园活动', description: '活动发布、分类与基础报名管理', route: '/activity/manage' },
  { title: '校园论坛', description: '帖子、评论、话题与内容治理', route: '/forum/post' },
  { title: '校园设施', description: '食堂、运动场、教学楼、宿舍管理与地图标点维护', route: '/facility/canteen' },
  { title: '旧物交易', description: '物品、分类与后台审核管理', route: '/market/item' },
  { title: '校园特惠', description: '商家、优惠活动与分类运营', route: '/discount/merchant' },
  { title: '会议模块', description: '查看会议历史、转写记录和会议智能体结果', route: '/meeting/history' },
  { title: 'AI 模块', description: '维护 AI 模型配置、智能体开关和默认模型', route: '/ai/model' },
  { title: '智能体设置', description: '集中维护智能体开关、默认模型和运行边界', route: '/ai/agent-settings' },
  { title: '智能体测试', description: '测试智能体调用、导入题库并维护示例输入', route: '/ai/rag/agents' },
  { title: '缓存监控', description: '查看普通智能体工具缓存命中率和接口明细', route: '/ai/agent-cache' },
  { title: '题库管理', description: '查看智能体导入的标准题库', route: QUESTION_BANK_ROUTES.questions },
  { title: '试卷生成', description: '从现有题库随机或手工组卷并下载 Word 试卷', route: QUESTION_BANK_ROUTES.createPaper },
  { title: '知识库管理', description: '维护 MaxKB 账号、环境地址和知识库文档', route: '/ai/knowledge' },
  { title: '知识库聊天', description: '选择 MaxKB 知识库后用 Java 智能体测试问答和召回片段', route: '/admin/knowledge-chat' },
  { title: '画像规则', description: '查看个人画像雷达图来源、更新策略和 Leader 使用边界', route: '/ai/profile-rules' },
]

const columns = {
  user: [
    { title: '用户名', dataIndex: 'username' },
    { title: '姓名', dataIndex: 'realName' },
    { title: '角色', dataIndex: 'role', type: 'tag' },
    { title: '手机号', dataIndex: 'phone' },
    { title: '状态', dataIndex: 'status', type: 'status' },
  ],
  activity: [
    { title: '标题', dataIndex: 'title' },
    { title: '组织者', dataIndex: 'organizerName' },
    { title: '地点', dataIndex: 'location' },
    { title: '状态', dataIndex: 'status', type: 'status' },
    { title: '开始时间', dataIndex: 'startTime' },
  ],
  activityCategory: [
    { title: '分类名称', dataIndex: 'name' },
    { title: '排序', dataIndex: 'sort' },
    { title: '状态', dataIndex: 'status', type: 'status' },
  ],
  registration: [
    { title: '报名人', dataIndex: 'realName' },
    { title: '学号', dataIndex: 'studentNo' },
    { title: '手机号', dataIndex: 'phone' },
    { title: '状态', dataIndex: 'status', type: 'status' },
    { title: '报名时间', dataIndex: 'signupTime' },
  ],
  signIn: [
    { title: '姓名', dataIndex: 'realName' },
    { title: '学号', dataIndex: 'studentNo' },
    { title: '签到状态', dataIndex: 'signInStatus', type: 'status' },
    { title: '签到时间', dataIndex: 'signInTime' },
  ],
  post: [
    { title: '标题', dataIndex: 'title' },
    { title: '作者', dataIndex: 'username' },
    { title: '话题', dataIndex: 'topicName', type: 'tag' },
    { title: '点赞数', dataIndex: 'likeCount' },
    { title: '发布时间', dataIndex: 'createTime' },
  ],
  comment: [
    { title: '所属帖子', dataIndex: 'postTitle' },
    { title: '内容', dataIndex: 'content' },
    { title: '作者', dataIndex: 'authorName' },
    { title: '点赞数', dataIndex: 'likeCount' },
    { title: '发布时间', dataIndex: 'createTime' },
  ],
  topic: [
    { title: '话题名称', dataIndex: 'topicName' },
    { title: '帖子数', dataIndex: 'postCount' },
    { title: '热门', dataIndex: 'isHot', type: 'status' },
    { title: '状态', dataIndex: 'status', type: 'status' },
  ],
  facility: [
    { title: '设施名称', dataIndex: 'facilityName', width: 120 },
    { title: '类型', dataIndex: 'facilityType', type: 'tag', width: 100 },
    { title: '位置', dataIndex: 'location', width: 100 },
    { title: '描述', dataIndex: 'description', type: 'text', width: 200 },
    { title: '状态', dataIndex: 'status', type: 'status', width: 100 },
    { title: '图片', dataIndex: 'images', type: 'images', width: 180 },
  ],
  sports: [
    { title: '设施名称', dataIndex: 'facilityName', width: 120 },
    { title: '类型', dataIndex: 'facilityType', type: 'tag', width: 100 },
    { title: '描述', dataIndex: 'description', type: 'text', width: 200 },
    { title: '状态', dataIndex: 'status', type: 'status', width: 100 },
    { title: '图片', dataIndex: 'images', type: 'images', width: 180 },
  ],
  stall: [
    { title: '照片', dataIndex: 'image', type: 'image' },
    { title: '档口名称', dataIndex: 'stallName' },
    { title: '品类', dataIndex: 'category', type: 'tag' },
    { title: '楼层', dataIndex: 'floor' },
    { title: '餐厅', dataIndex: 'restaurantName' },
    { title: '评分', dataIndex: 'score' },
    { title: '人均价', dataIndex: 'avgPrice' },
  ],
  dish: [
    { title: '照片', dataIndex: 'imageUrl', type: 'image' },
    { title: '菜品名称', dataIndex: 'name' },
    { title: '档口', dataIndex: 'stallName', type: 'tag' },
    { title: '分类', dataIndex: 'category', type: 'tag' },
    { title: '价格', dataIndex: 'price' },
    { title: '评分', dataIndex: 'rating' },
    { title: '销量', dataIndex: 'soldCount' },
    { title: '状态', dataIndex: 'isAvailable', type: 'status' },
  ],
  mapConfig: [
    { title: '配置项', dataIndex: 'key' },
    { title: '值', dataIndex: 'value' },
  ],
  marker: [
    { title: '缩略图', dataIndex: 'thumbnailUrl', type: 'image' },
    { title: '标记名称', dataIndex: 'markerName' },
    { title: '设施类型', dataIndex: 'facilityType', type: 'tag' },
    { title: '经纬度', dataIndex: 'position' },
    { title: '状态', dataIndex: 'status', type: 'status' },
  ],
  secondhandItem: [
    { title: '标题', dataIndex: 'title' },
    { title: '分类', dataIndex: 'categoryName', type: 'tag' },
    { title: '价格', dataIndex: 'price' },
    { title: '发布者', dataIndex: 'publisherName' },
    { title: '状态', dataIndex: 'statusText', type: 'status' },
  ],
  secondhandCategory: [
    { title: '分类名称', dataIndex: 'categoryName' },
    { title: '排序', dataIndex: 'sort' },
    { title: '状态', dataIndex: 'statusText', type: 'status' },
  ],
  merchant: [
    { title: '商家名称', dataIndex: 'merchantName' },
    { title: '分类', dataIndex: 'categoryName', type: 'tag' },
    { title: '营业时间', dataIndex: 'businessHours' },
    { title: '状态', dataIndex: 'statusText', type: 'status' },
  ],
  merchantCategory: [
    { title: '分类名称', dataIndex: 'categoryName' },
    { title: '排序', dataIndex: 'sort' },
    { title: '状态', dataIndex: 'statusText', type: 'status' },
  ],
  discountActivity: [
    { title: '活动标题', dataIndex: 'title' },
    { title: '商家', dataIndex: 'merchantName' },
    { title: '优惠类型', dataIndex: 'discountTypeText', type: 'tag' },
    { title: '状态', dataIndex: 'statusText', type: 'status' },
    { title: '有效期', dataIndex: 'timeRange' },
  ],
  systemConfig: [
    { title: '能力类型', dataIndex: 'modalityLabel', type: 'tag' },
    { title: '配置标识', dataIndex: 'configName' },
    { title: '服务商', dataIndex: 'providerDisplay', type: 'tag' },
    { title: '接入状态', dataIndex: 'runtimeStatus', type: 'tag' },
    { title: '模型 ID', dataIndex: 'model' },
    { title: '密钥', dataIndex: 'apiKeyMasked' },
    { title: '状态', dataIndex: 'statusText', type: 'status' },
    { title: '更新时间', dataIndex: 'updateTime' },
  ],
  meetingSession: [
    { title: '会议主题', dataIndex: 'title' },
    { title: '会议号', dataIndex: 'roomCode', type: 'tag' },
    { title: '会议类型', dataIndex: 'meetingTypeText', type: 'tag' },
    { title: '状态', dataIndex: 'statusText', type: 'status' },
    { title: '参会人数', dataIndex: 'participantCount' },
    { title: '记录数', dataIndex: 'recordCount' },
    { title: '智能体结果', dataIndex: 'resultCount' },
    { title: '更新时间', dataIndex: 'updateTime' },
  ],
  summary: [
    { title: '指标', dataIndex: 'label' },
    { title: '数值', dataIndex: 'value' },
  ],
}

const createPage = ({
  title,
  badge,
  description,
  columns: pageColumns,
  filters = ['全部'],
  emptyText,
  requiresInput,
  inputLabel,
  inputPlaceholder,
}) => ({
  title,
  badge,
  description,
  columns: pageColumns,
  filters: { status: filters },
  emptyText,
  requiresInput,
  inputLabel,
  inputPlaceholder,
})

export const workspacePages = {
  'user-manage': createPage({
    title: '',
    badge: '',
    description: '',
    columns: columns.user,
    filters: ['全部'],
    emptyText: '暂无用户数据',
  }),
  'activity-center': createPage({
    title: '活动管理',
    badge: '校园活动',
    description: '查看和维护活动列表。',
    columns: columns.activity,
    filters: ['全部'],
    emptyText: '暂无活动数据',
  }),
  'activity-category': createPage({
    title: '',
    badge: '',
    description: '',
    columns: columns.activityCategory,
    filters: ['全部'],
    emptyText: '暂无分类数据',
  }),
  'activity-audit': createPage({
    title: '报名审核',
    badge: '校园活动',
    description: '需要先指定活动后，才能查看对应报名记录。',
    columns: columns.registration,
    emptyText: '请先在页面逻辑中指定活动 ID 后再加载报名数据',
    requiresInput: true,
    inputLabel: '活动 ID',
    inputPlaceholder: '请输入活动 ID',
  }),
  'activity-signin': createPage({
    title: '签到管理',
    badge: '校园活动',
    description: '需要先指定活动后，才能查看对应签到记录。',
    columns: columns.signIn,
    emptyText: '请先在页面逻辑中指定活动 ID 后再加载签到数据',
    requiresInput: true,
    inputLabel: '活动 ID',
    inputPlaceholder: '请输入活动 ID',
  }),
  'activity-notice': createPage({
    title: '通知管理',
    badge: '校园活动',
    description: '当前后端未提供活动通知管理接口。',
    columns: [],
    emptyText: '通知接口尚未提供',
  }),
  'forum-post': createPage({
    title: '',
    badge: '',
    description: '',
    columns: columns.post,
    filters: ['全部'],
    emptyText: '暂无帖子数据',
  }),
  'forum-comment': createPage({
    title: '',
    badge: '',
    description: '',
    columns: columns.comment,
    emptyText: '请选择帖子后查看评论数据',
  }),
  'forum-topic': createPage({
    title: '',
    badge: '',
    description: '',
    columns: columns.topic,
    filters: ['全部'],
    emptyText: '暂无话题数据',
  }),
  'forum-report': createPage({
    title: '',
    badge: '',
    description: '',
    columns: [],
    filters: ['全部'],
    emptyText: '暂无举报数据',
  }),
  'facility-canteen': createPage({
    title: '食堂管理',
    badge: '校园设施',
    description: '管理校内各食堂基本信息与地图标点，点击"查看档口"进入该食堂的档口列表。',
    columns: columns.facility,
    emptyText: '暂无食堂数据',
  }),
  'facility-restaurant': createPage({
    title: '档口管理',
    badge: '校园设施',
    description: '管理食堂内的档口信息，包括档口名称、品类、楼层等。',
    columns: columns.stall,
    filters: ['全部'],
    emptyText: '暂无档口数据',
  }),
  'facility-sports': createPage({
    title: '运动场设置',
    badge: '校园设施',
    description: '管理运动场基本信息与地图标点位置。',
    columns: columns.sports,
    filters: ['全部', '球类场地', '水上及特殊场地', '田径及综合场地', '其他'],
    emptyText: '暂无运动场数据',
  }),
  'facility-teaching': createPage({
    title: '教学楼设置',
    badge: '校园设施',
    description: '管理教学楼基本信息与地图标点位置。',
    columns: columns.facility,
    emptyText: '暂无教学楼数据',
  }),
  'facility-dormitory': createPage({
    title: '宿舍设置',
    badge: '校园设施',
    description: '管理宿舍楼基本信息与地图标点位置。',
    columns: columns.facility,
    emptyText: '暂无宿舍数据',
  }),
  'facility-marker': createPage({
    title: '标点管理',
    badge: '校园设施',
    description: '在地图上查看和管理所有校园设施标点，支持新增、调整位置和上传缩略图。',
    columns: columns.marker,
    emptyText: '暂无标点数据',
  }),
  'facility-stall-dish': createPage({
    title: '档口菜品管理',
    badge: '校园设施',
    description: '查看和维护该档口的菜品列表。',
    columns: columns.dish,
    filters: ['全部'],
    emptyText: '暂无菜品数据',
  }),
  'facility-analytics': createPage({
    title: '设施统计',
    badge: '校园设施',
    description: '汇总展示设施数量、状态分布和热度排名。',
    columns: columns.summary,
    emptyText: '暂无设施统计数据',
  }),
  'map-marker': createPage({
    title: '',
    badge: '',
    description: '',
    columns: columns.marker,
    emptyText: '暂无地图标记数据',
  }),
  'map-analytics': createPage({
    title: '',
    badge: '',
    description: '',
    columns: [],
    emptyText: '暂无导航统计数据',
  }),
  'market-item': createPage({
    title: '',
    badge: '',
    description: '',
    columns: columns.secondhandItem,
    emptyText: '暂无物品数据',
  }),
  'market-category': createPage({
    title: '',
    badge: '',
    description: '',
    columns: columns.secondhandCategory,
    emptyText: '暂无旧物分类数据',
  }),
  'discount-merchant': createPage({
    title: '',
    badge: '',
    description: '',
    columns: columns.merchant,
    emptyText: '暂无商家数据',
  }),
  'discount-activity': createPage({
    title: '',
    badge: '',
    description: '',
    columns: columns.discountActivity,
    emptyText: '暂无优惠活动数据',
  }),
  'discount-category': createPage({
    title: '',
    badge: '',
    description: '',
    columns: columns.merchantCategory,
    emptyText: '暂无商家分类数据',
  }),
  'discount-analytics': createPage({
    title: '',
    badge: '',
    description: '',
    columns: columns.summary,
    emptyText: '暂无特惠统计数据',
  }),
  'system-config': createPage({
    title: '',
    badge: '',
    description: '',
    columns: columns.systemConfig,
    emptyText: '暂无 AI 模型配置数据',
  }),
  'meeting-history': createPage({
    title: '会议历史',
    badge: '会议模块',
    description: '查看会议历史、语音转写记录和会议智能体结果。',
    columns: columns.meetingSession,
    emptyText: '暂无会议历史数据',
  }),
  'voice-model-config': createPage({
    title: '语音模型配置',
    badge: '会议模块',
    description: '单独维护会议语音转写模型，当前使用 Java 后端直连讯飞实时转写。',
    columns: columns.systemConfig,
    emptyText: '暂无语音模型配置数据',
  }),
}

export const allNavItems = navigationSections.flatMap((section) => section.items)

export const getWorkspacePage = (pageKey) => workspacePages[pageKey]

export const getNavMetaByPath = (path) => {
  const item = allNavItems.find((navItem) => navItem.path === path)
  if (!item) return null

  const page = item.pageKey ? workspacePages[item.pageKey] : null
  return {
    ...item,
    title: page?.title || item.label,
    description: page?.description || '',
    badge: page?.badge || '',
  }
}

// 按路由取面包屑文案（分组名 / 菜单名），供布局顶栏统一渲染页面标题
export const getBreadcrumbByPath = (path) => {
  for (const group of portalGroups) {
    const item = group.items.find((navItem) => navItem.path === path)
    if (item) return [group.label, item.label]
  }
  return null
}
