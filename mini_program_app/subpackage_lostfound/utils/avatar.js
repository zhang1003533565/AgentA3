import { BASE_URL } from '@/utils/config.js'

export function firstText(...values) {
  for (const value of values) {
    if (value === null || value === undefined) continue
    const text = String(value).trim()
    if (text) return text
  }
  return ''
}

export function normalizeAvatarUrl(value) {
  const text = firstText(value)
  if (!text) return ''
  if (/^(https?:|data:|blob:|file:|wxfile:|\/static\/)/i.test(text)) return text
  if (text.startsWith('/')) return `${BASE_URL}${text}`
  return text
}

export function pickAvatar(source = {}) {
  return normalizeAvatarUrl(firstText(
    source.avatar,
    source.avatarUrl,
    source.userAvatar,
    source.profileAvatar,
    source.headImg,
    source.headImage,
    source.photo
  ))
}

export function buildDefaultAvatar(source = {}) {
  const seed = firstText(
    source.realName,
    source.username,
    source.nickName,
    source.nickname,
    source.studentId,
    source.personalNumber,
    'chat-user'
  )
  return `https://api.dicebear.com/7.x/avataaars/svg?seed=${encodeURIComponent(seed)}`
}

export function pickOtherAvatar(item = {}) {
  return normalizeAvatarUrl(firstText(
    item.otherAvatar,
    item.otherAvatarUrl,
    item.otherUserAvatar,
    item.sellerAvatar,
    item.sellerAvatarUrl,
    item.buyerAvatar,
    item.buyerAvatarUrl,
    item.userAvatar,
    item.avatar,
    item.avatarUrl
  ))
}

export function pickSenderAvatar(item = {}) {
  return normalizeAvatarUrl(firstText(
    item.senderAvatar,
    item.senderAvatarUrl,
    item.userAvatar,
    item.avatar,
    item.avatarUrl
  ))
}
