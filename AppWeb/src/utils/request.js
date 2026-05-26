import axios from 'axios'
import { message } from 'antd'

const getErrorMessage = (data, fallback = '请求失败') => {
  if (typeof data === 'string') return data
  return data?.msg || data?.message || data?.detail || data?.error || fallback
}

const request = axios.create({
  baseURL: 'http://localhost:8080',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json'
  }
})

request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

request.interceptors.response.use(
  (response) => {
    const res = response.data

    if (res?.code !== 200) {
      const errorMessage = getErrorMessage(res)

      if (res?.code === 401 && !response.config?.skipAuthRedirect) {
        localStorage.removeItem('token')
        localStorage.removeItem('userInfo')
        window.location.href = '/'
      }

      return Promise.reject({
        message: errorMessage,
        code: res?.code,
        data: res,
        showMessage: true
      })
    }

    return res
  },
  (error) => {
    console.error('响应错误:', error)

    if (error.response) {
      const { status, data } = error.response
      let errorMessage = getErrorMessage(data, `请求失败: ${status}`)

      if (status === 401) {
        errorMessage = getErrorMessage(data, '登录已过期，请重新登录')
        if (!error.config?.skipAuthRedirect) {
          localStorage.removeItem('token')
          localStorage.removeItem('userInfo')
          window.location.href = '/'
        }
      } else if (status === 400) {
        errorMessage = getErrorMessage(data, '请求参数错误')
      } else if (status === 403) {
        errorMessage = getErrorMessage(data, '没有权限访问')
      } else if (status === 404) {
        errorMessage = getErrorMessage(data, '请求的资源不存在')
      } else if (status === 500) {
        errorMessage = getErrorMessage(data, '服务器内部错误')
      }

      if (!error.config?.skipGlobalErrorMessage) {
        message.error(errorMessage)
      }

      return Promise.reject({
        message: errorMessage,
        code: data?.code,
        status,
        data,
        showMessage: true
      })
    }

    const errorMessage = error.request
      ? '网络请求失败，请检查网络连接'
      : error.message || '请求配置错误'

    if (!error.config?.skipGlobalErrorMessage) {
      message.error(errorMessage)
    }

    return Promise.reject({
      message: errorMessage,
      showMessage: true
    })
  }
)

export default request
