import request from '../utils/request'

// ========== 帖子 ==========

export const getPostList = (params = {}) =>
  request({
    url: '/api/forum/posts',
    method: 'get',
    params: {
      pageNum: params.pageNum ?? params.page ?? 1,
      pageSize: params.pageSize ?? params.size ?? 10,
      topicId: params.topicId,
      keyword: params.keyword,
      sortBy: params.sortBy,
      userId: params.userId,
    },
  })

export const getPostDetail = (id) =>
  request({
    url: `/api/forum/posts/${id}`,
    method: 'get',
  })

export const createPost = (data) =>
  request({
    url: '/api/forum/posts',
    method: 'post',
    data,
  })

export const updatePost = (id, data) =>
  request({
    url: `/api/forum/posts/${id}`,
    method: 'put',
    data,
  })

export const deletePost = (id) =>
  request({
    url: `/api/forum/posts/${id}`,
    method: 'delete',
  })

export const getHotPosts = (params = {}) =>
  request({
    url: '/api/forum/posts/hot',
    method: 'get',
    params: {
      pageNum: params.pageNum ?? params.page ?? 1,
      pageSize: params.pageSize ?? params.size ?? 10,
    },
  })

// ========== 评论 ==========

export const getCommentList = (params = {}) =>
  request({
    url: '/api/forum/comments',
    method: 'get',
    params: {
      postId: params.postId,
      pageNum: params.pageNum ?? params.page ?? 1,
      pageSize: params.pageSize ?? params.size ?? 20,
    },
  })

export const getCommentDetail = (id) =>
  request({
    url: `/api/forum/comments/${id}`,
    method: 'get',
  })

export const createComment = (data) =>
  request({
    url: '/api/forum/comments',
    method: 'post',
    data,
  })

export const deleteComment = (id) =>
  request({
    url: `/api/forum/comments/${id}`,
    method: 'delete',
  })

export const adminDeleteComment = (id) =>
  request({
    url: `/api/forum/comments/admin/${id}`,
    method: 'delete',
  })

// ========== 话题 ==========

export const getTopicList = (params = {}) =>
  request({
    url: '/api/forum/topics',
    method: 'get',
    params: {
      pageNum: params.pageNum ?? params.page ?? 1,
      pageSize: params.pageSize ?? params.size ?? 20,
      isHot: params.isHot,
      status: params.status,
    },
  })

export const getHotTopics = (limit = 5) =>
  request({
    url: '/api/forum/topics/hot',
    method: 'get',
    params: { limit },
  })

export const createTopic = (data) =>
  request({
    url: '/api/forum/topics',
    method: 'post',
    data,
  })

export const updateTopic = (id, data) =>
  request({
    url: `/api/forum/topics/${id}`,
    method: 'put',
    data,
  })

export const deleteTopic = (id) =>
  request({
    url: `/api/forum/topics/${id}`,
    method: 'delete',
  })

// ========== 审核 / 管理 ==========
