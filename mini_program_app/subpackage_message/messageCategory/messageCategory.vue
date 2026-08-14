<template>
	<view class="category-container">
		<nav-bar :title="pageTitle" fixed placeholder />
		
		<view class="category-header" v-if="list.length">
			<text class="category-count">共 {{ list.length }} 条</text>
			<text class="category-clear" @click="markAllRead" v-if="hasUnread">全部已读</text>
		</view>

		<view class="msg-list">
			<view class="msg-item" v-for="(item, index) in list" :key="index" @click="goToDetail(item)">
				<view class="msg-avatar-wrap" :style="{ backgroundColor: item.bgColor }">
					<image class="msg-avatar" :src="item.avatar || '/static/logo.png'" mode="aspectFill" />
					<view v-if="!item.isRead" class="unread-dot"></view>
				</view>
				<view class="msg-body">
					<view class="msg-header">
						<text class="msg-name">{{ item.name }}</text>
						<text class="msg-time">{{ item.time }}</text>
					</view>
					<text class="msg-action">{{ item.action }}</text>
					<text class="msg-desc" v-if="item.desc">{{ item.desc }}</text>
				</view>
			</view>

			<view v-if="loading" class="empty-state">
				<text class="empty-text">加载中...</text>
			</view>
			<view v-else-if="list.length === 0" class="empty-state">
				<image :src="`/static/icons/line/${emptyIcon}.svg`" mode="aspectFit" class="empty-img" />
				<text class="empty-text">{{ emptyText }}</text>
			</view>
		</view>
	</view>
</template>

<script>
	import NavBar from '@/components/nav-bar/nav-bar.vue'
	import { getPostList, getReceivedComments, getTopicPosts, parseImageList } from '@/api/forum.js'
	import { getCurrentUserId, markForumCategoryRead, isForumCategoryRead } from '@/utils/storage.js'
	export default {
		components: { NavBar },
		data() {
			return {
				type: 'like',
				list: [],
				loading: false,
				pageTitle: '消息',
				emptyIcon: 'message-circle',
				emptyText: '暂无消息',
				currentUserId: ''
			}
		},
		computed: {
			hasUnread() {
				return this.list.some((item) => !item.isRead)
			}
		},
		onLoad(options) {
			this.type = options.type || 'like'
			this.currentUserId = getCurrentUserId()
			this.initConfig()
			// 进入页面瞬间即标记该分类已读（同步持久化，不依赖异步数据加载）
			markForumCategoryRead(this.type)
			this.loadList()
		},
		methods: {
			initConfig() {
				const configs = {
					like: { title: '收到的点赞', icon: 'thumb-up', empty: '还没有人赞过你的帖子' },
					comment: { title: '收到的评论', icon: 'message-circle', empty: '还没有收到评论' },
					system: { title: '系统通知', icon: 'award', empty: '暂无系统通知' }
				}
				const cfg = configs[this.type] || configs.like
				this.pageTitle = cfg.title
				this.emptyIcon = cfg.icon
				this.emptyText = cfg.empty
			},
			async loadList() {
				this.loading = true
				try {
					if (this.type === 'comment') {
						await this.loadCommentMessages()
					} else if (this.type === 'like') {
						await this.loadLikeMessages()
					} else {
						await this.loadSystemMessages()
					}
				} catch (error) {
					this.list = []
				} finally {
					this.loading = false
				}
				this.restoreReadFlags()
				// 进入列表页即视为已读：持久化标记并通知来源页清除红点（静默，不弹提示）
				this.autoMarkRead()
			},
			// 收到的评论：别人在我帖子下的真实评论（聚合接口一次返回，避免 N+1 查询）
			async loadCommentMessages() {
				let items = []
				try {
					const res = await getReceivedComments()
					const records = res?.data || []
					items = records.map((comment) => ({
						id: comment.id,
						name: comment.username || '匿名用户',
						avatar: comment.avatar || '',
						action: `评论了你的帖子「${comment.postTitle || ''}」`,
						desc: comment.content || '',
						time: this.formatTime(comment.createTime),
						isRead: false,
						bgColor: '#E8F5E9',
						target: 'post',
						postId: comment.postId
					}))
				} catch (error) {
					items = []
				}
				items.sort((a, b) => String(b.time).localeCompare(String(a.time)))
				this.list = items
			},
			// 收到的点赞：我的帖子被点赞的真实记录
			async loadLikeMessages() {
				const uid = this.currentUserId
				let myPosts = []
				try {
					const res = await getPostList({ userId: uid, pageNum: 1, pageSize: 20 })
					myPosts = res?.data?.records || []
				} catch (error) {
					myPosts = []
				}
				const items = []
				for (const post of myPosts) {
					if (!post.likeCount || post.likeCount <= 0) continue
					items.push({
						id: `like_${post.id}`,
						name: '系统',
						avatar: '',
						action: `你的帖子收到 ${post.likeCount} 个赞`,
						desc: post.title || post.content || '',
						time: this.formatTime(post.createTime),
						isRead: false,
						bgColor: '#FDE8E8',
						target: 'post',
						postId: post.id
					})
				}
				items.sort((a, b) => String(b.time).localeCompare(String(a.time)))
				this.list = items
			},
			// 系统通知：公告话题下的真实帖子
			async loadSystemMessages() {
				let posts = []
				try {
					const res = await getTopicPosts(3, { pageNum: 1, pageSize: 20 })
					posts = res?.data?.records || []
				} catch (error) {
					posts = []
				}
				if (!posts.length) {
					try {
						const res = await getPostList({ topicId: 3, pageNum: 1, pageSize: 20 })
						posts = res?.data?.records || []
					} catch (error) {
						posts = []
					}
				}
				this.list = posts.map((post) => ({
					id: `sys_${post.id}`,
					name: '系统通知',
					avatar: '',
					action: post.title || '平台公告',
					desc: post.content || '',
					time: this.formatTime(post.createTime),
					isRead: false,
					bgColor: '#EEEEF2',
					target: 'post',
					postId: post.id
				}))
			},
			restoreReadFlags() {
				try {
					const flags = uni.getStorageSync('message_read_flags') || {}
					this.list.forEach((item) => {
						if (flags[`${this.type}_${item.id}`] === true) item.isRead = true
					})
				} catch (error) {}
			},
			// 进入页面时静默标记整个分类为已读（不弹提示）
			autoMarkRead() {
				this.list.forEach((item) => {
					item.isRead = true
				})
				this.persistReadFlags()
				this.notifyMainUpdate()
			},
			markAllRead() {
				this.list.forEach((item) => {
					item.isRead = true
				})
				this.persistReadFlags()
				uni.showToast({ title: '已全部标记为已读', icon: 'none' })
				this.notifyMainUpdate()
			},
			persistReadFlags() {
				try {
					const flags = uni.getStorageSync('message_read_flags') || {}
					this.list.forEach((item) => {
						flags[`${this.type}_${item.id}`] = true
					})
					flags[this.type] = true
					uni.setStorageSync('message_read_flags', flags)
				} catch (error) {}
			},			goToDetail(item) {
				item.isRead = true
				this.persistReadFlags()
				this.notifyMainUpdate()
				if (item.postId) {
					uni.navigateTo({
						url: `/subpackage_forum/postDetail/postDetail?id=${item.postId}`
					})
				}
			},
			notifyMainUpdate() {
				try {
					uni.$emit('message-read-updated', this.type)
				} catch (error) {}
			},
			formatTime(value) {
				if (!value) return ''
				const d = new Date(value)
				if (isNaN(d.getTime())) return String(value).replace('T', ' ').slice(0, 16)
				const pad = (n) => String(n).padStart(2, '0')
				return `${d.getMonth() + 1}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
			}
		}
	}
</script>

<style lang="scss">
	.category-container {
		min-height: 100vh;
		background-color: #F7F7F9;
	}

	.category-header {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 24rpx 32rpx 12rpx;
	}

	.category-count {
		font-size: 24rpx;
		color: #9CA3AF;
	}

	.category-clear {
		font-size: 24rpx;
		color: #5C7A99;
	}

	.msg-list {
		padding: 0 20rpx;
	}

	.msg-item {
		display: flex;
		align-items: flex-start;
		padding: 24rpx;
		background-color: #FFFFFF;
		border-radius: 20rpx;
		margin-bottom: 16rpx;
		position: relative;
		transition: background-color 0.2s;

		&:active {
			background-color: #F5F5F5;
		}
	}

	.msg-avatar-wrap {
		position: relative;
		width: 80rpx;
		height: 80rpx;
		border-radius: 50%;
		display: flex;
		align-items: center;
		justify-content: center;
		margin-right: 20rpx;
		flex-shrink: 0;
		overflow: hidden;
	}

	.msg-avatar {
		width: 80rpx;
		height: 80rpx;
		border-radius: 50%;
	}

	.unread-dot {
		position: absolute;
		top: -4rpx;
		right: -4rpx;
		width: 18rpx;
		height: 18rpx;
		background-color: #FF3B30;
		border-radius: 50%;
		border: 4rpx solid #FFFFFF;
	}

	.msg-body {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
	}

	.msg-header {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-bottom: 6rpx;
	}

	.msg-name {
		font-size: 30rpx;
		font-weight: 600;
		color: #1D1D1F;
	}

	.msg-time {
		font-size: 22rpx;
		color: #B2B2B2;
	}

	.msg-action {
		font-size: 26rpx;
		color: #5C7A99;
		margin-bottom: 6rpx;
	}

	.msg-desc {
		font-size: 26rpx;
		color: #666666;
		line-height: 1.5;
	}

	.empty-state {
		display: flex;
		flex-direction: column;
		align-items: center;
		padding-top: 200rpx;
	}

	.empty-img {
		width: 240rpx;
		height: 240rpx;
		margin-bottom: 24rpx;
		opacity: 0.5;
	}

	.empty-text {
		font-size: 28rpx;
		color: #8E8E93;
	}
</style>
