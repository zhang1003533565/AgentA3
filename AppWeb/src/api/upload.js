import request from '../utils/request'

export const getUploadUrl = () => `${request.defaults.baseURL}/api/upload/image`
