import request from '../utils/request'

// 获取报名列表（按活动）
export const getRegistrationList = (activityId) => {
  return request({
    url: `/api/registrations/activities/${activityId}/registrations`,
    method: 'get'
  })
}

// 审核报名
export const auditRegistration = (id, status, remark) => {
  return request({
    url: `/api/registrations/${id}/audit`,
    method: 'put',
    data: { status, remark }
  })
}

// 批量审核报名
export const batchAuditRegistration = (ids, status, remark) => {
  return request({
    url: '/api/registrations/batch-audit',
    method: 'put',
    data: { ids, status, remark }
  })
}
