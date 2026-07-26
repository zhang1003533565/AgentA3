<template>
	<view class="meeting-app-page">
		<nav-bar title="会议" :showBack="true" fixed placeholder :border="false" background="#FFFFFF">
			<template #right>
				<view class="top-actions">
					<view class="round-calendar" @click="go('/subpackage_meeting/meetingSchedule/meetingSchedule')">▣</view>
					<view class="round-plus" @click="go('/subpackage_meeting/startMeeting/startMeeting')">+</view>
				</view>
			</template>
		</nav-bar>

		<view class="meeting-body">
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
				<view class="history-entry" @click="go('/subpackage_meeting/meetingHistory/meetingHistory')">历史会议 ></view>
			</view>

			<view class="meeting-list">
				<view v-if="loading" class="empty-state">正在加载会议...</view>
				<view v-else-if="todayMeetingList.length === 0" class="empty-state">暂无会议，先发起或预约一场会议吧。</view>
				<view
					v-for="meeting in todayMeetingList"
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
		},
		// 核心：过滤，首页只保留 待开始 / 进行中，剔除已结束
		todayMeetingList() {
			return this.meetings.filter(item => item.status !== 'ended')
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
.meeting-app-page { min-height: 100vh; background: #f5f7fa; color: #101820; }
.meeting-body { padding: 8rpx 28rpx 72rpx; box-sizing: border-box; }
.top-actions {
	display: flex;
	align-items: center;
	gap: 20rpx;
}
.round-plus {
	width: 38rpx;
	height: 38rpx;
	border-radius: 50%;
	background: #86C9A8;
	color: #fff;
	display: flex;
	align-items: center;
	justify-content: center;
	font-size: 32rpx;
	line-height: 1;
}
.round-calendar {
	width: 38rpx;
	height: 38rpx;
	border-radius: 50%;
	background: #86C9A8;
	color: #fff;
	display: flex;
	align-items: center;
	justify-content: center;
	font-size: 28rpx;
	line-height: 1;
}

/* 快捷功能按钮 - 统一浅薄荷主题色 */
.quick-row { display: grid; grid-template-columns: repeat(3, 1fr); margin: 48rpx 8rpx 46rpx; }
.quick-item { display: flex; flex-direction: column; align-items: center; gap: 16rpx; color: #121b22; font-size: 24rpx; font-weight: 700; }
.quick-icon { width: 72rpx; height: 72rpx; border-radius: 20rpx; background: linear-gradient(135deg,#86C9A8,#A8DDC2); color: #fff; display: flex; align-items: center; justify-content: center; font-size: 34rpx; box-shadow: 0 12rpx 24rpx rgba(134, 201, 168,.16); }

.date-line {
	margin-bottom: 18rpx;
	font-size: 26rpx;
	color: #1e2930;
	display: flex;
	justify-content: space-between;
	align-items: center;
}
.history-entry {
	font-size: 24rpx;
	color: #86C9A8;
}

/* 会议列表卡片 */
.meeting-list { border-radius: 26rpx; overflow: hidden; background: #fff; }
.empty-state { padding: 38rpx 24rpx; color: #68747a; font-size: 25rpx; text-align: center; }
.meeting-item { min-height: 126rpx; padding: 24rpx 28rpx; display: flex; align-items: flex-start; justify-content: space-between; border-bottom: 1rpx solid #EEEEEE; box-sizing: border-box; }
.meeting-item:last-child { border-bottom: none; }
.meeting-item--active { background: linear-gradient(90deg, rgba(134, 201, 168, 0.08), rgba(255,255,255,.98)); }
.meeting-name { display: block; font-size: 27rpx; color: #121b22; font-weight: 800; }
.meeting-time, .meeting-code { display: block; margin-top: 9rpx; color: #68747a; font-size: 22rpx; }

/* 状态标签 - 淡绿底深绿字 */
.pill { min-width: 76rpx; height: 44rpx; border-radius: 999rpx; background: #E8F8F2; color: #57A77D; display: flex; align-items: center; justify-content: center; font-size: 22rpx; font-weight: 700; }
.pill--live { background: #D4F1E5; }
</style>