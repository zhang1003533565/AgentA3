const assert = require('node:assert/strict')
const { readFileSync } = require('node:fs')
const { join } = require('node:path')
const test = require('node:test')

function source(relativePath) {
  return readFileSync(join(__dirname, relativePath), 'utf8')
}

async function routesModule() {
  const text = source('aiToolRoutes.js')
  return import(`data:text/javascript;base64,${Buffer.from(text).toString('base64')}`)
}

test('AI Create loads published campus courses instead of hard-coding course cards', () => {
  const page = source('../subpackage_ai/aiCreate/aiCreate.vue')
  assert.match(page, /\['热门工具', '格式转换', '校园课程', '职场创意', '社交媒体'\]/)
  assert.match(page, /AI对话[\s\S]*AI伪原创[\s\S]*文案提取[\s\S]*视频去字幕[\s\S]*AI玩图/)
  assert.match(page, /试卷生成[\s\S]*题库生成[\s\S]*PPT生成/)
  assert.match(page, /getCampusCourses/)
  assert.match(page, /campusCourses\.value = records\.map/)
  assert.match(page, /campusCourseDetail\/campusCourseDetail\?courseId=/)
  assert.match(page, /v-if="tool\.courseId" class="course-cover-wrapper"/)
  assert.match(page, /course-cover-image/)
  assert.match(page, /if \(index === 2\) loadCampusCourses\(\)/)
  assert.match(page, /onMounted\(\(\) => \{\s*loadCampusCourses\(\)/)
  assert.doesNotMatch(page, /campusLoaded/)
  assert.doesNotMatch(page, /name: 'Python课程学习'/)
  assert.doesNotMatch(page, /name: '深度学习课程学习'/)
})

test('every visible AI Create action resolves to a real page and never silently returns', async () => {
  const { resolveAiToolDestination } = await routesModule()
  const visibleNames = [
    '智能写作', 'AI视频', 'AIPPT',
    'AI对话', 'AI伪原创', '文案提取', '视频去字幕', 'AI玩图',
    '试卷生成', '题库生成', 'PPT生成', '思维导图', '活动图', '架构图', '流程图', '复习资料',
    'PPT转PDF', 'PDF转PPT', 'PDF转Excel', 'PPT转图片', 'PDF转Word', 'PDF转图片', 'Word转PDF', '视频格式转换',
    'PPT大纲', '简历制作', '心得体会', '工作总结', '文本比较', '长文本写作', '周报日报', '影视解说', '文章配图', '合同模板',
    '视频灵感', '短视频文案', '视频标题', 'AI写小说', '旅游攻略', '视频介绍', '种草文案', '智能翻译', '好评文案', '带货标题',
    'Python个性化学习'
  ]
  for (const name of visibleNames) {
    const route = resolveAiToolDestination({ name, desc: `${name}的真实能力` })
    assert.match(route, /^\//, `${name} should resolve to a page`)
  }

  assert.match(resolveAiToolDestination({ name: 'PPT生成' }), /resourceGenerate\/resourceGenerate\?resourceType=presentation/)
  assert.equal(resolveAiToolDestination({ name: '试卷生成' }), '/subpackage_ai/paperHome/paperHome')
  assert.equal(resolveAiToolDestination({ name: '题库生成' }), '/subpackage_ai/questionBankGenerate/questionBankGenerate')
  assert.equal(resolveAiToolDestination({ name: '思维导图' }), '/subpackage_ai/mindmapGenerate/mindmapGenerate')
  assert.equal(resolveAiToolDestination({ name: '活动图' }), '/subpackage_ai/activityGenerate/activityGenerate')
  assert.equal(resolveAiToolDestination({ name: '架构图' }), '/subpackage_ai/architectureGenerate/architectureGenerate')
  assert.equal(resolveAiToolDestination({ name: '流程图' }), '/subpackage_ai/flowchartGenerate/flowchartGenerate')
  assert.equal(resolveAiToolDestination({ name: 'Python个性化学习' }), '/subpackage_learning/pythonHome/pythonHome')
  assert.equal(resolveAiToolDestination({ name: 'Python课程学习' }), '/subpackage_learning/pythonHome/pythonHome')
  assert.equal(resolveAiToolDestination({ name: '学习计划' }), '/subpackage_learning/learningPath/learningPath')
  assert.match(resolveAiToolDestination({ name: '深度学习课程学习', desc: 'AI辅助掌握深度学习知识' }), /aiConversation\/aiConversation\?prefill=/)
})

test('AI Create uses one total resolver for cards and tappable hero actions', () => {
  const page = source('../subpackage_ai/aiCreate/aiCreate.vue')
  assert.match(page, /resolveAiToolDestination/)
  assert.match(page, /@tap="handleToolTap\(\{ name: 'AI视频'/)
  assert.match(page, /@tap="handleToolTap\(\{ name: 'AIPPT'/)
  assert.match(page, /const destination = resolveAiToolDestination\(tool\)/)
  assert.match(page, /uni\.navigateTo\(\{\s*url: destination/)
})
