const assert = require('node:assert/strict')
const { readFileSync } = require('node:fs')
const { join } = require('node:path')
const test = require('node:test')

function source(relativePath) {
  return readFileSync(join(__dirname, relativePath), 'utf8')
}

async function learningViewModule() {
  const text = source('learningView.js')
  return import(`data:text/javascript;base64,${Buffer.from(text).toString('base64')}`)
}

function loadDialog(submitProfileAnswer, questions) {
  const script = source('../components/learning-profile-dialog/learning-profile-dialog.vue')
    .match(/<script>([\s\S]*?)<\/script>/)?.[1]
  assert.ok(script, 'profile dialog script should exist')
  const executable = script
    .replace(/^import .*$/gm, '')
    .replace('export default {', 'return {')
  return new Function(
    'submitProfileAnswer',
    'learningErrorMessage',
    'PYTHON_PROFILE_QUESTIONS',
    executable
  )(submitProfileAnswer, (_error, fallback) => fallback, questions)
}

function mountDialog(component, answeredQuestionIds = []) {
  const events = []
  const vm = {
    visible: true,
    answeredQuestionIds: [...answeredQuestionIds],
    ...component.data(),
    ...component.methods,
    $emit(event, payload) {
      events.push([event, payload])
      if (event === 'answered') {
        this.answeredQuestionIds = [...this.answeredQuestionIds, payload.questionId]
      }
    }
  }
  Object.defineProperty(vm, 'questions', {
    get: () => component.computed.questions.call(vm)
  })
  Object.defineProperty(vm, 'currentQuestion', {
    get: () => component.computed.currentQuestion.call(vm)
  })
  const visibleWatcher = component.watch.visible
  const handler = typeof visibleWatcher === 'function' ? visibleWatcher : visibleWatcher.handler
  handler.call(vm, true)
  return { vm, events }
}

test('profile dialog keeps one fixed round and submits all five Python questions without skipping', async () => {
  const { PYTHON_PROFILE_QUESTIONS } = await learningViewModule()
  const submitted = []
  const component = loadDialog(async payload => {
    submitted.push(payload)
    return { data: { updated: true } }
  }, PYTHON_PROFILE_QUESTIONS)
  const { vm, events } = mountDialog(component)
  const expectedIds = PYTHON_PROFILE_QUESTIONS.map(question => question.id)

  for (const expectedId of expectedIds) {
    assert.equal(vm.currentQuestion?.id, expectedId)
    vm.answer = `回答 ${expectedId}`
    await vm.submitAnswer()
  }

  assert.deepEqual(submitted.map(answer => answer.questionId), expectedIds)
  assert.equal(vm.currentQuestion, null)
  assert.equal(events.filter(([event]) => event === 'complete').length, 1)
})
