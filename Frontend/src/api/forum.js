import { request } from './request'

const unwrap = (promise) => promise.then((response) => response.data)

export const getPostList = (params = {}) => unwrap(request({ url: '/api/forum/posts', params }))
export const getHotPosts = (params = {}) => unwrap(request({ url: '/api/forum/posts/hot', params }))
export const getPostDetail = (id) => unwrap(request({ url: `/api/forum/posts/${id}` }))
export const publishPost = (data) => unwrap(request({ url: '/api/forum/posts', method: 'POST', data }))
export const deletePost = (id) => unwrap(request({ url: `/api/forum/posts/${id}`, method: 'DELETE' }))
export const getTopicList = (params = {}) => unwrap(request({ url: '/api/forum/topics', params }))
export const getTopicDetail = (id) => unwrap(request({ url: `/api/forum/topics/${id}` }))
export const getTopicPosts = (id, params = {}) => unwrap(request({ url: `/api/forum/topics/${id}/posts`, params }))
export const togglePostLike = (id) => unwrap(request({
  url: '/api/forum/likes',
  method: 'POST',
  data: { targetId: id, targetType: 'POST' },
}))
export const toggleCommentLike = (id) => unwrap(request({
  url: '/api/forum/likes',
  method: 'POST',
  data: { targetId: id, targetType: 'COMMENT' },
}))
export const togglePostFavorite = (id) => unwrap(request({
  url: `/api/forum/favorites/${id}`,
  method: 'POST',
}))
export const getCommentList = (params = {}) => unwrap(request({ url: '/api/forum/comments', params }))
export const createComment = (data) => unwrap(request({ url: '/api/forum/comments', method: 'POST', data }))
export const toggleFollowUser = (followId) => unwrap(request({
  url: '/api/forum/follows',
  method: 'POST',
  data: { followId },
}))
export const getUserPosts = (userId, params = {}) => unwrap(request({
  url: `/api/forum/users/${userId}/posts`,
  params,
}))
export const getMyFavoritePosts = (params = {}) => unwrap(request({
  url: '/api/forum/favorites/my',
  params,
}))
export const getUserLikes = (userId, params = {}) => unwrap(request({
  url: `/api/forum/users/${userId}/likes`,
  params,
}))
export const getFollowers = (userId, params = {}) => unwrap(request({
  url: `/api/forum/follows/followers/${userId}`,
  params,
}))
export const getFollowing = (userId, params = {}) => unwrap(request({
  url: `/api/forum/follows/following/${userId}`,
  params,
}))
