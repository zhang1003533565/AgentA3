<template>
	<view class="home-page">
		<common-page-header title="首页" />

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

				<view class="home-quick-entry__item" @click="navigate('/subpackage_lostfound/marketplaceHome/marketplaceHome')">
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

				<view class="home-quick-entry__item" @click="navigate('/subpackage_promotion/promotion/promotion')">
					<view class="home-quick-entry__icon-wrapper">
						<image class="home-quick-entry__icon" src="/static/APPIcon/tabbar/award.png" mode="aspectFit" />
					</view>
					<view class="home-quick-entry__card">
						<text class="home-quick-entry__text">惠</text>
					</view>
				</view>
			</view>

			<view class="headline-card" @click="goToNotice">
				<view class="headline-left">
					<view class="headline-dot"></view>
					<text class="headline-label">公告</text>
				</view>
				<view class="headline-marquee">
					<view class="headline-track">
						<text class="headline-text">{{ headline }}</text>
						<text class="headline-separator">·</text>
						<text class="headline-text">{{ headline }}</text>
					</view>
				</view>
				<view class="headline-arrow">
					<view class="arrow-line"></view>
				</view>
			</view>

			<view class="campus-ai-module">
				<view
					v-if="!campusAiExpanded"
					class="campus-ai-fold-card"
					@click="toggleCampusAi(true)"
					@touchstart="captureCampusAiTouchStart"
					@touchend="handleCampusAiTouchEnd"
				>
					<view class="campus-ai-fold-main">
						<view class="campus-ai-mark">
							<text class="campus-ai-mark__spark">✦</text>
						</view>
						<view class="campus-ai-copy">
							<text class="campus-ai-title">校园 AI</text>
							<text class="campus-ai-subtitle">记录会议 · 生成内容 · 智能总结</text>
						</view>
					</view>
					<text class="campus-ai-chevron">⌄</text>
				</view>

				<view v-else class="campus-ai-expand-card">
					<view class="campus-ai-expand-head">
						<text class="campus-ai-question">今天想做什么?</text>
						<view class="campus-ai-close" @click.stop="toggleCampusAi(false)">×</view>
					</view>

					<view class="campus-ai-actions">
						<view class="campus-ai-action campus-ai-action--meeting" @click.stop="navigate('/subpackage_meeting/meetingRoom/meetingRoom')">
							<view class="campus-ai-action-icon campus-ai-action-icon--meeting">
								<view class="campus-ai-mic">
									<view class="campus-ai-mic__stem"></view>
								</view>
							</view>
							<view class="campus-ai-action-copy">
								<text class="campus-ai-action-title">记录会议</text>
								<text class="campus-ai-action-desc">语音转写 / 纪要生成</text>
							</view>
						</view>

						<view class="campus-ai-action campus-ai-action--create" @click.stop="navigate('/subpackage_ai/aiCreate/aiCreate')">
							<view class="campus-ai-action-icon campus-ai-action-icon--create">
								<text class="campus-ai-spark">✦</text>
							</view>
							<view class="campus-ai-action-copy">
								<text class="campus-ai-action-title">灵感创作</text>
								<text class="campus-ai-action-desc">海报 / 文案 / PPT</text>
							</view>
						</view>
					</view>

					<view class="campus-ai-recent-label">最近使用</view>
					<view class="campus-ai-recent" @click.stop="navigate('/subpackage_meeting/meetingSchedule/meetingSchedule')">
						<view class="campus-ai-doc-icon"></view>
						<view class="campus-ai-recent-copy">
							<text class="campus-ai-recent-title">会议日程与历史</text>
							<text class="campus-ai-recent-desc">查看最近会议记录与 AI 纪要</text>
						</view>
						<text class="campus-ai-recent-arrow">›</text>
					</view>
				</view>
			</view>

			<home-schedule-card />
		</view>

		<ai-float-assistant />

		<app-main-tab-bar current="index" />
	</view>
</template>

<script>
import AppMainTabBar from '@/components/app-main-tab-bar/app-main-tab-bar.vue'
import AiFloatAssistant from '@/components/ai-float-assistant/ai-float-assistant.vue'
import HomeScheduleCard from '@/components/home-schedule-card/home-schedule-card.vue'
import CommonPageHeader from '@/components/common-page-header/common-page-header.vue'
import { getEnabledAnnouncements } from '@/api/notice.js'

export default {
	components: { AppMainTabBar, HomeScheduleCard, AiFloatAssistant, CommonPageHeader },
	data() {
		return {
			headline: '',
			announcements: [],
			campusAiExpanded: false,
			campusAiTouchStartY: 0,

			heroSlides: [
				{
					title: 'slide-1',
					image: '/static/index/1.png',
					theme: 'blue'
				},
				{
					title: 'slide-2',
					image: '/static/index/2.jpg',
					theme: 'violet'
				},
				{
					title: 'slide-3',
					image: '/static/index/3.jpg',
					theme: 'green'
				},
				{
					title: 'slide-4',
					image: '/static/index/4.jpg',
					theme: 'orange'
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
		toggleCampusAi(expanded) {
			this.campusAiExpanded = typeof expanded === 'boolean' ? expanded : !this.campusAiExpanded
		},
		captureCampusAiTouchStart(event) {
			const point = event.changedTouches?.[0] || event.touches?.[0]
			this.campusAiTouchStartY = point ? point.clientY : 0
		},
		handleCampusAiTouchEnd(event) {
			const point = event.changedTouches?.[0] || event.touches?.[0]
			const endY = point ? point.clientY : 0
			if (this.campusAiTouchStartY && this.campusAiTouchStartY - endY > 24) {
				this.toggleCampusAi(true)
			}
			this.campusAiTouchStartY = 0
		},
		goToNotice() {
			uni.navigateTo({
				url: '/pages/notice/notice'
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
		},


	},

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
	background: #FFFFFF;
	border-radius: 16rpx;
	padding: 20rpx 24rpx;
	display: flex;
	align-items: center;
	box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.05);
	overflow: hidden;
}

/* 左侧标签区域 */
.headline-left {
	flex-shrink: 0;
	display: flex;
	align-items: center;
	gap: 12rpx;
	margin-right: 20rpx;
}

.headline-dot {
	width: 6rpx;
	height: 6rpx;
	border-radius: 50%;
	background: #5C7A99;
}

.headline-label {
	font-size: 24rpx;
	color: #5C7A99;
	font-weight: 500;
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
	font-size: 28rpx;
	color: #4b585d;
	flex-shrink: 0;
}

.headline-separator {
	margin: 0 34rpx;
	font-size: 28rpx;
	color: #88a2a4;
}

/* 右侧简约箭头 */
.headline-arrow {
	flex-shrink: 0;
	margin-left: 16rpx;
	width: 40rpx;
	height: 40rpx;
	display: flex;
	align-items: center;
	justify-content: center;
}

.arrow-line {
	width: 8rpx;
	height: 8rpx;
	border-right: 2rpx solid #B0B8C0;
	border-bottom: 2rpx solid #B0B8C0;
	transform: rotate(-45deg);
}

@keyframes headline-marquee {
	0% {
		transform: translateX(100%);
	}
	100% {
		transform: translateX(-100%);
	}
}

.campus-ai-module {
	margin-top: 28rpx;
}

.campus-ai-fold-card,
.campus-ai-expand-card {
	position: relative;
	box-sizing: border-box;
	border: 1rpx solid rgba(226, 235, 239, 0.92);
	background: #ffffff;
	box-shadow: 0 18rpx 46rpx rgba(62, 83, 92, 0.10);
}

.campus-ai-fold-card {
	min-height: 144rpx;
	padding: 26rpx 30rpx;
	border-radius: 24rpx;
	display: flex;
	align-items: center;
	justify-content: space-between;
	overflow: hidden;
}

.campus-ai-fold-card::before {
	content: '';
	position: absolute;
	left: 0;
	right: 0;
	bottom: 0;
	height: 48rpx;
	background: linear-gradient(90deg, rgba(232, 255, 240, 0.72), rgba(236, 244, 255, 0.78));
	opacity: 0.58;
	pointer-events: none;
}

.campus-ai-fold-card:active,
.campus-ai-action:active,
.campus-ai-recent:active {
	transform: translateY(2rpx);
}

.campus-ai-fold-main {
	position: relative;
	z-index: 1;
	display: flex;
	align-items: center;
	min-width: 0;
}

.campus-ai-mark {
	width: 58rpx;
	height: 58rpx;
	border-radius: 20rpx;
	background: linear-gradient(135deg, #eefdf4, #edf5ff);
	display: flex;
	align-items: center;
	justify-content: center;
	box-shadow: inset 0 0 0 1rpx rgba(255, 255, 255, 0.82);
	flex-shrink: 0;
}

.campus-ai-mark__spark {
	color: #2476f2;
	font-size: 30rpx;
	font-weight: 900;
	line-height: 1;
}

.campus-ai-copy {
	margin-left: 18rpx;
	display: flex;
	flex-direction: column;
	min-width: 0;
}

.campus-ai-title {
	font-size: 29rpx;
	font-weight: 900;
	color: #17242b;
	line-height: 1.25;
}

.campus-ai-subtitle {
	margin-top: 6rpx;
	font-size: 22rpx;
	line-height: 1.35;
	color: #6d7d86;
	white-space: nowrap;
	overflow: hidden;
	text-overflow: ellipsis;
	max-width: 500rpx;
}

.campus-ai-chevron {
	position: relative;
	z-index: 1;
	width: 42rpx;
	height: 42rpx;
	border-radius: 50%;
	display: flex;
	align-items: center;
	justify-content: center;
	color: #5e6e78;
	font-size: 34rpx;
	line-height: 1;
}

.campus-ai-expand-card {
	min-height: 380rpx;
	padding: 26rpx 30rpx 28rpx;
	border-radius: 24rpx;
	overflow: hidden;
	background:
		linear-gradient(145deg, rgba(234, 255, 242, 0.72) 0%, rgba(255, 255, 255, 0.90) 42%),
		linear-gradient(35deg, rgba(255, 255, 255, 0) 40%, rgba(238, 245, 255, 0.92) 100%),
		#ffffff;
}

.campus-ai-expand-head {
	position: relative;
	z-index: 1;
	display: flex;
	align-items: center;
	justify-content: center;
	min-height: 42rpx;
}

.campus-ai-question {
	font-size: 28rpx;
	font-weight: 900;
	color: #1d282f;
	line-height: 1.3;
}

.campus-ai-close {
	position: absolute;
	right: 0;
	top: 50%;
	transform: translateY(-50%);
	width: 48rpx;
	height: 48rpx;
	border-radius: 50%;
	display: flex;
	align-items: center;
	justify-content: center;
	color: #687986;
	font-size: 32rpx;
	line-height: 1;
}

.campus-ai-actions {
	position: relative;
	z-index: 1;
	margin-top: 28rpx;
	display: grid;
	grid-template-columns: repeat(2, minmax(0, 1fr));
	gap: 18rpx;
}

.campus-ai-action {
	min-width: 0;
	min-height: 122rpx;
	padding: 22rpx 20rpx;
	border-radius: 20rpx;
	display: flex;
	align-items: center;
	box-sizing: border-box;
	border: 1rpx solid rgba(228, 238, 243, 0.92);
	box-shadow: 0 12rpx 28rpx rgba(73, 96, 106, 0.08);
}

.campus-ai-action--meeting {
	background: linear-gradient(135deg, rgba(244, 255, 247, 0.96), rgba(255, 255, 255, 0.94));
}

.campus-ai-action--create {
	background: linear-gradient(135deg, rgba(246, 250, 255, 0.96), rgba(255, 255, 255, 0.96));
}

.campus-ai-action-icon {
	width: 66rpx;
	height: 66rpx;
	border-radius: 50%;
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
}

.campus-ai-action-icon--meeting {
	background: #dff9e6;
}

.campus-ai-action-icon--create {
	background: #e4efff;
}

.campus-ai-mic {
	position: relative;
	width: 38rpx;
	height: 48rpx;
}

.campus-ai-mic::before {
	content: '';
	position: absolute;
	top: 2rpx;
	left: 50%;
	width: 18rpx;
	height: 30rpx;
	border-radius: 999rpx;
	background: #20b46b;
	transform: translateX(-50%);
}

.campus-ai-mic::after {
	content: '';
	position: absolute;
	top: 20rpx;
	left: 50%;
	width: 32rpx;
	height: 22rpx;
	border: 4rpx solid #20b46b;
	border-top: 0;
	border-radius: 0 0 18rpx 18rpx;
	transform: translateX(-50%);
	box-sizing: border-box;
}

.campus-ai-mic__stem {
	position: absolute;
	left: 50%;
	bottom: 0;
	width: 18rpx;
	height: 4rpx;
	border-radius: 999rpx;
	background: #20b46b;
	transform: translateX(-50%);
}

.campus-ai-mic__stem::before {
	content: '';
	position: absolute;
	left: 50%;
	bottom: 0;
	width: 4rpx;
	height: 14rpx;
	border-radius: 999rpx;
	background: #20b46b;
	transform: translateX(-50%);
}

.campus-ai-spark {
	color: #2d7df4;
	font-size: 34rpx;
	font-weight: 900;
	line-height: 1;
}

.campus-ai-action-copy {
	margin-left: 16rpx;
	min-width: 0;
	display: flex;
	flex-direction: column;
}

.campus-ai-action-title {
	font-size: 25rpx;
	font-weight: 900;
	color: #1f2d35;
	line-height: 1.32;
	white-space: nowrap;
	overflow: hidden;
	text-overflow: ellipsis;
}

.campus-ai-action-desc {
	margin-top: 6rpx;
	font-size: 20rpx;
	line-height: 1.3;
	color: #697a84;
	white-space: nowrap;
	overflow: hidden;
	text-overflow: ellipsis;
}

.campus-ai-recent-label {
	position: relative;
	z-index: 1;
	margin-top: 26rpx;
	font-size: 22rpx;
	color: #687986;
	line-height: 1.35;
}

.campus-ai-recent {
	position: relative;
	z-index: 1;
	margin-top: 12rpx;
	min-height: 78rpx;
	padding: 16rpx 18rpx;
	border-radius: 18rpx;
	background: rgba(255, 255, 255, 0.86);
	border: 1rpx solid rgba(226, 235, 239, 0.95);
	display: flex;
	align-items: center;
	box-sizing: border-box;
	box-shadow: 0 10rpx 24rpx rgba(73, 96, 106, 0.06);
}

.campus-ai-doc-icon {
	position: relative;
	width: 30rpx;
	height: 34rpx;
	border-radius: 6rpx;
	border: 3rpx solid #3478f6;
	box-sizing: border-box;
	flex-shrink: 0;
}

.campus-ai-doc-icon::before,
.campus-ai-doc-icon::after {
	content: '';
	position: absolute;
	left: 6rpx;
	right: 6rpx;
	height: 3rpx;
	border-radius: 999rpx;
	background: #3478f6;
}

.campus-ai-doc-icon::before {
	top: 9rpx;
}

.campus-ai-doc-icon::after {
	top: 18rpx;
}

.campus-ai-recent-copy {
	margin-left: 16rpx;
	min-width: 0;
	flex: 1;
	display: flex;
	flex-direction: column;
}

.campus-ai-recent-title {
	font-size: 23rpx;
	font-weight: 800;
	color: #21313a;
	line-height: 1.3;
	white-space: nowrap;
	overflow: hidden;
	text-overflow: ellipsis;
}

.campus-ai-recent-desc {
	margin-top: 4rpx;
	font-size: 19rpx;
	line-height: 1.3;
	color: #788892;
	white-space: nowrap;
	overflow: hidden;
	text-overflow: ellipsis;
}

.campus-ai-recent-arrow {
	margin-left: 12rpx;
	color: #748590;
	font-size: 38rpx;
	line-height: 1;
	flex-shrink: 0;
}

.meeting-entry-card {
	position: relative;
	margin-top: 28rpx;
	padding: 28rpx;
	border-radius: 32rpx;
	overflow: hidden;
	background:
		radial-gradient(circle at 78% 18%, rgba(255, 214, 125, 0.42), transparent 28%),
		linear-gradient(135deg, #153f49 0%, #1e665f 48%, #d39d51 100%);
	box-shadow: 0 18rpx 38rpx rgba(31, 93, 91, 0.18);
}

.meeting-entry-card__glow {
	position: absolute;
	right: -70rpx;
	top: -86rpx;
	width: 260rpx;
	height: 260rpx;
	border-radius: 50%;
	border: 2rpx solid rgba(255, 255, 255, 0.22);
	box-shadow: inset 0 0 36rpx rgba(255, 255, 255, 0.18);
}

.meeting-entry-card__content {
	position: relative;
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 18rpx;
}

.meeting-entry-card__copy {
	flex: 1;
	min-width: 0;
	display: flex;
	flex-direction: column;
}

.meeting-entry-card__eyebrow {
	align-self: flex-start;
	padding: 6rpx 14rpx;
	border-radius: 999rpx;
	background: rgba(255, 255, 255, 0.16);
	color: rgba(255, 255, 255, 0.82);
	font-size: 19rpx;
	font-weight: 800;
	letter-spacing: 2rpx;
}

.meeting-entry-card__title {
	margin-top: 14rpx;
	font-size: 46rpx;
	line-height: 1.1;
	color: #FFFFFF;
	font-weight: 900;
	letter-spacing: 3rpx;
}

.meeting-entry-card__desc {
	margin-top: 12rpx;
	max-width: 390rpx;
	font-size: 24rpx;
	line-height: 1.58;
	color: rgba(255, 255, 255, 0.86);
}

.meeting-entry-card__agents {
	display: flex;
	gap: 10rpx;
	margin-top: 18rpx;
}

.meeting-entry-card__tag {
	padding: 7rpx 16rpx;
	border-radius: 999rpx;
	background: rgba(255, 255, 255, 0.14);
	border: 1rpx solid rgba(255, 255, 255, 0.22);
	color: rgba(255, 255, 255, 0.92);
	font-size: 20rpx;
	font-weight: 700;
}

.meeting-entry-card__visual {
	position: relative;
	width: 220rpx;
	height: 190rpx;
	flex-shrink: 0;
}

.meeting-board {
	position: absolute;
	right: 12rpx;
	top: 36rpx;
	width: 154rpx;
	height: 112rpx;
	border-radius: 28rpx;
	background: rgba(255, 255, 255, 0.92);
	box-shadow: 0 18rpx 28rpx rgba(12, 48, 49, 0.22);
	transform: rotate(-5deg);
}

.meeting-board::before {
	content: '';
	position: absolute;
	left: 24rpx;
	top: -16rpx;
	width: 58rpx;
	height: 28rpx;
	border-radius: 18rpx;
	background: #ffd67d;
}

.meeting-board__line {
	position: absolute;
	left: 24rpx;
	right: 30rpx;
	height: 8rpx;
	border-radius: 999rpx;
	background: rgba(30, 102, 95, 0.18);
}

.meeting-board__line--wide {
	top: 42rpx;
	right: 18rpx;
	background: rgba(30, 102, 95, 0.42);
}

.meeting-board__line:not(.meeting-board__line--wide) {
	top: 66rpx;
}

.meeting-board__spark {
	position: absolute;
	right: 22rpx;
	bottom: 18rpx;
	width: 22rpx;
	height: 22rpx;
	border-radius: 50%;
	background: #d39d51;
	box-shadow: 0 0 18rpx rgba(211, 157, 81, 0.78);
}

.meeting-avatar {
	position: absolute;
	width: 58rpx;
	height: 58rpx;
	border-radius: 22rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	color: #153f49;
	font-size: 22rpx;
	font-weight: 900;
	background: #fff7df;
	box-shadow: 0 12rpx 24rpx rgba(11, 49, 52, 0.2);
}

.meeting-avatar--one {
	left: 10rpx;
	top: 20rpx;
	transform: rotate(8deg);
}

.meeting-avatar--two {
	left: 28rpx;
	bottom: 20rpx;
	background: #dff6ed;
	transform: rotate(-8deg);
}

.meeting-avatar--three {
	right: 0;
	bottom: 8rpx;
	background: #ffe6b8;
	transform: rotate(10deg);
}

.meeting-entry-card__action {
	position: relative;
	margin-top: 22rpx;
	display: inline-flex;
	align-items: center;
	gap: 12rpx;
	padding: 14rpx 20rpx;
	border-radius: 999rpx;
	background: rgba(255, 255, 255, 0.16);
	color: #FFFFFF;
	font-size: 24rpx;
	font-weight: 800;
}

.meeting-entry-card__arrow {
	width: 32rpx;
	height: 32rpx;
	border-radius: 50%;
	display: flex;
	align-items: center;
	justify-content: center;
	background: rgba(255, 255, 255, 0.24);
	font-size: 20rpx;
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
