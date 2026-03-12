// Token 存储
export const setToken = (token) => {
  localStorage.setItem('token', token)
}

export const getToken = () => {
  return localStorage.getItem('token')
}

export const removeToken = () => {
  localStorage.removeItem('token')
}

// 用户信息存储
export const setUserInfo = (userInfo) => {
  localStorage.setItem('userInfo', JSON.stringify(userInfo))
}

export const getUserInfo = () => {
  const userInfo = localStorage.getItem('userInfo')
  return userInfo ? JSON.parse(userInfo) : null
}

export const removeUserInfo = () => {
  localStorage.removeItem('userInfo')
}

// 清除所有登录信息
export const clearAuth = () => {
  removeToken()
  removeUserInfo()
}
