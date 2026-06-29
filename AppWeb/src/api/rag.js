import request from '../utils/request'

const base = '/api/ai/rag'

export const getRagCapabilities = () => request.get(`${base}/capabilities`)

export const getRagFramework = () => request.get(`${base}/framework`)

export const getRagAgents = () => request.get(`${base}/agents`)

export const getRagAgent = (agentName) => request.get(`${base}/agents/${agentName}`)

export const saveRagAgentExampleInput = (agentName, input) => request.post(`${base}/agents/${agentName}/example-input`, { input })

export const getAgentToolCacheStats = () => request.get(`${base}/tool-cache/stats`)

export const clearAgentToolCache = () => request.delete(`${base}/tool-cache`)

export const getAiModelProviders = () => request.get(`${base}/model-providers`)

export const runRagQuery = (data) => request.post(`${base}/query`, data, {
  timeout: 120000,
})

export const convertPdf = (data) => request.post(`${base}/pdf/convert`, data, {
  headers: { 'Content-Type': 'multipart/form-data' },
  timeout: 120000,
})

export const convertPpt = (data) => request.post(`${base}/ppt/convert`, data, {
  headers: { 'Content-Type': 'multipart/form-data' },
  timeout: 120000,
})

export const getTextToSqlSchema = () => request.get(`${base}/text-to-sql/schema`)

export const executeTextToSql = (data) => request.post(`${base}/text-to-sql/execute`, data)
