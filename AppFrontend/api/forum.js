import { request } from '@/utils/request'
import { uploadImage } from '@/utils/upload'

/** 上传论坛图片 */
export function uploadForumImage(filePath) {
  return uploadImage(filePath)
}

export function parseImageList(images) {
  if (Array.isArray(images)) return images.filter(Boolean)
  if (!images) return []
  if (typeof images === 'string') {
    try {
      const parsed = JSON.parse(images)
      return Array.isArray(parsed) ? parsed.filter(Boolean) : []
    } catch (e) {
      return images
        .split(',')
        .map((item) => item.trim())
        .filter(Boolean)
    }
  }
  return []
}

export function getTopicDetail(topicId) {
  return request({
    url: `/api/forum/topics/${topicId}`,
    method: 'GET'
  })
}

export function getTopicPosts(topicId, params = {}) {
  return request({
    url: `/api/forum/topics/${topicId}/posts`,
    method: 'GET',
    params
  })
}

export function getPostList(params = {}) {
  return request({
    url: '/api/forum/posts',
    method: 'GET',
    params
  })
}

export function getHotPosts(params = {}) {
  return request({
    url: '/api/forum/posts/hot',
    method: 'GET',
    params
  })
}

export function getPostDetail(postId) {
  return request({
    url: `/api/forum/posts/${postId}`,
    method: 'GET'
  })
}

export function publishPost(data) {
  return request({
    url: '/api/forum/posts',
    method: 'POST',
    data
  })
}

export function updatePost(postId, data) {
  return request({
    url: `/api/forum/posts/${postId}`,
    method: 'PUT',
    data
  })
}

export function deletePost(postId) {
  return request({
    url: `/api/forum/posts/${postId}`,
    method: 'DELETE'
  })
}

export function getTopicList(params = {}) {
  return request({
    url: '/api/forum/topics',
    method: 'GET',
    params
  })
}

export function getHotTopics(params = {}) {
  return request({
    url: '/api/forum/topics/hot',
    method: 'GET',
    params
  })
}

export function togglePostLike(targetId) {
  return request({
    url: '/api/forum/likes',
    method: 'POST',
    data: { targetId }
  })
}

export function getPostLikeStatus(targetId) {
  return request({
    url: '/api/forum/likes/status',
    method: 'GET',
    params: { targetId }
  })
}

export function getCommentList(params = {}) {
  return request({
    url: '/api/forum/comments',
    method: 'GET',
    params
  })
}

export function createComment(data) {
  return request({
    url: '/api/forum/comments',
    method: 'POST',
    data
  })
}

export function deleteComment(commentId) {
  return request({
    url: `/api/forum/comments/${commentId}`,
    method: 'DELETE'
  })
}

export function toggleFollowUser(followUserId) {
  return request({
    url: '/api/forum/follows',
    method: 'POST',
    data: { followId: followUserId }
  })
}

export function getFollowStatus(userId) {
  return request({
    url: `/api/forum/follows/status/${userId}`,
    method: 'GET'
  })
}

export function getFollowers(userId, params = {}) {
  return request({
    url: `/api/forum/follows/followers/${userId}`,
    method: 'GET',
    params
  })
}

export function getFollowing(userId, params = {}) {
  return request({
    url: `/api/forum/follows/following/${userId}`,
    method: 'GET',
    params
  })
}

export function getUserPosts(userId, params = {}) {
  return request({
    url: `/api/forum/users/${userId}/posts`,
    method: 'GET',
    params
  })
}

export function getUserLikes(userId, params = {}) {
  return request({
    url: `/api/forum/users/${userId}/likes`,
    method: 'GET',
    params
  })
}
