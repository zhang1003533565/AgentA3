<template>
	<view class="meeting-app-page">
		<nav-bar title="参会记录" :showBack="true" fixed placeholder :border="false" background="#FFFFFF" />

		<view class="page-body">
			<!-- 会议信息 -->
			<view class="meeting-info">
				<text class="meeting-title">{{ title }}</text>
				<view class="meeting-meta">
					<text>{{ dateRangeText }}</text>
					<text class="dot">·</text>
					<text>会议时长</text>
					<text class="highlight">{{ duration }}分钟</text>
					<text class="dot">·</text>
					<text class="highlight">{{ participantCount }}人参会</text>
				</view>
			</view>

			<!-- 参会成员 -->
			<view class="section-title">参会成员</view>
			<view class="member-card">
				<view v-for="(member, index) in members" :key="index" class="member-item">
					<view class="member-avatar">
						<image v-if="member.avatar" :src="member.avatar" mode="aspectFit" />
						<text v-else>{{ member.name.slice(0, 1) }}</text>
					</view>
					<view class="member-content">
						<view class="member-header">
							<view class="member-name-row">
								<text class="member-name">{{ member.name }}</text>
								<view v-if="member.status !== '多次进出'" class="status-tag" :class="`status-tag--${statusClass(member.status)}`">{{ member.status }}</view>
								<text v-else class="multi-label">多次进出</text>
							</view>
							<text class="member-duration">{{ member.duration }}分钟</text>
						</view>
						<view v-if="member.status !== '多次进出'" class="member-time">
							<text>{{ member.joinTime }} 加入 · {{ member.leaveTime }} 离开</text>
						</view>
						<view v-else class="member-multi">
							<view class="multi-summary">
								<text>{{ member.entries.length }}次进出 · </text>
								<text class="view-detail" @click.stop="toggleDetail(member)">
									查看详情
									<text class="arrow" :class="{ open: member.showDetail }">▼</text>
								</text>
							</view>
							<view v-if="member.showDetail" class="multi-entries">
								<view v-for="(entry, idx) in member.entries" :key="idx" class="entry-row">
									<text>第{{ entry.seq }}次 {{ entry.joinTime }} 加入 · {{ entry.leaveTime }} 离开</text>
								</view>
							</view>
						</view>
					</view>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getMeetingDetail } from '@/api/ai.js'

const MOCK_MEMBERS = [
	{ name: '测试学生', status: '全程参会', joinTime: '14:00', leaveTime: '14:42', duration: 42, entries: [] },
	{ name: '成员 2', status: '迟到加入', joinTime: '14:08', leaveTime: '14:42', duration: 34, entries: [] },
	{ name: '成员 3', status: '提前离开', joinTime: '14:00', leaveTime: '14:25', duration: 25, entries: [] },
	{ name: '成员 4', status: '多次进出', joinTime: '', leaveTime: '', duration: 31, entries: [
		{ seq: 1, joinTime: '14:00', leaveTime: '14:12' },
		{ seq: 2, joinTime: '14:23', leaveTime: '14:42' }
	]},
	{ name: '成员 5', status: '全程参会', joinTime: '14:03', leaveTime: '14:41', duration: 38, entries: [] },
	{ name: '成员 6', status: '全程参会', joinTime: '14:20', leaveTime: '14:39', duration: 19, entries: [] }
]

export default {
	components: { NavBar },
	data() {
		return {
			sessionId: '',
			title: '会议',
			startTime: '',
			endTime: '',
			duration: 0,
			participantCount: 0,
			members: []
		}
	},
	computed: {
		dateRangeText() {
			if (!this.startTime || !this.endTime) return ''
			const start = new Date(this.startTime)
			const end = new Date(this.endTime)
			if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime())) return ''
			const month = start.getMonth() + 1
			const day = start.getDate()
			const startStr = `${String(start.getHours()).padStart(2, '0')}:${String(start.getMinutes()).padStart(2, '0')}`
			const endStr = `${String(end.getHours()).padStart(2, '0')}:${String(end.getMinutes()).padStart(2, '0')}`
			return `${month}月${day}日 ${startStr}-${endStr}`
		}
	},
	onLoad(options) {
		if (options?.sessionId) this.sessionId = decodeURIComponent(options.sessionId)
		if (options?.title) this.title = decodeURIComponent(options.title)
		this.loadMeeting()
	},
	methods: {
		async loadMeeting() {
			if (!this.sessionId) {
				this.useMockData()
				return
			}
			try {
				const res = await getMeetingDetail(this.sessionId)
				const detail = res?.data || {}
				const session = detail.session || {}
				this.title = session.title || this.title
				this.startTime = session.startTime || ''
				this.endTime = session.endTime || session.scheduledStartTime || ''
				this.participantCount = Array.isArray(detail.participants) ? detail.participants.length : 0
				this.duration = this.computeDuration(this.startTime, this.endTime)

				if (Array.isArray(detail.participantRecords) && detail.participantRecords.length > 0) {
					this.members = detail.participantRecords.map(m => ({ ...m, showDetail: false }))
				} else {
					this.useMockData()
				}
			} catch (error) {
				this.useMockData()
			}
		},
		useMockData() {
			this.title = '项目进度同步会'
			this.startTime = '2025-07-29T14:00:00'
			this.endTime = '2025-07-29T14:42:00'
			this.duration = 42
			this.participantCount = 6
			this.members = MOCK_MEMBERS.map(m => ({ ...m, showDetail: false }))
		},
		computeDuration(start, end) {
			if (!start || !end) return 0
			const s = new Date(start).getTime()
			const e = new Date(end).getTime()
			if (Number.isNaN(s) || Number.isNaN(e) || e <= s) return 0
			return Math.round((e - s) / 60000)
		},
		statusClass(status) {
			const map = { '全程参会': 'full', '迟到加入': 'late', '提前离开': 'early' }
			return map[status] || 'full'
		},
		toggleDetail(member) {
			member.showDetail = !member.showDetail
		}
	}
}
</script>

<style lang="scss" scoped>
.meeting-app-page {
	min-height: 100vh;
	background: #F8FAFC;
	color: #1F2937;
}

.page-body {
	padding: 24rpx 32rpx 48rpx;
}

/* 会议信息区 */
.meeting-info {
	display: flex;
	flex-direction: column;
	align-items: center;
	padding: 24rpx 0 40rpx;
}

.meeting-title {
	font-size: 40rpx;
	font-weight: 800;
	color: #111827;
	margin-bottom: 16rpx;
}

.meeting-meta {
	display: flex;
	align-items: center;
	gap: 8rpx;
	font-size: 24rpx;
	color: #6B7280;
	flex-wrap: wrap;
	justify-content: center;
}

.dot {
	color: #D1D5DB;
}

.highlight {
	color: #22C55E;
}

/* 列表标题 */
.section-title {
	font-size: 28rpx;
	font-weight: 700;
	color: #1F2937;
	margin-bottom: 16rpx;
}

/* 成员卡片 */
.member-card {
	background: #fff;
	border-radius: 24rpx;
	padding: 8rpx 28rpx;
	box-shadow: 0 8rpx 32rpx rgba(15, 23, 42, 0.04);
}

.member-item {
	display: flex;
	align-items: flex-start;
	gap: 20rpx;
	padding: 28rpx 0;
	border-bottom: 1rpx solid #F1F5F9;

	&:last-child {
		border-bottom: none;
	}
}

.member-avatar {
	width: 80rpx;
	height: 80rpx;
	border-radius: 50%;
	background: #E5E7EB;
	color: #9CA3AF;
	display: flex;
	align-items: center;
	justify-content: center;
	font-size: 30rpx;
	font-weight: 700;
	flex-shrink: 0;
	overflow: hidden;

	image {
		width: 100%;
		height: 100%;
	}
}

.member-content {
	flex: 1;
	min-width: 0;
}

.member-header {
	display: flex;
	align-items: flex-start;
	justify-content: space-between;
	gap: 16rpx;
	margin-bottom: 8rpx;
}

.member-name-row {
	display: flex;
	align-items: center;
	gap: 12rpx;
	flex-wrap: wrap;
}

.member-name {
	font-size: 30rpx;
	font-weight: 700;
	color: #1F2937;
}

.member-duration {
	font-size: 30rpx;
	font-weight: 700;
	color: #1F2937;
	flex-shrink: 0;
}

.member-time {
	font-size: 24rpx;
	color: #9CA3AF;
}

.status-tag {
	height: 36rpx;
	padding: 0 14rpx;
	border-radius: 8rpx;
	font-size: 20rpx;
	font-weight: 700;
	display: flex;
	align-items: center;

	&--full {
		background: #DCFCE7;
		color: #16A34A;
	}

	&--late {
		background: #FEF3C7;
		color: #D97706;
	}

	&--early {
		background: #FEE2E2;
		color: #DC2626;
	}
}

.multi-label {
	font-size: 20rpx;
	color: #9CA3AF;
}

.member-multi {
	font-size: 24rpx;
}

.multi-summary {
	color: #9CA3AF;
}

.view-detail {
	color: #3B82F6;
}

.arrow {
	display: inline-block;
	font-size: 20rpx;
	margin-left: 4rpx;
	transition: transform 0.2s;

	&.open {
		transform: rotate(180deg);
	}
}

.multi-entries {
	margin-top: 12rpx;
	display: flex;
	flex-direction: column;
	gap: 8rpx;
}

.entry-row {
	color: #6B7280;
}
</style>
