const assert = require('node:assert/strict')
const { readFileSync } = require('node:fs')
const { join } = require('node:path')
const test = require('node:test')

async function extractorModule() {
  const text = readFileSync(join(__dirname, 'utils/mindmapTopicExtractor.js'), 'utf8')
  return import(`data:text/javascript;base64,${Buffer.from(text).toString('base64')}`)
}

test('extracts a compact center topic from typed mind map request', async () => {
  const { extractMindmapCenterTopic } = await extractorModule()

  assert.equal(
    extractMindmapCenterTopic({ userText: '请帮我生成一份Linux学习路线的思维导图' }),
    'Linux学习路线'
  )
})

test('uses clean file name as center topic when it carries the actual subject', async () => {
  const { extractMindmapCenterTopic } = await extractorModule()

  assert.equal(
    extractMindmapCenterTopic({
      fileName: '计算机课程体系.pdf',
      text: '第一章 课程导论\n第二章 操作系统与网络基础'
    }),
    '计算机课程体系'
  )
})

test('prefers parsed document title over noisy file names with names and ids', async () => {
  const { extractMindmapCenterTopic } = await extractorModule()

  assert.equal(
    extractMindmapCenterTopic({
      fileName: '成理-716-星核创研-吴洪宇（2025170203230001）.docx',
      text: '星核创研项目汇报\n项目背景、研究目标、技术路线、阶段成果'
    }),
    '星核创研项目'
  )
})
