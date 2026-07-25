import axios from 'axios'
import request from '../utils/request'
import { API_BASE_URL } from '../config/apiBase'

const base = '/api/knowledge/maxkb'

export const getMaxKbEnvironments = () => request.get(`${base}/environments`)

export const getMaxKbAccounts = (params = {}) => request({
  url: `${base}/accounts`,
  method: 'get',
  params,
})

export const createMaxKbAccount = (data) => request.post(`${base}/accounts`, data)

export const updateMaxKbAccount = (accountId, data) => request.put(`${base}/accounts/${accountId}`, data)

export const deleteMaxKbAccount = (accountId) => request.delete(`${base}/accounts/${accountId}`)

export const updateMaxKbAccountStatus = (accountId, status) =>
  request.put(`${base}/accounts/${accountId}/status`, { status })

export const testMaxKbAccount = (accountId) => request.post(`${base}/accounts/${accountId}/test`)

export const getMaxKbKnowledges = (accountId, params = {}) => request({
  url: `${base}/accounts/${accountId}/knowledges`,
  method: 'get',
  params,
  timeout: 30000,
})

export const getMaxKbKnowledgeDetail = (accountId, knowledgeId) =>
  request.get(`${base}/accounts/${accountId}/knowledges/${knowledgeId}`)

export const getMaxKbDocuments = (accountId, knowledgeId, params = {}) => request({
  url: `${base}/accounts/${accountId}/knowledges/${knowledgeId}/documents`,
  method: 'get',
  params,
  timeout: 30000,
})

export const uploadMaxKbDocuments = (accountId, knowledgeId, data) => request.post(
  `${base}/accounts/${accountId}/knowledges/${knowledgeId}/documents/upload`,
  data,
  {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000,
  }
)

export const getMaxKbParagraphs = (accountId, knowledgeId, documentId, params = {}) => request({
  url: `${base}/accounts/${accountId}/knowledges/${knowledgeId}/documents/${documentId}/paragraphs`,
  method: 'get',
  params,
  timeout: 30000,
})

export const getMaxKbAssetUrl = (accountId, path) =>
  `${base}/accounts/${accountId}/assets?path=${encodeURIComponent(path || '')}`

export const fetchMaxKbAsset = async (accountId, path) => {
  const token = localStorage.getItem('token')
  const response = await axios.get(`${API_BASE_URL}${getMaxKbAssetUrl(accountId, path)}`, {
    responseType: 'blob',
    timeout: 30000,
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
  return response.data
}

export const runMaxKbHitTest = (accountId, data) => request.post(
  `${base}/accounts/${accountId}/hit-test`,
  data,
  { timeout: 60000 }
)

export const runKnowledgeAgentChat = (data) => request.post(
  `${base}/chat`,
  data,
  { timeout: 120000 }
)

export const getKnowledgeChatCacheStats = () => request.get(`${base}/chat/cache/stats`)

export const clearKnowledgeChatCache = () => request.delete(`${base}/chat/cache`)
