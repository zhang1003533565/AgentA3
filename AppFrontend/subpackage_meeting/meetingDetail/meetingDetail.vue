<template>
	<view class="meeting-app-page">
		<view class="status-bar"></view>
		<view class="top-bar">
			<view class="back" @click="back">‹</view>
			<text class="nav-title">会议详情</text>
			<view></view>
		</view>

		<view class="detail-card">
			<view class="detail-head">
				<text class="detail-title">{{ title }}</text>
				<view class="pill">{{ statusText }}</view>
			</view>
			<view class="info-row"><text class="info-icon">◷</text><text>时间</text><text>{{ timeText }}</text></view>
			<view class="info-row"><text class="info-icon">♙</text><text>{{ hostName }}</text><text>（主持人）</text></view>
			<view class="info-row"><text class="info-icon">♟</text><text>会议号</text><text>{{ roomCode }}</text><text class="copy" @click="copy(roomCode)">□</text></view>
			<view class="link-row">
				<text class="info-icon">↗</text>
				<view class="link-copy"><text>会议链接</text><text class="url">https://meeting.app/join/{{ compactRoomCode }}</text></view>
				<text class="copy" @click="copyMeetingLink">□</text>
				<text class="copy">⇱</text>
			</view>
			<view class="member-head">
				<text>参会人 ({{ participants.length }})</text>
				<text class="all-link">查看全部</text>
			</view>
			<view class="avatar-row">
				<view v-for="(item, index) in avatars" :key="`${item}-${index}`" class="mini-avatar">{{ item }}</view>
			</view>
		</view>

		<view class="action-bar">
			<view class="detail-action green" @click="shareMeeting"><text>⇧</text><text>分享会议</text></view>
			<view class="detail-action" @click="addSchedule"><text>▣</text><text>添加到日程</text></view>
			<view class="detail-action red" @click="deleteMeeting"><text>⌫</text><text>删除会议</text></view>
		</view>
	</view>
</template>

<script>
import { getMeetingDetail } from '@/api/ai.js'

export default {
	data() {
		return {
			sessionId: '',
			title: '会议',
			roomCode: '',
			status: '',
			startTime: '',
			scheduledStartTime: '',
			participants: []
		}
	},
	computed: {
		compactRoomCode() { return this.roomCode.replace(/\s+/g, '') },
		avatars() {
			return this.participants.slice(0, 5).map(name => name.slice(0, 1))
		},
		hostName() {
			return this.participants[0] || '未填写'
		},
		statusText() {
			const map = { active: '进行中', idle: '待开始', paused: '已暂停', ended: '已结束' }
			return map[this.status] || '会议'
		},
		timeText() {
			const source = this.scheduledStartTime || this.startTime
			if (!source) return '未设置'
			const date = new Date(source)
			if (Number.isNaN(date.getTime())) return '未设置'
			const month = String(date.getMonth() + 1).padStart(2, '0')
			const day = String(date.getDate()).padStart(2, '0')
			const hour = String(date.getHours()).padStart(2, '0')
			const minute = String(date.getMinutes()).padStart(2, '0')
			return `${month}-${day} ${hour}:${minute}`
		}
	},
	onLoad(options) {
		if (options?.sessionId) this.sessionId = decodeURIComponent(options.sessionId)
		if (options?.title) this.title = decodeURIComponent(options.title)
		if (options?.roomCode) this.roomCode = decodeURIComponent(options.roomCode)
		this.loadMeeting()
	},
	methods: {
		async loadMeeting() {
			if (!this.sessionId) return
			try {
				const res = await getMeetingDetail(this.sessionId)
				const detail = res?.data || {}
				const session = detail.session || {}
				this.title = session.title || this.title
				this.roomCode = session.roomCode || this.roomCode
				this.status = session.status || this.status
				this.startTime = session.startTime || ''
				this.scheduledStartTime = session.scheduledStartTime || ''
				this.participants = Array.isArray(detail.participants) ? detail.participants : []
			} catch (error) {}
		},
		back() { uni.navigateBack() },
		copy(data) { uni.setClipboardData({ data, success: () => uni.showToast({ title: '已复制', icon: 'none' }) }) },
		copyMeetingLink() { this.copy('https://meeting.app/join/' + this.compactRoomCode) },
		shareMeeting() { this.copy(`会议：${this.title}\n会议号：${this.roomCode}\nhttps://meeting.app/join/${this.compactRoomCode}`) },
		addSchedule() { uni.showToast({ title: '日程接口待接入', icon: 'none' }) },
		deleteMeeting() { uni.showToast({ title: '删除接口待接入', icon: 'none' }) }
	}
}
</script>

<style lang="scss" scoped>
.meeting-app-page { min-height: 100vh; background: #fff; padding: 0 24rpx 70rpx; box-sizing: border-box; color: #151f25; }
.status-bar { height: var(--status-bar-height); min-height: 42rpx; }
.top-bar { height: 88rpx; display: grid; grid-template-columns: 70rpx 1fr 70rpx; align-items: center; }
.back { font-size: 48rpx; line-height: 1; }
.nav-title { text-align: center; font-size: 29rpx; font-weight: 850; }
.detail-card { margin-top: 26rpx; padding: 30rpx; border-radius: 24rpx; background: #fff; box-shadow: 0 18rpx 54rpx rgba(31,42,48,.08); }
.detail-head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 30rpx; }
.detail-title { font-size: 34rpx; font-weight: 900; color: #0f1a20; }
.pill { height: 38rpx; padding: 0 18rpx; border-radius: 999rpx; background: #c8efd9; color: #1f7f68; font-size: 20rpx; display: flex; align-items: center; }
.info-row { height: 62rpx; display: grid; grid-template-columns: 38rpx 100rpx 1fr 32rpx; align-items: center; color: #2e3b42; font-size: 24rpx; }
.info-icon { color: #6b767c; }
.copy { color: #9aa3a8; }
.link-row { min-height: 92rpx; display: grid; grid-template-columns: 38rpx 1fr 32rpx 32rpx; gap: 8rpx; align-items: center; color: #2e3b42; font-size: 24rpx; }
.link-copy { display: flex; flex-direction: column; gap: 8rpx; }
.url { color: #5c6470; text-decoration: underline; font-size: 23rpx; }
.member-head { margin-top: 28rpx; display: flex; justify-content: space-between; font-size: 25rpx; }
.all-link { color: #8d989e; font-size: 23rpx; }
.avatar-row { margin-top: 18rpx; display: flex; gap: 18rpx; }
.mini-avatar { width: 48rpx; height: 48rpx; border-radius: 50%; background: linear-gradient(135deg,#e8eee9,#a7cfbe); display: flex; align-items: center; justify-content: center; font-size: 19rpx; color: #183b32; font-weight: 850; }
.action-bar { position: fixed; left: 24rpx; right: 24rpx; bottom: calc(env(safe-area-inset-bottom) + 20rpx); min-height: 110rpx; border-radius: 24rpx; background: rgba(255,255,255,.96); box-shadow: 0 -8rpx 36rpx rgba(31,42,48,.08); display: grid; grid-template-columns: repeat(3, 1fr); align-items: center; }
.detail-action { display: flex; flex-direction: column; align-items: center; gap: 8rpx; color: #2a353b; font-size: 22rpx; }
.green { color: #1f7f68; }
.red { color: #ef3d34; }
</style>
