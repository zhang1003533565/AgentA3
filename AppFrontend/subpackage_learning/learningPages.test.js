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

test('learning package registers all four student pages without disturbing existing preload order', () => {
  const config = json('../pages.json')
  const learning = config.subPackages.find(item => item.root === 'subpackage_learning')
  assert.ok(learning)
  assert.deepEqual(learning.pages.map(item => item.path), [
    'pythonHome/pythonHome',
    'resourceGenerate/resourceGenerate',
    'learningPath/learningPath',
    'recommendations/recommendations'
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
  assert.match(home, /智感工坊/)
})
