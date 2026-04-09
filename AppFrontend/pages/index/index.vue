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

				<view class="home-quick-entry__item" @click="navigate('/pages/promotion/promotion')">
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
				<view class="supplement-card supplement-card--ai">
					<view class="ai-card-content">
						<view class="ai-card-text">
							<view class="title-deco">
								<view class="t-deco-line"></view>
								<view class="t-deco-dot"></view>
							</view>
							<text class="ai-card-title">智感工坊</text>
							<text class="ai-card-subtitle">让创意点亮校园生活</text>

							<view class="ai-card-btn" @click="navigate('/subpackage_ai/aiCreate/aiCreate')">
								<text class="ai-card-btn-text">立即体验</text>
								<view class="btn-arrow">→</view>
							</view>
						</view>
						<view class="ai-card-visual">
							<view class="ai-scene">
								<!-- 背景装饰圆 -->
								<view class="bg-circles">
									<view class="bg-circle c-1"></view>
									<view class="bg-circle c-2"></view>
									<view class="bg-circle c-3"></view>
								</view>
								<!-- 轨道装饰 -->
								<view class="orbit-ring orbit-1">
									<view class="orbit-dot"></view>
								</view>
								<view class="orbit-ring orbit-2">
									<view class="orbit-dot"></view>
								</view>
								<!-- 3D AI 盾牌 -->
								<view class="ai-shield">
									<view class="shield-body">
										<text class="shield-text">AI</text>
									</view>
									<view class="shield-shine"></view>
									<view class="shield-glow"></view>
								</view>
								<!-- 功能图标环绕 -->
								<view class="feature-orbit">
									<view class="orbit-icon icon-1">✍</view>
									<view class="orbit-icon icon-2">▶</view>
									<view class="orbit-icon icon-3">◈</view>
								</view>
								<!-- 上升箭头 -->
								<view class="trend-arrow">
									<view class="arrow-body"></view>
									<view class="arrow-head"></view>
								</view>
								<!-- 粒子效果 -->
								<view class="particles">
									<view class="particle p-1"></view>
									<view class="particle p-2"></view>
									<view class="particle p-3"></view>
									<view class="particle p-4"></view>
									<view class="particle p-5"></view>
									<view class="particle p-6"></view>
								</view>
							</view>
						</view>
					</view>
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
import { getEnabledAnnouncements } from '@/api/notice.js'

export default {
	components: { AppMainTabBar, HomeScheduleCard },
	data() {
		return {
			headline: '',
			announcements: [],
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
		this.fetchAnnouncements()
	},
	onShow() {
		this.checkLogin()
		this.fetchAnnouncements()
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
		},
		async fetchAnnouncements() {
			try {
				const res = await getEnabledAnnouncements()
				if (res.code === 200 && res.data && res.data.length > 0) {
					this.announcements = res.data
					this.headline = res.data[0].title
				} else {
					this.headline = '暂无公告'
				}
			} catch (err) {
				console.error('获取公告失败:', err)
				this.headline = '暂无公告'
			}
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
	grid-template-columns: 1fr;
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
	background: linear-gradient(135deg, #6B8BA4 0%, #5C7A99 50%, #4A6278 100%);
	overflow: hidden;
	min-height: 240rpx;
}

.ai-card-content {
	display: flex;
	justify-content: space-between;
	align-items: center;
	height: 100%;
	padding: 8rpx 0;
}

.ai-card-text {
	display: flex;
	flex-direction: column;
	gap: 12rpx;
	position: relative;
}

/* 标题装饰 */
.title-deco {
	display: flex;
	align-items: center;
	gap: 8rpx;
	margin-bottom: 4rpx;
}

.t-deco-line {
	width: 30rpx;
	height: 3rpx;
	background: linear-gradient(90deg, #FFD700, transparent);
	border-radius: 2rpx;
}

.t-deco-dot {
	width: 6rpx;
	height: 6rpx;
	background: #FFD700;
	border-radius: 50%;
	box-shadow: 0 0 10rpx rgba(255, 215, 0, 0.8);
}

.ai-card-title {
	font-size: 48rpx;
	font-weight: 900;
	color: #FFFFFF;
	letter-spacing: 4rpx;
	text-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.2);
}

.ai-card-subtitle {
	font-size: 24rpx;
	color: rgba(255, 255, 255, 0.9);
	line-height: 1.5;
	letter-spacing: 1rpx;
}

/* 功能标签 */
.feature-tags {
	display: flex;
	gap: 12rpx;
	margin-top: 8rpx;
}

.f-tag {
	padding: 8rpx 16rpx;
	background: rgba(255, 255, 255, 0.15);
	border: 1rpx solid rgba(255, 255, 255, 0.3);
	border-radius: 24rpx;
	font-size: 20rpx;
	color: rgba(255, 255, 255, 0.95);
	backdrop-filter: blur(4rpx);
	display: flex;
	align-items: center;
	gap: 6rpx;
}

.tag-icon {
	font-size: 18rpx;
	opacity: 0.9;
}

/* 底部数据展示 */
.data-show {
	display: flex;
	align-items: center;
	gap: 20rpx;
	margin-top: 20rpx;
	padding-top: 16rpx;
	border-top: 1rpx solid rgba(255, 255, 255, 0.15);
}

.data-item {
	display: flex;
	flex-direction: column;
	gap: 4rpx;
}

.data-num {
	font-size: 28rpx;
	font-weight: 800;
	color: #FFD700;
	text-shadow: 0 2rpx 8rpx rgba(255, 215, 0, 0.4);
}

.data-label {
	font-size: 18rpx;
	color: rgba(255, 255, 255, 0.7);
}

.data-divider {
	width: 1rpx;
	height: 30rpx;
	background: rgba(255, 255, 255, 0.2);
}

.ai-card-btn {
	margin-top: 24rpx;
	align-self: flex-start;
	background: linear-gradient(135deg, #8FA8B8, #6B8BA4);
	padding: 16rpx 36rpx;
	border-radius: 30rpx;
	display: flex;
	align-items: center;
	gap: 12rpx;
	box-shadow: 
		0 8rpx 24rpx rgba(92, 122, 153, 0.4),
		inset 0 2rpx 4rpx rgba(255, 255, 255, 0.3);
	position: relative;
	overflow: hidden;
}

.ai-card-btn::before {
	content: '';
	position: absolute;
	top: 0;
	left: -100%;
	width: 100%;
	height: 100%;
	background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.4), transparent);
	animation: btn-shine 2s ease-in-out infinite;
}

@keyframes btn-shine {
	0% { left: -100%; }
	50%, 100% { left: 100%; }
}

.ai-card-btn-text {
	font-size: 26rpx;
	font-weight: 700;
	color: #FFFFFF;
	letter-spacing: 2rpx;
}

.btn-arrow {
	width: 32rpx;
	height: 32rpx;
	background: rgba(255, 255, 255, 0.25);
	border-radius: 50%;
	display: flex;
	align-items: center;
	justify-content: center;
	font-size: 20rpx;
	color: #FFFFFF;
	font-weight: 700;
}

.ai-card-visual {
	position: relative;
	width: 300rpx;
	height: 200rpx;
	flex-shrink: 0;
}

.ai-scene {
	position: relative;
	width: 100%;
	height: 100%;
}

/* 背景装饰圆 */
.bg-circles {
	position: absolute;
	inset: 0;
	pointer-events: none;
}

.bg-circle {
	position: absolute;
	border-radius: 50%;
	border: 1rpx solid rgba(255, 255, 255, 0.1);
}

.c-1 {
	width: 120rpx;
	height: 120rpx;
	top: 20rpx;
	left: 20rpx;
}

.c-2 {
	width: 160rpx;
	height: 160rpx;
	top: 0;
	left: 60rpx;
	border-color: rgba(255, 255, 255, 0.08);
}

.c-3 {
	width: 80rpx;
	height: 80rpx;
	bottom: 10rpx;
	right: 30rpx;
	border-color: rgba(255, 255, 255, 0.12);
}

/* 轨道装饰 */
.orbit-ring {
	position: absolute;
	border: 2rpx dashed rgba(255, 255, 255, 0.2);
	border-radius: 50%;
}

.orbit-1 {
	width: 140rpx;
	height: 140rpx;
	top: 15rpx;
	left: 40rpx;
	animation: orbit-rotate 10s linear infinite;
}

.orbit-2 {
	width: 180rpx;
	height: 180rpx;
	top: -5rpx;
	left: 20rpx;
	border-color: rgba(255, 255, 255, 0.1);
	animation: orbit-rotate 15s linear infinite reverse;
}

.orbit-dot {
	position: absolute;
	width: 8rpx;
	height: 8rpx;
	background: #FFD700;
	border-radius: 50%;
	box-shadow: 0 0 12rpx rgba(255, 215, 0, 0.8);
}

.orbit-1 .orbit-dot {
	top: -4rpx;
	left: 50%;
	transform: translateX(-50%);
}

.orbit-2 .orbit-dot {
	bottom: -4rpx;
	left: 30%;
}

@keyframes orbit-rotate {
	0% { transform: rotate(0deg); }
	100% { transform: rotate(360deg); }
}

/* 3D AI 盾牌 */
.ai-shield {
	position: absolute;
	top: 50%;
	left: 50%;
	transform: translate(-50%, -50%);
	width: 120rpx;
	height: 140rpx;
}

.shield-body {
	position: absolute;
	inset: 0;
	background: linear-gradient(135deg, rgba(255, 255, 255, 0.95), rgba(255, 255, 255, 0.7));
	clip-path: polygon(50% 0%, 100% 25%, 100% 75%, 50% 100%, 0% 75%, 0% 25%);
	display: flex;
	align-items: center;
	justify-content: center;
	box-shadow: 
		0 20rpx 40rpx rgba(0, 0, 0, 0.3),
		inset 0 2rpx 10rpx rgba(255, 255, 255, 0.8);
}

.shield-text {
	font-size: 44rpx;
	font-weight: 900;
	color: #5C7A99;
	letter-spacing: 2rpx;
	text-shadow: 0 2rpx 4rpx rgba(0, 0, 0, 0.1);
}

.shield-shine {
	position: absolute;
	top: 10rpx;
	left: 20rpx;
	width: 30rpx;
	height: 40rpx;
	background: linear-gradient(135deg, rgba(255, 255, 255, 0.8), transparent);
	clip-path: polygon(50% 0%, 100% 25%, 100% 75%, 50% 100%, 0% 75%, 0% 25%);
	pointer-events: none;
}

.shield-glow {
	position: absolute;
	inset: -20rpx;
	background: radial-gradient(circle, rgba(255, 255, 255, 0.3) 0%, transparent 70%);
	border-radius: 50%;
	pointer-events: none;
	animation: glow-pulse 2s ease-in-out infinite;
}

@keyframes glow-pulse {
	0%, 100% { opacity: 0.5; transform: scale(1); }
	50% { opacity: 0.8; transform: scale(1.1); }
}

/* 功能图标环绕 */
.feature-orbit {
	position: absolute;
	inset: 0;
	pointer-events: none;
}

.orbit-icon {
	position: absolute;
	width: 36rpx;
	height: 36rpx;
	background: rgba(255, 255, 255, 0.15);
	border: 1rpx solid rgba(255, 255, 255, 0.3);
	border-radius: 50%;
	display: flex;
	align-items: center;
	justify-content: center;
	font-size: 16rpx;
	color: rgba(255, 255, 255, 0.9);
	backdrop-filter: blur(4rpx);
}

.icon-1 {
	top: 20rpx;
	right: 40rpx;
	animation: icon-float 3s ease-in-out infinite;
}

.icon-2 {
	top: 80rpx;
	right: 10rpx;
	animation: icon-float 3s ease-in-out 1s infinite;
}

.icon-3 {
	bottom: 30rpx;
	right: 50rpx;
	animation: icon-float 3s ease-in-out 2s infinite;
}

@keyframes icon-float {
	0%, 100% { transform: translateY(0) scale(1); }
	50% { transform: translateY(-10rpx) scale(1.05); }
}

/* 上升箭头 */
.trend-arrow {
	position: absolute;
	bottom: 20rpx;
	right: 10rpx;
	width: 60rpx;
	height: 50rpx;
}

.arrow-body {
	position: absolute;
	bottom: 0;
	left: 20rpx;
	width: 8rpx;
	height: 40rpx;
	background: linear-gradient(180deg, #FFA94D, #FF8C42);
	border-radius: 4rpx;
	transform: rotate(-30deg);
	transform-origin: bottom center;
}

.arrow-head {
	position: absolute;
	top: 0;
	left: 8rpx;
	width: 0;
	height: 0;
	border-left: 16rpx solid transparent;
	border-right: 16rpx solid transparent;
	border-bottom: 24rpx solid #FF8C42;
	transform: rotate(-30deg);
}

/* 粒子效果 */
.particles {
	position: absolute;
	inset: 0;
	pointer-events: none;
}

.particle {
	position: absolute;
	width: 4rpx;
	height: 4rpx;
	background: rgba(255, 255, 255, 0.6);
	border-radius: 50%;
	animation: particle-float 4s ease-in-out infinite;
}

.p-1 { top: 20%; left: 10%; animation-delay: 0s; }
.p-2 { top: 40%; left: 5%; animation-delay: 0.5s; }
.p-3 { top: 60%; left: 15%; animation-delay: 1s; }
.p-4 { top: 30%; right: 5%; animation-delay: 1.5s; }
.p-5 { top: 70%; right: 15%; animation-delay: 2s; }
.p-6 { top: 50%; left: 20%; animation-delay: 2.5s; }

@keyframes particle-float {
	0%, 100% { 
		opacity: 0.3; 
		transform: translateY(0) scale(1); 
	}
	50% { 
		opacity: 0.8; 
		transform: translateY(-20rpx) scale(1.5); 
	}
}

/* 装饰线条 */
.deco-lines {
	position: absolute;
	top: 20rpx;
	left: 10rpx;
	width: 40rpx;
	height: 60rpx;
}

.d-line {
	position: absolute;
	width: 3rpx;
	background: rgba(255, 255, 255, 0.4);
	border-radius: 2rpx;
}

.d-line-1 {
	height: 20rpx;
	left: 10rpx;
	top: 0;
}

.d-line-2 {
	height: 35rpx;
	left: 20rpx;
	top: 10rpx;
}

.d-line-3 {
	height: 25rpx;
	left: 30rpx;
	top: 5rpx;
}

/* 光点装饰 */
.sparkles {
	position: absolute;
	inset: 0;
	pointer-events: none;
}

.sparkle-dot {
	position: absolute;
	width: 8rpx;
	height: 8rpx;
	background: #FFD700;
	border-radius: 50%;
	box-shadow: 0 0 16rpx 4rpx rgba(255, 215, 0, 0.8);
}

.s-1 {
	top: 20rpx;
	right: 30rpx;
	animation: sparkle-blink 1.5s ease-in-out infinite;
}

.s-2 {
	top: 60rpx;
	left: 20rpx;
	animation: sparkle-blink 1.5s ease-in-out 0.5s infinite;
}

.s-3 {
	bottom: 40rpx;
	right: 50rpx;
	width: 6rpx;
	height: 6rpx;
	animation: sparkle-blink 1.5s ease-in-out 1s infinite;
}

.s-4 {
	top: 40rpx;
	left: 50rpx;
	width: 5rpx;
	height: 5rpx;
	animation: sparkle-blink 2s ease-in-out 0.3s infinite;
}

.s-5 {
	bottom: 60rpx;
	left: 35rpx;
	width: 4rpx;
	height: 4rpx;
	animation: sparkle-blink 2s ease-in-out 0.8s infinite;
}

@keyframes sparkle-blink {
	0%, 100% {
		opacity: 0.4;
		transform: scale(0.8);
	}
	50% {
		opacity: 1;
		transform: scale(1.2);
	}
}

/* 环形装饰 */
.ring-deco {
	position: absolute;
	inset: 0;
	pointer-events: none;
}

.r-ring {
	position: absolute;
	border: 2rpx solid rgba(255, 255, 255, 0.2);
	border-radius: 50%;
}

.r-ring-1 {
	width: 60rpx;
	height: 60rpx;
	top: 30rpx;
	left: 30rpx;
	animation: ring-rotate 8s linear infinite;
}

.r-ring-2 {
	width: 80rpx;
	height: 80rpx;
	bottom: 20rpx;
	right: 20rpx;
	border-color: rgba(255, 255, 255, 0.1);
	animation: ring-rotate 10s linear infinite reverse;
}

@keyframes ring-rotate {
	0% {
		transform: rotate(0deg);
	}
	100% {
		transform: rotate(360deg);
	}
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
