import request from '../utils/request'

// ========== 管理端接口 ==========

// 获取报名列表（按活动）- 管理端
export const getRegistrationList = (activityId, params = {}) => {
  return request({
    url: `/api/registrations/activities/${activityId}/registrations`,
    method: 'get',
    params
  })
}

// 审核报名
export const auditRegistration = (id, auditStatus, remark) => {
  return request({
    url: `/api/registrations/${id}/audit`,
    method: 'put',
    params: { auditStatus, remark }
  })
}

// 批量审核报名
export const batchAuditRegistration = (registrationIds, auditStatus, remark) => {
  return request({
    url: '/api/registrations/batch-audit',
    method: 'put',
    params: { registrationIds, auditStatus, remark }
  })
}

// ========== 用户端接口 ==========

// 报名活动
export const registerActivity = (activityId) => {
  return request({
    url: '/api/registrations',
    method: 'post',
    params: { activityId }
  })
}

// 取消报名
export const cancelRegistration = (id) => {
  return request({
    url: `/api/registrations/${id}`,
    method: 'delete'
  })
}

// 获取我的报名列表
export const getMyRegistrations = (params = {}) => {
  return request({
    url: '/api/registrations/my-registrations',
    method: 'get',
    params
  })
}
