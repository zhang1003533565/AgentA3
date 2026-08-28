const option = (value, label, description = '') => ({ value, label, description })

export const DIAGRAM_TYPES = ['flowchart', 'architecture', 'mind_map']

export const diagramToolConfig = {
  flowchart: {
    title: '流程图',
    subtitle: '生成清晰的业务与逻辑流程',
    inputLabel: '描述您的流程需求',
    placeholder: '例如：生成请假申请流程，包含员工提交、主管审批和人事备案。主管拒绝后返回修改。',
    progress: ['理解流程需求', '识别步骤与角色', '分析判断与分支', '编排节点和连线', '完成流程图'],
    defaults: { scene: 'AUTO', granularity: 'AUTO', direction: 'VERTICAL', decision: 'AUTO', lane: 'AUTO' },
    sections: [
      { key: 'scene', label: '流程场景', columns: 5, options: [option('AUTO', '自动', '智能识别'), option('ADMIN', '行政流程'), option('BUSINESS', '业务流程'), option('LEARNING', '学习流程'), option('LIFE', '生活流程')] },
      { key: 'granularity', label: '节点粒度', columns: 4, options: [option('AUTO', '自动', '智能推荐'), option('SIMPLE', '简略', '仅核心步骤'), option('STANDARD', '标准', '主要步骤与说明'), option('DETAILED', '详细', '完整步骤与分支')] },
      { key: 'direction', label: '显示方向', columns: 2, options: [option('VERTICAL', '纵向显示', '自上而下'), option('HORIZONTAL', '横向显示', '从左到右')] },
      { key: 'decision', label: '判断节点', columns: 3, options: [option('AUTO', '自动判断', 'AI 根据内容决定'), option('FORCE', '强制包含', '主动包含判断节点'), option('NONE', '不使用', '尽量生成线性流程')] },
      { key: 'lane', label: '角色泳道', columns: 4, options: [option('AUTO', '自动', '智能推荐'), option('NONE', '不显示', '普通流程图'), option('ROLE', '按角色', '按参与角色划分'), option('DEPARTMENT', '按部门', '按部门划分')] },
    ],
  },
  architecture: {
    title: '架构图',
    subtitle: '生成系统架构可视化资源',
    inputLabel: '描述您的架构需求',
    placeholder: '例如：生成校园二手交易系统的整体架构图，包含 Web 前端、业务服务、数据存储和第三方服务。',
    progress: ['理解系统需求', '识别系统边界', '规划架构层级', '梳理模块关系', '完成架构图'],
    defaults: { systemType: 'WEB', autoLayers: true, layers: [], focus: ['FRONTEND', 'BACKEND', 'DATABASE'], relation: 'AUTO' },
    systemTypes: [option('WEB', 'Web系统'), option('APP', 'APP系统'), option('MINI_PROGRAM', '小程序'), option('ADMIN', '管理后台')],
    layers: [option('CLIENT', '用户层', '面向最终用户的交互与展示层'), option('APPLICATION', '应用层', '业务功能与应用逻辑实现层'), option('SERVICE', '服务层', '核心服务、接口与业务处理层'), option('DATA', '数据层', '数据存储、缓存与持久化层')],
    focus: [option('FRONTEND', '前端模块'), option('BACKEND', '后端服务'), option('DATABASE', '数据存储'), option('THIRD_PARTY', '第三方服务')],
    relations: [option('AUTO', '自动分析', 'AI 选择合适的表达方式'), option('MODULE', '模块关系', '展示组件层级与连接'), option('DATA_FLOW', '数据流向', '突出信息传递路径'), option('CALL', '调用关系', '突出模块调用依赖')],
  },
  mind_map: {
    title: '思维导图',
    subtitle: '梳理主题与知识结构',
    inputLabel: '输入内容',
    placeholder: '请输入想生成思维导图的内容或要求，也可以导入 PDF、Word 或 PPT。',
    progress: ['理解主题内容', '提取中心主题', '归纳一级分支', '补充知识节点', '完成思维导图'],
    defaults: { centerTopic: '', depth: 'auto', structure: 'auto', detail: 'standard' },
    sections: [
      { key: 'depth', label: '层级深度', columns: 4, options: [option('auto', '自动', '智能推荐'), option('2', '2层', '简洁'), option('3', '3层', '适中'), option('4', '4层', '详细')] },
      { key: 'structure', label: '结构方式', columns: 5, options: [option('auto', '自动', '智能选择'), option('知识梳理', '知识梳理', '提炼关键点'), option('课程体系', '课程体系', '按模块组织'), option('复习提纲', '复习提纲', '重点回顾'), option('项目拆解', '项目拆解', '分解任务')] },
      { key: 'detail', label: '展开程度', columns: 3, options: [option('simple', '简洁', '只保留核心'), option('standard', '标准', '平衡展示'), option('detail', '详细', '完整展开')] },
    ],
  },
}

export function createDiagramSettings() {
  return Object.fromEntries(DIAGRAM_TYPES.map((type) => [type, structuredClone(diagramToolConfig[type].defaults)]))
}

export function diagramConfig(type) {
  return diagramToolConfig[type] || diagramToolConfig.flowchart
}

