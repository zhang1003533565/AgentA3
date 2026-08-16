/**
 * 本地存储封装（token、用户信息等）
 */
const TOKEN_KEY = 'token'
const USER_INFO_KEY = 'userInfo'

export function getToken() {
  return uni.getStorageSync(TOKEN_KEY) || ''
}

export function setToken(token) {
  uni.setStorageSync(TOKEN_KEY, token)
}

export function getUserInfo() {
  try {
    const str = uni.getStorageSync(USER_INFO_KEY)
    return str ? JSON.parse(str) : null
  } catch {
    return null
  }
}

/**
 * 获取当前登录用户 ID。
 * 优先取 userInfo 中的 id/userId；若缺失（旧版本登录数据不含 id），
 * 则从 JWT token 的 payload 中解析 userId claim。
 */
export function getCurrentUserId() {
  const userInfo = getUserInfo()
  if (userInfo) {
    const id = userInfo.id || userInfo.userId
    if (id) return id
  }
  const token = getToken()
  if (!token) return ''
  try {
    const parts = token.split('.')
    if (parts.length < 2) return ''
    // base64url -> base64
    let payload = parts[1].replace(/-/g, '+').replace(/_/g, '/')
    while (payload.length % 4 !== 0) payload += '='
    const decoded = decodeURIComponent(escape(atob(payload)))
    const data = JSON.parse(decoded)
    return data.userId || ''
  } catch (error) {
    return ''
  }
}

export function setUserInfo(info) {
  uni.setStorageSync(USER_INFO_KEY, JSON.stringify(info || {}))
}

/** 清除登录态（登出或 401 时调用） */
export function clearAuth() {
  uni.removeStorageSync(TOKEN_KEY)
  uni.removeStorageSync(USER_INFO_KEY)
}

const FORUM_READ_FLAG_KEY = 'forum_message_read_flags'

/** 读取论坛消息分类已读标记（兼容对象与 JSON 字符串两种存储形式） */
function getForumReadFlags() {
  try {
    const raw = uni.getStorageSync(FORUM_READ_FLAG_KEY)
    if (!raw) return {}
    if (typeof raw === 'object') return raw
    if (typeof raw === 'string') {
      try {
        const parsed = JSON.parse(raw)
        return parsed && typeof parsed === 'object' ? parsed : {}
      } catch (e) {
        return {}
      }
    }
    return {}
  } catch (e) {
    return {}
  }
}

/** 将某分类（like/comment/system）标记为已读 */
export function markForumCategoryRead(category) {
  if (!category) return
  const flags = getForumReadFlags()
  flags[category] = true
  try {
    uni.setStorageSync(FORUM_READ_FLAG_KEY, flags)
  } catch (e) {}
}

/** 判断某分类是否已读 */
export function isForumCategoryRead(category) {
  const flags = getForumReadFlags()
  return flags[category] === true
}

