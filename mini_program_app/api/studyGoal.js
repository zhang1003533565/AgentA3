/**
 * 学习计划结构化拆解接口封装
 *
 * 链路：上传 .xlsx/.csv 或粘贴文本 -> AI 拆解预览 -> 确认入库 -> 勾选任务联动进度
 */
import { request } from '@/utils/request.js'
import { BASE_URL } from '@/utils/config.js'
import { getToken } from '@/utils/storage.js'

const REQUEST_TIMEOUT = 120000

/** 解析 uni.uploadFile 返回的 Result<T> 结构 */
function handleUploadResponse(res, resolve, reject) {
  let body = res.data
  if (typeof body === 'string') {
    try {
      body = JSON.parse(body)
    } catch (error) {
      uni.showToast({ title: '响应解析失败，请稍后重试', icon: 'none' })
      reject(new Error('响应解析失败'))
      return
    }
  }
  if (body && body.code === 200) {
    resolve(body)
    return
  }
  const message = (body && (body.msg || body.message)) || `请求失败: ${res.statusCode}`
  uni.showToast({ title: message, icon: 'none' })
  reject(body || new Error(message))
}

/**
 * 上传数据表拆解（.xlsx/.csv），返回 { code, msg, data: { goal, tasks } } 预览结果
 */
export function decomposeStudyFile(filePath) {
  return new Promise((resolve, reject) => {
    const token = getToken()
    uni.uploadFile({
      url: `${BASE_URL}/api/study-goal/decompose`,
      filePath,
      name: 'file',
      header: token ? { Authorization: `Bearer ${token}` } : {},
      success: (res) => handleUploadResponse(res, resolve, reject),
      fail: (err) => {
        uni.showToast({ title: '上传失败，请检查网络', icon: 'none' })
        reject(err)
      }
    })
  })
}

/**
 * 粘贴学习计划文本拆解，返回结构化预览结果
 */
export function decomposeStudyText(planText) {
  return request({
    url: '/api/study-goal/decompose-text',
    method: 'POST',
    data: { planText },
    timeout: REQUEST_TIMEOUT
  })
}

/**
 * 确认入库：将 Goal 与 Tasks 一并写入数据库，返回目标详情
 */
export function saveStudyGoal(payload) {
  return request({ url: '/api/study-goal/save', method: 'POST', data: payload })
}

/**
 * 勾选/取消勾选任务完成状态，服务端自动重算 Goal 进度并返回最新进度
 */
export function updateStudyTaskCompletion(taskId, isCompleted) {
  return request({
    url: `/api/study-goal/tasks/${taskId}/completion`,
    method: 'PUT',
    data: { isCompleted }
  })
}

/**
 * 查询目标详情；filter 支持 all / pending（剩余）/ completed（已完成）
 */
export function getStudyGoalDetail(goalId, filter = 'all') {
  return request({ url: `/api/study-goal/${goalId}`, method: 'GET', data: { filter } })
}

/**
 * 分页查询「我的学习计划」列表（按更新时间倒序），用于退出后找回历史计划
 */
export function listMyGoals(page = 1, size = 10) {
  return request({ url: '/api/study-goal/my', method: 'GET', data: { page, size } })
}

/**
 * 查询指定目标的剩余任务（is_completed=false）清单
 */
export function getRemainingTasks(goalId) {
  return request({ url: `/api/study-goal/${goalId}/remaining-tasks`, method: 'GET' })
}
