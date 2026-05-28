<template>
	<view class="meeting-app-page soft-page">
		<view class="status-bar"></view>
		<view class="top-bar">
			<view class="back" @click="back">‹</view>
			<text class="nav-title">发起会议</text>
			<view class="nav-spacer"></view>
		</view>

		<view class="panel type-panel">
			<text class="panel-title">会议类型</text>
			<view class="type-card type-card--active">
				<view class="type-icon">♙</view>
				<view class="type-copy">
					<text class="type-name">快速会议</text>
					<text class="type-desc">立即开始会议</text>
				</view>
				<view class="check-dot">✓</view>
			</view>
			<view class="type-card" @click="goReserveMeeting">
				<view class="type-icon type-icon--clock">◷</view>
				<view class="type-copy">
					<text class="type-name">预约会议</text>
					<text class="type-desc">选择时间并设置会议</text>
				</view>
				<text class="chevron">›</text>
			</view>
		</view>

		<view class="panel setting-panel">
			<text class="panel-title">会议设置</text>
			<view class="field-block">
				<text class="field-label">会议主题</text>
				<input v-model="meetingTitle" class="plain-input" placeholder="项目进度同步会" />
			</view>
			<view class="setting-row">
				<text>开启麦克风</text>
				<switch :checked="micOn" color="#23866d" @change="micOn = $event.detail.value" />
			</view>
			<view class="setting-row">
				<view class="label-help"><text>使用个人会议号</text><text class="info">ⓘ</text></view>
				<switch :checked="personalId" color="#23866d" @change="personalId = $event.detail.value" />
			</view>
		</view>

		<view class="bottom-button-wrap">
			<view class="main-button" :class="{ 'main-button--disabled': creating }" @click="startNow">{{ creating ? '正在创建会议' : '立即开始会议' }}</view>
		</view>
	</view>
</template>

<script>
import { createQuickMeeting } from '@/api/ai.js'
import { buildMeetingParticipants } from '@/utils/meetingUser.js'

export default {
	data() {
		return {
			meetingTitle: '项目进度同步会',
			micOn: true,
			personalId: false,
			creating: false
		}
	},
	methods: {
		back() { uni.navigateBack() },
		goReserveMeeting() {
			uni.navigateTo({ url: '/subpackage_meeting/reserveMeeting/reserveMeeting' })
		},
		async startNow() {
			if (this.creating) return
			this.creating = true
			try {
				const res = await createQuickMeeting({
					title: this.meetingTitle || '快速会议',
					participants: buildMeetingParticipants()
				})
				const session = res?.data?.session || {}
				uni.redirectTo({
					url: `/subpackage_meeting/meetingLive/meetingLive?title=${encodeURIComponent(session.title || this.meetingTitle || '快速会议')}&roomCode=${encodeURIComponent(session.roomCode || '')}&sessionId=${encodeURIComponent(session.sessionId || '')}`
				})
			} catch (error) {
				uni.showToast({ title: '会议创建失败，请稍后重试', icon: 'none' })
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
.type-card--active { background: linear-gradient(90deg, rgba(230,246,237,.96), rgba(248,251,249,.95)); }
.type-icon { width: 54rpx; height: 54rpx; border-radius: 16rpx; background: #cdeedc; color: #1e8a70; display: flex; align-items: center; justify-content: center; font-size: 30rpx; }
.type-icon--clock { background: #ece9ff; color: #8b7ce8; }
.type-copy { flex: 1; display: flex; flex-direction: column; gap: 7rpx; }
.type-name { font-size: 27rpx; font-weight: 850; color: #172228; }
.type-desc { font-size: 22rpx; color: #8b9499; }
.check-dot { width: 40rpx; height: 40rpx; border-radius: 50%; background: #2b8d75; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 24rpx; font-weight: 900; }
.chevron { color: #8c969b; font-size: 42rpx; }
.field-block { padding-bottom: 28rpx; }
.field-label { display: block; color: #29343a; font-size: 25rpx; margin-bottom: 16rpx; }
.plain-input { height: 52rpx; color: #1b252a; font-size: 27rpx; }
.setting-row { height: 82rpx; display: flex; align-items: center; justify-content: space-between; font-size: 26rpx; color: #1c272d; }
.label-help { display: flex; align-items: center; gap: 8rpx; }
.info { color: #9aa3a8; font-size: 22rpx; }
.bottom-button-wrap { position: fixed; left: 24rpx; right: 24rpx; bottom: calc(env(safe-area-inset-bottom) + 24rpx); padding: 18rpx 0; background: rgba(255,255,255,.92); }
.main-button { height: 86rpx; border-radius: 16rpx; background: #23866d; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 29rpx; font-weight: 900; box-shadow: 0 16rpx 32rpx rgba(35,134,109,.18); }
.main-button--disabled { opacity: .65; }
</style>
