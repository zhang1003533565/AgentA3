import request from '../utils/request'

/**
 * Get AI Model Configuration (with masked API key)
 */
export function getAiModelConfig() {
  return request({
    url: '/api/ai/model-config',
    method: 'get'
  })
}

/**
 * Update AI Model Configuration
 */
export function updateAiModelConfig(data) {
  return request({
    url: '/api/ai/model-config',
    method: 'put',
    data
  })
}

/**
 * Test AI Model Connectivity
 */
export function testAiModelConfig() {
  return request({
    url: '/api/ai/model-config/test-connection',
    method: 'post'
  })
}
