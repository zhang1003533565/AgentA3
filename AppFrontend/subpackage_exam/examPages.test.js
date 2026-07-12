const assert = require('node:assert/strict')
const { readFileSync } = require('node:fs')
const { join } = require('node:path')
const test = require('node:test')

function page(relativePath) {
  return readFileSync(join(__dirname, relativePath), 'utf8')
}

test('paper list supports search, pagination, refresh and in-progress routing', () => {
  const source = page('paperList/paperList.vue')
  assert.match(source, /getExamPapers/)
  assert.match(source, /keyword/)
  assert.match(source, /pageNum/)
  assert.match(source, /loadMore/)
  assert.match(source, /refresher-enabled/)
  assert.match(source, /inProgressAttemptId/)
  assert.match(source, /attempt\/attempt/)
  assert.match(source, /paperDetail\/paperDetail/)
  assert.match(source, /加载失败/)
  assert.match(source, /暂无已发布试卷/)
})

test('paper detail starts or resumes attempts and opens blank PDF and history', () => {
  const source = page('paperDetail/paperDetail.vue')
  for (const api of ['getExamPaperDetail', 'startExamAttempt', 'downloadExamPaperPdf']) {
    assert.match(source, new RegExp(`\\b${api}\\b`))
  }
  assert.match(source, /inProgressAttemptId/)
  assert.match(source, /attempt\/attempt/)
  assert.match(source, /attemptHistory\/attemptHistory/)
  assert.match(source, /空白试卷 PDF/)
  assert.match(source, /加载失败/)
})

test('attempt history renders every attempt and routes by status', () => {
  const source = page('attemptHistory/attemptHistory.vue')
  assert.match(source, /getExamAttemptHistory/)
  assert.match(source, /attemptNo/)
  assert.match(source, /IN_PROGRESS/)
  assert.match(source, /AUTO_SUBMITTED/)
  assert.match(source, /objectiveScore/)
  assert.match(source, /attemptResult\/attemptResult/)
  assert.match(source, /attempt\/attempt/)
  assert.match(source, /暂无答题记录/)
})

test('attempt result displays objective score and post-submit answer details', () => {
  const source = page('attemptResult/attemptResult.vue')
  assert.match(source, /getExamAttemptResult/)
  assert.match(source, /objectiveScore/)
  assert.match(source, /objectiveTotalScore/)
  assert.match(source, /userAnswerJson/)
  assert.match(source, /answerJson/)
  assert.match(source, /analysis/)
  assert.match(source, /scoringJson/)
  assert.match(source, /加载失败/)
})
