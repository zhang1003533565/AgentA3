import request from '../utils/request'

const base = '/api/profile'

export const getUserProfileRules = () =>
  request({
    url: `${base}/rules`,
    method: 'get',
  })

export const getMyProfileRadar = () =>
  request({
    url: `${base}/radar/my`,
    method: 'get',
  })
