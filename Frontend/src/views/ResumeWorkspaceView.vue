<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import AppTabBar from '../components/AppTabBar.vue'

const router = useRouter()
const route = useRoute()

// 是否处于“仅模板市场”模式（?tab=templates），呈现迁移前的整页模板市场
const isTemplatesMode = computed(() => route.query.tab === 'templates')

// 接收嵌入应用发来的“已选择模板”通知，模板市场模式下自动跳回工作台
const handleFrameMessage = (event) => {
  const data = event.data
  if (!data || data.type !== 'aiResumeTemplateSelected') return
  if (!isTemplatesMode.value) return
  if (!data.templateId) return
  console.log('✅ 已选择模板，跳转到工作台:', data.templateId)
  router.push('/resume/workspace')
}

// 模板列表数据 - 从 templates.json 加载
const templates = ref([])

// 当前选中的模板 ID
const selectedTemplateId = ref('202501')

// 简历表单 iframe 引用
const formFrameRef = ref(null)

// 待切换模板 ID（桥接尚未就绪时暂存，iframe 加载后自动应用）
const pendingTemplateId = ref(null)

// 处理点击模板
const handleTemplateClick = (templateId) => {
  selectedTemplateId.value = templateId
  pendingTemplateId.value = templateId
  switchTemplateInFrame(templateId)
}

// 通过 iframe 内的桥接，直接调用模板市场页面原有的切换模板函数
const switchTemplateInFrame = (templateId, retries = 5) => {
  const frame = formFrameRef.value
  const win = frame && frame.contentWindow
  const bridge = win && win.__resumeBridge
  if (bridge && bridge.switchTemplate(templateId)) {
    pendingTemplateId.value = null
    console.log('✅ 已切换到模板:', templateId)
    return true
  }
  if (retries > 0) {
    setTimeout(() => switchTemplateInFrame(templateId, retries - 1), 200)
  } else {
    console.warn('❌ 模板切换失败，桥接不可用:', templateId)
  }
  return false
}

// 向 iframe 注入桥接脚本，暴露模板市场原有的切换模板能力
const injectResumeBridge = () => {
  const frame = formFrameRef.value
  const doc = frame && frame.contentDocument
  if (!doc || doc.getElementById('__resume_bridge_script__')) return
  const script = doc.createElement('script')
  script.id = '__resume_bridge_script__'
  script.textContent = `
    ;(function () {
      if (window.__resumeBridge) return
      var getResumeStore = function () {
        var appEl = document.getElementById('app')
        var app = appEl && appEl.__vue_app__
        if (!app || !app._context || !app._context.provides) return null
        var provides = app._context.provides
        var keys = Reflect.ownKeys(provides)
        for (var i = 0; i < keys.length; i++) {
          var value = provides[keys[i]]
          if (value && value._s && typeof value._s.get === 'function') {
            var store = value._s.get('resume')
            if (store && typeof store.updateResumeSetting === 'function') return store
          }
        }
        return null
      }
      window.__resumeBridge = {
        switchTemplate: function (id) {
          var store = getResumeStore()
          if (!store) return false
          store.updateResumeSetting({ currentTemplate: id })
          return true
        },
        getCurrentTemplate: function () {
          var store = getResumeStore()
          return store ? store.resumeSetting.currentTemplate : null
        }
      }
      var installTemplateNotifier = function (attempt) {
        var store = getResumeStore()
        if (store && typeof store.$onAction === 'function') {
          store.$onAction(function (ctx) {
            var patch = ctx.args[0]
            if (ctx.name === 'updateResumeSetting' && patch && patch.currentTemplate) {
              ctx.after(function () {
                try {
                  window.parent.postMessage({ type: 'aiResumeTemplateSelected', templateId: patch.currentTemplate }, '*')
                } catch (e) {}
              })
            }
          })
        } else if (attempt < 10) {
          setTimeout(function () { installTemplateNotifier(attempt + 1) }, 200)
        }
      }
      installTemplateNotifier(0)
    })()
  `
  doc.head.appendChild(script)
}

// 应用 iframe 内嵌页面的样式覆盖：隐藏顶部导航栏、改为淡蓝色商务风、撑满高度
const applyFrameStyleOverrides = () => {
  const frame = formFrameRef.value
  const doc = frame && frame.contentDocument
  if (!doc || doc.getElementById('__resume_style_overrides__')) return
  const style = doc.createElement('style')
  style.id = '__resume_style_overrides__'
  style.textContent = `
    /* 隐藏顶部导航栏（简历制作/模板市场/AI深度交流/网站配置/简历模板设计） */
    header.navbar { display: none !important; }

    /* 撑满 iframe 高度，左侧简历板块与右侧模板市场等高、底部不留空白 */
    .resume[data-v-d9239fc1] { height: 100vh !important; }

    /* 顶部按钮排（预览填充/清空数据/导出JSON/导入JSON）整体右移，为返回按钮留出空间 */
    .btn-group[data-v-d9239fc1] {
      gap: 12px !important;
      padding-left: 130px !important;
      justify-content: center !important;
    }
    .btn-group[data-v-d9239fc1] .ant-btn {
      padding-left: 10px !important;
      padding-right: 10px !important;
    }

    /* 淡蓝色商务风主题变量（替换原有紫色系） */
    :root {
      --bg-color: #f2f7fc !important;
      --bg-card-color: #ffffff !important;
      --text-color: #1f2937 !important;
      --text-color2: #ffffff !important;
      --primary-color: #2f6fed !important;
      --primary-color-hover: #245ac2 !important;
      --primary-color-active: #1d4f9e !important;
      --card-color: #ffffff !important;
      --color-1: #0e2f5e !important;
      --color-2: #17468a !important;
      --color-3: #2f6fed !important;
      --color-4: #3a78c4 !important;
      --color-5: #5b96d6 !important;
      --color-6: #93bae3 !important;
      --color-7: #e2edf9 !important;
      --chat-bg: #f5f8fc !important;
      --chat-user-bubble: var(--color-4) !important;
      --chat-ai-bubble: var(--bg-card-color) !important;
      --chat-bubble-shadow: rgba(15, 23, 42, 0.06) !important;
      --chat-input-bg: var(--bg-card-color) !important;
      --chat-border: #dbe7f4 !important;
      --chat-input-text: var(--text-color) !important;
      --chat-placeholder: #8aa2bd !important;
    }
    body { background-color: var(--bg-color) !important; }

    /* Ant Design 主色：按钮 / 链接 / 焦点 / 选中态统一改为蓝色 */
    .ant-btn-primary,
    .ant-btn-primary:not(:disabled):focus,
    .ant-btn-primary:not(:disabled):active {
      background-color: #2f6fed !important;
      border-color: #2f6fed !important;
    }
    .ant-btn-primary:not(:disabled):hover {
      background-color: #245ac2 !important;
      border-color: #245ac2 !important;
    }
    .ant-btn-primary.ant-btn-background-ghost {
      background: transparent !important;
      color: #2f6fed !important;
      border-color: #2f6fed !important;
    }
    .ant-btn-primary.ant-btn-background-ghost:not(:disabled):hover {
      background: transparent !important;
      color: #245ac2 !important;
      border-color: #245ac2 !important;
    }
    .ant-btn-link,
    a { color: #2f6fed !important; }
    .ant-input:focus,
    .ant-input-focused,
    .ant-select-focused .ant-select-selector,
    .ant-select:not(.ant-select-disabled):hover .ant-select-selector,
    .ant-picker:hover,
    .ant-picker-focused,
    .ant-input-number:hover,
    .ant-input-number-focused {
      border-color: #5b96d6 !important;
      box-shadow: 0 0 0 2px rgba(47, 111, 237, 0.12) !important;
    }
    .ant-checkbox-checked .ant-checkbox-inner,
    .ant-radio-checked .ant-radio-inner,
    .ant-switch-checked {
      border-color: #2f6fed !important;
      background-color: #2f6fed !important;
    }
    .ant-radio-checked .ant-radio-inner::after {
      background-color: #2f6fed !important;
    }
    .ant-select-item-option-selected {
      background-color: rgba(47, 111, 237, 0.1) !important;
      color: #2f6fed !important;
    }
    .ant-collapse > .ant-collapse-item > .ant-collapse-header .ant-collapse-arrow,
    .ant-message .anticon {
      color: #2f6fed !important;
    }
  `
  doc.head.appendChild(style)
}

// iframe 加载完成后注入桥接，并应用等待中的模板切换
const onFrameLoaded = () => {
  injectResumeBridge()
  applyFrameStyleOverrides()
  // 同步嵌入应用当前模板到右侧选中态
  const frame = formFrameRef.value
  const bridge = frame && frame.contentWindow && frame.contentWindow.__resumeBridge
  if (bridge && typeof bridge.getCurrentTemplate === 'function' && !pendingTemplateId.value) {
    const current = bridge.getCurrentTemplate()
    if (current) selectedTemplateId.value = current
  }
  if (pendingTemplateId.value) {
    switchTemplateInFrame(pendingTemplateId.value)
  }
}

// 模板效果缩略图映射（与模板市场页面使用的资源一致）
const THUMBNAIL_MAP = {
  templateA: '/airesume/assets/preview-2zk25jB6.jpg',
  templateB: '/airesume/assets/preview-Cd6e9xvr.jpg',
  templateC: '/airesume/assets/preview-Ck76ZAWi.jpg',
  templateD: '/airesume/assets/preview-B2AHLd9n.jpg',
  dev: '/airesume/assets/preview-BhqGPQHX.jpg',
}

// 根据 folderPath 获取模板颜色
const getTemplateColor = (folderPath) => {
  const colorMap = {
    'templateA': '#165DFF',
    'templateB': '#0f4dbf',
    'templateC': '#4A90E8',
    'templateD': '#1e40af',
    'dev': '#3b82f6',
  }
  return colorMap[folderPath] || '#165DFF'
}

// 获取缩略图 URL
const getThumbnailUrl = (folderPath, fileName) => {
  return THUMBNAIL_MAP[folderPath] || `/airesume/${folderPath}/${fileName}`
}

// 图片加载失败时的回退处理
const handleImageError = (event, folderPath) => {
  // 如果缩略图加载失败，回退到默认模板缩略图
  event.target.src = THUMBNAIL_MAP[folderPath] || THUMBNAIL_MAP.templateA
  console.warn(`❌ 模板 ${folderPath} 的缩略图加载失败`)
}

// 加载模板列表
onMounted(async () => {
  window.addEventListener('message', handleFrameMessage)
  try {
    const response = await fetch('/airesume/templates.json')
    const data = await response.json()
    templates.value = data
    console.log('✅ 模板列表加载成功:', templates.value.length, '个模板')
  } catch (error) {
    console.error('❌ 模板列表加载失败:', error)
    // 如果加载失败，使用默认空数组
    templates.value = []
  }
})

onUnmounted(() => {
  window.removeEventListener('message', handleFrameMessage)
})
</script>

<template>
  <div class="two-column-layout">
    <!-- 顶部 AppTabBar -->
    <AppTabBar />

    <!-- 仅模板市场模式：整页展示模板市场（迁移前形态，不跳转） -->
    <template v-if="isTemplatesMode">
      <iframe
        ref="formFrameRef"
        src="/airesume/index.html?embedded=1#/template"
        title="简历模板市场"
        class="templates-full-frame"
        @load="onFrameLoaded"
      ></iframe>
    </template>

    <!-- 工作台模式：两栏布局（左侧简历填写与预览 + 右侧模板市场） -->
    <template v-else>
      <!-- 返回简历页面按钮 -->
      <button class="workspace-back" type="button" aria-label="返回简历页面" @click="router.push('/resume')">
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="m15 5-7 7 7 7" />
        </svg>
        <span>返回</span>
      </button>
      
      <!-- 两栏布局容器：左侧表单 + 右侧模板市场 -->
      <div class="layout-container">
        <!-- 第 1 栏：简历填写与预览 -->
        <div class="left-panel">
          <iframe
            ref="formFrameRef"
            src="/airesume/index.html?embedded=1#/"
            title="简历信息录入"
            class="form-frame"
            @load="onFrameLoaded"
          ></iframe>
        </div>
        
        <!-- 第 2 栏：模板市场板块（新增）-->
        <div class="templates-panel">
          <div class="templates-header">
            <h3 class="templates-title">模板市场</h3>
            <p class="templates-subtitle">选择喜欢的简历模板</p>
          </div>
          
          <div class="templates-body">
            <div class="templates-grid">
              <div
                v-for="template in templates"
                :key="template.id"
                class="template-card"
                :class="{ active: selectedTemplateId === template.id }"
                @click="handleTemplateClick(template.id)"
              >
                <div class="template-preview-box">
                  <!-- 显示真实的简历缩略图 -->
                  <img 
                    :src="getThumbnailUrl(template.folderPath, template.thumbnail)"
                    :alt="template.name"
                    class="template-thumbnail"
                    @error="handleImageError($event, template.folderPath)"
                  />
                </div>
                <div class="template-info">
                  <div class="template-name">{{ template.name }}</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.two-column-layout {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  background-color: #f2f7fc;
}

.workspace-back {
  position: fixed;
  top: 72px;
  left: 16px;
  z-index: 100;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 14px;
  border: 1px solid #d0e3fd;
  border-radius: 18px;
  background: #ffffff;
  color: #245ac2;
  font-size: 14px;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.08);
  transition: all 0.2s ease;
}

.workspace-back:hover {
  background: #eef4fb;
  border-color: #9dc0ec;
}

.workspace-back svg {
  width: 16px;
  height: 16px;
  fill: none;
  stroke: currentColor;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.layout-container {
  display: flex;
  align-items: stretch;
  position: absolute;
  top: 60px;
  left: 0;
  right: 0;
  bottom: 0;
  gap: 0; /* 无间隔，通过 padding 控制 */
}

/* ===== 第 1 栏：简历填写表单（原样保留）===== */
.left-panel {
  flex: 1;
  min-width: 450px;
  height: 100%;
  background-color: transparent;
}

.form-frame {
  width: 100%;
  height: 100%;
  border: none;
  display: block;
}

/* 仅模板市场模式：整页模板市场 iframe */
.templates-full-frame {
  position: absolute;
  top: 60px;
  left: 0;
  right: 0;
  bottom: 0;
  width: 100%;
  height: calc(100vh - 60px);
  border: none;
  display: block;
  background: #f2f7fc;
}

/* ===== 第 2 栏：模板市场（关闭自动拉伸）===== */
.templates-panel {
  width: 220px;
  min-width: 220px;
  max-width: 250px;
  height: 100%;
  background-color: #f2f7fc;
  border-left: 1px solid #d0e3fd;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  flex-grow: 0; /* 关闭自动拉伸 */
}

.templates-header {
  padding: 12px 8px;
  border-bottom: 1px solid #d0e3fd;
  background-color: #ffffff;
  flex-shrink: 0;
}

.templates-title {
  margin: 0 0 4px 0;
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
  text-align: center;
}

.templates-subtitle {
  margin: 0;
  font-size: 11px;
  color: #94a3b8;
  text-align: center;
}

.templates-body {
  flex: 1;
  overflow-y: auto;
  padding: 0; /* 消除左右空白 */
  display: flex;
  flex-direction: column;
}

/* 垂直均匀排列 */
.templates-grid {
  display: flex;
  flex-direction: column;
  gap: 12px; /* 均匀上下间距 */
  align-items: stretch;
  padding: 8px; /* 增加内边距适应缩略图 */
}

.template-card {
  cursor: pointer;
  transition: all 0.2s ease;
  width: 100%; /* 填满整个容器，左右不留白 */
}

.template-card:hover .template-preview-box {
  transform: translateY(-2px);
}

.template-card.active .template-preview-box {
  transform: scale(1.03); /* 稍微缩小缩放效果 */
}

/* 调整卡片高度 - 增大缩略图尺寸 */
.template-preview-box {
  border-radius: 6px;
  overflow: hidden;
  background-color: #ffffff;
  border: 1px solid #e8f0fe;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.06);
  transition: all 0.2s ease;
  aspect-ratio: 210 / 297; /* A4 纸张比例 */
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 6px;
}

.template-thumbnail {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.template-name {
  margin-top: 4px; /* 减小上边距 */
  text-align: center;
  font-size: 10px; /* 适应更窄容器 */
  font-weight: 500;
  color: #475569;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 模板信息区域 */
.template-info {
  padding: 8px 6px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

</style>
