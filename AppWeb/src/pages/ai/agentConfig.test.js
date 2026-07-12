import assert from 'node:assert/strict'
import test from 'node:test'

import {
  QUESTION_GENERATION_AGENT_PREFIX,
  QUESTION_TYPE_OPTIONS,
  buildQuestionGenerationAgentMappings,
} from './agentConfig.js'

test('exports the question generation agent prefix and five question types', () => {
  assert.equal(QUESTION_GENERATION_AGENT_PREFIX, 'ai.question-generation.agent.')
  assert.deepEqual(QUESTION_TYPE_OPTIONS.map(({ value }) => value), [
    'single_choice',
    'multiple_choice',
    'true_false',
    'fill_blank',
    'short_answer',
  ])
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
