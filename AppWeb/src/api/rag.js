import request from '../utils/request'

const base = '/api/ai/rag'

export const getRagStrategies = () => request.get(`${base}/strategies`)

export const getRagStrategy = (strategyName) => request.get(`${base}/strategies/${strategyName}`)

export const getRagCapabilities = () => request.get(`${base}/capabilities`)

export const getRagFramework = () => request.get(`${base}/framework`)

export const getRagAgents = () => request.get(`${base}/agents`)

export const getRagAgent = (agentName) => request.get(`${base}/agents/${agentName}`)

export const saveRagAgentExampleInput = (agentName, input) => request.post(`${base}/agents/${agentName}/example-input`, { input })

export const getAiModelProviders = () => request.get(`${base}/model-providers`)

export const runRagQuery = (data) => request.post(`${base}/query`, data, {
  timeout: 120000,
})

export const runRagRecallTest = (data) => request.post(`${base}/recall-test`, data, {
  timeout: 120000,
})

export const ingestRagDocuments = (data) => request.post(`${base}/documents`, data, {
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

export const getRagDocuments = () => request.get(`${base}/documents`)

export const getRagDocumentChunks = (params) => request.get(`${base}/documents/chunks`, { params })

export const getRagVectorStoreHealth = () => request.get(`${base}/vector-store/health`)

export const getRagEmbeddingHealth = () => request.get(`${base}/embedding/health`)

export const getRagGraphStoreHealth = () => request.get(`${base}/graph-store/health`)

export const getTextToSqlSchema = () => request.get(`${base}/text-to-sql/schema`)

export const executeTextToSql = (data) => request.post(`${base}/text-to-sql/execute`, data)

export const evaluateRag = (data) => request.post(`${base}/evaluate`, data, {
  timeout: 120000,
})
