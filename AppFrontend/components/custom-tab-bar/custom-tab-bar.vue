<template>
	<view class="tab-bar">
		<view class="tab-item" @click="onTab(0)">
			<view class="tab-icon-wrap" :class="{ active: current === 'index' }">
				<svg class="tab-icon-svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
					<path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
					<polyline points="9 22 9 12 15 12 15 22" />
				</svg>
			</view>
			<text class="tab-text" :class="{ active: current === 'index' }">首页</text>
		</view>
		<view class="tab-item" @click="onTab(1)">
			<view class="tab-icon-wrap" :class="{ active: current === 'activity' }">
				<svg class="tab-icon-svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
					<rect x="3" y="4" width="18" height="18" rx="2" ry="2" />
					<line x1="16" y1="2" x2="16" y2="6" />
					<line x1="8" y1="2" x2="8" y2="6" />
					<line x1="3" y1="10" x2="21" y2="10" />
				</svg>
			</view>
			<text class="tab-text" :class="{ active: current === 'activity' }">活动</text>
		</view>
		<view class="tab-item tab-plus" @click="onPlus">
			<view class="plus-btn">
				<text class="plus-icon">+</text>
			</view>
		</view>
		<view class="tab-item" @click="onTab(3)">
			<view class="tab-icon-wrap" :class="{ active: current === 'message' }">
				<svg class="tab-icon-svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
					<path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
					<path d="M13.73 21a2 2 0 0 1-3.46 0" />
				</svg>
			</view>
			<text class="tab-text" :class="{ active: current === 'message' }">消息</text>
		</view>
		<view class="tab-item" @click="onTab(4)">
			<view class="tab-icon-wrap" :class="{ active: current === 'mine' }">
				<svg class="tab-icon-svg tab-icon-person" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
					<circle cx="12" cy="8" r="4" />
					<path d="M4 20c0-4 4-6 8-6s8 2 8 6" />
				</svg>
			</view>
			<text class="tab-text" :class="{ active: current === 'mine' }">我的</text>
		</view>
	</view>
</template>

<script>
	export default {
		name: 'CustomTabBar',
		props: {
			current: {
				type: String,
				default: 'index'  // index | activity | message | mine
			}
		},
		data() {
			return {}
		},
		methods: {
			onTab(index) {
				// 首页、活动发现、消息、我的；活动与首页「校园活动」均进入活动发现，我的活动在活动发现页内入口
				const routes = ['/pages/index/index', '/subpackage_activity/activityList/activityList', '', '/pages/message/message', '/pages/mine/mine']
				const url = routes[index]
				if (!url) return
				const pages = getCurrentPages()
				const cur = pages[pages.length - 1]
				const curRoute = cur ? ('/' + cur.route) : ''
				if (curRoute === url) return
				// 使用 reLaunch 切换 tab 页，保证底部栏常驻（各 tab 页与子页均自带 custom-tab-bar）
				uni.reLaunch({ url })
			},
			onPlus() {
				uni.showActionSheet({
					itemList: ['扫码签到', '发失物招领'],
					success: (res) => {
						if (res.tapIndex === 0) {
							uni.scanCode({
								success: () => {
									uni.showToast({ title: '签到成功', icon: 'success' })
								}
							})
						} else if (res.tapIndex === 1) {
							uni.navigateTo({ url: '/pages/lostfound/lostfound' })
						}
					}
				})
			}
		}
	}
</script>

<style lang="scss" scoped>
	.tab-bar {
		position: fixed;
		left: 0;
		right: 0;
		bottom: 0;
		height: 100rpx;
		padding-bottom: env(safe-area-inset-bottom);
		background-color: #FFFFFF;
		border-top: 1px solid #EEEEEE;
		display: flex;
		align-items: flex-end;
		justify-content: space-around;
		z-index: 9999;
		overflow: visible;
	}
	.tab-item {
		flex: 1;
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		padding-bottom: 8rpx;
	}
	.tab-icon-wrap {
		width: 48rpx;
		height: 48rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		margin-bottom: 2rpx;
		color: #48484A;
	}
	.tab-icon-wrap.active {
		color: #5C7A99;
	}
	.tab-icon-svg {
		width: 44rpx;
		height: 44rpx;
	}
	.tab-text {
		font-size: 20rpx;
		color: #8E8E93;
		font-weight: 400;
	}
	.tab-text.active {
		color: #5C7A99;
		font-weight: 600;
	}
	.tab-plus {
		flex: 1;
		padding-bottom: 0;
		justify-content: flex-end;
		align-items: center;
		overflow: visible;
	}
	.plus-btn {
		width: 88rpx;
		height: 88rpx;
		border-radius: 50%;
		background-color: #5C7A99;
		display: flex;
		align-items: center;
		justify-content: center;
		margin-bottom: 0;
		transform: translateY(-24rpx);
	}
	.plus-icon {
		font-size: 52rpx;
		color: #FFFFFF;
		line-height: 1;
		font-weight: 300;
	}
</style>
