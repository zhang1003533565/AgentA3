import request from '../utils/request'

// 获取帖子列表
export const getPostList = (params) => {
  return request({
    url: '/api/forum/posts',
    method: 'get',
    params
  })
}

// 获取帖子详情
export const getPostDetail = (id) => {
  return request({
    url: `/api/forum/posts/${id}`,
    method: 'get'
  })
}

// 删除帖子
export const deletePost = (id) => {
  return request({
    url: `/api/forum/posts/${id}`,
    method: 'delete'
  })
}

// 获取评论列表
export const getCommentList = (params) => {
  return request({
    url: '/api/forum/comments',
    method: 'get',
    params
  })
}

// 删除评论
export const deleteComment = (id) => {
  return request({
    url: `/api/forum/comments/${id}`,
    method: 'delete'
  })
}

// 获取话题列表
export const getTopicList = () => {
  return request({
    url: '/api/forum/topics',
    method: 'get'
  })
}

// 创建话题
export const createTopic = (data) => {
  return request({
    url: '/api/forum/topics',
    method: 'post',
    data
  })
}

// 更新话题
export const updateTopic = (id, data) => {
  return request({
    url: `/api/forum/topics/${id}`,
    method: 'put',
    data
  })
}

// 删除话题
export const deleteTopic = (id) => {
  return request({
    url: `/api/forum/topics/${id}`,
    method: 'delete'
  })
}

// ========== 审核相关接口（管理端） ==========

// 批量删除帖子
export const batchDeletePosts = (ids) => {
  return request({
    url: '/api/audit/posts/batch',
    method: 'delete',
    data: ids
  })
}

// 批量删除评论
export const batchDeleteComments = (ids) => {
  return request({
    url: '/api/audit/comments/batch',
    method: 'delete',
    data: ids
  })
}
