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

/** 获取论坛消息未读数聚合（评论/点赞/系统通知一次返回） */
export function getForumMessageUnread() {
  return request({
    url: '/api/forum/posts/messages/unread',
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

export function toggleForumLike(targetId, targetType) {
  return request({
    url: '/api/forum/likes',
    method: 'POST',
    data: { targetId, targetType }
  })
}

export function getForumLikeStatus(targetId, targetType) {
  return request({
    url: '/api/forum/likes/status',
    method: 'GET',
    params: { targetId, targetType }
  })
}

export function togglePostLike(targetId) {
  return toggleForumLike(targetId, 'POST')
}

export function toggleCommentLike(targetId) {
  return toggleForumLike(targetId, 'COMMENT')
}

export function getPostLikeStatus(targetId) {
  return getForumLikeStatus(targetId, 'POST')
}

export function getCommentLikeStatus(targetId) {
  return getForumLikeStatus(targetId, 'COMMENT')
}

export function togglePostFavorite(postId) {
  return request({
    url: `/api/forum/favorites/${postId}`,
    method: 'POST'
  })
}

export function getPostFavoriteStatus(postId) {
  return request({
    url: `/api/forum/favorites/status/${postId}`,
    method: 'GET'
  })
}

export function getMyFavoritePosts(params = {}) {
  return request({
    url: '/api/forum/favorites/my',
    method: 'GET',
    params
  })
}

export function getCommentList(params = {}) {
  return request({
    url: '/api/forum/comments',
    method: 'GET',
    params
  })
}

/** 获取我收到的他人评论（聚合一次返回，避免逐个帖子查询） */
export function getReceivedComments() {
  return request({
    url: '/api/forum/comments/received',
    method: 'GET'
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

export function createReport(data) {
  return request({
    url: '/api/forum/reports',
    method: 'POST',
    data
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

export function getMyFollowers(params = {}) {
  return request({
    url: '/api/forum/follows/my/followers',
    method: 'GET',
    params
  })
}

export function getMyFollowing(params = {}) {
  return request({
    url: '/api/forum/follows/my/following',
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

export function getMyForumPosts(params = {}) {
  return request({
    url: '/api/forum/users/posts/me',
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
