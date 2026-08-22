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

export const removeRegistrationByManager = (id) => {
  return request({
    url: `/api/registrations/${id}/manage`,
    method: 'delete'
  })
}

// 管理端为学生手动添加报名
export const adminAddRegistration = (activityId, userId) => {
  return request({
    url: '/api/registrations/admin/add',
    method: 'post',
    params: { activityId, userId }
  })
}

// 获取全部报名列表（管理端，可筛选）
export const getAllRegistrations = (params = {}) => {
  return request({
    url: '/api/registrations',
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
    params: { registrationIds, auditStatus, remark },
    // Spring @RequestParam Long[] 需要能绑定成 registrationIds=1&registrationIds=2
    // 避免 axios 默认序列化成 registrationIds[]=1&registrationIds[]=2 导致无法接收
    paramsSerializer: (params) => {
      const sp = new URLSearchParams()
      const ids = params.registrationIds || []
      ids.forEach((id) => {
        sp.append('registrationIds', id)
      })
      if (params.auditStatus !== undefined && params.auditStatus !== null) {
        sp.append('auditStatus', params.auditStatus)
      }
      if (params.remark !== undefined && params.remark !== null) {
        sp.append('remark', params.remark)
      }
      return sp.toString()
    }
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
