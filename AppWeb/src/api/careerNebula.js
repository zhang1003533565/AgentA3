import request from '../utils/request'

export const getCareerNebulaMap = async () => {
  const response = await request.get('/api/admin/career-nebula', { timeout: 30000 })
  return response.data
}

export const saveCareerNebulaMap = async (payload) => {
  const response = await request.put('/api/admin/career-nebula', payload, { timeout: 30000 })
  return response.data
}
