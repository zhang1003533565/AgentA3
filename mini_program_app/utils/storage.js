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

export function setUserInfo(info) {
  uni.setStorageSync(USER_INFO_KEY, JSON.stringify(info || {}))
}

/** 清除登录态（登出或 401 时调用） */
export function clearAuth() {
  uni.removeStorageSync(TOKEN_KEY)
  uni.removeStorageSync(USER_INFO_KEY)
}
