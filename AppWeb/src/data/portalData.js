export const navigationSections = [
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
    ],
  },
  {
    label: '校园设施',
    items: [
      { path: '/facility/restaurant', label: '餐厅管理', icon: 'shop', pageKey: 'facility-restaurant' },
      { path: '/facility/stall-dish', label: '档口菜品管理', icon: 'shop', pageKey: 'facility-stall-dish', hidden: true },
      { path: '/facility/sports', label: '运动场管理', icon: 'thunder', pageKey: 'facility-sports' },
      { path: '/facility/teaching', label: '教学楼管理', icon: 'bank', pageKey: 'facility-teaching' },
      { path: '/facility/dormitory', label: '宿舍管理', icon: 'home', pageKey: 'facility-dormitory' },
      { path: '/facility/analytics', label: '设施统计', icon: 'bar-chart', pageKey: 'facility-analytics' },
    ],
  },
  {
    label: '地图导航',
    items: [
      { path: '/map/marker', label: '标记管理', icon: 'pushpin', pageKey: 'map-marker' },
      { path: '/map/analytics', label: '导航统计', icon: 'line-chart', pageKey: 'map-analytics' },
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
    label: 'AI 模块',
    items: [
      { path: '/ai/model', label: '模型配置', icon: 'robot', pageKey: 'system-config' },
    ],
  },
]

export const moduleCards = [
  { title: '校园活动', description: '活动发布、分类与基础报名管理', route: '/activity/manage' },
  { title: '校园论坛', description: '帖子、评论、话题与内容治理', route: '/forum/post' },
  { title: '校园设施', description: '设施管理、地图关联与基础信息维护', route: '/facility/restaurant' },
  { title: '地图导航', description: '标记管理与导航统计', route: '/map/marker' },
  { title: '旧物交易', description: '物品、分类与后台审核管理', route: '/market/item' },
  { title: '校园特惠', description: '商家、优惠活动与分类运营', route: '/discount/merchant' },
  { title: 'AI 模块', description: '维护 AI 模型配置并测试接口连通性', route: '/ai/model' },
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
    { title: '设施名称', dataIndex: 'facilityName' },
    { title: '类型', dataIndex: 'facilityType', type: 'tag' },
    { title: '位置', dataIndex: 'location' },
    { title: '状态', dataIndex: 'status', type: 'status' },
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
    { title: '服务商', dataIndex: 'provider', type: 'tag' },
    { title: 'API Key', dataIndex: 'apiKeyMasked' },
    { title: '状态', dataIndex: 'statusText', type: 'status' },
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
  'facility-restaurant': createPage({
    title: '',
    badge: '',
    description: '',
    columns: columns.stall,
    filters: ['全部'],
    emptyText: '暂无档口数据',
  }),
  'facility-sports': createPage({
    title: '',
    badge: '',
    description: '',
    columns: columns.facility,
    emptyText: '暂无运动场数据',
  }),
  'facility-teaching': createPage({
    title: '',
    badge: '',
    description: '',
    columns: columns.facility,
    emptyText: '暂无教学楼数据',
  }),
  'facility-dormitory': createPage({
    title: '',
    badge: '',
    description: '',
    columns: columns.facility,
    emptyText: '暂无宿舍数据',
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
    emptyText: '暂无 DeepSeek 配置数据',
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
