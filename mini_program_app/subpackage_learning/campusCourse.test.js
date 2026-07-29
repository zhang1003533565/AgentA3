const assert = require('node:assert/strict')
const { readFileSync } = require('node:fs')
const { join } = require('node:path')
const test = require('node:test')

const source = (relativePath) => readFileSync(join(__dirname, relativePath), 'utf8')

test('campus course API uses authenticated app endpoints for books and progress', () => {
  const api = source('../api/campusCourse.js')
  assert.match(api, /\/api\/app\/campus-courses/)
  assert.match(api, /chapters\/\$\{encodeURIComponent\(chapterId\)\}\/progress/)
  assert.match(api, /method: 'PUT'/)
})

test('campus course detail renders book chapters, progress and linked exams', () => {
  const page = source('campusCourseDetail/campusCourseDetail.vue')
  assert.match(page, /课程目录/)
  assert.match(page, /今日学习任务/)
  assert.match(page, /updateCampusChapterProgress/)
  assert.match(page, /subpackage_exam\/paperDetail\/paperDetail\?paperId=/)
  assert.match(page, /safe-markdown/)
})

test('campus course detail is registered in the learning subpackage', () => {
  const pages = source('../pages.json')
  assert.match(pages, /campusCourseDetail\/campusCourseDetail/)
})
