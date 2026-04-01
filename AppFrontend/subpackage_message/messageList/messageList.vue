<template>
	<view class="message-container">
		<nav-bar title="消息通知" />
		
		<view class="msg-list">
			<view class="msg-item" v-for="(item, index) in messages" :key="index" @click="goToDetail(item)">
				<view class="msg-icon-wrap" :class="item.type">
					<image class="msg-icon" :src="`/static/icons/line/${getIcon(item.type)}.svg`" mode="aspectFit" />
					<view v-if="!item.isRead" class="unread-dot"></view>
				</view>
				<view class="msg-body">
					<view class="msg-header">
						<text class="msg-title">{{item.title}}</text>
						<text class="msg-time">{{item.time}}</text>
					</view>
					<view class="msg-footer">
						<text class="msg-desc">{{item.desc}}</text>
					</view>
				</view>
			</view>
			
			<view v-if="messages.length === 0" class="empty-state">
				<image src="/static/images/empty-msg.png" mode="aspectFit" class="empty-img" />
				<text class="empty-text">暂无消息通知</text>
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
				messages: [
					{
						id: 1,
						type: 'activity',
						title: '活动报名成功',
						desc: '您报名的“校园摄影大赛”已审核通过，请准时参加。',
						time: '10:30',
						isRead: false
					},
					{
						id: 4,
						type: 'activity',
						title: '签到提醒',
						desc: '您参加的“校园摄影大赛 - 线下分享会”即将开始，请尽快签到。',
						time: '13:50',
						isRead: false
					},
					{
						id: 5,
						type: 'activity',
						title: '需要签到',
						desc: '您报名的“学术讲座：人工智能前沿”正在进行中，请尽快前往教3-101进行签到。',
						time: '刚刚',
						isRead: false
					},
					{
						id: 2,
						type: 'forum',
						title: '收到新的回复',
						desc: '“张同学”回复了你的帖子：我也觉得这个图书馆的位子很难约...',
						time: '昨天',
						isRead: true
					},
					{
						id: 3,
						type: 'system',
						title: '系统维护通知',
						desc: '校园网将于今晚 23:00 进行常规维护，届时可能会有短暂断网。',
						time: '03-15',
						isRead: true
					}
				]
			}
		},
		onLoad() {
		},
		methods: {
			getIcon(type) {
				const icons = {
					activity: 'calendar',
					forum: 'message-circle',
					system: 'award'
				}
				return icons[type] || 'message-circle'
			},
			goToDetail(item) {
				item.isRead = true
				uni.navigateTo({
					url: `/subpackage_message/messageDetail/messageDetail?id=${item.id}`
				})
			}
		}
	}
</script>

<style lang="scss">
	.message-container {
		min-height: 100vh;
		background-color: #FFFFFF;
	}

	.msg-list {
		padding: 0;
	}

	.msg-item {
		display: flex;
		align-items: center;
		padding: 24rpx 32rpx;
		background-color: #FFFFFF;
		position: relative;
		transition: background-color 0.2s;

		&:active {
			background-color: #F5F5F5;
		}

		&::after {
			content: "";
			position: absolute;
			left: 144rpx;
			right: 0;
			bottom: 0;
			height: 1rpx;
			background-color: #F0F0F0;
		}

		&:last-child::after {
			display: none;
		}
	}

	.msg-icon-wrap {
		position: relative;
		width: 90rpx;
		height: 90rpx;
		border-radius: 12rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		margin-right: 22rpx;
		flex-shrink: 0;

		&.activity { background-color: #7AA1D2; color: #FFFFFF; }
		&.forum { background-color: #8CC08C; color: #FFFFFF; }
		&.system { background-color: #E6B87A; color: #FFFFFF; }
	}

	.msg-icon {
		width: 44rpx;
		height: 44rpx;
	}

	.unread-dot {
		position: absolute;
		top: -8rpx;
		right: -8rpx;
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
		justify-content: center;
	}

	.msg-header {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-bottom: 6rpx;
	}

	.msg-title {
		font-size: 32rpx;
		font-weight: 500;
		color: #1D1D1F;
	}

	.msg-time {
		font-size: 24rpx;
		color: #B2B2B2;
	}

	.msg-footer {
		display: flex;
		align-items: center;
	}

	.msg-desc {
		font-size: 26rpx;
		color: #999999;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
		flex: 1;
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
