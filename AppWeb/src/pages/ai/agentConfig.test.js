import assert from 'node:assert/strict'
import test from 'node:test'

import {
  QUESTION_GENERATION_AGENT_PREFIX,
  QUESTION_TYPE_OPTIONS,
  buildQuestionGenerationAgentMappings,
  buildLlmModelOptions,
  resolveQuestionGenerationAgentStatus,
} from './agentConfig.js'

test('exports the question generation agent prefix and all supported question types', () => {
  assert.equal(QUESTION_GENERATION_AGENT_PREFIX, 'ai.question-generation.agent.')
  assert.deepEqual(QUESTION_TYPE_OPTIONS.map(({ value }) => value), [
    'single_choice',
    'multiple_choice',
    'true_false',
    'fill_blank',
    'short_answer',
    'calculation',
    'programming',
  ])
})

test('builds tested vision model options for vision agents', () => {
  const originalGetItem = globalThis.localStorage
  globalThis.localStorage = {
    getItem: (key) => key.includes('prefixes')
      ? JSON.stringify({ 'ai.service.vision.qwen_vl': true })
      : JSON.stringify({}),
  }
  try {
    assert.deepEqual(buildLlmModelOptions([
      { configKey: 'ai.service.vision.qwen_vl.provider', configValue: 'qwen' },
      { configKey: 'ai.service.vision.qwen_vl.base-url', configValue: 'https://vision.test/v1' },
      { configKey: 'ai.service.vision.qwen_vl.api-key', configValue: 'test-key' },
      { configKey: 'ai.service.vision.qwen_vl.model', configValue: 'qwen-vl-test' },
    ]), [{
      value: 'ai.service.vision.qwen_vl',
      label: '[视觉理解] qwen-vl-test',
      modality: 'vision',
      isDefault: false,
    }])
  } finally {
    globalThis.localStorage = originalGetItem
  }
})

test('builds question type mappings from enabled non-empty config rows', () => {
  assert.deepEqual(buildQuestionGenerationAgentMappings([
    { configKey: 'ai.question-generation.agent.single_choice', configValue: 'agent_a', status: 1 },
    { configKey: 'ai.question-generation.agent.multiple_choice', configValue: '  agent_b  ', status: 1 },
    { configKey: 'ai.question-generation.agent.true_false', configValue: 'disabled_agent', status: 0 },
    { configKey: 'ai.question-generation.agent.fill_blank', configValue: '   ', status: 1 },
    { configKey: 'ai.agent-bindings.agent_a.model', configValue: 'model_a', status: 1 },
  ]), {
    single_choice: 'agent_a',
    multiple_choice: 'agent_b',
  })
})

test('marks a mapping to an absent agent as missing and ignores its orphan model binding', () => {
  assert.deepEqual(resolveQuestionGenerationAgentStatus(
    'removed_agent',
    [{ name: 'available_agent', enabled: true }],
    { removed_agent: 'ai.service.text.orphan' },
  ), {
    exists: false,
    enabled: null,
    boundModel: '',
  })
})
