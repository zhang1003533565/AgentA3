import request from '../utils/request'

export const getLangfuseConfig = () => request({ url: '/api/ai/observability', method: 'get' })

export const updateLangfuseConfig = (data) => request({
  url: '/api/ai/observability',
  method: 'put',
  data,
})

export const testLangfuseConfig = () => request({ url: '/api/ai/observability/test', method: 'post' })
