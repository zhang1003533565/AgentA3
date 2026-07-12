import assert from 'node:assert/strict'
import test from 'node:test'

import { getExamPaperPublishAction, getExamPaperPublishState, getPublishRefreshArgs } from './examPaperPublishState.js'

test('未发布试卷显示未发布状态并提供发布到 App 操作', () => {
  assert.deepEqual(getExamPaperPublishState(false), { label: '未发布', color: 'default' })
  assert.deepEqual(getExamPaperPublishAction(false), {
    label: '发布到 App',
    successMessage: '试卷已发布到 App',
    confirmTitle: null,
    confirmDescription: null,
  })
})

test('已发布试卷显示已发布状态并要求确认取消发布', () => {
  assert.deepEqual(getExamPaperPublishState(true), { label: '已发布', color: 'green' })
  assert.deepEqual(getExamPaperPublishAction(true), {
    label: '取消发布',
    successMessage: '试卷已取消发布',
    confirmTitle: '确认取消发布？',
    confirmDescription: '取消后新用户将无法开始答题，已有进行中的答题不受影响。',
  })
})

test('发布操作完成后保留当前分页和搜索条件刷新', () => {
  assert.deepEqual(
    getPublishRefreshArgs({ current: 3, pageSize: 20 }, '  期末考试  '),
    [3, 20, '期末考试'],
  )
})
