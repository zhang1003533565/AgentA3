const assert = require('node:assert/strict')
const { readFileSync } = require('node:fs')
const { join } = require('node:path')
const test = require('node:test')

function source(relativePath) {
  return readFileSync(join(__dirname, relativePath), 'utf8')
}

function json(relativePath) {
  return JSON.parse(source(relativePath))
}

async function sourceModule(relativePath) {
  const text = source(relativePath)
  return import(`data:text/javascript;base64,${Buffer.from(text).toString('base64')}`)
}

test('learning package registers student learning pages without disturbing existing preload order', () => {
  const config = json('../pages.json')
  const learning = config.subPackages.find(item => item.root === 'subpackage_learning')
  assert.ok(learning)
  assert.deepEqual(learning.pages.map(item => item.path), [
    'pythonHome/pythonHome',
    'resourceGenerate/resourceGenerate',
    'learningPath/learningPath',
    'recommendations/recommendations',
    'knowledgeGraph/knowledgeGraph',
    'campusCourseDetail/campusCourseDetail'
  ])

  assert.deepEqual(config.preloadRule['pages/index/index'].packages.slice(0, 14), [
    'subpackage_promotion', 'subpackage_forum', 'subpackage_message', 'subpackage_signin',
    'subpackage_schedule', 'subpackage_lostfound', 'subpackage_facility', 'subpackage_sports',
    'subpackage_teaching', 'subpackage_dormitory', 'subpackage_community', 'subpackage_ai',
    'subpackage_meeting', 'subpackage_notice'
  ])
  assert.equal(config.preloadRule['pages/index/index'].packages.at(-1), 'subpackage_learning')
})

test('every learning page consumes one shared honest six-state contract', async () => {
  const { LEARNING_PAGE_STATES, stateCopy, classifyLearningError, learningErrorMessage } = await sourceModule('learningView.js')
  assert.deepEqual(LEARNING_PAGE_STATES, [
    'loading', 'ready', 'empty', 'dependency_unavailable', 'network_error', 'generation_failed'
  ])
  for (const state of LEARNING_PAGE_STATES) {
    assert.equal(typeof stateCopy(state).title, 'string')
    assert.ok(stateCopy(state).title.length > 0)
  }
  assert.equal(classifyLearningError({ statusCode: 503 }), 'dependency_unavailable')
  assert.equal(classifyLearningError({ errMsg: 'request:fail timeout' }), 'network_error')
  assert.equal(classifyLearningError({ data: { code: 'GENERATION_FAILED' } }), 'generation_failed')
  assert.equal(learningErrorMessage({ code: 503, msg: '课程知识库维护中' }), '课程知识库维护中')

  for (const page of [
    'pythonHome/pythonHome.vue',
    'resourceGenerate/resourceGenerate.vue',
    'learningPath/learningPath.vue',
    'recommendations/recommendations.vue'
  ]) {
    const text = source(page)
    assert.match(text, /stateCopy/)
    assert.match(text, /pageState/)
    assert.match(text, /retryLoad|retryGeneration/)
  }
})

test('learning navigation builds encoded queries without relying on a missing mini-program URL global', async () => {
  const { buildQueryString } = await sourceModule('learningView.js')
  assert.equal(buildQueryString({ topic: '列表 切片', empty: '', id: 12 }), 'topic=%E5%88%97%E8%A1%A8%20%E5%88%87%E7%89%87&id=12')
  for (const file of [
    'aiToolRoutes.js', 'pythonHome/pythonHome.vue', 'learningPath/learningPath.vue', 'recommendations/recommendations.vue'
  ]) {
    assert.doesNotMatch(source(file), /URLSearchParams/)
  }
})

test('profile dialog asks the stable Python questions one at a time and submits real answers', async () => {
  const { PYTHON_PROFILE_QUESTIONS } = await sourceModule('learningView.js')
  assert.deepEqual(PYTHON_PROFILE_QUESTIONS.map(item => item.id), [
    'python_goal',
    'python_level',
    'python_weak_topic',
    'python_resource_preference',
    'python_weekly_time'
  ])
  const dialog = source('../components/learning-profile-dialog/learning-profile-dialog.vue')
  assert.match(dialog, /submitProfileAnswer/)
  assert.match(dialog, /questionId:\s*this\.currentQuestion\.id/)
  assert.match(dialog, /answer:\s*this\.answer\.trim\(\)/)
  assert.match(dialog, /this\.questionIndex\s*\+=\s*1/)
})

test('learning pages wire home, recoverable resources, backend-owned path and all recommendation actions', () => {
  const home = source('pythonHome/pythonHome.vue')
  assert.match(home, /getPythonHome/)
  assert.match(home, /learning-profile-dialog/)
  assert.match(home, /resourceGenerate\/resourceGenerate/)
  assert.match(home, /learningPath\/learningPath/)
  assert.match(home, /recommendations\/recommendations/)
  assert.match(home, /knowledgeGraph\/knowledgeGraph/)

  const resources = source('resourceGenerate/resourceGenerate.vue')
  for (const name of ['streamLearningResources', 'getLearningWorkflow', 'retryLearningResource', 'restoreLearningState']) {
    assert.match(resources, new RegExp(`\\b${name}\\b`))
  }
  assert.match(resources, /pythonLearningWorkflowId/)
  assert.match(resources, /learning-resource-viewer/)
  assert.match(resources, /workflowId/)
  assert.match(resources, /if \(!failedTypes\.length\) return this\.startGeneration\(\)/)
  assert.match(resources, /type === 'preview'[\s\S]{0,180}resource\?\.previewUrl/)
  assert.match(resources, /downloadAssistantResource/)

  const path = source('learningPath/learningPath.vue')
  for (const name of ['getPythonPath', 'startPathItem', 'completePathItem', 'replanPythonPath']) {
    assert.match(path, new RegExp(`\\b${name}\\b`))
  }
  assert.match(path, /item\.status/)
  assert.doesNotMatch(path, /item\.status\s*=\s*['"]/)

  const recommendations = source('recommendations/recommendations.vue')
  assert.match(recommendations, /getPythonRecommendations/)
  assert.match(recommendations, /recordRecommendationInteraction/)
  for (const action of ['view', 'open', 'complete', 'dismiss']) {
    assert.match(recommendations, new RegExp(`['"]${action}['"]`))
  }
})

test('learning resources render reviewed evidence through safe markdown and expose per-type retry', () => {
  const safe = source('../components/safe-markdown/safe-markdown.vue')
  assert.match(safe, /markdownToNodes/)
  assert.match(safe, /<rich-text/)
  assert.doesNotMatch(safe, /v-html/)

  const viewer = source('../components/learning-resource-viewer/learning-resource-viewer.vue')
  assert.match(viewer, /safe-markdown/)
  assert.match(viewer, /reviewStatus|groundingStatus/)
  assert.match(viewer, /learningResourceReviewStatus/)
  assert.match(viewer, /resource\.metadata\?\.evidenceIds/)
  assert.match(viewer, /evidenceIds|evidenceChain/)
  assert.match(viewer, /\$emit\(['"]retry['"]/)
  assert.doesNotMatch(viewer, /v-html/)
})

test('knowledge graph exposes evidence-backed filtering and node actions', async () => {
  const { filterGraphNodes, graphLevels, graphStatus } = await sourceModule('knowledgeGraphView.js')
  const nodes = [
    { id: 'python.a', title: '基础', level: 0, order: 0, status: 'mastered' },
    { id: 'python.b', title: '列表切片', level: 1, order: 0, status: 'weak' }
  ]
  assert.equal(filterGraphNodes(nodes, '切片', 'all').length, 1)
  assert.equal(filterGraphNodes(nodes, '', 'weak')[0].id, 'python.b')
  assert.equal(graphLevels(nodes).length, 2)
  assert.equal(graphStatus('mastered').label, '已掌握')

  const page = source('knowledgeGraph/knowledgeGraph.vue')
  assert.match(page, /getPythonKnowledgeGraph/)
  assert.match(page, /prerequisiteIds/)
  assert.match(page, /resourceGenerate\/resourceGenerate/)
  assert.match(page, /subpackage_exam\/paperList/)
  assert.doesNotMatch(page, /v-html/)
})

test('resource generation displays the persisted multi-agent trace', () => {
  const resources = source('resourceGenerate/resourceGenerate.vue')
  assert.match(resources, /智能体协作链路/)
  assert.match(resources, /learningState\.trace/)
  assert.match(resources, /traceStatus/)
})

test('AIPPT uses real outline, slide, task, progress, preview and download APIs', () => {
  const container = source('resourceGenerate/resourceGenerate.vue')
  const page = source('resourceGenerate/AIPresentationFlow.vue')
  for (const name of [
    'generatePptOutline', 'generatePptSlides', 'createPptSlidesTask', 'createPptTask', 'streamPptTask',
    'getPptTask', 'retryPptTask', 'downloadPptPreview', 'downloadPptTaskFile'
  ]) {
    assert.match(page, new RegExp(`\\b${name}\\b`))
  }
  assert.doesNotMatch(page, /startMockGeneration|前端演示暂不提供真实文件/)
  assert.match(page, /exportFormats:\s*\['pptx', 'pdf'\]/)
  assert.match(page, /fallback[\s\S]*pollGenerationTask|pollGenerationTask/)
  assert.match(page, /retryGenerationTask/)
  assert.match(page, /重试渲染/)
  assert.match(container, /presentation-entry-card/)
  assert.match(container, /先选一套现成模板，再提交资料生成可编辑 PPT/)
  assert.match(container, /startTemplateFirstPresentation/)
  assert.match(container, /:initial-entry-mode="presentationEntryMode"/)
  assert.match(container, /presentationEntry \|\| ''/)
  assert.match(container, /presentationEntryMode = requestedEntry === 'templateFirst'/)
  assert.match(container, /if \(this\.isPresentationMode\) return[\s\S]*uni\.getStorageSync\(WORKFLOW_STORAGE_KEY\)/)
  assert.match(container, /for \(let index = 0; index < 2; index \+= 1\)[\s\S]*decodeURIComponent\(decoded\)/)
})

test('AIPPT exposes the backend template catalog and sends the selected template through generation', () => {
  const page = source('resourceGenerate/AIPresentationFlow.vue')
  assert.match(page, />PPT 模板</)
  assert.match(page, /templateExpanded/)
  assert.match(page, /pptStyles\.length/)
  assert.match(page, /downloadPptTemplateThumbnail/)
  assert.match(page, /templateId:\s*this\.pptStyle/)
  assert.match(page, /pptStyles:\s*\[\]/)
  assert.match(page, /@tap="selectPptTemplate\(template\.id\)"/)
  assert.match(page, /@tap\.stop="showTemplateDetail\(template\.id\)"/)
  assert.match(page, /下一步：选择模板/)
  assert.match(page, /initialEntryMode/)
  assert.match(page, /entryMode:\s*templateFirst \? 'templateFirst' : 'sourceFirst'/)
  assert.match(page, /templateStepIndex/)
  assert.match(page, /uploadStepIndex/)
  assert.match(page, /this\.templateFirstEnabled[\s\S]*this\.templateEntryMode = 'upload'[\s\S]*this\.currentStep = this\.uploadStepIndex/)
  assert.match(page, /!this\.templateFirstEnabled[\s\S]*this\.templateEntryMode = 'library'[\s\S]*this\.currentStep = this\.templateStepIndex/)
  assert.match(page, /template-library-card__tags/)
  assert.match(page, /layout-viewer/)
  assert.match(page, /layout-caption__desc/)
  assert.match(page, /layout\.previewItems/)
  assert.match(page, /normalizeTemplateLayout/)
  assert.match(page, /使用该模板/)
  assert.match(page, /shortTitle:\s*'上传资料'[\s\S]*shortTitle:\s*'选模板'[\s\S]*shortTitle:\s*'大纲来源'/)
  assert.match(page, /shortTitle:\s*'选模板'[\s\S]*shortTitle:\s*'上传资料'[\s\S]*shortTitle:\s*'大纲来源'/)
  assert.doesNotMatch(page, /@tap="showTemplateDetail">查看模板详情/)
  assert.doesNotMatch(page, /showTemplateUpload\('library'\)/)
  assert.doesNotMatch(page, /id:\s*'simple',\s*name:\s*'简洁学习风'/)
})

test('AIPPT upload and manual input come before template selection and keep actions floating', () => {
  const page = source('resourceGenerate/AIPresentationFlow.vue')
  assert.match(page, /previewExpanded\s*\?\s*content\s*:\s*content\.slice\(0,\s*420\)/)
  assert.match(page, /显示全部/)
  assert.match(page, /收起内容/)
  assert.match(page, /manualSourceContent/)
  assert.match(page, /applyManualSourceInput/)
  assert.match(page, /source-input-card/)
  assert.match(page, /source-input-card__head/)
  assert.match(page, /source-input-card__icon--empty/)
  assert.match(page, /source-input-card__copy/)
  assert.match(page, />FILE<\/text>/)
  assert.match(page, /source-input-card__icon::after/)
  assert.match(page, /source-input-card__icon text/)
  assert.match(page, /align-items:center;justify-content:center/)
  assert.match(page, /text-align:center/)
  assert.match(page, /source-textarea/)
  assert.match(page, /手动输入资料\.txt/)
  assert.match(page, /上传文件或直接粘贴内容/)
  assert.match(page, /fileInfo && !fileInfo\.manual[\s\S]*this\.fileContent = content/)
  assert.doesNotMatch(page, /capabilityCards/)
  assert.doesNotMatch(page, /capability-strip/)
  assert.doesNotMatch(page, /资料解析/)
  assert.doesNotMatch(page, /<text class="field__label">学习场景<\/text>/)
  assert.doesNotMatch(page, /<text class="field__label">上传学习资料<\/text>/)
  assert.doesNotMatch(page, /<text class="field__label">或直接输入内容<\/text>/)
  assert.match(page, /\[1,\s*2,\s*3,\s*4,\s*5,\s*6,\s*8\]\.includes\(this\.currentStep\)/)
  assert.match(page, /\.single-action--floating,\.bottom-actions\{position:fixed/)
  assert.match(page, /position:fixed/)
  assert.match(page, /safe-area-inset-bottom/)
  assert.match(page, /AI 正在解析文本结构/)
  assert.match(page, /正在校验并转换大纲格式/)
  assert.match(page, /operation-feedback__track/)
  assert.doesNotMatch(page, /<view class="upload-preference-card">/)
  assert.match(page, /<text class="settings-section__title">预计页数<\/text>/)
  assert.match(page, /settings-section__title settings-section__title--block">内容详细程度/)
  assert.match(page, /配图生成方式/)
  assert.match(page, /outline-design-hero/)
  assert.match(page, /template-usage-layouts/)
  assert.match(page, /generation-focus-card/)
  assert.match(page, /slide-layout-lock-row/)
  assert.match(page, /render-runtime-card/)
  assert.match(page, /operationFeedback\.progress < 88/)
  assert.doesNotMatch(page, /ppt-flow--operation-busy/)
  assert.match(page, /\.operation-feedback\{margin-top:20rpx;padding:22rpx/)
  assert.match(page, /程序员的头发正在替你加班/)
  assert.match(page, /请求仍在处理中，请不要关闭或刷新页面/)
  assert.match(page, /feedbackTicks % 3 === 0/)
  assert.match(page, /@keyframes banter-in/)
})

test('AIPPT stepper follows the approved flow-card design', () => {
  const container = source('resourceGenerate/resourceGenerate.vue')
  const page = source('resourceGenerate/AIPresentationFlow.vue')
  assert.match(page, /stepper-card/)
  assert.doesNotMatch(page, /AI 复习资料 PPT/)
  assert.doesNotMatch(page, /PPT 生成流程/)
  assert.match(page, /\{\{ currentStep \}\} \/ \{\{ stepMeta\.length \}\}/)
  assert.doesNotMatch(page, /inlinePreviousEnabled/)
  assert.doesNotMatch(page, /stepper-card__back/)
  assert.match(page, /scroll-into-view="`ppt-step-\$\{currentStep\}`"/)
  assert.match(page, /stepper__track-value/)
  assert.match(page, /stepper__track-pulse/)
  assert.match(page, /@keyframes stepper-pulse/)
  assert.match(page, /@tap="goPrevious">上一步<\/button>/)
  assert.match(page, /stepStateLabel\(item\)/)
  assert.match(page, /return '进行中'/)
  assert.match(container, /presentation-history-action/)
  assert.match(container, /history-lucide\.svg/)
  assert.match(container, /openPresentationHistory/)
  assert.doesNotMatch(page, /history-entry/)
})

test('course resources never display review success when grounding still says model-only', async () => {
  const { learningResourceReviewStatus } = await sourceModule('learningView.js')
  assert.equal(learningResourceReviewStatus({
    groundingStatus: 'model_only', metadata: { reviewStatus: 'passed' }
  }), 'model_only')
  assert.equal(learningResourceReviewStatus({
    groundingStatus: 'grounded', metadata: { reviewStatus: 'passed' }
  }), 'passed')
  assert.equal(learningResourceReviewStatus({}, true), 'generation_failed')
})

test('home keeps the existing AI entry without exposing Python learning', () => {
  const home = source('../pages/index/index.vue')
  const existing = home.indexOf("navigate('/subpackage_ai/aiCreate/aiCreate')")
  assert.ok(existing >= 0)
  assert.doesNotMatch(home, /navigate\('\/subpackage_learning\/pythonHome\/pythonHome'\)/)
  assert.doesNotMatch(home, /Python 个性化学习/)
  assert.match(home, /灵感创作/)
})
