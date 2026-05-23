import request from '../utils/request'

// ========== 管理端接口 ==========

// 开启签到
export const openSignIn = (activityId) => {
  return request({
    url: '/api/signins/open',
    method: 'post',
    params: { activityId }
  })
}

// 关闭签到
export const closeSignIn = (activityId) => {
  return request({
    url: '/api/signins/close',
    method: 'post',
    params: { activityId }
  })
}

// 获取签到状态
export const getSignInStatus = (activityId) => {
  return request({
    url: `/api/signins/activity/${activityId}/status`,
    method: 'get'
  })
}

// 获取签到列表（按活动）- 管理端
export const getSignInList = (activityId, page = 1, size = 999) => {
  return request({
    url: `/api/signins/activities/${activityId}/signins`,
    method: 'get',
    params: { page, size }
  })
}

export const reviewSignIn = (id, reviewStatus, remark) => {
  return request({
    url: `/api/signins/${id}/review`,
    method: 'put',
    params: { reviewStatus, remark }
  })
}

export const batchReviewSignIn = (signInIds, reviewStatus, remark) => {
  return request({
    url: '/api/signins/batch-review',
    method: 'put',
    params: { signInIds, reviewStatus, remark },
    paramsSerializer: (params) => {
      const sp = new URLSearchParams()
      const ids = params.signInIds || []
      ids.forEach((id) => sp.append('signInIds', id))
      if (params.reviewStatus !== undefined && params.reviewStatus !== null) {
        sp.append('reviewStatus', params.reviewStatus)
      }
      if (params.remark !== undefined && params.remark !== null && params.remark !== '') {
        sp.append('remark', params.remark)
      }
      return sp.toString()
    }
  })
}

// 补签 - 管理端
export const supplementSignIn = (activityId, studentId, data) => {
  return request({
    url: `/api/signins/activity/${activityId}/student/${studentId}/supplement`,
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

// ========== 用户端接口 ==========

// 学生签到
export const studentSignIn = (activityId, data) => {
  return request({
    url: `/api/signins/${activityId}`,
    method: 'post',
    data
  })
}

// 获取学生签到状态
export const getStudentSignInStatus = (activityId) => {
  return request({
    url: `/api/signins/activities/${activityId}/signin-status`,
    method: 'get'
  })
}
