<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppTabBar from '../components/AppTabBar.vue'
import { getLatestJobRecommendations, JOB_BOSS_CTA, JOB_SALARY_HINT, resolveBossJobSearchLink, resolveBossJobSearchLinkFromJob } from '../api/jobRecommendations'

const router = useRouter()
const searchKeyword = ref('')
const hotJobsLoading = ref(true)
const hotJobs = ref([])

const hotSearches = ['AI算法', 'Java开发', '前端架构', '云原生', '产品经理', '数据分析']

const categoryPages = [
  [
    {
      id: 'it_ai',
      main: '互联网与人工智能',
      sub: '前端 后端 AI算法 产品 运营',
    },
    {
      id: 'chip',
      main: '电子与通信技术',
      sub: '硬件 芯片 通信 网络',
    },
    {
      id: 'finance',
      main: '金融与保险',
      sub: '银行 投资 风控 审计',
    },
    {
      id: 'education',
      main: '教育与培训',
      sub: '教师 教研 留学 心理',
    },
    {
      id: 'health',
      main: '医疗与健康',
      sub: '临床 护理 医技 康复',
    },
    {
      id: 'biotech',
      main: '生物制药与化工',
      sub: '基因 制药 化学 质检',
    },
  ],
  [
    {
      id: 'manufacturing',
      main: '制造业与工业生产',
      sub: '机械 电气 生产 供应链',
    },
    {
      id: 'automobile',
      main: '汽车与交通装备',
      sub: '研发 测试 智驾 服务',
    },
    {
      id: 'construction',
      main: '建筑工程与地产',
      sub: '设计 施工 造价 物业',
    },
    {
      id: 'energy',
      main: '能源矿业与环保',
      sub: '电力 新能源 环保 安全',
    },
    {
      id: 'retail',
      main: '电商与零售',
      sub: '选品 运营 直播 门店',
    },
    {
      id: 'marketing',
      main: '市场广告与公关',
      sub: '品牌 投流 内容 活动',
    },
  ],
  [
    {
      id: 'media',
      main: '文化传媒与内容',
      sub: '编辑 摄像 编导 自媒体',
    },
    {
      id: 'design',
      main: '艺术与设计',
      sub: '平面 三维 室内 交互',
    },
    {
      id: 'legal',
      main: '法律咨询与知识产权',
      sub: '律师 法务 咨询 合规',
    },
    {
      id: 'admin',
      main: '企业管理与行政',
      sub: '行政 人事 秘书 经理',
    },
    {
      id: 'sales',
      main: '销售与客户服务',
      sub: '大客户 渠道 客服 商务',
    },
    {
      id: 'logistics',
      main: '物流仓储与供应链',
      sub: '仓储 配送 采购 报关',
    },
  ],
  [
    {
      id: 'hospitality',
      main: '餐饮酒店与旅游',
      sub: '厨师 酒店 导游 会展',
    },
    {
      id: 'public',
      main: '公共服务与政府',
      sub: '社区 外事 消防 应急',
    },
    {
      id: 'sports',
      main: '体育与健身',
      sub: '教练 康复 赛事 电竟',
    },
    {
      id: 'service',
      main: '家政与生活服务',
      sub: '月嫂 维修 美业 宠物',
    },
    {
      id: 'security',
      main: '安保与应急服务',
      sub: '安检 消防 安全 风险',
    },
    {
      id: 'freelance',
      main: '自由职业与新兴职业',
      sub: '自媒体 写手 AI创作 顾问',
    },
  ],
]

const categoryDetails = {
  it_ai: {
    title: '互联网与人工智能',
    groups: [
      { name: '开发与技术', tags: ['前端开发工程师', '后端开发工程师', '全栈工程师', '测试工程师', '运维工程师'] },
      { name: 'AI与算法', tags: ['算法工程师', '机器学习工程师', '大模型工程师', 'AI应用工程师', '提示词工程师'] },
      { name: '产品与设计', tags: ['产品经理', '数据分析师', 'UI设计师', 'UX设计师'] },
    ],
  },
  chip: {
    title: '电子与通信技术',
    groups: [
      { name: '研发与设计', tags: ['电子工程师', '通信工程师', '嵌入式工程师', 'PCB设计工程师'] },
      { name: '测试与运维', tags: ['射频工程师', '设备测试工程师', '网络优化工程师', '技术支持'] },
    ],
  },
  finance: {
    title: '金融与保险',
    groups: [
      { name: '金融业务', tags: ['投资经理', '风控专员', '财富顾问', '保险顾问'] },
      { name: '财务支持', tags: ['审计专员', '财务分析师', '税务专员', '会计'] },
    ],
  },
  education: {
    title: '教育与培训',
    groups: [
      { name: '教学岗位', tags: ['学科教师', '课程顾问', '教研老师', '升学顾问'] },
      { name: '支持岗位', tags: ['班主任', '教学运营', '心理咨询师'] },
    ],
  },
  health: {
    title: '医疗与健康',
    groups: [
      { name: '临床方向', tags: ['临床医生', '护士', '康复治疗师', '医技人员'] },
      { name: '健康服务', tags: ['健康管理师', '营养师', '心理咨询师'] },
    ],
  },
  biotech: {
    title: '生物制药与化工',
    groups: [
      { name: '研发岗位', tags: ['生物研发工程师', '制药工程师', '化学分析师'] },
      { name: '质量岗位', tags: ['质量专员', '检验工程师', '注册申报专员'] },
    ],
  },
  manufacturing: {
    title: '制造业与工业生产',
    groups: [
      { name: '生产方向', tags: ['机械工程师', '工艺工程师', '设备工程师', '生产主管'] },
      { name: '供应链方向', tags: ['计划专员', '采购专员', '质量工程师'] },
    ],
  },
  automobile: {
    title: '汽车与交通装备',
    groups: [
      { name: '研发方向', tags: ['整车工程师', '智驾工程师', '测试工程师'] },
      { name: '服务方向', tags: ['售后工程师', '服务顾问', '供应链专员'] },
    ],
  },
  construction: {
    title: '建筑工程与地产',
    groups: [
      { name: '工程方向', tags: ['建筑设计师', '施工员', '造价工程师', '项目经理'] },
      { name: '地产方向', tags: ['招商主管', '物业经理', '策划专员'] },
    ],
  },
  energy: {
    title: '能源矿业与环保',
    groups: [
      { name: '能源方向', tags: ['电气工程师', '新能源工程师', '储能工程师'] },
      { name: '环保方向', tags: ['环保工程师', 'EHS专员', '安全工程师'] },
    ],
  },
  retail: {
    title: '电商与零售',
    groups: [
      { name: '电商方向', tags: ['电商运营', '选品专员', '直播运营', '投流专员'] },
      { name: '零售方向', tags: ['门店店长', '陈列专员', '招商主管'] },
    ],
  },
  marketing: {
    title: '市场广告与公关',
    groups: [
      { name: '品牌方向', tags: ['品牌经理', '媒介经理', '活动策划', '广告优化师'] },
      { name: '内容方向', tags: ['内容运营', '文案策划', '公关专员'] },
    ],
  },
  media: {
    title: '文化传媒与内容',
    groups: [
      { name: '内容方向', tags: ['编辑', '编导', '摄像师', '新媒体运营'] },
      { name: '创作方向', tags: ['短视频策划', '主播', '后期剪辑'] },
    ],
  },
  design: {
    title: '艺术与设计',
    groups: [
      { name: '视觉方向', tags: ['平面设计师', '三维设计师', '插画师'] },
      { name: '空间方向', tags: ['室内设计师', '展陈设计师', '交互设计师'] },
    ],
  },
  legal: {
    title: '法律咨询与知识产权',
    groups: [
      { name: '法律方向', tags: ['律师', '法务', '合规专员', '知识产权顾问'] },
      { name: '咨询方向', tags: ['咨询顾问', '项目顾问'] },
    ],
  },
  admin: {
    title: '企业管理与行政',
    groups: [
      { name: '行政方向', tags: ['行政专员', '前台', '秘书', '总助'] },
      { name: '人力方向', tags: ['招聘专员', 'HRBP', '培训专员'] },
    ],
  },
  sales: {
    title: '销售与客户服务',
    groups: [
      { name: '销售方向', tags: ['大客户经理', '渠道经理', '招商主管', '商务经理'] },
      { name: '服务方向', tags: ['客服专员', '售后专员', '呼叫中心专员'] },
    ],
  },
  logistics: {
    title: '物流仓储与供应链',
    groups: [
      { name: '仓配方向', tags: ['仓储主管', '配送专员', '物流专员'] },
      { name: '供应链方向', tags: ['采购专员', '计划专员', '报关专员'] },
    ],
  },
  hospitality: {
    title: '餐饮酒店与旅游',
    groups: [
      { name: '餐饮方向', tags: ['厨师', '餐厅经理', '店长'] },
      { name: '文旅方向', tags: ['酒店管家', '导游', '会展执行'] },
    ],
  },
  public: {
    title: '公共服务与政府',
    groups: [
      { name: '服务方向', tags: ['社区工作者', '外事专员', '政务服务专员'] },
      { name: '应急方向', tags: ['消防员', '应急专员'] },
    ],
  },
  sports: {
    title: '体育与健身',
    groups: [
      { name: '训练方向', tags: ['健身教练', '体育教练', '康复师'] },
      { name: '赛事方向', tags: ['赛事运营', '裁判', '场馆管理员'] },
    ],
  },
  service: {
    title: '家政与生活服务',
    groups: [
      { name: '家庭服务', tags: ['家政服务员', '月嫂', '育婴师'] },
      { name: '生活服务', tags: ['维修师傅', '美甲师', '宠物美容师'] },
    ],
  },
  security: {
    title: '安保与应急服务',
    groups: [
      { name: '安保方向', tags: ['保安', '安检员', '安全管理员'] },
      { name: '风险方向', tags: ['风险评估师', '安全工程师'] },
    ],
  },
  freelance: {
    title: '自由职业与新兴职业',
    groups: [
      { name: '创作方向', tags: ['自由撰稿人', '独立设计师', '自媒体博主', 'AI内容创作者'] },
      { name: '服务方向', tags: ['线上顾问', '配音员', '翻译'] },
    ],
  },
}

const logoColors = ['#ff6b6b', '#4ecdc4', '#45b7d1', '#96ceb4', '#ff9f43', '#a29bfe']

const displayHotJobs = computed(() => hotJobs.value.slice(0, 3))
const displayLatestJobs = computed(() => hotJobs.value.slice(0, 6))

const hotDirections = computed(() => {
  const directions = []
  const seen = new Set()
  for (const job of hotJobs.value) {
    const query = String(job?.jobTitle || '').trim()
    if (!query || seen.has(query)) continue
    seen.add(query)
    directions.push({
      label: query,
      query,
      skills: parseJobSkills(job.skills),
    })
  }
  if (directions.length) {
    return directions.slice(0, 6)
  }
  return hotSearches.map((item) => ({
    label: item,
    query: item,
    skills: [item],
  }))
})

const hotJobsWeekLabel = computed(() => {
  const first = hotJobs.value[0]
  if (!first?.weekStartDate || !first?.weekEndDate) return ''
  return `${String(first.weekStartDate).slice(0, 10)} — ${String(first.weekEndDate).slice(0, 10)}`
})

function parseJobSkills(skillsText) {
  return String(skillsText || '')
    .split(/[,，、]/)
    .map((item) => item.trim())
    .filter(Boolean)
}

function resolveJobSearchLink(job) {
  return resolveBossJobSearchLinkFromJob(job)
}

function openBossSearch(keyword) {
  const query = String(keyword || '').trim() || '软件工程师'
  window.open(resolveBossJobSearchLink(query), '_blank', 'noopener,noreferrer')
}

async function loadHotJobs() {
  hotJobsLoading.value = true
  try {
    const result = await getLatestJobRecommendations()
    hotJobs.value = Array.isArray(result?.data) ? result.data : []
  } catch {
    hotJobs.value = []
  } finally {
    hotJobsLoading.value = false
  }
}

onMounted(loadHotJobs)

const currentPage = ref(0)
const activeCategoryId = ref('')
const detailPinned = ref(false)

const pageCount = computed(() => categoryPages.length)
const currentCategories = computed(() => categoryPages[currentPage.value] ?? [])
const activeCategory = computed(() => categoryDetails[activeCategoryId.value] ?? null)

function changePage(step) {
  const nextPage = currentPage.value + step
  if (nextPage < 0 || nextPage >= pageCount.value) {
    return
  }
  currentPage.value = nextPage
  activeCategoryId.value = ''
  detailPinned.value = false
}

function showCategory(id) {
  activeCategoryId.value = id
}

function resetPreview() {
  if (!detailPinned.value) {
    activeCategoryId.value = ''
  }
}

function keepPreview() {
  if (activeCategoryId.value) {
    detailPinned.value = true
  }
}

function releasePreview() {
  detailPinned.value = false
  activeCategoryId.value = ''
}
</script>

<template>
  <div class="home-view">
    <AppTabBar embedded />

    <section class="search-area">
      <div class="container">
        <div class="search-box-wrap">
          <input
            v-model="searchKeyword"
            type="text"
            placeholder="搜索职位、公司，例如：AI 大模型工程师"
            @keyup.enter="openBossSearch(searchKeyword)"
          />
          <button type="button" @click="openBossSearch(searchKeyword)">搜索</button>
        </div>
        <div class="hot-searches">
          <span>热门搜索：</span>
          <span
            v-for="item in hotSearches"
            :key="item"
            class="hot-search-tag"
            @click="openBossSearch(item)"
          >{{ item }}</span>
        </div>
      </div>
    </section>

    <section class="container cat-diagnosis-area">
      <div class="left-panel" @mouseleave="resetPreview">
        <div class="cat-menu-page active">
          <button
            v-for="item in currentCategories"
            :key="item.id"
            type="button"
            class="cat-menu-item"
            :class="{ active: activeCategoryId === item.id }"
            @mouseenter="showCategory(item.id)"
          >
            <div class="cat-row">
              <span class="cat-main">{{ item.main }}</span>
              <span class="cat-sub-list">{{ item.sub }}</span>
            </div>
            <span class="cat-arrow">></span>
          </button>
        </div>

        <div class="cat-pagination">
          <span class="page-num">{{ currentPage + 1 }} / {{ pageCount }}</span>
          <div class="page-btns">
            <button type="button" class="page-btn" :disabled="currentPage === 0" @click="changePage(-1)">
              <
            </button>
            <button type="button" class="page-btn" :disabled="currentPage === pageCount - 1" @click="changePage(1)">
              >
            </button>
          </div>
        </div>
      </div>

      <div class="right-panel" @mouseenter="keepPreview" @mouseleave="releasePreview">
        <div v-if="!activeCategory" class="diagnosis-banner">
          <div class="text-box">
            <h2>拒绝盲目内卷，先做岗位体检</h2>
            <p>
              上传简历，AI 深度解析你的能力短板。
              <br />
              一键生成专属学习路径与高薪岗位适配报告。
            </p>
            <button type="button" class="diagnosis-btn" @click="router.push('/resume')">上传简历，开启诊断</button>
            <button type="button" class="diagnosis-btn diagnosis-btn--ghost" @click="router.push('/jobs/hot')">
              查看岗位雷达
            </button>
          </div>
        </div>

        <div v-else class="detail-panel active">
          <div class="detail-title">{{ activeCategory.title }}</div>
          <div v-for="group in activeCategory.groups" :key="group.name" class="detail-item">
            <div class="detail-item-title">{{ group.name }}</div>
            <div class="detail-tags">
              <span v-for="tag in group.tags" :key="tag">{{ tag }}</span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section class="container section-block">
      <div class="section-header">
        <h2>热门岗位</h2>
        <p v-if="hotJobsWeekLabel" class="section-meta">{{ hotJobsWeekLabel }} · 岗位方向由 AI 整理，薪资以 BOSS 直聘为准</p>
      </div>
      <div v-if="hotJobsLoading" class="section-empty">正在加载热门岗位…</div>
      <div v-else-if="!displayHotJobs.length" class="section-empty">
        <p>暂无岗位推荐</p>
        <button type="button" class="section-link-btn" @click="router.push('/jobs/hot')">前往岗位雷达</button>
      </div>
      <div v-else class="grid-3">
        <article v-for="job in displayHotJobs" :key="job.id || job.jobTitle" class="info-card">
          <div class="card-header-simple">
            <div class="title">{{ job.jobTitle }}</div>
            <a
              :href="resolveJobSearchLink(job)"
              target="_blank"
              rel="noreferrer"
              class="salary-hint salary-hint--link"
            >{{ JOB_SALARY_HINT }}</a>
          </div>
          <div class="card-desc">技能方向：{{ job.skills || '详见 BOSS 直聘' }}</div>
          <div class="card-tags">
            <span v-for="item in parseJobSkills(job.skills)" :key="item">{{ item }}</span>
          </div>
          <div class="card-actions">
            <a :href="resolveJobSearchLink(job)" target="_blank" rel="noreferrer" class="more-btn">{{ JOB_BOSS_CTA }}</a>
          </div>
          <div class="card-benefits">岗位名称与技能方向仅供参考，具体薪资与 JD 以 BOSS 直聘为准</div>
        </article>
      </div>
      <div class="view-more-wrap">
        <button type="button" class="view-more-btn" @click="router.push('/jobs/hot')">查看更多</button>
      </div>
    </section>

    <section class="container section-block">
      <div class="section-header">
        <h2>热门招聘方向</h2>
        <p class="section-meta">由本周岗位雷达整理，点击可前往 BOSS 直聘查看真实公司与薪资</p>
      </div>
      <div v-if="hotJobsLoading" class="section-empty">正在加载招聘方向…</div>
      <div v-else class="grid-3">
        <article v-for="(direction, index) in hotDirections" :key="direction.query" class="info-card">
          <div class="company-header">
            <div class="company-logo" :style="{ background: logoColors[index % logoColors.length] }">
              {{ direction.label.charAt(0) }}
            </div>
            <div class="company-info">
              <div class="company-name">{{ direction.label }}</div>
              <a
                :href="resolveBossJobSearchLink(direction.query)"
                target="_blank"
                rel="noreferrer"
                class="salary-hint salary-hint--link company-meta-link"
              >{{ JOB_SALARY_HINT }}</a>
            </div>
          </div>
          <div class="card-desc"><strong>相关技能方向：</strong></div>
          <div class="card-tags">
            <span v-for="item in direction.skills" :key="item">{{ item }}</span>
          </div>
          <div class="card-actions">
            <a
              :href="resolveBossJobSearchLink(direction.query)"
              target="_blank"
              rel="noreferrer"
              class="more-btn"
            >{{ JOB_BOSS_CTA }}</a>
          </div>
          <div class="card-benefits no-border">不含具体公司与薪资，请在 BOSS 直聘搜索结果中查看</div>
        </article>
      </div>
      <div class="view-more-wrap">
        <button type="button" class="view-more-btn" @click="router.push('/jobs/hot')">前往岗位雷达</button>
      </div>
    </section>

    <section class="container section-block">
      <div class="section-header">
        <h2>最新职位</h2>
        <p v-if="hotJobsWeekLabel" class="section-meta">{{ hotJobsWeekLabel }} · 岗位来自本周雷达，不含公司与薪资</p>
        <p v-else class="section-meta">岗位来自本周雷达，公司与薪资请前往 BOSS 直聘查看</p>
      </div>
      <div v-if="hotJobsLoading" class="section-empty">正在加载最新职位…</div>
      <div v-else-if="!displayLatestJobs.length" class="section-empty">
        <p>暂无职位推荐</p>
        <button type="button" class="section-link-btn" @click="router.push('/jobs/hot')">前往岗位雷达</button>
      </div>
      <div v-else class="job-list">
        <article v-for="(job, index) in displayLatestJobs" :key="job.id || `latest-${job.jobTitle}`" class="job-list-item">
          <div class="job-list-main">
            <div class="job-list-logo" :style="{ background: logoColors[index % logoColors.length] }">
              {{ String(job.jobTitle || '岗').charAt(0) }}
            </div>
            <div class="job-list-info">
              <div class="job-list-title">{{ job.jobTitle }}</div>
              <div class="job-list-meta">{{ JOB_SALARY_HINT }}</div>
              <div class="card-tags job-list-tags">
                <span v-for="item in parseJobSkills(job.skills)" :key="item">{{ item }}</span>
              </div>
            </div>
          </div>
          <a :href="resolveJobSearchLink(job)" target="_blank" rel="noreferrer" class="job-list-btn">{{ JOB_BOSS_CTA }}</a>
        </article>
      </div>
      <div class="view-more-wrap">
        <button type="button" class="view-more-btn" @click="router.push('/jobs/hot')">查看更多</button>
      </div>
    </section>

    <footer class="footer">
      <div class="container footer-grid">
        <div>
          <h4>关于我们</h4>
          <ul>
            <li><a href="javascript:void(0)">公司简介</a></li>
            <li><a href="javascript:void(0)">联系我们</a></li>
            <li><a href="javascript:void(0)">加入我们</a></li>
          </ul>
        </div>
        <div>
          <h4>产品与服务</h4>
          <ul>
            <li><a href="javascript:void(0)" @click="router.push('/jobs/hot')">岗位雷达</a></li>
            <li><a href="javascript:void(0)" @click="router.push('/resume')">人岗匹配诊断</a></li>
            <li><a href="javascript:void(0)">学习路径推荐</a></li>
          </ul>
        </div>
        <div>
          <h4>帮助与支持</h4>
          <ul>
            <li><a href="javascript:void(0)">帮助中心</a></li>
            <li><a href="javascript:void(0)">常见问题</a></li>
            <li><a href="javascript:void(0)">在线客服</a></li>
          </ul>
        </div>
        <div>
          <h4>法律合规</h4>
          <ul>
            <li><a href="javascript:void(0)">服务协议</a></li>
            <li><a href="javascript:void(0)">隐私政策</a></li>
            <li><a href="javascript:void(0)">免责声明</a></li>
          </ul>
        </div>
      </div>

      <div class="container footer-bottom">
        <p>© 2026 数智诊断港 | 本平台数据仅用于学术研究与个人职业发展规划</p>
        <p>ICP备案号：粤 ICP 备 XXXXXXX 号</p>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.home-view {
  isolation: isolate;
  min-height: 100vh;
  background: #eaf3fc;
  color: #333;
}

.home-view * {
  box-sizing: border-box;
}

.container {
  width: min(1200px, calc(100% - 40px));
  margin: 0 auto;
}

.footer a {
  color: inherit;
  text-decoration: none;
}

.footer a:hover {
  color: #fff;
}

.search-area {
  padding: 40px 0 30px;
  background: linear-gradient(180deg, #dce8f4 0%, #eaf3fc 100%);
  border-bottom: 1px solid #d0dceb;
}

.search-box-wrap {
  display: flex;
  align-items: center;
  max-width: 800px;
  margin: 0 auto;
  padding: 4px 4px 4px 20px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}

.search-box-wrap input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: 0;
  padding: 12px 0;
  font-size: 16px;
  background: transparent;
}

.search-box-wrap button {
  border: 0;
  padding: 12px 32px;
  border-radius: 6px;
  background: #0066ff;
  color: #fff;
  font-size: 16px;
  font-weight: 700;
  cursor: pointer;
}

.hot-searches {
  display: flex;
  justify-content: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 16px;
}

.hot-searches span {
  padding: 4px 12px;
  border-radius: 20px;
  background: #fff;
  font-size: 14px;
  color: #444;
}

.hot-search-tag {
  cursor: pointer;
}

.hot-search-tag:hover {
  color: #0066ff;
}

.section-meta {
  margin: 8px 0 0;
  color: #667085;
  font-size: 13px;
}

.salary-hint {
  flex-shrink: 0;
  max-width: 42%;
  color: #667085;
  font-size: 12px;
  line-height: 1.4;
  text-align: right;
}

.salary-hint--link {
  color: #2f76bd;
  text-decoration: none;
}

.salary-hint--link:hover {
  text-decoration: underline;
}

.company-meta-link {
  display: inline-block;
  margin-top: 4px;
  text-align: left;
}

.section-empty {
  padding: 36px 20px;
  text-align: center;
  color: #667085;
}

.section-link-btn,
.view-more-btn {
  cursor: pointer;
}

.card-actions {
  margin-top: auto;
  padding-top: 12px;
}

.diagnosis-btn--ghost {
  margin-left: 12px;
  color: #0066ff;
  background: rgba(255, 255, 255, 0.92);
}

.cat-diagnosis-area {
  display: flex;
  gap: 20px;
  align-items: stretch;
  padding: 30px 0;
}

.left-panel {
  display: flex;
  flex-direction: column;
  width: 280px;
  flex-shrink: 0;
  background: #fff;
  border: 1px solid #dae5f0;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
}

.cat-menu-page {
  display: flex;
  flex-direction: column;
}

.cat-menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 12px 16px;
  border: 0;
  border-bottom: 1px solid #eef5fb;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.cat-menu-item:hover,
.cat-menu-item.active {
  background: #eaf3fc;
  color: #0066ff;
}

.cat-row {
  flex: 1;
  overflow: hidden;
}

.cat-main {
  font-size: 15px;
  font-weight: 600;
}

.cat-sub-list {
  display: inline-block;
  max-width: 150px;
  margin-left: 8px;
  overflow: hidden;
  color: #888;
  font-size: 12px;
  text-overflow: ellipsis;
  vertical-align: middle;
  white-space: nowrap;
}

.cat-arrow {
  margin-left: auto;
  color: #ccd5e4;
}

.cat-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: auto;
  padding: 12px 16px;
  border-top: 1px solid #eef5fb;
}

.page-num {
  color: #0066ff;
  font-size: 14px;
  font-weight: 500;
}

.page-btns {
  display: flex;
  gap: 8px;
}

.page-btn {
  width: 24px;
  height: 24px;
  border: 0;
  border-radius: 4px;
  background: #dcecfb;
  color: #0066ff;
  cursor: pointer;
}

.page-btn:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.right-panel {
  flex: 1;
  overflow: hidden;
  border-radius: 8px;
  background: #fff;
}

.diagnosis-banner {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 380px;
  padding: 30px;
  background-image: url('/banner-bg.jpg');
  background-position: center;
  background-size: cover;
  text-align: center;
}

.text-box {
  max-width: 85%;
  padding: 30px 40px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.85);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.05);
}

.text-box h2 {
  margin: 0 0 12px;
  color: #1e2b4c;
  font-size: 28px;
}

.text-box p {
  margin: 0 0 24px;
  color: #444;
  font-size: 15px;
  line-height: 1.7;
}

.diagnosis-btn {
  border: 0;
  padding: 12px 36px;
  border-radius: 30px;
  background: #1a5cff;
  color: #fff;
  font-size: 18px;
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(26, 92, 255, 0.25);
}

.detail-panel {
  max-height: 380px;
  padding: 24px 30px;
  overflow-y: auto;
}

.detail-title {
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #eef5fb;
  color: #0066ff;
  font-size: 18px;
  font-weight: 700;
}

.detail-item {
  margin-bottom: 18px;
}

.detail-item-title {
  margin-bottom: 8px;
  font-size: 14px;
  font-weight: 600;
}

.detail-tags,
.card-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.detail-tags span,
.card-tags span {
  padding: 4px 12px;
  border-radius: 4px;
  background: #eaf3fc;
  color: #555;
  font-size: 13px;
}

.section-block {
  padding: 40px 0 20px;
}

.section-header {
  margin-bottom: 30px;
  text-align: center;
}

.section-header h2 {
  margin: 0;
  color: #222;
  font-size: 26px;
}

.section-header span,
.salary {
  color: #0066ff;
}

.grid-3 {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 20px;
}

.info-card {
  display: flex;
  flex-direction: column;
  padding: 20px;
  border: 1px solid #dae5f0;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.info-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.06);
}

.card-header-simple,
.company-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.card-header-simple {
  align-items: center;
  margin-bottom: 10px;
}

.card-header-simple.compact {
  margin-bottom: 6px;
}

.title {
  color: #222;
  font-size: 17px;
  font-weight: 600;
}

.title.small {
  font-size: 15px;
}

.company-header {
  align-items: flex-start;
  margin-bottom: 12px;
}

.company-logo {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  border-radius: 8px;
  color: #fff;
  font-size: 18px;
  font-weight: 700;
}

.company-name {
  margin-bottom: 2px;
  font-size: 16px;
  font-weight: 600;
}

.company-meta {
  color: #999;
  font-size: 12px;
  line-height: 1.5;
}

.card-desc {
  margin-bottom: 10px;
  color: #555;
  font-size: 13px;
  line-height: 1.6;
}

.card-benefits {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid #eef5fb;
  color: #0066ff;
  font-size: 13px;
}

.card-benefits.no-border {
  padding-top: 0;
  border-top: 0;
}

.company-salary .salary {
  font-weight: 600;
}

.more-btn-wrap {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}

.more-btn,
.view-more-btn {
  display: inline-block;
  border: 1px solid #0066ff;
  color: #0066ff;
  text-decoration: none;
  text-align: center;
}

.more-btn {
  width: 100%;
  padding: 8px 0;
  border-radius: 4px;
}

.view-more-wrap {
  margin-top: 30px;
  padding-bottom: 10px;
  text-align: center;
}

.view-more-btn {
  padding: 10px 40px;
  border-radius: 6px;
}

.job-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.job-list-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 20px;
  border: 1px solid #dae5f0;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.job-list-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.06);
}

.job-list-main {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  flex: 1;
  min-width: 0;
}

.job-list-logo {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  border-radius: 8px;
  color: #fff;
  font-size: 16px;
  font-weight: 700;
}

.job-list-info {
  flex: 1;
  min-width: 0;
}

.job-list-title {
  color: #222;
  font-size: 16px;
  font-weight: 600;
  line-height: 1.4;
}

.job-list-meta {
  margin-top: 4px;
  color: #667085;
  font-size: 12px;
}

.job-list-tags {
  margin-top: 8px;
}

.job-list-btn {
  flex-shrink: 0;
  padding: 8px 18px;
  border: 1px solid #0066ff;
  border-radius: 4px;
  color: #0066ff;
  font-size: 14px;
  text-decoration: none;
  white-space: nowrap;
}

.job-list-btn:hover {
  background: #f0f7ff;
}

.footer {
  margin-top: 20px;
  padding: 40px 0 20px;
  border-top: 1px solid #3a3a3a;
  background: #222;
  color: #d0d0d0;
}

.footer-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 30px;
  margin-bottom: 30px;
}

.footer h4 {
  margin: 0 0 15px;
  color: #fff;
  font-size: 16px;
}

.footer ul {
  margin: 0;
  padding: 0;
  list-style: none;
  font-size: 14px;
  line-height: 2.2;
}

.footer-bottom {
  padding-top: 20px;
  border-top: 1px solid #3a3a3a;
  color: #888;
  font-size: 12px;
  text-align: center;
}

@media (max-width: 992px) {
  .cat-diagnosis-area {
    flex-direction: column;
    align-items: stretch;
  }

  .left-panel {
    width: 100%;
  }

  .grid-3,
  .footer-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 680px) {
  .container {
    width: min(100%, calc(100% - 24px));
  }

  .search-box-wrap {
    flex-direction: column;
    gap: 12px;
    padding: 16px;
  }

  .search-box-wrap button {
    width: 100%;
  }

  .grid-3,
  .footer-grid {
    grid-template-columns: 1fr;
  }

  .job-list-item {
    flex-direction: column;
    align-items: stretch;
  }

  .job-list-btn {
    width: 100%;
    text-align: center;
  }

  .text-box {
    max-width: 100%;
    padding: 24px;
  }
}
</style>
