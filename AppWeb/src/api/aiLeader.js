import request from '../utils/request'

const base = '/api/ai/leader'

export const queryLeaderAgent = (data) => request.post(`${base}/query`, {
  ...(data || {}),
  agentName: 'leader_agent',
  metadata: {
    source: 'web_ai_conversation',
    ...((data && data.metadata) || {}),
  },
}, { timeout: 120000 })

export const getLeaderSessions = (params = {}) => request.get(`${base}/sessions`, { params })

export const getLeaderSessionDetail = (sessionId) => (
  request.get(`${base}/sessions/${encodeURIComponent(sessionId)}`)
)
