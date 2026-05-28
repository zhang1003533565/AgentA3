<template>
	<view class="meeting-app-page">
		<nav-bar title="日程" :showBack="true" fixed placeholder :border="false" background="#FFFFFF">
			<template #right>
				<view class="top-actions"><view class="calendar-mini">▣</view><view class="round-plus" @click="go('/subpackage_meeting/startMeeting/startMeeting')">+</view></view>
			</template>
		</nav-bar>

		<view class="schedule-body">
			<text class="month-title">{{ monthTitle }}</text>
			<view class="week-row">
				<view v-for="day in days" :key="day.num" class="day-cell" :class="{ active: day.num === currentDay }">
					<text class="weekday">{{ day.week }}</text>
					<text class="daynum">{{ day.num }}</text>
				</view>
			</view>

			<view class="schedule-list">
				<view v-if="loading" class="empty-state">正在加载会议...</view>
				<view v-else-if="meetings.length === 0" class="empty-state">暂无真实会议日程</view>
				<block v-else>
					<view
						v-for="meeting in meetings"
						:key="meeting.sessionId"
						class="schedule-item"
						:class="{ active: meeting.status === 'active' }"
						@click="openDetail(meeting)"
					>
						<view class="time-block"><text>{{ meetingTime(meeting) }}</text><text>{{ meetingStatus(meeting) }}</text></view>
						<view class="schedule-main"><text class="schedule-name">{{ meeting.title || '未命名会议' }}</text><text class="schedule-meta">{{ meeting.participantCount || 0 }}人 · {{ formatRoomCode(meeting.roomCode) }}</text></view>
						<view class="pill" :class="{ live: meeting.status === 'active' }">{{ meeting.status === 'active' ? '进行中' : '详情' }}</view>
					</view>
				</block>
			</view>
		</view>
	</view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getMeetings } from '@/api/ai.js'

function buildWeekDays(date) {
	const weekdays = ['日', '一', '二', '三', '四', '五', '六']
	const start = new Date(date)
	start.setDate(date.getDate() - date.getDay())
	return Array.from({ length: 7 }, (_, index) => {
		const day = new Date(start)
		day.setDate(start.getDate() + index)
		return { week: weekdays[day.getDay()], num: String(day.getDate()) }
	})
}

export default {
	components: { NavBar },
	data() {
		const now = new Date()
		return {
			loading: false,
			meetings: [],
			currentDay: String(now.getDate()),
			days: buildWeekDays(now)
		}
	},
	onShow() {
		this.loadMeetings()
	},
	computed: {
		monthTitle() {
			const now = new Date()
			return `${now.getFullYear()}年 ${now.getMonth() + 1}月`
		}
	},
	methods: {
		go(url) { uni.navigateTo({ url }) },
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
			uni.navigateTo({ url: `/subpackage_meeting/meetingDetail/meetingDetail?sessionId=${encodeURIComponent(meeting.sessionId)}` })
		},
		formatRoomCode(roomCode) {
			const code = roomCode || '未生成'
			return code.replace(/(.{3})/g, '$1 ').trim()
		},
		meetingTime(meeting) {
			const source = meeting.scheduledStartTime || meeting.startTime || meeting.createTime
			if (!source) return '--:--'
			const date = new Date(source)
			if (Number.isNaN(date.getTime())) return '--:--'
			return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
		},
		meetingStatus(meeting) {
			const map = { active: '会议中', idle: '待开始', paused: '已暂停', ended: '已结束' }
			return map[meeting.status] || '会议'
		}
	}
}
</script>

<style lang="scss" scoped>
.meeting-app-page { min-height: 100vh; background: #fff; color: #101820; }
.schedule-body { padding: 8rpx 28rpx 72rpx; box-sizing: border-box; }
.top-actions { display: flex; align-items: center; gap: 28rpx; }
.calendar-mini { font-size: 30rpx; }
.round-plus { width: 38rpx; height: 38rpx; border-radius: 50%; background: #1f7f68; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 32rpx; }
.month-title { display: block; margin: 14rpx 0 24rpx; font-size: 31rpx; font-weight: 850; }
.week-row { display: grid; grid-template-columns: repeat(7, 1fr); margin-bottom: 34rpx; }
.day-cell { display: flex; flex-direction: column; align-items: center; gap: 12rpx; font-size: 22rpx; color: #1a242a; }
.weekday { color: #68747a; }
.daynum { width: 46rpx; height: 34rpx; border-radius: 999rpx; display: flex; align-items: center; justify-content: center; font-weight: 850; }
.day-cell.active .daynum { background: #1f7f68; color: #fff; }
.schedule-list { border-radius: 24rpx; overflow: hidden; box-shadow: 0 22rpx 54rpx rgba(22,35,42,.08); }
.empty-state { padding: 38rpx 24rpx; background: #fff; color: #68747a; font-size: 25rpx; text-align: center; }
.schedule-item { min-height: 112rpx; display: grid; grid-template-columns: 92rpx 1fr 82rpx; align-items: center; gap: 10rpx; padding: 20rpx 22rpx; background: #fff; border-bottom: 1rpx solid #f0f2f2; box-sizing: border-box; }
.schedule-item.active { background: linear-gradient(90deg, rgba(229,246,236,.95), #fff); }
.time-block, .schedule-main { display: flex; flex-direction: column; gap: 9rpx; }
.time-block { font-size: 25rpx; color: #1e2930; }
.schedule-name { font-size: 25rpx; font-weight: 850; }
.schedule-meta { color: #68747a; font-size: 22rpx; }
.pill { height: 42rpx; border-radius: 999rpx; background: #eef8f3; color: #1f7f68; display: flex; align-items: center; justify-content: center; font-size: 21rpx; font-weight: 750; }
.pill.live { background: #c8efd9; }
</style>
