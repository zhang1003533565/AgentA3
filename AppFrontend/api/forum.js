import { request } from '@/utils/request'

// 论坛相关API接口

// 获取话题详情
export function getTopicDetail(topicId) {
  return request({
    url: `/api/forum/topics/${topicId}`,
    method: 'get'
  })
}

// 获取话题下的帖子列表
export function getTopicPosts(topicId, params) {
  return request({
    url: `/api/forum/topics/${topicId}/posts`,
    method: 'get',
    params
  })
}

// 获取帖子列表
export function getPostList(params) {
  return request({
    url: '/api/forum/posts',
    method: 'get',
    params
  })
}

// 获取热门帖子
export function getHotPosts(params) {
  return request({
    url: '/api/forum/posts/hot',
    method: 'get',
    params
  })
}

// 获取帖子详情
export function getPostDetail(postId) {
  return request({
    url: `/api/forum/posts/${postId}`,
    method: 'get'
  })
}

// 发布新帖子
export function publishPost(data) {
  return request({
    url: '/api/forum/posts',
    method: 'post',
    data
  })
}

// 编辑帖子
export function updatePost(postId, data) {
  return request({
    url: `/api/forum/posts/${postId}`,
    method: 'put',
    data
  })
}

// 删除帖子
export function deletePost(postId) {
  return request({
    url: `/api/forum/posts/${postId}`,
    method: 'delete'
  })
}

// 获取话题列表
export function getTopicList(params) {
  return request({
    url: '/api/forum/topics',
    method: 'get',
    params
  })
}

// 获取热门话题
export function getHotTopics(params) {
  return request({
    url: '/api/forum/topics/hot',
    method: 'get',
    params
  })
}

// 点赞帖子
export function likePost(postId) {
  return request({
    url: `/api/forum/posts/${postId}/like`,
    method: 'post'
  })
}

// 取消点赞
export function unlikePost(postId) {
  return request({
    url: `/api/forum/posts/${postId}/unlike`,
    method: 'post'
  })
}

// 收藏帖子
export function favoritePost(postId) {
  return request({
    url: `/api/forum/posts/${postId}/favorite`,
    method: 'post'
  })
}

// 取消收藏
export function unfavoritePost(postId) {
  return request({
    url: `/api/forum/posts/${postId}/unfavorite`,
    method: 'post'
  })
}

// 获取用户收藏的帖子
export function getUserFavorites(params) {
  return request({
    url: '/api/forum/favorites',
    method: 'get',
    params
  })
}

// 搜索帖子
export function searchPosts(keyword, params) {
  return request({
    url: '/api/forum/posts/search',
    method: 'get',
    params: {
      keyword,
      ...params
    }
  })
}
