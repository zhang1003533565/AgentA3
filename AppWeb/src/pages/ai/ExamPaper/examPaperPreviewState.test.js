import assert from 'node:assert/strict'
import test from 'node:test'

import {
  SOURCE_LAYOUT_DEFAULTS,
  DEFAULT_RANDOM_RULES,
  buildExamPaperRequest,
  createPreviewSignature,
  createPreviewProof,
  getValidationErrorMessage,
  shouldAcceptPreviewGeneration,
} from './examPaperPreviewState.js'
import { PREVIEW_CREATE_IS_ABORTABLE, PREVIEW_REQUEST_TIMEOUT } from '../../../api/examPaperPreviewConfig.js'

test('表单校验失败时返回用户可见的首个错误', () => {
  assert.equal(getValidationErrorMessage({
    errorFields: [{ errors: ['请输入试卷标题'] }],
  }), '请输入试卷标题')
  assert.equal(getValidationErrorMessage({}), '请检查试卷信息和页面格式')
})

test('随机选题默认只请求一道题，避免小题库首次操作必然失败', () => {
  assert.deepEqual(DEFAULT_RANDOM_RULES, [{ type: 'single_choice', quantity: 1 }])
})

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

test('预览请求不携带 proof，确认请求仅携带服务端返回的 proof 字段', () => {
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
  assert.equal('previewProof' in request, false)
  const proof = createPreviewProof({ token: 't', configurationHash: 'c', questionHash: 'q', blobUrl: 'blob:x' })
  const confirmed = buildExamPaperRequest({
    title: '安全考试', selectionMode: 'manual', layout: SOURCE_LAYOUT_DEFAULTS,
  }, [{ questionId: 9, score: 3 }], proof)
  assert.deepEqual(confirmed.previewProof, { token: 't', configurationHash: 'c', questionHash: 'q' })
})

test('预览请求超时覆盖后端 10 分钟转换上限且创建请求不可取消', () => {
  assert.ok(PREVIEW_REQUEST_TIMEOUT > 600_000)
  assert.equal(PREVIEW_CREATE_IS_ABORTABLE, false)
})

test('仅当前、已挂载且签名仍匹配的 generation 可以落地', () => {
  const base = { generation: 3, currentGeneration: 3, mounted: true, requestedSignature: 'same', currentSignature: 'same' }
  assert.equal(shouldAcceptPreviewGeneration(base), true)
  assert.equal(shouldAcceptPreviewGeneration({ ...base, currentGeneration: 4 }), false)
  assert.equal(shouldAcceptPreviewGeneration({ ...base, mounted: false }), false)
  assert.equal(shouldAcceptPreviewGeneration({ ...base, currentSignature: 'changed' }), false)
})
