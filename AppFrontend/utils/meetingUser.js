import { getUserInfo } from './storage.js'

export function getCurrentDisplayName() {
  const user = getUserInfo()
  const candidates = [
    user?.realName,
    user?.username,
    user?.personalNumber,
    user?.studentId
  ]
  const name = candidates.find(item => typeof item === 'string' && item.trim())
  return name ? name.trim() : ''
}

export function buildMeetingParticipants(extraParticipants = []) {
  const names = [getCurrentDisplayName(), ...extraParticipants]
    .filter(name => typeof name === 'string' && name.trim())
    .map(name => name.trim())
  return Array.from(new Set(names))
}

export function toMeetingMembers(participants = [], currentName = getCurrentDisplayName()) {
  return participants
    .filter(name => typeof name === 'string' && name.trim())
    .map((name, index) => {
      const displayName = name.trim()
      return {
        name: displayName,
        isSelf: !!currentName && displayName === currentName,
        className: `avatar-${['a', 'b', 'c', 'd'][index % 4]}`
      }
    })
}
