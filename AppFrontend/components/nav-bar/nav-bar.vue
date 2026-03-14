<template>
	<view class="nav-bar" :style="{ paddingTop: statusBarHeight + 'px' }">
		<view class="nav-inner">
			<view v-if="showBack" class="nav-back" @click="onBack">
				<text class="nav-back-icon">‹</text>
			</view>
			<view class="nav-title-wrap">
				<text class="nav-title">{{ title }}</text>
			</view>
		</view>
	</view>
</template>

<script>
	export default {
		name: 'NavBar',
		props: {
			title: { type: String, default: '' },
			showBack: { type: Boolean, default: true }
		},
		data() {
			return {
				statusBarHeight: 20
			}
		},
		mounted() {
			try {
				const sys = uni.getSystemInfoSync()
				this.statusBarHeight = sys.statusBarHeight || 20
			} catch (e) {}
		},
		methods: {
			onBack() {
				uni.navigateBack({
					delta: 1,
					fail: () => {
						uni.reLaunch({ url: '/pages/index/index' })
					}
				})
			}
		}
	}
</script>

<style lang="scss" scoped>
	/* 参考图：白底、左侧返回、标题在「除返回键外剩余区域」内水平居中、底部分割线 */
	.nav-bar {
		background-color: #FFFFFF;
		border-bottom: 1px solid #E2E8F0;
		padding-left: 32rpx;
		padding-right: 32rpx;
		padding-bottom: 8rpx;
	}
	.nav-inner {
		height: 60rpx;
		display: flex;
		align-items: center;
	}
	.nav-back {
		width: 60rpx;
		height: 60rpx;
		display: flex;
		align-items: center;
		justify-content: flex-start;
		margin-left: -32rpx;
		flex-shrink: 0;
	}
	.nav-back-icon {
		font-size: 48rpx;
		color: #1D1D1F;
		line-height: 1;
		font-weight: 400;
	}
	.nav-title-wrap {
		flex: 1;
		min-width: 0;
		display: flex;
		justify-content: center;
		align-items: center;
	}
	.nav-title {
		font-size: 36rpx;
		font-weight: 700;
		color: #1D1D1F;
		letter-spacing: -0.03em;
		line-height: 60rpx;
	}
</style>
