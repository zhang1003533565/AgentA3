import request from '../utils/request'

export const getUploadUrl = (folder) => {
  const base = `${request.defaults.baseURL}/api/upload/image`
  if (!folder) return base
  return `${base}?folder=${encodeURIComponent(folder)}`
}

export const MAP_BUILDING_UPLOAD_FOLDER = 'map-buildings'
export const CANTEEN_STALL_UPLOAD_FOLDER = 'canteen-stalls'
export const DISH_UPLOAD_FOLDER = 'dishes'

export const uploadImage = async (file, folder) => {
  const formData = new FormData()
  formData.append('file', file)
  const response = await fetch(getUploadUrl(folder), {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${localStorage.getItem('token') || ''}`,
    },
    body: formData,
  })
  const result = await response.json()
  if (!response.ok || result?.code !== 200) {
    throw new Error(result?.message || result?.msg || '图片上传失败')
  }
  const url = result?.data?.url
  if (!url) throw new Error('上传成功但未返回图片地址')
  return url
}
