const assert = require('node:assert/strict')
const { readFileSync } = require('node:fs')
const { join } = require('node:path')
const test = require('node:test')

const source = readFileSync(join(__dirname, 'profileRadar.vue'), 'utf8')

async function sourceModule(fileName) {
  const text = readFileSync(join(__dirname, fileName), 'utf8')
  return import(`data:text/javascript;base64,${Buffer.from(text).toString('base64')}`)
}

test('profile radar only renders scores after real evidence is applied', () => {
  assert.match(source, /dataStatus === 'evidence_ready'/)
  assert.match(source, /appliedEvidenceCount > 0/)
  assert.match(source, /v-else-if="hasProfileData"/)
  assert.doesNotMatch(source, /DEFAULT_DIMENSIONS|综合分|本地兜底/)
  assert.doesNotMatch(source, /score:\s*(76|82|78|72|64|68|74)/)
})

test('profile radar keeps actionable information and removes internal explanations', () => {
  assert.match(source, />能力分布</)
  assert.match(source, />画像解读</)
  assert.match(source, />优势</)
  assert.match(source, />需要关注</)
  assert.match(source, />下一步建议</)
  assert.match(source, /\/subpackage_learning\/pythonHome\/pythonHome/)

  assert.doesNotMatch(source, /LEARNING PROFILE|Leader 使用规则|置信依据|更新策略：|维度参考线/)
})

test('profile radar limits secondary labels and advice to concise sets', () => {
  assert.match(source, /profileTags\.slice\(0, 3\)/)
  assert.match(source, /improvementSuggestions\.slice\(0, 3\)/)
  assert.match(source, /advantageDimensions\.slice\(0, 3\)/)
  assert.match(source, /gapDimensions\.slice\(0, 3\)/)
})

test('profile radar draws only after loading releases and the canvas is mounted', () => {
  const finallyBlock = source.match(/finally\s*\{([\s\S]*?)\r?\n\s*\}\r?\n\s*\},\r?\n\s*handleEmptyAction/)
  assert.ok(finallyBlock)
  assert.match(finallyBlock[1], /this\.loading = false/)
  assert.match(finallyBlock[1], /this\.\$nextTick/)
  assert.match(finallyBlock[1], /this\.drawRadar\(\)/)
})

test('radar labels stay inside the canvas safe area instead of clipping at both sides', async () => {
  const { radarLabelLayout } = await sourceModule('profileRadarLayout.js')
  const left = radarLabelLayout('学习进度', { x: 28, y: 160 }, 158, 316)
  const right = radarLabelLayout('学习目标', { x: 288, y: 160 }, 158, 316)

  assert.equal(left.align, 'right')
  assert.ok(left.x - 48 >= 6)
  assert.equal(right.align, 'left')
  assert.ok(right.x + 48 <= 310)
  assert.match(source, /radarLabelLayout\(item\.shortName, label, center, size\)/)
  assert.match(source, /ctx\.fillText\(item\.shortName, labelLayout\.x, labelLayout\.y\)/)
})
