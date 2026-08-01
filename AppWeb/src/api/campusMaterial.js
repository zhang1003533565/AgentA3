import request from '../utils/request'

const base = '/api/admin/materials'

// 课程资料池：列表（默认仅未下架，includeDeleted=true 含已下架）
export const getCourseMaterials = (courseId, includeDeleted = false) =>
  request.get(base, { params: { courseId, includeDeleted } })

// 引用检查：返回引用该资料的章节名称列表
export const checkMaterialReference = (id) => request.get(`${base}/${id}/check`)

// 删除资料：默认软删除；physical=true 追加物理删除文件（不可逆）
export const deleteMaterial = (id, physical = false) =>
  request.delete(`${base}/${id}`, { params: { physical } })

// 查询章节已绑定资料（按存储顺序）
export const getChapterMaterials = (courseId, chapterId) =>
  request.get(`${base}/chapter/${chapterId}`, { params: { courseId } })

// 绑定章节资料：写入章节 material_ids（保留传入顺序）
export const bindChapterMaterials = (courseId, chapterId, materialIds) =>
  request.put(`${base}/chapter/${chapterId}`, { courseId, materialIds })

/**
 * 文件夹分批上传（multipart）。同一 uploadBatchId 跨请求由后端累计校验总大小。
 * 使用 fetch 以保证正确的 multipart 边界；沿用项目 token 鉴权方式。
 */
export const uploadMaterialBatch = async (courseId, files, uploadBatchId) => {
  const formData = new FormData()
  formData.append('courseId', courseId)
  files.forEach((file) => formData.append('files', file))
  if (uploadBatchId) formData.append('uploadBatchId', uploadBatchId)

  const response = await fetch(`${request.defaults.baseURL}${base}/folder/upload`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${localStorage.getItem('token') || ''}` },
    body: formData,
  })
  const result = await response.json().catch(() => null)
  if (!response.ok || result?.code !== 200) {
    throw new Error(result?.msg || result?.message || '资料上传失败')
  }
  return result.data
}
