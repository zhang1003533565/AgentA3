<template>
	<view class="message-detail-container">
		<nav-bar :title="messageTitle" fixed placeholder />
		
		<view class="detail-content">
			<view class="article-header">
				<text class="article-title">{{ message.title }}</text>
				<view class="article-meta">
					<text class="article-type" :class="message.type">{{ getTypeName(message.type) }}</text>
					<text class="article-time">{{ message.time }}</text>
				</view>
			</view>
			
			<view class="article-body">
				<text class="article-text">{{ message.desc }}</text>
			</view>
			
			<!-- 底部卡片链接 -->
			<view v-if="message.type === 'activity' || message.type === 'forum'" class="link-card" @click="handleLinkClick">
				<view class="link-icon" :class="message.type">
					<icon-line :name="getIcon(message.type)" size="service" color="#FFFFFF" />
				</view>
				<view class="link-info">
					<text class="link-title">{{ message.title }}</text>
				</view>
				<text class="link-arrow">›</text>
			</view>
		</view>
	</view>
</template>

<script>
	import NavBar from '@/components/nav-bar/nav-bar.vue'
	export default {
		components: { NavBar },
		data() {
			return {
				messageTitle: '消息详情',
				message: {
					id: null,
					type: '',
					title: '',
					desc: '',
					time: ''
				}
			}
		},
		onLoad(options) {
			if (options.id) {
				// 模拟获取详情，实际开发中应从后端或全局状态获取
				this.loadMessageDetail(options.id)
			}
		},
		methods: {
			loadMessageDetail(id) {
				// 这里模拟数据，实际应根据 ID 请求接口
				const mockData = {
					'1': { id: 1, type: 'activity', title: '活动报名成功', desc: '您报名的“校园摄影大赛”已审核通过，请准时参加。具体参赛要求已发送至您的校园邮箱，请查收。活动时间：2026-03-25 14:00，地点：艺术楼一楼报告厅。', time: '10:30' },
					'2': { id: 2, type: 'forum', title: '收到新的回复', desc: '“张同学”回复了你的帖子：我也觉得这个图书馆的位子很难约，尤其是考研期间。建议早上 6:50 就准时抢位。', time: '昨天' },
					'4': { id: 4, type: 'activity', title: '签到提醒', desc: '您参加的“校园摄影大赛 - 线下分享会”即将开始，请尽快前往艺术楼一楼报告厅签到。签到截止时间：14:15。', time: '13:50' },
					'5': { id: 5, type: 'activity', title: '需要签到', desc: '您报名的“学术讲座：人工智能前沿”正在进行中，请尽快前往教3-101进行签到。签到有效范围：教学楼周边200米。', time: '刚刚' },
					'3': { id: 3, type: 'system', title: '系统维护通知', desc: '校园网将于今晚 23:00 进行常规维护，届时可能会有短暂断网（约 15-30 分钟）。请同学们提前保存好在线作业或文档，以免数据丢失。感谢您的理解与支持。', time: '03-15' }
				}
				if (mockData[id]) {
					this.message = mockData[id]
				}
			},
			getTypeName(type) {
				const names = { activity: '活动通知', forum: '论坛互动', system: '系统消息' }
				return names[type] || '其他消息'
			},
			getIcon(type) {
				const icons = { activity: 'calendar', forum: 'message-circle' }
				return icons[type] || 'message-circle'
			},
			handleLinkClick() {
				if (this.message.title === '签到提醒' || this.message.title === '需要签到') {
					this.goToSignIn()
				} else if (this.message.type === 'activity') {
					this.goToActivity()
				} else if (this.message.type === 'forum') {
					this.goToForum()
				}
			},
			goToSignIn() {
				uni.navigateTo({
					url: '/subpackage_signin/signIn/signIn'
				})
			},
			goToActivity() {
				uni.navigateTo({
					url: '/subpackage_activity/activityDetail/activityDetail?id=1'
				})
			},
			goToForum() {
				uni.navigateTo({
					url: '/subpackage_forum/forumList/forumList'
				})
			}
		}
	}
</script>

<style lang="scss">
	.message-detail-container {
		min-height: 100vh;
		background-color: #FFFFFF;
	}

	.detail-content {
		padding: 40rpx 40rpx;
	}

	.article-header {
		margin-bottom: 40rpx;
	}

	.article-title {
		font-size: 44rpx;
		font-weight: 700;
		color: #1D1D1F;
		line-height: 1.4;
		margin-bottom: 24rpx;
		display: block;
	}

	.article-meta {
		display: flex;
		align-items: center;
		gap: 20rpx;
	}

	.article-type {
		font-size: 26rpx;
		&.activity { color: #7AA1D2; }
		&.forum { color: #8CC08C; }
		&.system { color: #E6B87A; }
	}

	.article-time {
		font-size: 26rpx;
		color: #B2B2B2;
	}

	.article-body {
		margin-bottom: 60rpx;
	}

	.article-text {
		font-size: 32rpx;
		color: #3A3A3C;
		line-height: 1.8;
		white-space: pre-wrap;
	}

	.link-card {
		display: flex;
		align-items: center;
		background-color: #F8F9FB;
		padding: 24rpx;
		border-radius: 12rpx;
		transition: background-color 0.2s;

		&:active {
			background-color: #F0F2F5;
		}
	}

	.link-icon {
		width: 80rpx;
		height: 80rpx;
		border-radius: 12rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		margin-right: 20rpx;
		flex-shrink: 0;

		&.activity { background-color: #7AA1D2; }
		&.forum { background-color: #8CC08C; }
	}

	.link-info {
		flex: 1;
		min-width: 0;
	}

	.link-title {
		font-size: 30rpx;
		color: #1D1D1F;
		font-weight: 500;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.link-arrow {
		font-size: 36rpx;
		color: #C7C7CC;
		margin-left: 12rpx;
	}
</style>
