/**
 * 架构图数据模型 + 默认数据（100% 还原截图）
 * 数据结构：
 *   {
 *     title: 页面标题
 *     subtitle: 副标题
 *     layers: [   // 6 个主架构层（从上到下）
 *       {
 *         key: 层标识
 *         name: 层名称（左侧标签）
 *         color: 主色（边框/文字/icon）
 *         bg: 浅色背景
 *         iconKey: 内置图标标识
 *         nodes: [
 *           { name, description, tech: [] }
 *         ]
 *       }
 *     ],
 *     thirdParty: [  // 右侧第三方服务栏
 *       { name, description, iconKey }
 *     ],
 *     features: [ '高可用', '易扩展', ... ]  // 底部特性标签
 *   }
 */

// 通用图标（统一 SVG 路径）—— 复用项目已有线性图标
export const ARCH_ICONS = {
  monitor:    'M3 4h18v12H3zM8 20h8M12 16v4',
  mobile:     'M7 2h10v20H7zM11 18h2',
  globe:      'M12 2a10 10 0 100 20 10 10 0 000-20zM2 12h20M12 2a16 16 0 010 20M12 2a16 16 0 000 20',
  wechat:     'M9 4a7 7 0 100 12l-1 3 3-2a8 8 0 008-2M15 10a3 3 0 110 6',
  settings:   'M12 8a4 4 0 100 8 4 4 0 000-8zM19 12a7 7 0 00-.1-1.2l2-1.5-2-3.4-2.3.9a7 7 0 00-2-1.2L14 3h-4l-.6 2.6a7 7 0 00-2 1.2l-2.3-.9-2 3.4 2 1.5A7 7 0 005 12a7 7 0 00.1 1.2l-2 1.5 2 3.4 2.3-.9a7 7 0 002 1.2L10 21h4l.6-2.6a7 7 0 002-1.2l2.3.9 2-3.4-2-1.5c.07-.4.1-.8.1-1.2z',
  nginx:      'M4 4l16 16M20 4L4 20M12 4v16M4 12h16',
  cloud:      'M6 18a4 4 0 010-8 6 6 0 0111-3 5 5 0 011 9H6zM9 14l-2 2M12 14l-2 2M15 14l-2 2',
  user:       'M12 12a4 4 0 100-8 4 4 0 000 8zM4 21a8 8 0 0116 0',
  shop:       'M3 7l1-3h16l1 3v3a2 2 0 01-4 0 2 2 0 01-4 0 2 2 0 01-4 0 2 2 0 01-4 0V7zM5 10v10h14V10',
  cart:       'M3 4h2l2 12h12l2-8H7M9 20a1 1 0 100 2 1 1 0 000-2zM18 20a1 1 0 100 2 1 1 0 000-2z',
  message:    'M21 12a8 8 0 11-3-6.2L21 4l-1 4-3.5 1.2A8 8 0 0121 12z',
  search:     'M11 4a7 7 0 104.5 12.4L21 21M11 4a7 7 0 010 14',
  upload:     'M12 16V4M6 10l6-6 6 6M4 20h16',
  database:   'M4 6c0-1.7 3.6-3 8-3s8 1.3 8 3-3.6 3-8 3-8-1.3-8-3zM4 6v6c0 1.7 3.6 3 8 3s8-1.3 8-3V6M4 12v6c0 1.7 3.6 3 8 3s8-1.3 8-3v-6',
  mysql:      'M5 7c2-3 5-3 7 0M9 5c2 0 4 1 5 3M6 9c0 2 1 4 3 5M19 17c-2 3-5 3-7 0M15 19c-2 0-4-1-5-3M18 15c0-2-1-4-3-5M12 8v12',
  redis:      'M4 7h16v10H4zM4 11h16M8 7v10M16 7v10',
  elastic:    'M12 2a10 10 0 100 20 10 10 0 000-20zM4 12h16M12 4a16 12 0 010 16M12 4a16 12 0 000 16',
  docker:     'M3 12h18M5 9h2M8 9h2M11 9h2M14 9h2M3 12a5 5 0 005 5h8a6 6 0 006-6l-1-1H3',
  linux:      'M12 3a4 4 0 014 4v3a8 8 0 012 5 6 6 0 01-6 6 6 6 0 01-6-6 8 8 0 012-5V7a4 4 0 014-4zM9 10h.01M15 10h.01M9 14h6',
  cicd:       'M4 12a8 8 0 0114-5l2-2v6h-6l2-2a6 6 0 100 6M12 8v5l3 2',
  monitor2:   'M3 4h18v12H3zM7 20h10M12 16v4',
  log:        'M5 4h11l3 3v13H5zM8 4v5h11M9 14h6M9 17h6',
  sms:        'M21 12a8 8 0 11-3-6.2L21 4l-1 4-3.5 1.2A8 8 0 0121 12zM11 11h.01M13 11h.01M15 11h.01',
  oss:        'M3 15a4 4 0 014-4 5 5 0 0110-1 4 4 0 010 8H7a4 4 0 01-4-3z',
  payment:    'M3 7h18v10H3zM3 11h18M7 15h4',
  mail:       'M3 6h18v12H3zM3 6l9 7 9-7',
}

// 默认数据（100% 还原参考图）
export const DEFAULT_ARCHITECTURE_DATA = {
  title: '二手交易平台架构图',
  subtitle: '分层解耦 · 高可用 · 易扩展 · 高性能 · 安全可靠 · 可维护',
  layers: [
    {
      key: 'client',
      name: '客户端层',
      color: '#4D6BFE',
      bg: '#EEF0FF',
      border: '#C7D2FE',
      iconKey: 'monitor',
      nodes: [
        { name: '移动 App', description: '买卖物品、下单交易', tech: [] },
        { name: 'Web 端',  description: '浏览商品、管理订单', tech: [] },
        { name: '微信小程序', description: '快速交易、消息通知', tech: [] },
        { name: '管理后台', description: '运营管理、数据统计', tech: [] },
      ],
    },
    {
      key: 'gateway',
      name: '接入层',
      color: '#8B5CF6',
      bg: '#F5F3FF',
      border: '#DDD6FE',
      iconKey: 'nginx',
      nodes: [
        {
          name: 'Nginx',
          description: '静态资源、反向代理\nHTTPS、负载均衡',
          tech: [],
          iconKey: 'nginx',
        },
        {
          name: 'Spring Cloud Gateway',
          description: 'API 路由、鉴权、限流\n熔断降级、黑白名单',
          tech: [],
          iconKey: 'cloud',
        },
      ],
    },
    {
      key: 'service',
      name: '服务层',
      color: '#10B981',
      bg: '#ECFDF5',
      border: '#A7F3D0',
      iconKey: 'shop',
      nodes: [
        {
          name: '用户服务',
          description: '用户管理、认证授权\n个人信息、地址管理',
          tech: ['Spring Boot'],
          iconKey: 'user',
        },
        {
          name: '商品服务',
          description: '商品发布、编辑\n分类管理、浏览',
          tech: ['Spring Boot'],
          iconKey: 'shop',
        },
        {
          name: '订单服务',
          description: '下单、支付、退款\n订单状态、物流',
          tech: ['Spring Boot'],
          iconKey: 'cart',
        },
        {
          name: '消息服务',
          description: '聊天、通知提醒\n系统消息、推送',
          tech: ['Spring Boot', 'WebSocket'],
          iconKey: 'message',
        },
        {
          name: '搜索服务',
          description: '商品搜索、筛选\n热搜、推荐',
          tech: ['Spring Boot'],
          iconKey: 'search',
        },
        {
          name: '文件服务',
          description: '图片上传、存储\n文件管理、预览',
          tech: ['Spring Boot'],
          iconKey: 'upload',
        },
      ],
    },
    {
      key: 'dao',
      name: '数据访问层',
      color: '#3B82F6',
      bg: '#EFF6FF',
      border: '#BFDBFE',
      iconKey: 'database',
      nodes: [
        {
          name: 'MyBatis-Plus',
          description: 'ORM 框架、SQL 映射、事务管理、分页插件、代码生成',
          tech: [],
          iconKey: 'database',
        },
      ],
    },
    {
      key: 'storage',
      name: '数据存储层',
      color: '#EC4899',
      bg: '#FDF2F8',
      border: '#FBCFE8',
      iconKey: 'database',
      nodes: [
        {
          name: 'MySQL',
          description: '业务数据存储\n用户、商品、订单等',
          tech: [],
          iconKey: 'mysql',
        },
        {
          name: 'Redis',
          description: '缓存、会话、分布式锁\n热点数据、验证码等',
          tech: [],
          iconKey: 'redis',
        },
        {
          name: 'Elasticsearch',
          description: '搜索索引、日志分析\n商品搜索、数据分析',
          tech: [],
          iconKey: 'elastic',
        },
      ],
    },
    {
      key: 'infra',
      name: '基础设施层',
      color: '#F59E0B',
      bg: '#FFFBEB',
      border: '#FDE68A',
      iconKey: 'server',
      nodes: [
        {
          name: 'Docker',
          description: '容器化部署\n环境隔离',
          tech: [],
          iconKey: 'docker',
        },
        {
          name: 'Linux',
          description: '操作系统\n服务器',
          tech: [],
          iconKey: 'linux',
        },
        {
          name: 'CI/CD',
          description: '自动构建\n自动部署',
          tech: [],
          iconKey: 'cicd',
        },
        {
          name: '监控告警',
          description: 'Prometheus\nGrafana',
          tech: [],
          iconKey: 'monitor2',
        },
        {
          name: '日志中心',
          description: 'ELK 日志收集\n链路追踪',
          tech: [],
          iconKey: 'log',
        },
      ],
    },
  ],
  thirdParty: [
    { name: '短信服务', description: '验证码、通知', iconKey: 'sms' },
    { name: '对象存储', description: '图片、文件存储源', iconKey: 'oss' },
    { name: '支付服务', description: '微信支付\n支付宝', iconKey: 'payment' },
    { name: '邮件服务', description: '邮件通知', iconKey: 'mail' },
  ],
  features: ['高可用', '易扩展', '高性能', '安全可靠', '可维护'],
}
