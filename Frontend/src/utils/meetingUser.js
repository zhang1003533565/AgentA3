import { getUserInfo } from './auth'

export function getCurrentDisplayName() {
  const user = getUserInfo() || {}
  const candidates = [user.realName, user.username, user.personalNumber, user.studentId]
  const name = candidates.find((item) => typeof item === 'string' && item.trim())
  return name ? name.trim() : ''
}

export function buildMeetingParticipants(extraParticipants = []) {
  const names = [getCurrentDisplayName(), ...extraParticipants]
    .filter((name) => typeof name === 'string' && name.trim())
    .map((name) => name.trim())
  return Array.from(new Set(names))
}
