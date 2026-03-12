import axios from 'axios'
import { message } from 'antd'

// 创建 axios 实例
const request = axios.create({
  baseURL: 'http://localhost:8080', // 后端接口地址
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
request.interceptors.request.use(
  (config) => {
    // 在发送请求之前做些什么
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    // 对请求错误做些什么
    return Promise.reject(error)
  }
)

// 响应拦截器
request.interceptors.response.use(
  (response) => {
    // 对响应数据做点什么
    const res = response.data
    
    // 如果返回的状态码不是 200，说明接口有问题，把错误信息抛出去
    if (res.code !== 200) {
      message.error(res.msg || res.message || '请求失败')
      
      // 401: Token过期或无效
      if (res.code === 401) {
        // 清除token并跳转到登录页
        localStorage.removeItem('token')
        localStorage.removeItem('userInfo')
        window.location.href = '/'
      }
      
      return Promise.reject(new Error(res.msg || res.message || '请求失败'))
    }
    
    return res
  },
  (error) => {
    // 对响应错误做点什么
    console.error('响应错误:', error)
    
    if (error.response) {
      const { status, data } = error.response
      
      switch (status) {
        case 400:
          message.error(data.message || '请求参数错误')
          break
        case 401:
          message.error('登录已过期，请重新登录')
          localStorage.removeItem('token')
          localStorage.removeItem('userInfo')
          window.location.href = '/'
          break
        case 403:
          message.error('没有权限访问')
          break
        case 404:
          message.error('请求的资源不存在')
          break
        case 500:
          message.error('服务器内部错误')
          break
        default:
          message.error(data.message || `请求失败: ${status}`)
      }
    } else if (error.request) {
      message.error('网络请求失败，请检查网络连接')
    } else {
      message.error('请求配置错误')
    }
    
    return Promise.reject(error)
  }
)

export default request
