import { API_BASE_URL } from './request'
import { getToken } from '../utils/auth'

export const AI_RESOURCE_ACCEPT = [
  '.jpg', '.jpeg', '.png', '.webp', '.gif',
  '.pdf', '.doc', '.docx', '.ppt', '.pptx', '.xls', '.xlsx', '.csv',
  '.txt', '.md', '.mmd', '.json', '.zip',
  '.mp3', '.wav', '.m4a', '.ogg', '.mp4', '.mov', '.webm',
].join(',')

export async function uploadAiResource(file) {
  const body = new FormData()
  body.append('file', file)
  const token = getToken()
  const response = await fetch(`${API_BASE_URL}/api/upload/resource`, {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body,
  })
  const payload = await response.json().catch(() => ({}))
  if (!response.ok || payload?.code !== 200) {
    throw new Error(payload?.msg || payload?.message || `上传失败: ${response.status}`)
  }
  return payload.data
}

export function uploadAiResources(files) {
  return Promise.all(Array.from(files || []).map(uploadAiResource))
}
