<template>
	<view class="message-container">
		<nav-bar title="消息通知" />
		
		<!-- 分类板块 -->
		<view class="msg-sections">
			<view
				v-for="section in sections"
				:key="section.key"
				class="msg-section"
				@click="goToSection(section)"
			>
				<view class="section-icon-wrap" :style="{ backgroundColor: section.bgColor }">
					<image class="section-icon" :src="`/static/icons/line/${section.icon}.svg`" mode="aspectFit" />
					<view v-if="section.unread > 0" class="unread-badge">
						<text>{{ section.unread > 99 ? '99+' : section.unread }}</text>
					</view>
				</view>
				<view class="section-info">
					<text class="section-title">{{ section.title }}</text>
					<text class="section-desc">{{ section.desc }}</text>
				</view>
				<text class="section-arrow">›</text>
			</view>
		</view>
	</view>
</template>

<script>
	import NavBar from '@/components/nav-bar/nav-bar.vue'
	import { getCommentList, getPostList, getTopicPosts } from '@/api/forum.js'
	import { getCurrentUserId, markForumCategoryRead, isForumCategoryRead } from '@/utils/storage.js'
	export default {
		components: { NavBar },
		data() {
			return {
				unreadStats: {
					like: 0,
					comment: 0,
					system: 0
				}
			}
		},
		computed: {
			sections() {
				const getUnread = (key) => this.unreadStats[key] || 0
				return [
					{ key: 'like', title: '收到的点赞', desc: '看看谁赞了你的内容', icon: 'thumb-up', bgColor: '#E8797A', unread: getUnread('like') },
					{ key: 'comment', title: '收到的评论', desc: '新的评论与回复', icon: 'message-circle', bgColor: '#8CC08C', unread: getUnread('comment') },
					{ key: 'system', title: '系统通知', desc: '平台维护与公告', icon: 'award', bgColor: '#9BA3AF', unread: getUnread('system') }
				]
			}
		},
		onShow() {
			this.loadUnreadStats()
		},
		methods: {
			async loadUnreadStats() {
				// 与消息列表页同源：真实统计帖子评论/点赞/公告，已读状态覆盖
				const uid = getCurrentUserId()
				let commentCount = 0
				let likeCount = 0
				let myPosts = []
				try {
					const res = await getPostList({ userId: uid, pageNum: 1, pageSize: 20 })
					myPosts = res?.data?.records || []
				} catch (error) {
					myPosts = []
				}
				for (const post of myPosts) {
					if (post.likeCount > 0) likeCount += 1
					try {
						const res = await getCommentList({ postId: post.id, pageNum: 1, pageSize: 20 })
						const comments = res?.data?.records || []
						for (const comment of comments) {
							if (String(comment.userId) !== String(uid)) commentCount += 1
						}
					} catch (error) {}
				}
				let systemCount = 0
				try {
					const res = await getTopicPosts(3, { pageNum: 1, pageSize: 20 })
					systemCount = (res?.data?.records || []).length
				} catch (error) {
					systemCount = 0
				}
				// 已读状态覆盖未读数
				this.unreadStats = {
					like: isForumCategoryRead('like') ? 0 : likeCount,
					comment: isForumCategoryRead('comment') ? 0 : commentCount,
					system: isForumCategoryRead('system') ? 0 : systemCount
				}
			},
			goToSection(section) {
				// 点击即标记该分类已读：立即持久化并清零本地计数，返回后不再显示红点
				markForumCategoryRead(section.key)
				this.unreadStats[section.key] = 0
				// 每个板块跳转到独立的分类消息列表页
				uni.navigateTo({
					url: `/subpackage_message/messageCategory/messageCategory?type=${section.key}`
				})
			}
		}
	}
</script>

<style lang="scss">
	.message-container {
		min-height: 100vh;
		background-color: #F7F7F9;
	}

	/* 分类板块 */
	.msg-sections {
		margin: 20rpx;
		background-color: #FFFFFF;
		border-radius: 24rpx;
		padding: 8rpx 0;
	}

	.msg-section {
		display: flex;
		align-items: center;
		padding: 28rpx;
		position: relative;

		&::after {
			content: "";
			position: absolute;
			left: 140rpx;
			right: 0;
			bottom: 0;
			height: 1rpx;
			background-color: #F0F0F0;
		}

		&:last-child::after {
			display: none;
		}

		&:active {
			background-color: #F8F9FA;
		}
	}

	.section-icon-wrap {
		position: relative;
		width: 88rpx;
		height: 88rpx;
		border-radius: 20rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		margin-right: 24rpx;
		flex-shrink: 0;
	}

	.section-icon {
		width: 44rpx;
		height: 44rpx;
		color: #FFFFFF;
	}

	.unread-badge {
		position: absolute;
		top: -10rpx;
		right: -10rpx;
		min-width: 36rpx;
		height: 36rpx;
		padding: 0 8rpx;
		background-color: #FF3B30;
		border-radius: 18rpx;
		border: 4rpx solid #FFFFFF;
		display: flex;
		align-items: center;
		justify-content: center;

		text {
			font-size: 20rpx;
			color: #FFFFFF;
			font-weight: 600;
		}
	}

	.section-info {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 6rpx;
	}

	.section-title {
		font-size: 30rpx;
		font-weight: 600;
		color: #1D1D1F;
	}

	.section-desc {
		font-size: 24rpx;
		color: #9CA3AF;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.section-arrow {
		font-size: 40rpx;
		color: #C7C7CC;
		margin-left: 12rpx;
		flex-shrink: 0;
	}
</style>
