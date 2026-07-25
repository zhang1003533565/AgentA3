<template>
	<view class="meeting-app-page">
		<nav-bar title="会议" :showBack="true" fixed placeholder :border="false" background="#FFFFFF">
			<template #right>
				<view class="top-actions">
					<view class="scan-icon"></view>
					<view class="round-plus" @click="go('/subpackage_meeting/startMeeting/startMeeting')">+</view>
				</view>
			</template>
		</nav-bar>

		<view class="meeting-body">
			<view class="home-hero">
			<view class="hero-close">×</view>
			<view class="hero-copy">
				<text class="hero-title">高效会议\n从这里开始</text>
				<text class="hero-desc">智能协作 · 实时共享</text>
			</view>
			<view class="hero-scene">
				<view class="board"></view>
				<view class="chair chair--one"></view>
				<view class="chair chair--two"></view>
				<view class="chair chair--three"></view>
				<view class="table"></view>
			</view>
		</view>

		<view class="quick-row">
			<view class="quick-item" @click="go('/subpackage_meeting/startMeeting/startMeeting')">
				<view class="quick-icon">▶</view>
				<text>发起会议</text>
			</view>
			<view class="quick-item" @click="go('/subpackage_meeting/reserveMeeting/reserveMeeting')">
				<view class="quick-icon">▣</view>
				<text>预约会议</text>
			</view>
			<view class="quick-item" @click="go('/subpackage_meeting/joinMeeting/joinMeeting')">
				<view class="quick-icon">+</view>
				<text>加入会议</text>
			</view>
		</view>

		<view class="date-line">
			<text>{{ todayLabel }}</text>
		</view>

		<view class="meeting-list">
			<view v-if="loading" class="empty-state">正在加载会议...</view>
			<view v-else-if="meetings.length === 0" class="empty-state">暂无会议，先发起或预约一场会议吧。</view>
			<view
				v-for="meeting in meetings"
				v-else
				:key="meeting.sessionId"
				class="meeting-item"
				:class="{ 'meeting-item--active': meeting.status === 'active' }"
				@click="openDetail(meeting)"
			>
				<view>
					<text class="meeting-name">{{ meeting.title || '未命名会议' }}</text>
					<text class="meeting-time">{{ meetingTime(meeting) }}</text>
					<text class="meeting-code">会议号：{{ formatRoomCode(meeting.roomCode) }}</text>
				</view>
				<view
					class="pill"
					:class="{ 'pill--live': meeting.status === 'active' }"
					@click.stop="enterMeeting(meeting)"
				>{{ meeting.status === 'active' ? '进行中' : '加入' }}</view>
			</view>
		</view>
		</view>
	</view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getMeetings, startMeeting } from '@/api/ai.js'

export default {
	components: { NavBar },
	data() {
		return {
			loading: false,
			meetings: []
		}
	},
	onShow() {
		this.loadMeetings()
	},
	computed: {
		todayLabel() {
			const now = new Date()
			const weekdays = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']
			return `今天 · ${now.getMonth() + 1}月${now.getDate()}日 ${weekdays[now.getDay()]}`
		}
	},
	methods: {
		go(url) {
			uni.navigateTo({ url })
		},
		async loadMeetings() {
			this.loading = true
			try {
				const res = await getMeetings({ pageNum: 1, pageSize: 20 })
				this.meetings = res?.data?.records || []
			} catch (error) {
				this.meetings = []
			} finally {
				this.loading = false
			}
		},
		openDetail(meeting) {
			uni.navigateTo({
				url: `/subpackage_meeting/meetingDetail/meetingDetail?sessionId=${encodeURIComponent(meeting.sessionId)}`
			})
		},
		async enterMeeting(meeting) {
			if (!meeting?.sessionId) return
			if (meeting.status !== 'active') {
				try {
					await startMeeting(meeting.sessionId)
				} catch (error) {}
			}
			uni.navigateTo({
				url: `/subpackage_meeting/meetingLive/meetingLive?sessionId=${encodeURIComponent(meeting.sessionId)}&title=${encodeURIComponent(meeting.title || '未命名会议')}&roomCode=${encodeURIComponent(meeting.roomCode || '')}`
			})
		},
		formatRoomCode(roomCode) {
			const code = roomCode || '未生成'
			return code.replace(/(.{3})/g, '$1 ').trim()
		},
		meetingTime(meeting) {
			const source = meeting.scheduledStartTime || meeting.startTime || meeting.createTime
			if (!source) return meeting.meetingType === 'reserved' ? '待开始' : '会议中'
			const date = new Date(source)
			if (Number.isNaN(date.getTime())) return meeting.meetingType === 'reserved' ? '待开始' : '会议中'
			const hh = String(date.getHours()).padStart(2, '0')
			const mm = String(date.getMinutes()).padStart(2, '0')
			return meeting.meetingType === 'reserved' && meeting.status !== 'active' ? `${hh}:${mm} 预约` : `${hh}:${mm} 开始`
		}
	}
}
</script>

<style lang="scss" scoped>
.meeting-app-page { min-height: 100vh; background: #ffffff; color: #101820; }
.meeting-body { padding: 8rpx 28rpx 72rpx; box-sizing: border-box; }
.top-actions { display: flex; align-items: center; gap: 30rpx; }
.round-plus { width: 38rpx; height: 38rpx; border-radius: 50%; background: #1f7f68; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 32rpx; line-height: 1; }
.scan-icon { width: 30rpx; height: 30rpx; border: 3rpx solid #101820; border-radius: 6rpx; position: relative; box-sizing: border-box; }
.scan-icon::after { content: ''; position: absolute; inset: 8rpx; background: #fff; }
.home-hero { height: 258rpx; border-radius: 28rpx; overflow: hidden; position: relative; background: linear-gradient(135deg, #24735f 0%, #8cc7aa 100%); box-shadow: 0 20rpx 40rpx rgba(31, 127, 104, 0.15); }
.hero-close { position: absolute; right: 18rpx; top: 14rpx; color: rgba(255,255,255,.85); font-size: 34rpx; z-index: 3; }
.hero-copy { position: absolute; left: 28rpx; top: 44rpx; z-index: 2; display: flex; flex-direction: column; }
.hero-title { color: #fff; font-size: 40rpx; line-height: 1.28; font-weight: 900; white-space: pre-line; }
.hero-desc { margin-top: 22rpx; color: rgba(255,255,255,.9); font-size: 23rpx; }
.hero-scene { position: absolute; right: 12rpx; bottom: 4rpx; width: 270rpx; height: 205rpx; }
.board { position: absolute; width: 102rpx; height: 74rpx; left: 52rpx; top: 28rpx; border: 5rpx solid rgba(255,255,255,.75); border-radius: 8rpx; transform: rotate(-2deg); }
.table { position: absolute; width: 112rpx; height: 38rpx; left: 82rpx; bottom: 38rpx; border-radius: 50%; background: #65b696; box-shadow: 0 10rpx 0 rgba(34, 110, 90, .36); }
.chair { position: absolute; width: 58rpx; height: 64rpx; border-radius: 20rpx 20rpx 14rpx 14rpx; background: #7fc4a5; box-shadow: 0 10rpx 18rpx rgba(22,77,64,.18); }
.chair--one { left: 16rpx; bottom: 32rpx; transform: rotate(-12deg); }
.chair--two { right: 24rpx; bottom: 46rpx; transform: rotate(9deg); }
.chair--three { right: 66rpx; top: 60rpx; transform: rotate(-6deg); }
.quick-row { display: grid; grid-template-columns: repeat(3, 1fr); margin: 48rpx 8rpx 46rpx; }
.quick-item { display: flex; flex-direction: column; align-items: center; gap: 16rpx; color: #121b22; font-size: 24rpx; font-weight: 700; }
.quick-icon { width: 72rpx; height: 72rpx; border-radius: 20rpx; background: linear-gradient(135deg,#198468,#49aa88); color: #fff; display: flex; align-items: center; justify-content: center; font-size: 34rpx; box-shadow: 0 12rpx 24rpx rgba(31,127,104,.16); }
.date-line { margin-bottom: 18rpx; font-size: 26rpx; color: #1e2930; }
.meeting-list { border-radius: 26rpx; overflow: hidden; box-shadow: 0 22rpx 54rpx rgba(22,35,42,.08); }
.empty-state { padding: 38rpx 24rpx; background: #fff; color: #68747a; font-size: 25rpx; text-align: center; }
.meeting-item { min-height: 126rpx; padding: 24rpx 28rpx; background: #fff; display: flex; align-items: flex-start; justify-content: space-between; border-bottom: 1rpx solid #f0f2f2; box-sizing: border-box; }
.meeting-item--active { background: linear-gradient(90deg, rgba(229,246,236,.95), rgba(255,255,255,.98)); }
.meeting-name { display: block; font-size: 27rpx; color: #121b22; font-weight: 800; }
.meeting-time, .meeting-code { display: block; margin-top: 9rpx; color: #68747a; font-size: 22rpx; }
.pill { min-width: 76rpx; height: 44rpx; border-radius: 999rpx; background: #eef8f3; color: #1f7f68; display: flex; align-items: center; justify-content: center; font-size: 22rpx; font-weight: 700; }
.pill--live { background: #c8efd9; }
</style>
