<template>
	<view class="nav-bar" :class="{ 'nav-bar--glass': glass }" :style="{ paddingTop: statusBarHeight + 'px' }">
		<view class="nav-inner">
			<view v-if="showBack" class="nav-back" @click="onBack">
				<text class="nav-back-icon">{{ backIcon }}</text>
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
			showBack: { type: Boolean, default: true },
			glass: { type: Boolean, default: false },
			backIcon: { type: String, default: '‹' }
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
				const pages = getCurrentPages()
				// 仅一页或从 reLaunch 进入（无上一页）时，直接回首页
				if (pages.length <= 1) {
					uni.reLaunch({ url: '/pages/index/index' })
					return
				}
				uni.navigateBack({ delta: 1 })
			}
		}
	}
</script>

<style lang="scss" scoped>
	/* 白底、左侧返回、标题相对整条导航栏左右绝对居中、底部分割线 */
	.nav-bar {
		background-color: #FFFFFF;
		border-bottom: 1px solid #E2E8F0;
		padding-left: 32rpx;
		padding-right: 32rpx;
		padding-bottom: 0;
		position: relative;
		z-index: 1000;

		&.nav-bar--glass {
			background: rgba(255, 255, 255, 0.72);
			backdrop-filter: blur(20rpx);
			-webkit-backdrop-filter: blur(20rpx);
			border-bottom-color: rgba(226, 232, 240, 0.6);
			padding-bottom: 24rpx;
		}
	}
	.nav-inner {
		position: relative;
		height: 88rpx;
		display: flex;
		align-items: center;
	}
	.nav-back {
		width: 88rpx;
		height: 88rpx;
		display: flex;
		align-items: center;
		justify-content: flex-start;
		flex-shrink: 0;
		position: relative;
		z-index: 1;
	}
	.nav-back-icon {
		font-size: 48rpx;
		color: #1D1D1F;
		line-height: 88rpx;
		font-weight: 400;
	}
	.nav-title-wrap {
		position: absolute;
		left: 0;
		right: 0;
		top: 0;
		bottom: 0;
		display: flex;
		justify-content: center;
		align-items: center;
		pointer-events: none;
	}
	.nav-title {
		font-size: 36rpx;
		font-weight: 700;
		color: #1D1D1F;
		letter-spacing: -0.03em;
		line-height: 88rpx;
	}
</style>
