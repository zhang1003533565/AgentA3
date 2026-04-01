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
						<icon-line :name="item.icon" size="service" color="#4E7EA5" />
					</view>
					<text class="quick-label">{{ item.name }}</text>
				</view>
			</view>

			<view class="headline-card" @click="goToNotice">
				<text class="headline-tag">云游头条</text>
				<text class="headline-text">{{ headline }}</text>
				<view class="headline-arrow">›</view>
			</view>

			<view class="city-card">
				<view class="skyline">
					<view class="skyline-build skyline-build-a"></view>
					<view class="skyline-build skyline-build-b"></view>
					<view class="skyline-build skyline-build-c"></view>
					<view class="skyline-build skyline-build-d"></view>
					<view class="skyline-bridge"></view>
					<view class="skyline-cloud skyline-cloud-a"></view>
					<view class="skyline-cloud skyline-cloud-b"></view>
				</view>

				<view class="coupon-bubble" @click="goToServiceHub('惠民补贴')">
					<text class="coupon-text">惠民补贴 | 点击领取</text>
					<text class="coupon-arrow">›</text>
				</view>

				<view class="city-mascot">
					<view class="mascot-head"></view>
					<view class="mascot-face"></view>
					<view class="mascot-body"></view>
				</view>

				<view class="city-stats">
					<view class="city-stat">
						<text class="city-stat-value">4月2日</text>
						<text class="city-stat-label">活动日期</text>
					</view>
					<view class="city-stat">
						<text class="city-stat-value">8个活动</text>
						<text class="city-stat-label">热门活动</text>
					</view>
				</view>

				<view class="feature-grid">
					<view
						v-for="item in featureCards"
						:key="item.title"
						class="feature-card"
						:class="`feature-card--${item.theme}`"
						@click="navigate(item.path, item.mode)"
					>
						<text class="feature-title">{{ item.title }}</text>
						<text class="feature-desc">{{ item.desc }}</text>
						<view class="feature-arrow">›</view>
					</view>
				</view>
			</view>
		</view>

		<app-main-tab-bar current="index" />
	</view>
</template>

<script>
import AppMainTabBar from '@/components/app-main-tab-bar/app-main-tab-bar.vue'
import IconLine from '@/components/icon-line/icon-line.vue'

export default {
	components: { AppMainTabBar, IconLine },
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
				{ name: '学', icon: 'book-open', path: '/subpackage_schedule/schedule/schedule', theme: 'mint' },
				{ name: '住', icon: 'key', path: '/subpackage_dormitory/dormitoryList/dormitoryList', theme: 'sand' },
				{ name: '食', icon: 'venue', path: '/subpackage_facility/restaurantDetail/restaurantDetail?id=3', theme: 'orange' },
				{ name: '购', icon: 'credit-card', path: '/subpackage_lostfound/lostfoundList/lostfoundList', theme: 'peach' },
				{ name: '行', icon: 'bus', path: '/pages/map/map', theme: 'sky' }
			],
			featureCards: [
				{ title: '暖城乐购', desc: '校园服务', path: '/pages/serviceHub/serviceHub?title=校园服务', theme: 'amber' },
				{ title: '汽车申报', desc: '访客登记', path: '/pages/serviceHub/serviceHub?title=访客登记', theme: 'mint' },
				{ title: '一卡通充值', desc: '生活缴费', path: '/pages/serviceHub/serviceHub?title=一卡通充值', theme: 'sky' },
				{ title: '论坛热帖', desc: '校园讨论', path: '/subpackage_forum/forumList/forumList', mode: 'reLaunch', theme: 'rose' }
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
	margin-top: 0;
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
	margin-top: 26rpx;
	background: rgba(255, 255, 255, 0.82);
	border-radius: 24rpx;
	padding: 22rpx 24rpx;
	display: flex;
	align-items: center;
	box-shadow: 0 10rpx 24rpx rgba(184, 195, 200, 0.16);
}

.headline-tag {
	flex-shrink: 0;
	font-size: 30rpx;
	font-weight: 800;
	color: #53b4b4;
}

.headline-text {
	flex: 1;
	margin-left: 18rpx;
	font-size: 30rpx;
	color: #4b585d;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
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

.city-card {
	position: relative;
	margin-top: 30rpx;
	padding: 32rpx 22rpx 24rpx;
	border-radius: 34rpx;
	background: linear-gradient(180deg, #f7f9ef 0%, #f6f8f0 100%);
	box-shadow: 0 20rpx 40rpx rgba(180, 191, 166, 0.18);
	overflow: hidden;
}

.skyline {
	position: relative;
	height: 170rpx;
	margin-bottom: 18rpx;
}

.skyline-build {
	position: absolute;
	bottom: 20rpx;
	background: linear-gradient(180deg, #b3e4ca, #8fd6b2);
	border-radius: 12rpx 12rpx 0 0;
}

.skyline-build-a { left: 40rpx; width: 56rpx; height: 88rpx; }
.skyline-build-b { left: 118rpx; width: 70rpx; height: 118rpx; }
.skyline-build-c { left: 206rpx; width: 76rpx; height: 140rpx; }
.skyline-build-d { left: 308rpx; width: 90rpx; height: 92rpx; background: linear-gradient(180deg, #f8c383, #f3a35f); }

.skyline-bridge {
	position: absolute;
	left: 20rpx;
	right: 24rpx;
	bottom: 24rpx;
	height: 22rpx;
	border-radius: 20rpx;
	background: linear-gradient(90deg, #ffd49a, #ffb978);
}

.skyline-cloud {
	position: absolute;
	background: rgba(255, 255, 255, 0.7);
	border-radius: 999rpx;
}

.skyline-cloud-a {
	right: 120rpx;
	top: 30rpx;
	width: 86rpx;
	height: 22rpx;
}

.skyline-cloud-b {
	right: 40rpx;
	top: 64rpx;
	width: 56rpx;
	height: 18rpx;
}

.coupon-bubble {
	display: inline-flex;
	align-items: center;
	padding: 16rpx 24rpx;
	background: linear-gradient(180deg, #a7d7d2, #86c0c2);
	border-radius: 24rpx;
	box-shadow: 0 10rpx 20rpx rgba(116, 172, 177, 0.22);
}

.coupon-text {
	font-size: 30rpx;
	font-weight: 700;
	color: #416972;
}

.coupon-arrow {
	margin-left: 16rpx;
	font-size: 28rpx;
	color: #416972;
}

.city-mascot {
	position: absolute;
	right: 34rpx;
	top: 174rpx;
	width: 148rpx;
	height: 178rpx;
}

.mascot-head {
	position: absolute;
	left: 24rpx;
	top: 0;
	width: 100rpx;
	height: 86rpx;
	border-radius: 44rpx;
	background: linear-gradient(180deg, #eef5fd, #d8e5f4);
	box-shadow: inset 0 -8rpx 0 rgba(165, 187, 205, 0.26);
}

.mascot-face {
	position: absolute;
	left: 42rpx;
	top: 18rpx;
	width: 68rpx;
	height: 62rpx;
	border-radius: 30rpx;
	background: #ffe3c9;
}

.mascot-body {
	position: absolute;
	left: 36rpx;
	top: 74rpx;
	width: 82rpx;
	height: 78rpx;
	border-radius: 24rpx 24rpx 18rpx 18rpx;
	background: linear-gradient(180deg, #f8c786, #ec8b47);
}

.city-stats {
	display: flex;
	align-items: center;
	gap: 44rpx;
	margin-top: 26rpx;
	padding-right: 170rpx;
}

.city-stat {
	display: flex;
	flex-direction: column;
}

.city-stat-value {
	font-size: 50rpx;
	font-weight: 800;
	color: #598698;
	line-height: 1.1;
}

.city-stat-label {
	margin-top: 6rpx;
	font-size: 26rpx;
	color: #7f9899;
}

.feature-grid {
	display: grid;
	grid-template-columns: repeat(2, 1fr);
	gap: 18rpx;
	margin-top: 28rpx;
}

.feature-card {
	position: relative;
	min-height: 142rpx;
	border-radius: 24rpx;
	padding: 22rpx 20rpx;
	overflow: hidden;
}

.feature-card--amber { background: linear-gradient(180deg, #fff2e3, #fff8f1); }
.feature-card--mint { background: linear-gradient(180deg, #f0fbf2, #f8fff8); }
.feature-card--sky { background: linear-gradient(180deg, #eef7ff, #f8fbff); }
.feature-card--rose { background: linear-gradient(180deg, #fff0f0, #fff9f6); }

.feature-title {
	display: block;
	font-size: 34rpx;
	font-weight: 800;
	color: #54727b;
}

.feature-desc {
	display: block;
	margin-top: 10rpx;
	font-size: 24rpx;
	color: #9ca9ad;
}

.feature-arrow {
	position: absolute;
	left: 18rpx;
	bottom: 14rpx;
	width: 26rpx;
	height: 26rpx;
	border-radius: 50%;
	background: rgba(88, 122, 128, 0.12);
	color: #6a8788;
	display: flex;
	align-items: center;
	justify-content: center;
	font-size: 18rpx;
}
</style>
