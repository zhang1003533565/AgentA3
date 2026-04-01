<template>
	<view class="signin-container">
		<nav-bar title="活动签到" />
		
		<view class="signin-content" :style="{ paddingTop: navBarHeight + 'px' }">
			<view class="signin-card block-white">
				<view class="activity-info">
					<text class="label">当前活动</text>
					<text class="activity-title">{{ activity.title || '暂无正在进行的活动' }}</text>
					<view class="info-row">
						<image class="info-icon" src="/static/icons/line/calendar.svg" mode="aspectFit" />
						<text class="info-text">{{ activity.time || '-' }}</text>
					</view>
					<view class="info-row">
						<image class="info-icon" src="/static/icons/line/map.svg" mode="aspectFit" />
						<text class="info-text">{{ activity.location || '-' }}</text>
					</view>
				</view>

				<view class="divider"></view>

				<view class="status-section">
					<view class="status-badge" :class="signInStatus">
						{{ getStatusText() }}
					</view>
				</view>

				<view class="action-section">
					<view class="scan-box" @click="handleSignIn" v-if="signInStatus === 'pending'">
						<view class="scan-icon-wrap">
							<image class="scan-icon" src="/static/icons/line/search.svg" mode="aspectFit" />
						</view>
						<text class="scan-tip">点击扫码签到</text>
					</view>
					
					<view class="success-box" v-else-if="signInStatus === 'success'">
						<image class="success-icon" src="/static/icons/line/award.svg" mode="aspectFit" />
						<text class="success-time">签到时间：{{ signInTime }}</text>
						<button class="done-btn" @click="goBack">返回首页</button>
					</view>
				</view>
			</view>

			<view class="notice-card block-white">
				<view class="notice-header">
					<image class="notice-icon" src="/static/icons/line/message-circle.svg" mode="aspectFit" />
					<text class="notice-title">签到说明</text>
				</view>
				<view class="notice-body">
					<text class="notice-item">1. 请确保已开启手机定位权限</text>
					<text class="notice-item">2. 签到需在活动地点 200 米范围内有效</text>
					<text class="notice-item">3. 如遇扫码失败，请联系现场工作人员进行手动补签</text>
				</view>
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
				navBarHeight: 88,
				signInStatus: 'pending', // pending, success, fail
				signInTime: '',
				activity: {
					id: 1,
					title: '校园摄影大赛 - 线下分享会',
					time: '2026-03-25 14:00 - 16:00',
					location: '艺术楼一楼报告厅'
				}
			}
		},
		onLoad(options) {
			const sys = uni.getSystemInfoSync()
			this.navBarHeight = (sys.statusBarHeight || 20) + 44
			
			if (options.activityId) {
				// 根据 ID 加载活动信息
				console.log('加载活动:', options.activityId)
			}
		},
		methods: {
			getStatusText() {
				const texts = {
					pending: '待签到',
					success: '签到成功',
					fail: '签到失败'
				}
				return texts[this.signInStatus]
			},
			handleSignIn() {
				// 模拟扫码签到逻辑
				uni.scanCode({
					success: (res) => {
						console.log('扫码结果:', res.result)
						uni.showLoading({ title: '签到中...' })
						setTimeout(() => {
							uni.hideLoading()
							this.signInStatus = 'success'
							const now = new Date()
							this.signInTime = `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}`
							uni.showToast({ title: '签到成功', icon: 'success' })
						}, 1000)
					}
				})
			},
			goBack() {
				uni.reLaunch({ url: '/pages/index/index' })
			}
		}
	}
</script>

<style lang="scss">
	.signin-container {
		min-height: 100vh;
		background-color: #F7F7F9;
	}

	.signin-content {
		padding: 24rpx 32rpx;
	}

	.signin-card {
		padding: 40rpx 32rpx;
		background-color: #FFFFFF;
		border-radius: 24rpx;
		margin-bottom: 24rpx;
	}

	.activity-info {
		.label {
			font-size: 24rpx;
			color: #8E8E93;
			margin-bottom: 12rpx;
			display: block;
		}
		.activity-title {
			font-size: 36rpx;
			font-weight: 700;
			color: #1D1D1F;
			margin-bottom: 24rpx;
			display: block;
		}
		.info-row {
			display: flex;
			align-items: center;
			gap: 12rpx;
			margin-bottom: 8rpx;
			color: #4A4A4A;
			font-size: 28rpx;

			.info-icon {
				width: 32rpx;
				height: 32rpx;
				flex-shrink: 0;
			}
		}
	}

	.divider {
		height: 1px;
		background-color: #EEEEEE;
		margin: 40rpx 0;
	}

	.status-section {
		display: flex;
		justify-content: center;
		margin-bottom: 60rpx;
	}

	.status-badge {
		padding: 8rpx 24rpx;
		border-radius: 30rpx;
		font-size: 26rpx;
		font-weight: 600;
		
		&.pending { background-color: rgba(255, 149, 0, 0.1); color: #FF9500; }
		&.success { background-color: rgba(52, 199, 89, 0.1); color: #34C759; }
		&.fail { background-color: rgba(255, 59, 48, 0.1); color: #FF3B30; }
	}

	.action-section {
		display: flex;
		flex-direction: column;
		align-items: center;
	}

	.scan-box {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 24rpx;
		
		&:active .scan-icon-wrap {
			transform: scale(0.95);
			background-color: #E5E5E5;
		}
	}

	.scan-icon-wrap {
		width: 160rpx;
		height: 160rpx;
		background-color: #F2F2F2;
		border-radius: 40rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		color: $color-primary;
		transition: all 0.2s;
	}

	.scan-icon {
		width: 48rpx;
		height: 48rpx;
	}

	.scan-tip {
		font-size: 28rpx;
		color: #8E8E93;
	}

	.success-box {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 20rpx;
	}

	.success-icon {
		width: 48rpx;
		height: 48rpx;
	}

	.success-time {
		font-size: 30rpx;
		color: #1D1D1F;
		font-weight: 500;
	}

	.done-btn {
		margin-top: 40rpx;
		width: 300rpx;
		height: 80rpx;
		line-height: 80rpx;
		background-color: $color-primary;
		color: #FFFFFF;
		border-radius: 40rpx;
		font-size: 28rpx;
		border: none;
		&::after { border: none; }
	}

	.notice-card {
		padding: 32rpx;
		background-color: #FFFFFF;
		border-radius: 24rpx;
	}

	.notice-header {
		display: flex;
		align-items: center;
		gap: 12rpx;
		margin-bottom: 20rpx;
		color: #1D1D1F;

		.notice-icon {
			width: 32rpx;
			height: 32rpx;
			flex-shrink: 0;
		}
		
		.notice-title {
			font-size: 30rpx;
			font-weight: 600;
		}
	}

	.notice-body {
		display: flex;
		flex-direction: column;
		gap: 12rpx;
	}

	.notice-item {
		font-size: 26rpx;
		color: #8E8E93;
		line-height: 1.5;
	}
</style>
