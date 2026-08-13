<template>
	<view class="meeting-app-page soft-page">
		<view class="status-bar"></view>
		<view class="top-bar">
			<view class="back" @click="back">‹</view>
			<text class="nav-title">预约会议</text>
			<view class="nav-spacer"></view>
		</view>

		<view class="panel type-panel">
			<text class="panel-title">会议类型</text>
			<view class="type-card" @click="goStartMeeting">
				<view class="type-icon">♙</view>
				<view class="type-copy">
					<text class="type-name">快速会议</text>
					<text class="type-desc">立即开始会议</text>
				</view>
				<view class="radio-dot"></view>
			</view>
			<view class="type-card type-card--active">
				<view class="type-icon type-icon--clock">◷</view>
				<view class="type-copy">
					<text class="type-name">预约会议</text>
					<text class="type-desc">选择时间并设置会议</text>
				</view>
				<view class="check-dot">✓</view>
			</view>
		</view>

		<view class="panel setting-panel">
			<text class="panel-title">会议设置</text>
			<view class="field-block">
				<view class="field-label-wrap">
					<text class="field-label">会议主题</text>
					<text class="required-mark">*</text>
				</view>
				<view class="input-box">
					<input v-model="meetingTitle" class="plain-input" placeholder="" />
					<view class="clear-btn" @click="clearTitle">
						<text class="clear-icon">×</text>
					</view>
				</view>
			</view>
			<view class="field-block field-block--inline">
				<text class="field-label">预约日期</text>
				<picker mode="date" :value="scheduledDate" @change="scheduledDate = $event.detail.value">
					<view class="picker-value">{{ scheduledDate }}</view>
				</picker>
			</view>
			<view class="field-block field-block--inline">
				<text class="field-label">开始时间</text>
				<picker mode="time" :value="scheduledTime" @change="scheduledTime = $event.detail.value">
					<view class="picker-value">{{ scheduledTime }}</view>
				</picker>
			</view>
			<view class="field-block field-block--inline">
				<text class="field-label">预计时长</text>
				<picker mode="selector" :range="durationOptions" :value="durationIndex" @change="durationIndex = $event.detail.value">
					<view class="picker-value">{{ durationOptions[durationIndex] }}</view>
				</picker>
			</view>
		</view>

		<view class="bottom-button-wrap">
			<view class="main-button" :class="{ 'main-button--disabled': creating }" @click="reserveNow">{{ creating ? '正在预约会议' : '预约会议' }}</view>
		</view>
	</view>
</template>

<script>
import { reserveMeeting } from '@/api/ai.js'
import { buildMeetingParticipants } from '@/utils/meetingUser.js'

export default {
	data() {
		const now = new Date()
		const date = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
		const nextHour = new Date(now.getTime() + 60 * 60 * 1000)
		const time = `${String(nextHour.getHours()).padStart(2, '0')}:00`
		return {
			meetingTitle: '项目进度同步会',
			scheduledDate: date,
			scheduledTime: time,
			durationOptions: ['15分钟', '30分钟', '45分钟', '1小时', '1.5小时', '2小时'],
			durationIndex: 1,
			creating: false
		}
	},
	methods: {
		back() { uni.navigateBack() },
		clearTitle() { this.meetingTitle = '' },
		goStartMeeting() { uni.redirectTo({ url: '/subpackage_meeting/startMeeting/startMeeting' }) },
		async reserveNow() {
			if (this.creating) return
			const title = this.meetingTitle.trim()
			if (!title) {
				uni.showToast({ title: '请输入会议主题', icon: 'none' })
				return
			}
			this.creating = true
			try {
				const durationMap = [15, 30, 45, 60, 90, 120]
				await reserveMeeting({
					title,
					scheduledStartTime: `${this.scheduledDate}T${this.scheduledTime}:00`,
					expectedDurationMinutes: durationMap[this.durationIndex],
					participants: buildMeetingParticipants()
				})
				uni.showToast({ title: '会议已预约', icon: 'none' })
				setTimeout(() => {
					uni.redirectTo({ url: '/subpackage_meeting/meetingSchedule/meetingSchedule' })
				}, 450)
			} catch (error) {
				uni.showToast({ title: (error && (error.msg || error.message)) || '会议预约失败，请稍后重试', icon: 'none' })
			} finally {
				this.creating = false
			}
		}
	}
}
</script>

<style lang="scss" scoped>
.meeting-app-page { min-height: 100vh; background: #fff; padding: 0 24rpx 150rpx; box-sizing: border-box; color: #121a20; }
.soft-page { background: linear-gradient(180deg, #fbfcfb 0%, #ffffff 100%); }
.status-bar { height: var(--status-bar-height); min-height: 42rpx; }
.top-bar { height: 88rpx; display: grid; grid-template-columns: 70rpx 1fr 70rpx; align-items: center; }
.back { font-size: 48rpx; line-height: 1; color: #111; }
.nav-title { text-align: center; font-size: 30rpx; font-weight: 800; }
.nav-spacer { width: 70rpx; }
.panel { margin-top: 24rpx; padding: 28rpx; border-radius: 24rpx; background: #fff; box-shadow: 0 18rpx 52rpx rgba(31, 42, 48, .08); }
.panel-title { display: block; font-size: 27rpx; font-weight: 900; margin-bottom: 24rpx; color: #192328; }
.type-card { min-height: 96rpx; padding: 20rpx 22rpx; border-radius: 18rpx; background: #f7f7f7; display: flex; align-items: center; gap: 20rpx; margin-bottom: 18rpx; }
.type-card--active { background: linear-gradient(90deg, #E8F8F2, #f8fbf9); }
.type-icon { width: 54rpx; height: 54rpx; border-radius: 16rpx; background: #E8F8F2; color: #57A77D; display: flex; align-items: center; justify-content: center; font-size: 30rpx; }
.type-icon--clock { background: #E8F8F2; color: #57A77D; }
.type-copy { flex: 1; display: flex; flex-direction: column; gap: 7rpx; }
.type-name { font-size: 27rpx; font-weight: 850; color: #172228; }
.type-desc { font-size: 22rpx; color: #8b9499; }
.check-dot { width: 40rpx; height: 40rpx; border-radius: 50%; background: #86C9A8; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 24rpx; font-weight: 900; }
/* 未选中圆圈 - 图二圈选样式 */
.radio-dot { width: 40rpx; height: 40rpx; border-radius: 50%; border: 3rpx solid #d1d5db; background: transparent; box-sizing: border-box; }
.chevron { color: #8c969b; font-size: 42rpx; }
.field-block { padding-bottom: 28rpx; }
.field-block--inline { display: flex; align-items: center; justify-content: space-between; gap: 24rpx; }
.field-label-wrap { display: flex; align-items: center; gap: 6rpx; margin-bottom: 16rpx; }
.field-label { display: block; color: #29343a; font-size: 25rpx; margin-bottom: 16rpx; }
.field-block--inline .field-label { margin-bottom: 0; }
.required-mark { color: #ff4d4f; font-size: 25rpx; }

/* 和发起会议页面统一输入框样式 */
.input-box {
	height: 88rpx;
	background: #f7f7f7;
	border-radius: 16rpx;
	padding: 0 24rpx;
	display: flex;
	align-items: center;
}
.plain-input {
	flex: 1;
	height: 88rpx;
	color: #1b252a;
	font-size: 26rpx;
}
.clear-btn {
	width: 40rpx;
	height: 40rpx;
	border-radius: 50%;
	background: #d1d5db;
	display: flex;
	align-items: center;
	justify-content: center;
	margin-left: 16rpx;
	flex-shrink: 0;
}
.clear-icon { color: #fff; font-size: 28rpx; line-height: 1; font-weight: 500; }

.picker-value { min-width: 190rpx; height: 88rpx; padding: 0 18rpx; border-radius: 16rpx; background: #f7f7f7; color: #1b252a; font-size: 26rpx; display: flex; align-items: center; justify-content: center; }
.picker-value--arrow { gap: 8rpx; }
.picker-value--placeholder { color: #9aa3a8; }
.picker-arrow { color: #8c969b; font-size: 36rpx; line-height: 1; }
.setting-row { height: 82rpx; display: flex; align-items: center; justify-content: space-between; font-size: 26rpx; color: #1c272d; }
.label-help { display: flex; align-items: center; gap: 8rpx; }
.info { color: #9aa3a8; font-size: 22rpx; }
.bottom-button-wrap { position: fixed; left: 24rpx; right: 24rpx; bottom: calc(env(safe-area-inset-bottom) + 24rpx); padding: 18rpx 0; background: rgba(255,255,255,.92); }
.main-button { height: 86rpx; border-radius: 16rpx; background: #86C9A8; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 29rpx; font-weight: 900; box-shadow: 0 16rpx 32rpx rgba(134,201,168,.18); }
.main-button--disabled { opacity: .65; }
</style>