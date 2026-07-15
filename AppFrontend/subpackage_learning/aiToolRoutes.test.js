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

test('AI Create keeps the existing visible campus tools and their order', () => {
  const page = source('../subpackage_ai/aiCreate/aiCreate.vue')
  assert.match(page, /\['热门工具', '格式转换', '校园必备', '职场创意', '社交媒体'\]/)
  assert.match(page, /AI对话[\s\S]*AI伪原创[\s\S]*文案提取[\s\S]*视频去字幕[\s\S]*AI玩图/)
  assert.match(page, /实践报告[\s\S]*课程报告[\s\S]*英语作文[\s\S]*活动总结[\s\S]*学科出题[\s\S]*学习计划[\s\S]*考研题目[\s\S]*文章主题大纲[\s\S]*雅思大作文[\s\S]*思想汇报/)
})

test('every visible AI Create action resolves to a real page and never silently returns', async () => {
  const { resolveAiToolDestination } = await routesModule()
  const visibleNames = [
    '智能写作', 'AI视频', 'AIPPT',
    'AI对话', 'AI伪原创', '文案提取', '视频去字幕', 'AI玩图',
    '试卷生成', 'PPT生成', '思维导图', '活动图', '架构图', '流程图', '复习资料',
    'PPT转PDF', 'PDF转PPT', 'PDF转Excel', 'PPT转图片', 'PDF转Word', 'PDF转图片', 'Word转PDF', '视频格式转换',
    '实践报告', '课程报告', '英语作文', '活动总结', '学科出题', '学习计划', '考研题目', '文章主题大纲', '雅思大作文', '思想汇报',
    'PPT大纲', '简历制作', '心得体会', '工作总结', '文本比较', '长文本写作', '周报日报', '影视解说', '文章配图', '合同模板',
    '视频灵感', '短视频文案', '视频标题', 'AI写小说', '旅游攻略', '视频介绍', '种草文案', '智能翻译', '好评文案', '带货标题',
    'Python代码实验'
  ]
  for (const name of visibleNames) {
    const route = resolveAiToolDestination({ name, desc: `${name}的真实能力` })
    assert.match(route, /^\//, `${name} should resolve to a page`)
  }

  assert.match(resolveAiToolDestination({ name: 'PPT生成' }), /resourceGenerate\/resourceGenerate\?resourceType=presentation/)
  assert.match(resolveAiToolDestination({ name: '思维导图' }), /resourceType=mind_map/)
  assert.match(resolveAiToolDestination({ name: '试卷生成' }), /resourceType=practice_set/)
  assert.match(resolveAiToolDestination({ name: 'Python代码实验' }), /resourceType=code_lab/)
  assert.equal(resolveAiToolDestination({ name: '学习计划' }), '/subpackage_learning/learningPath/learningPath')
  assert.match(resolveAiToolDestination({ name: '课程报告', desc: '课程报告助力提升' }), /aiConversation\/aiConversation\?prefill=/)
})

test('AI Create uses one total resolver for cards and tappable hero actions', () => {
  const page = source('../subpackage_ai/aiCreate/aiCreate.vue')
  assert.match(page, /resolveAiToolDestination/)
  assert.match(page, /@tap="handleToolTap\(\{ name: 'AI视频'/)
  assert.match(page, /@tap="handleToolTap\(\{ name: 'AIPPT'/)
  assert.match(page, /const destination = resolveAiToolDestination\(tool\)/)
  assert.match(page, /uni\.navigateTo\(\{\s*url: destination/)
})
