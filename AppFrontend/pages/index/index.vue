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
			<view class="home-quick-entry">
				<view class="home-quick-entry__item" @click="navigate('/subpackage_community/communityActivity/communityActivity')">
					<view class="home-quick-entry__icon-wrapper">
						<image class="home-quick-entry__icon home-quick-entry__icon--xlarge" src="/static/APPIcon/tabbar/compass.png" mode="aspectFit" />
					</view>
					<view class="home-quick-entry__card">
						<text class="home-quick-entry__text">活</text>
					</view>
				</view>

				<view class="home-quick-entry__item" @click="navigate('/subpackage_facility/restaurantDetail/restaurantDetail?id=3')">
					<view class="home-quick-entry__icon-wrapper">
						<image class="home-quick-entry__icon home-quick-entry__icon--medium" src="/static/APPIcon/tabbar/venue.png" mode="aspectFit" />
					</view>
					<view class="home-quick-entry__card">
						<text class="home-quick-entry__text">食</text>
					</view>
				</view>

				<view class="home-quick-entry__item" @click="navigate('/subpackage_lostfound/lostfoundList/lostfoundList')">
					<view class="home-quick-entry__icon-wrapper">
						<image class="home-quick-entry__icon" src="/static/APPIcon/tabbar/clipboard.png" mode="aspectFit" />
					</view>
					<view class="home-quick-entry__card">
						<text class="home-quick-entry__text">失</text>
					</view>
				</view>

				<view class="home-quick-entry__item" @click="navigate('/subpackage_forum/forumList/forumList', 'reLaunch')">
					<view class="home-quick-entry__icon-wrapper">
						<image class="home-quick-entry__icon home-quick-entry__icon--small" src="/static/APPIcon/tabbar/message-circle.png" mode="aspectFit" />
					</view>
					<view class="home-quick-entry__card">
						<text class="home-quick-entry__text">坛</text>
					</view>
				</view>

				<view class="home-quick-entry__item" @click="navigate('/pages/serviceHub/serviceHub?title=校园特惠')">
					<view class="home-quick-entry__icon-wrapper">
						<image class="home-quick-entry__icon" src="/static/APPIcon/tabbar/award.png" mode="aspectFit" />
					</view>
					<view class="home-quick-entry__card">
						<text class="home-quick-entry__text">惠</text>
					</view>
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

.home-quick-entry {
	display: flex;
	justify-content: space-between;
	padding: 0 24rpx;
	margin-top: 24rpx;
}

.home-quick-entry__item {
	position: relative;
	width: 120rpx;
	height: 150rpx;
	display: flex;
	justify-content: center;
	align-items: flex-end;
}

.home-quick-entry__icon-wrapper {
	position: absolute;
	left: 50%;
	top: -32rpx;
	transform: translateX(-50%);
	z-index: 2;

	width: 200rpx;
	height: 200rpx;

	display: flex;
	justify-content: center;
	align-items: center;
	pointer-events: none;
}

.home-quick-entry__card {
	position: relative;
	width: 100%;
	height: 96rpx;
	background: #ffffff;
	border-radius: 28rpx;
	box-shadow: 0 8rpx 20rpx rgba(0, 0, 0, 0.06);
	display: flex;
	justify-content: center;
	align-items: flex-end;
	padding-bottom: 16rpx;
	box-sizing: border-box;
	z-index: 1;
}

.home-quick-entry__icon {
	width: 200rpx;
	height: 200rpx;
	display: block;
	object-fit: contain;
}

.home-quick-entry__icon--xlarge {
	width: 240rpx;
	height: 240rpx;
}

.home-quick-entry__icon--medium {
	width: 175rpx;
	height: 175rpx;
}

.home-quick-entry__icon--small {
	width: 160rpx;
	height: 160rpx;
}

.home-quick-entry__text {
	font-size: 24rpx;
	color: #4f7f8f;
	font-weight: 600;
	line-height: 1;
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
