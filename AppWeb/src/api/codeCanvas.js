import request from '../utils/request'

/**
 * 代码画布：根据后端程序代码，AI 生成前端预览页面 HTML
 * @param {{ code: string, requirement?: string, title?: string }} data
 * @returns Promise<{ code: number, msg: string, data: { title: string, summary: string, entities: string, html: string } }>
 */
export const generatePreviewPage = (data) =>
  request({
    url: '/api/ai/code-canvas/generate',
    method: 'post',
    // AI 生成 HTML 耗时较长，单独放宽超时到 180 秒
    timeout: 180000,
    data: {
      code: data.code,
      requirement: data.requirement || '',
      title: data.title || '',
    },
  })
