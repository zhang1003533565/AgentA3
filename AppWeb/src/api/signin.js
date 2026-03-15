import request from '../utils/request'

// 获取签到列表（按活动）
export const getSignInList = (activityId) => {
  return request({
    url: `/api/signins/activities/${activityId}/signins`,
    method: 'get'
  })
}

// 补签
export const supplementSignIn = (id, data) => {
  return request({
    url: `/api/signins/${id}/supplement`,
    method: 'post',
    data
  })
}

// 导出签到名单
export const exportSignInList = (activityId) => {
  return request({
    url: `/api/signins/activities/${activityId}/export`,
    method: 'get',
    responseType: 'blob'
  })
}
