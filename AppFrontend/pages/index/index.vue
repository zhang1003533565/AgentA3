<template>
	<view class="home-page">
		<view class="hero-shell">
			<swiper
				class="hero-swiper"
				indicator-dots
				autoplay
				circular
				interval="3200"
				duration="500"
			>
				<swiper-item v-for="item in heroSlides" :key="item.title">
					<view class="poster-card" :class="`poster-card--${item.theme}`">
						<image class="poster-image" :src="item.image" mode="aspectFill" />
					</view>
				</swiper-item>
			</swiper>
		</view>

		<view class="main-content">
			<view class="quick-nav">
				<view
					v-for="item in quickChannels"
					:key="item.name"
					class="quick-item"
					@click="navigate(item.path, item.mode)"
				>
					<view class="quick-icon" :class="`quick-icon--${item.theme}`">
						<image class="quick-icon-img" :src="`/static/icons/line/${item.icon}.svg`" mode="aspectFit" />
					</view>
					<text class="quick-label">{{ item.name }}</text>
				</view>
			</view>

			<view class="headline-card" @click="goToNotice">
				<view class="headline-horn" aria-hidden="true">
					<view class="headline-horn-body"></view>
					<view class="headline-horn-mouth"></view>
					<view class="headline-horn-tail"></view>
				</view>
				<view class="headline-marquee">
					<view class="headline-track">
						<text class="headline-text">{{ headline }}</text>
						<text class="headline-separator">·</text>
						<text class="headline-text">{{ headline }}</text>
					</view>
				</view>
				<view class="headline-arrow">›</view>
			</view>

			<view class="supplement-grid">
				<view class="supplement-card supplement-card--ai" @click="goToServiceHub('AI创作')">
					<text class="supplement-title">AI创作</text>
					<text class="supplement-desc">文案、海报与灵感生成</text>
					<view class="supplement-arrow">›</view>
				</view>
				<view class="supplement-card supplement-card--course">
					<text class="supplement-title">课程表</text>
					<text class="supplement-desc">查看今日课程与本周安排</text>
					<view class="supplement-arrow" @click.stop="switchHomeDay">›</view>
				</view>
			</view>

			<home-schedule-card />
		</view>

		<app-main-tab-bar current="index" />
	</view>
</template>

<script>
import AppMainTabBar from '@/components/app-main-tab-bar/app-main-tab-bar.vue'
import HomeScheduleCard from '@/components/home-schedule-card/home-schedule-card.vue'

export default {
	components: { AppMainTabBar, HomeScheduleCard },
	data() {
		return {
			headline: '关于 2025 级新生校园服务一体通平台上线的通知',
			heroSlides: [
				{
					title: 'slide-1',
					image: 'https://images.unsplash.com/photo-1519389950473-47ba0277781c?auto=format&fit=crop&w=1200&q=80',
					theme: 'blue'
				},
				{
					title: 'slide-2',
					image: 'https://images.unsplash.com/photo-1523050854058-8df90110c9f1?auto=format&fit=crop&w=1200&q=80',
					theme: 'violet'
				},
				{
					title: 'slide-3',
					image: 'https://images.unsplash.com/photo-1503676382389-4809596d5290?auto=format&fit=crop&w=1200&q=80',
					theme: 'green'
				}
			],
			quickChannels: [
				{ name: '活', icon: 'compass', path: '/subpackage_community/communityActivity/communityActivity', theme: 'mint' },
				{ name: '食', icon: 'venue', path: '/subpackage_facility/restaurantDetail/restaurantDetail?id=3', theme: 'sand' },
				{ name: '旧', icon: 'clipboard', path: '/subpackage_lostfound/lostfoundList/lostfoundList', theme: 'orange' },
				{ name: '坛', icon: 'message-circle', path: '/subpackage_forum/forumList/forumList', mode: 'reLaunch', theme: 'peach' },
				{ name: '惠', icon: 'award', path: '/pages/serviceHub/serviceHub?title=校园特惠', theme: 'sky' }
			]
		}
	},
	onLoad() {
		this.checkLogin()
	},
	onShow() {
		this.checkLogin()
	},
	methods: {
		checkLogin() {
			const token = uni.getStorageSync('token')
			const userInfoStr = uni.getStorageSync('userInfo')
			if (!token || !userInfoStr) {
				uni.reLaunch({
					url: '/pages/login/login'
				})
			}
		},
		navigate(path, mode = 'navigateTo') {
			if (mode === 'reLaunch') {
				uni.reLaunch({ url: path })
				return
			}
			uni.navigateTo({ url: path })
		},
		goToNotice() {
			uni.navigateTo({
				url: '/pages/notice/notice'
			})
		},
		goToServiceHub(title) {
			uni.navigateTo({
				url: `/pages/serviceHub/serviceHub?title=${encodeURIComponent(title)}`
			})
		}
	}
}
</script>

<style lang="scss">
.home-page {
	min-height: 100vh;
	background: #f8f6ef;
	padding-bottom: 240rpx;
}

.hero-shell {
	padding: 0;
	position: relative;
}

.hero-swiper {
	height: 602rpx;
}

.hero-swiper :deep(.uni-swiper-dots),
.hero-swiper ::v-deep .uni-swiper-dots {
	bottom: 56rpx !important;
}

.poster-card {
	position: relative;
	overflow: hidden;
	border-radius: 0;
	background: transparent;
	height: 602rpx;
}

.poster-card--violet {
	background: transparent;
}

.poster-card--green {
	background: transparent;
}

.poster-image {
	position: absolute;
	inset: 0;
	width: 100%;
	height: 100%;
}

.main-content {
	padding: 20rpx 28rpx 0;
	margin-top: -46rpx;
	position: relative;
	z-index: 3;
}

.quick-nav {
	display: grid;
	grid-template-columns: repeat(5, 1fr);
	gap: 12rpx;
	padding: 0 2rpx;
}

.quick-item {
	background: rgba(255, 255, 255, 0.86);
	border-radius: 24rpx;
	padding: 16rpx 0 12rpx;
	display: flex;
	flex-direction: column;
	align-items: center;
	box-shadow: 0 10rpx 22rpx rgba(160, 176, 184, 0.16);
}

.quick-icon {
	width: 72rpx;
	height: 72rpx;
	border-radius: 22rpx;
	display: flex;
	align-items: center;
	justify-content: center;
}

.quick-icon--mint { background: linear-gradient(180deg, #dff4dc, #c5ecb8); }
.quick-icon--sand { background: linear-gradient(180deg, #f8edd1, #f3d89a); }
.quick-icon--orange { background: linear-gradient(180deg, #fee5c3, #ffc785); }
.quick-icon--peach { background: linear-gradient(180deg, #fde0d7, #fdc0ac); }
.quick-icon--sky { background: linear-gradient(180deg, #dbf0fb, #b6dbf3); }

.quick-label {
	margin-top: 10rpx;
	font-size: 34rpx;
	font-weight: 700;
	color: #4d7895;
}

.headline-card {
	margin-top: 34rpx;
	background: rgba(255, 255, 255, 0.82);
	border-radius: 24rpx;
	padding: 22rpx 24rpx;
	display: flex;
	align-items: center;
	box-shadow: 0 10rpx 24rpx rgba(184, 195, 200, 0.16);
	overflow: hidden;
}

.headline-horn {
	flex-shrink: 0;
	position: relative;
	width: 42rpx;
	height: 42rpx;
	margin-right: 18rpx;
}

.headline-horn-body {
	position: absolute;
	left: 0;
	top: 10rpx;
	width: 18rpx;
	height: 20rpx;
	background: #62b6b3;
	border-radius: 6rpx;
}

.headline-horn-mouth {
	position: absolute;
	left: 14rpx;
	top: 6rpx;
	width: 0;
	height: 0;
	border-top: 14rpx solid transparent;
	border-bottom: 14rpx solid transparent;
	border-left: 20rpx solid #62b6b3;
}

.headline-horn-tail {
	position: absolute;
	left: 4rpx;
	bottom: 0;
	width: 8rpx;
	height: 12rpx;
	background: #4f8f90;
	border-radius: 4rpx;
	transform: skew(-10deg);
}

.headline-marquee {
	flex: 1;
	overflow: hidden;
	min-width: 0;
}

.headline-track {
	display: inline-flex;
	align-items: center;
	white-space: nowrap;
	animation: headline-marquee 16s linear infinite;
	will-change: transform;
}

.headline-text {
	font-size: 30rpx;
	color: #4b585d;
	flex-shrink: 0;
}

.headline-separator {
	margin: 0 34rpx;
	font-size: 30rpx;
	color: #88a2a4;
}

.headline-arrow {
	margin-left: 14rpx;
	width: 34rpx;
	height: 34rpx;
	border-radius: 50%;
	background: #4e8487;
	color: #fff;
	display: flex;
	align-items: center;
	justify-content: center;
	font-size: 22rpx;
}

@keyframes headline-marquee {
	0% {
		transform: translateX(100%);
	}
	100% {
		transform: translateX(-100%);
	}
}

.supplement-grid {
	display: grid;
	grid-template-columns: 1fr 1fr;
	gap: 18rpx;
	margin-top: 28rpx;
}

.supplement-card {
	position: relative;
	min-height: 168rpx;
	border-radius: 28rpx;
	padding: 28rpx 24rpx;
	box-shadow: 0 16rpx 30rpx rgba(193, 198, 182, 0.18);
}

.supplement-card--ai {
	background: linear-gradient(180deg, #fff4df, #fffaf1);
}

.supplement-card--course {
	background: linear-gradient(180deg, #eef5ff, #f8fbff);
}

.supplement-title {
	display: block;
	font-size: 38rpx;
	font-weight: 800;
	color: #57737d;
}

.supplement-desc {
	display: block;
	margin-top: 12rpx;
	font-size: 24rpx;
	line-height: 1.6;
	color: #93a2a7;
}

.supplement-arrow {
	position: absolute;
	right: 20rpx;
	bottom: 18rpx;
	width: 34rpx;
	height: 34rpx;
	border-radius: 50%;
	background: rgba(113, 137, 145, 0.12);
	color: #66828b;
	display: flex;
	align-items: center;
	justify-content: center;
	font-size: 22rpx;
}

</style>
