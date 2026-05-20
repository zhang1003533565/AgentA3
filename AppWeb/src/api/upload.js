import request from '../utils/request'

export const getUploadUrl = (folder) => {
  const base = `${request.defaults.baseURL}/api/upload/image`
  if (!folder) return base
  return `${base}?folder=${encodeURIComponent(folder)}`
}

export const MAP_BUILDING_UPLOAD_FOLDER = 'map-buildings'
