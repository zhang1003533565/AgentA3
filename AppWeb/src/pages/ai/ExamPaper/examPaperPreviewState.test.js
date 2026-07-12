import assert from 'node:assert/strict'
import test from 'node:test'

import {
  SOURCE_LAYOUT_DEFAULTS,
  buildExamPaperRequest,
  createPreviewSignature,
  shouldAcceptPreviewGeneration,
} from './examPaperPreviewState.js'
import { PREVIEW_REQUEST_TIMEOUT } from '../../../api/examPaperPreviewConfig.js'

test('源码默认值保持 A3 横向装订双栏和 425 栏距', () => {
  assert.deepEqual(SOURCE_LAYOUT_DEFAULTS, {
    renderMode: 'TEMPLATE',
    pageSize: 'A3',
    orientation: 'LANDSCAPE',
    marginPreset: 'BINDING',
    customMarginTop: null,
    customMarginRight: null,
    customMarginBottom: null,
    customMarginLeft: null,
    columnsCount: 2,
    columnSpace: 425,
    hasBindingLine: true,
    headerInfo: '煤矿___________    部门___________   岗位___________    姓名___________',
    titleFontSize: 50,
    subtitleFontSize: 24,
    bodyFontSize: 21,
  })
})

test('预览签名对对象字段顺序稳定但会响应题目顺序和分值变化', () => {
  const first = createPreviewSignature(
    { title: '安全考试', durationMinutes: 60, layout: { ...SOURCE_LAYOUT_DEFAULTS } },
    [{ questionId: 2, score: 5 }, { questionId: 1, score: 10 }],
  )
  const reorderedFields = createPreviewSignature(
    { layout: { bodyFontSize: 21, ...SOURCE_LAYOUT_DEFAULTS }, durationMinutes: 60, title: '安全考试' },
    [{ score: 5, questionId: 2 }, { score: 10, questionId: 1 }],
  )
  const changedScore = createPreviewSignature(
    { title: '安全考试', durationMinutes: 60, layout: { ...SOURCE_LAYOUT_DEFAULTS } },
    [{ questionId: 2, score: 6 }, { questionId: 1, score: 10 }],
  )

  assert.equal(first, reorderedFields)
  assert.notEqual(first, changedScore)
})

test('预览和确认共用完整嵌套 layout 请求且不携带 preview token', () => {
  const request = buildExamPaperRequest({
    title: '  安全考试  ',
    subtitle: '  A 卷 ',
    durationMinutes: 60,
    precautions: ' 认真作答 ',
    selectionMode: 'manual',
    layout: { ...SOURCE_LAYOUT_DEFAULTS, pageSize: 'B4' },
    previewToken: 'untrusted-token',
  }, [{ questionId: 9, score: 3 }])

  assert.equal(request.title, '安全考试')
  assert.equal(request.subtitle, 'A 卷')
  assert.equal(request.selectionMode, 'MANUAL')
  assert.equal(request.layout.pageSize, 'B4')
  assert.equal(request.layout.renderMode, 'TEMPLATE')
  assert.equal(request.questions[0].sortOrder, 1)
  assert.equal('previewToken' in request, false)
  assert.equal('pageSize' in request, false)
})

test('预览请求超时大于后端 30 秒转换上限', () => {
  assert.ok(PREVIEW_REQUEST_TIMEOUT >= 60_000)
})

test('仅当前、已挂载且签名仍匹配的 generation 可以落地', () => {
  const base = { generation: 3, currentGeneration: 3, mounted: true, requestedSignature: 'same', currentSignature: 'same' }
  assert.equal(shouldAcceptPreviewGeneration(base), true)
  assert.equal(shouldAcceptPreviewGeneration({ ...base, currentGeneration: 4 }), false)
  assert.equal(shouldAcceptPreviewGeneration({ ...base, mounted: false }), false)
  assert.equal(shouldAcceptPreviewGeneration({ ...base, currentSignature: 'changed' }), false)
})
