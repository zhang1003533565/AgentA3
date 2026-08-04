<template>
	<view class="meeting-app-page">
		<nav-bar title="会议" :showBack="true" fixed placeholder :border="false" background="#FFFFFF" />

		<view class="meeting-body">
			<!-- 快捷入口 -->
			<view class="quick-row">
				<view class="quick-item" @click="go('/subpackage_meeting/startMeeting/startMeeting')">
					<view class="quick-icon quick-icon--start">
						<image class="quick-icon__img" src="@/static/icons/camera.svg" mode="aspectFit" />
					</view>
					<text class="quick-label">发起会议</text>
				</view>
				<view class="quick-item" @click="go('/subpackage_meeting/reserveMeeting/reserveMeeting')">
					<view class="quick-icon quick-icon--reserve">
						<image class="quick-icon__img" src="@/static/icons/line/calendar.svg" mode="aspectFit" />
					</view>
					<text class="quick-label">预约会议</text>
				</view>
				<view class="quick-item" @click="go('/subpackage_meeting/joinMeeting/joinMeeting')">
					<view class="quick-icon quick-icon--join">
						<image class="quick-icon__img" src="@/static/icons/line/login.svg" mode="aspectFit" />
					</view>
					<text class="quick-label">加入会议</text>
				</view>
			</view>

			<!-- 今日会议头部 -->
			<view class="section-header">
				<view class="section-title-wrap">
					<text class="section-title">今日会议</text>
					<text class="section-date">{{ todayLabel }}</text>
				</view>
				<view class="history-entry" @click="go('/subpackage_meeting/meetingHistory/meetingHistory')">
					<text>历史会议</text>
					<text class="history-arrow">></text>
				</view>
			</view>

			<!-- 会议列表 -->
			<view class="meeting-list">
				<view v-if="loading" class="loading-state">
					<text>正在加载会议...</text>
				</view>

				<view v-else-if="todayMeetingList.length === 0" class="empty-state">
					<image class="empty-illustration" src="@/static/illustrations/meeting-empty.svg" mode="aspectFit" />
					<text class="empty-title">暂无会议</text>
					<text class="empty-desc">今天还没有会议安排，立即发起或预约一场吧</text>
					<view class="empty-action" @click="go('/subpackage_meeting/startMeeting/startMeeting')">
						<text>立即发起会议</text>
					</view>
				</view>

				<view
					v-for="meeting in todayMeetingList"
					v-else
					:key="meeting.sessionId"
					class="meeting-item"
					:class="{ 'meeting-item--active': meeting.status === 'active' }"
					@click="openDetail(meeting)"
				>
					<view class="meeting-meta">
						<view class="meeting-title-row">
							<view class="status-dot" :class="`status-dot--${meeting.status}`" />
							<text class="meeting-name">{{ meeting.title || '未命名会议' }}</text>
						</view>
						<text class="meeting-time">{{ meetingTime(meeting) }}</text>
						<text class="meeting-code">会议号 {{ formatRoomCode(meeting.roomCode) }}</text>
					</view>
					<view
						class="pill"
						:class="{ 'pill--live': meeting.status === 'active', 'pill--ended': meeting.status === 'ended' }"
						@click.stop="enterMeeting(meeting)"
					>
						{{ actionText(meeting) }}
					</view>
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
			return `${now.getMonth() + 1}月${now.getDate()}日 ${weekdays[now.getDay()]}`
		},
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
			return meeting.meetingType === 'reserved' && meeting.status !== 'active' ? `${hh}:${mm} 开始` : `${hh}:${mm} 开始`
		},
		actionText(meeting) {
			if (meeting.status === 'active') return '进入'
			if (meeting.status === 'ended') return '查看记录'
			return '加入'
		}
	}
}
</script>

<style lang="scss" scoped>
$primary: #3B82F6;
$primary-deep: #2563EB;
$primary-light: #EFF6FF;
$text-main: #1F2937;
$text-secondary: #6B7280;
$text-muted: #9CA3AF;
$bg-page: #F8FAFC;
$card-radius: 28rpx;

.meeting-app-page {
	min-height: 100vh;
	background: $bg-page;
	color: $text-main;
}

.meeting-body {
	padding: 24rpx 32rpx 72rpx;
	box-sizing: border-box;
}

/* 快捷入口 */
.quick-row {
	display: grid;
	grid-template-columns: repeat(3, 1fr);
	gap: 20rpx;
	margin: 36rpx 0 48rpx;
}

.quick-item {
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: 18rpx;
	padding: 32rpx 0;
	background: #fff;
	border-radius: $card-radius;
	box-shadow: 0 8rpx 28rpx rgba(59, 130, 246, 0.08);
}

.quick-icon {
	width: 96rpx;
	height: 96rpx;
	border-radius: 50%;
	display: flex;
	align-items: center;
	justify-content: center;

	&--start {
		background: linear-gradient(135deg, #3B82F6, #60A5FA);
		box-shadow: 0 12rpx 28rpx rgba(59, 130, 246, 0.28);
	}

	&--reserve {
		background: linear-gradient(135deg, #0EA5E9, #38BDF8);
		box-shadow: 0 12rpx 28rpx rgba(14, 165, 233, 0.24);
	}

	&--join {
		background: linear-gradient(135deg, #6366F1, #818CF8);
		box-shadow: 0 12rpx 28rpx rgba(99, 102, 241, 0.24);
	}
}

.quick-icon__img {
	width: 44rpx;
	height: 44rpx;
	filter: brightness(0) invert(1);
}

.quick-label {
	font-size: 26rpx;
	font-weight: 600;
	color: $text-main;
}

/* 分区头部 */
.section-header {
	display: flex;
	justify-content: space-between;
	align-items: center;
	margin-bottom: 24rpx;
}

.section-title-wrap {
	display: flex;
	align-items: baseline;
	gap: 16rpx;
}

.section-title {
	font-size: 34rpx;
	font-weight: 800;
	color: $text-main;
}

.section-date {
	font-size: 24rpx;
	color: $text-secondary;
}

.history-entry {
	display: flex;
	align-items: center;
	gap: 6rpx;
	font-size: 26rpx;
	color: $primary;
	font-weight: 600;
}

.history-arrow {
	font-size: 22rpx;
}

/* 会议列表 */
.meeting-list {
	border-radius: $card-radius;
	overflow: hidden;
	background: #fff;
	box-shadow: 0 8rpx 32rpx rgba(15, 23, 42, 0.04);
}

.loading-state,
.empty-state {
	display: flex;
	flex-direction: column;
	align-items: center;
	padding: 72rpx 40rpx;
	text-align: center;
}

.loading-state {
	color: $text-secondary;
	font-size: 26rpx;
}

.empty-illustration {
	width: 320rpx;
	height: 240rpx;
	margin-bottom: 32rpx;
}

.empty-title {
	font-size: 32rpx;
	font-weight: 700;
	color: $text-main;
	margin-bottom: 12rpx;
}

.empty-desc {
	font-size: 26rpx;
	color: $text-secondary;
	margin-bottom: 36rpx;
	line-height: 1.6;
}

.empty-action {
	min-width: 280rpx;
	height: 84rpx;
	padding: 0 40rpx;
	border-radius: 999rpx;
	background: linear-gradient(90deg, $primary, $primary-deep);
	color: #fff;
	font-size: 28rpx;
	font-weight: 700;
	display: flex;
	align-items: center;
	justify-content: center;
	box-shadow: 0 12rpx 32rpx rgba(59, 130, 246, 0.28);
}

.meeting-item {
	min-height: 144rpx;
	padding: 28rpx 32rpx;
	display: flex;
	align-items: center;
	justify-content: space-between;
	border-bottom: 1rpx solid #F1F5F9;
	box-sizing: border-box;
	position: relative;

	&:last-child {
		border-bottom: none;
	}

	&--active::before {
		content: '';
		position: absolute;
		left: 0;
		top: 28rpx;
		bottom: 28rpx;
		width: 6rpx;
		border-radius: 0 6rpx 6rpx 0;
		background: $primary;
	}
}

.meeting-meta {
	flex: 1;
	min-width: 0;
	padding-right: 24rpx;
}

.meeting-title-row {
	display: flex;
	align-items: center;
	gap: 12rpx;
	margin-bottom: 10rpx;
}

.status-dot {
	width: 14rpx;
	height: 14rpx;
	border-radius: 50%;
	background: $text-muted;
	flex-shrink: 0;

	&--active {
		background: #22C55E;
		box-shadow: 0 0 0 6rpx rgba(34, 197, 94, 0.15);
	}

	&--reserved,
	&--pending {
		background: #F59E0B;
	}
}

.meeting-name {
	font-size: 30rpx;
	color: $text-main;
	font-weight: 700;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.meeting-time,
.meeting-code {
	display: block;
	margin-top: 10rpx;
	color: $text-secondary;
	font-size: 24rpx;
}

/* 状态标签 */
.pill {
	min-width: 104rpx;
	height: 52rpx;
	padding: 0 20rpx;
	border-radius: 999rpx;
	background: $primary-light;
	color: $primary;
	display: flex;
	align-items: center;
	justify-content: center;
	font-size: 24rpx;
	font-weight: 700;
	flex-shrink: 0;

	&--live {
		background: #DCFCE7;
		color: #16A34A;
	}

	&--ended {
		background: #F3F4F6;
		color: $text-secondary;
	}
}
</style>
