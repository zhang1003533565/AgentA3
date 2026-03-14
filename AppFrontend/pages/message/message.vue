<template>
	<view class="page-wrap">
		<nav-bar title="通知与消息" :showBack="false" />
		<view class="message-page">
			<view class="page-header">
				<text class="page-desc">系统通知、活动助手</text>
			</view>
			<!-- 分组列表：每组白块，组间 12px 灰间距 -->
			<view class="message-groups">
				<view v-for="(group, gIndex) in messageGroups" :key="gIndex" class="message-group">
					<view class="group-block">
						<view class="group-title">{{ group.title }}</view>
						<view class="msg-list">
							<view v-if="!(group.list && group.list.length)" class="empty-tip">
								<text class="empty-text">暂无{{ group.title }}</text>
							</view>
							<view v-else class="msg-item" v-for="(item, i) in group.list" :key="i" @click="onMsg(item)">
								<view class="msg-icon-wrap">
									<text class="msg-icon">{{ item.icon || '🔔' }}</text>
								</view>
								<view class="msg-content">
									<view class="msg-row">
										<text class="msg-title">{{ item.title }}</text>
										<text class="msg-time">{{ item.time }}</text>
									</view>
									<text class="msg-desc">{{ item.desc }}</text>
								</view>
							</view>
						</view>
					</view>
				</view>
			</view>
		</view>
		<custom-tab-bar current="message" />
	</view>
</template>

<script>
	import CustomTabBar from '@/components/custom-tab-bar/custom-tab-bar.vue'
	import NavBar from '@/components/nav-bar/nav-bar.vue'
	export default {
		components: { CustomTabBar, NavBar },
		data() {
			return {
				messageGroups: [
					{ title: '系统通知', list: [] },
					{ title: '活动通知', list: [] }
				]
			}
		},
		onLoad() {
			// 可在此请求分组数据
		},
		methods: {
			onMsg(item) {
				// 进入通知详情
			}
		}
	}
</script>

<style lang="scss" scoped>
	.page-wrap {
		min-height: 100vh;
		background-color: #F7F7F9;
		padding-bottom: 120rpx;
	}
	.message-page {
		padding: 32rpx;
	}
	.page-header {
		padding: 24rpx 0 32rpx;
	}
	.page-desc {
		display: block;
		font-size: 24rpx;
		font-weight: 400;
		color: #4A4A4A;
		margin-top: 8rpx;
	}

	/* 分组：每组一块白底，组间 12px 灰间距 */
	.message-groups {
		display: flex;
		flex-direction: column;
		gap: 24rpx;   /* 12px */
	}
	.message-group {
		width: 100%;
	}
	.group-block {
		background-color: #FFFFFF;
		border-radius: 12rpx;
		overflow: hidden;
		padding: 0 32rpx;
	}
	.group-title {
		font-size: 28rpx;
		font-weight: 600;
		color: #8E8E93;
		padding: 24rpx 0 16rpx;
		border-bottom: 1px solid #EEEEEE;
	}
	.msg-list {
		display: flex;
		flex-direction: column;
	}
	.msg-item {
		display: flex;
		align-items: flex-start;
		padding: 24rpx 0;
		border-bottom: 1px solid #EEEEEE;
	}
	.msg-item:last-child {
		border-bottom: none;
	}
	/* 左侧图标：圆形容器 + 微弱渐变背景 */
	.msg-icon-wrap {
		width: 72rpx;
		height: 72rpx;
		border-radius: 50%;
		background: linear-gradient(145deg, #F5F5F7 0%, #EBEBED 100%);
		display: flex;
		align-items: center;
		justify-content: center;
		flex-shrink: 0;
		margin-right: 24rpx;
	}
	.msg-icon {
		font-size: 36rpx;
	}
	.msg-content {
		flex: 1;
		min-width: 0;
	}
	.msg-row {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-bottom: 8rpx;
	}
	.msg-title {
		font-size: 28rpx;
		font-weight: 400;
		color: #4A4A4A;
		flex: 1;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.msg-time {
		font-size: 24rpx;
		color: #8E8E93;
		margin-left: 16rpx;
	}
	.msg-desc {
		font-size: 24rpx;
		font-weight: 400;
		color: #8E8E93;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
		display: block;
	}
	.empty-tip {
		text-align: center;
		padding: 48rpx 0;
	}
	.empty-text {
		font-size: 28rpx;
		font-weight: 400;
		color: #8E8E93;
	}
</style>
