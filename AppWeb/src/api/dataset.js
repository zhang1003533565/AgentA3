import request from '../utils/request'

const base = '/api/datasets'

// ====== Dataset（知识库） ======

export const getDatasets = (params = {}) =>
  request.get(base, { params: { current: params.current ?? 1, size: params.size ?? 20, keyword: params.keyword } })

export const getDataset = (datasetId) =>
  request.get(`${base}/${datasetId}`)

export const createDataset = (data) =>
  request.post(base, data)

export const updateDataset = (datasetId, data) =>
  request.put(`${base}/${datasetId}`, data)

export const deleteDataset = (datasetId) =>
  request.delete(`${base}/${datasetId}`)

// ====== ProcessRule（处理规则） ======

export const createProcessRule = (datasetId, data) =>
  request.post(`${base}/${datasetId}/process-rules`, data)

// ====== Document（文档） ======

export const getDocuments = (datasetId, params = {}) =>
  request.get(`${base}/${datasetId}/documents`, { params: { current: params.current ?? 1, size: params.size ?? 20, keyword: params.keyword } })

export const getDocument = (documentId) =>
  request.get(`${base}/documents/${documentId}`)

export const createDocument = (datasetId, data) =>
  request.post(`${base}/${datasetId}/documents`, data, { timeout: 120000 })

export const deleteDocument = (documentId) =>
  request.delete(`${base}/documents/${documentId}`)

export const toggleDocument = (documentId, enabled) =>
  request.put(`${base}/documents/${documentId}/toggle`, null, { params: { enabled } })

// ====== Segment（分段） ======

export const getSegments = (documentId, params = {}) =>
  request.get(`${base}/documents/${documentId}/segments`, { params: { current: params.current ?? 1, size: params.size ?? 20, keyword: params.keyword } })

export const getSegment = (segmentId) =>
  request.get(`${base}/segments/${segmentId}`)

export const updateSegment = (segmentId, data) =>
  request.put(`${base}/segments/${segmentId}`, data)

export const deleteSegment = (segmentId) =>
  request.delete(`${base}/segments/${segmentId}`)

export const toggleSegment = (segmentId, enabled) =>
  request.put(`${base}/segments/${segmentId}/toggle`, null, { params: { enabled } })

// ====== ChildChunk（子片段） ======

export const getChildChunks = (segmentId) =>
  request.get(`${base}/segments/${segmentId}/child-chunks`)

// ====== 文件转换（复用原有 RAG 代理） ======

const ragBase = '/api/ai/rag'

export const convertPdf = (data) =>
  request.post(`${ragBase}/pdf/convert`, data, { headers: { 'Content-Type': 'multipart/form-data' }, timeout: 120000 })

export const convertPpt = (data) =>
  request.post(`${ragBase}/ppt/convert`, data, { headers: { 'Content-Type': 'multipart/form-data' }, timeout: 120000 })

// ====== 召回测试（复用原有 RAG 代理） ======

export const runRagRecallTest = (data) =>
  request.post(`${ragBase}/recall-test`, data, { timeout: 120000 })

// ====== 健康检查（复用原有 RAG 代理） ======

export const getRagVectorStoreHealth = () => request.get(`${ragBase}/vector-store/health`)
export const getRagEmbeddingHealth = () => request.get(`${ragBase}/embedding/health`)
export const getRagGraphStoreHealth = () => request.get(`${ragBase}/graph-store/health`)

// ====== 系统配置 ======

export const getSystemConfigList = (params = {}) =>
  request.get('/api/system-config/list', {
    params: { current: params.current ?? 1, size: params.size ?? 10, prefixes: params.prefixes },
  })
