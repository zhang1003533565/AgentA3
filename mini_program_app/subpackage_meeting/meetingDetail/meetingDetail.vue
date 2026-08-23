<template>
	<view class="meeting-app-page">
		<nav-bar title="会议详情" :showBack="true" fixed placeholder :border="false" background="#FFFFFF" @right-click="shareMeeting">
			<template #right>
				<view class="share-btn" @click="shareMeeting">
					<image class="share-icon" src="@/static/icons/icon-forward.svg" mode="aspectFit" />
				</view>
			</template>
		</nav-bar>

		<view class="detail-body">
			<!-- 会议标题与状态 -->
			<view class="detail-header">
				<view class="detail-title-row">
					<text class="detail-title">{{ title }}</text>
					<view class="status-tag" :class="`status-tag--${status}`">{{ statusText }}</view>
				</view>
				<view class="detail-meta">
					<view class="meta-item">
						<image class="meta-icon" src="@/static/icons/line/clock.svg" mode="aspectFit" />
						<text>{{ timeText }}</text>
					</view>
					<view class="meta-item">
						<image class="meta-icon" src="@/static/icons/line/users.svg" mode="aspectFit" />
						<text>{{ hostName }}（主持人）</text>
					</view>
				</view>
			</view>

			<!-- 会议信息：仅非历史记录（已结束）会议展示 -->
			<view v-if="status !== 'ended'" class="section-card">
				<view class="section-title">会议信息</view>
				<view class="info-row">
					<text class="info-label">会议号</text>
					<view class="info-value-wrap">
						<text class="info-value">{{ roomCode }}</text>
						<text class="copy-text" @click="copy(roomCode)">复制</text>
					</view>
				</view>
				<view class="info-row">
					<text class="info-label">会议链接</text>
					<view class="info-value-wrap">
						<text class="info-value link">https://meeting.app/join/{{ compactRoomCode }}</text>
						<text class="copy-text" @click="copyMeetingLink">复制</text>
					</view>
				</view>
			</view>

			<!-- 参会人 -->
			<view class="section-card participant-summary-card">
				<view class="section-title">参会人（{{ allParticipantNames.length }}）</view>
				<view class="participant-summary" @click="goMeetingHistory">
					<view class="participant-avatars">
						<view
							v-for="(name, index) in displayedParticipants"
							:key="index"
							class="summary-avatar"
							:style="{ zIndex: displayedParticipants.length - index }"
						>
							{{ name.slice(0, 1) }}
						</view>
					</view>
					<text class="participant-summary-text">{{ participantSummaryText }}</text>
					<view class="record-link">
						<text>查看参会记录</text>
						<text class="record-link-arrow">></text>
					</view>
				</view>
			</view>

			<!-- AI 会议纪要：预约（待开始）会议不展示 -->
			<view v-if="status !== 'idle'" class="entry-card" @click="onAiCardClick">
				<view class="entry-icon entry-icon--ai">
					<image class="entry-icon__img" src="@/static/icons/line/sparkles.svg" mode="aspectFit" />
				</view>
				<view class="entry-content">
					<text class="entry-title">AI会议纪要</text>
					<text class="entry-subtitle">查看 AI 整理的会议纪要</text>
				</view>
				<view class="entry-status" :class="{ 'entry-status--ready': aiMinutesStatus === 'generated', 'entry-status--pending': aiMinutesStatus === 'generating' }">
					<text>{{ aiMinutesStatusText }}</text>
				</view>
				<text class="entry-arrow">></text>
			</view>

			<!-- AI 会议纪要展开区：仅展示 Agent 2 (meeting_summary_agent) 的结果 -->
			<view v-if="showAiResults && aiMinutesResult" class="expand-panel">
				<view class="result-block">
					<view class="block-meta">
						<text class="result-tag">会议纪要</text>
						<text class="block-time">{{ formatDateTime(aiMinutesResult.createTime) }}</text>
					</view>
					<text class="block-text">{{ aiMinutesResult.answer }}</text>
				</view>
			</view>

			<!-- 会议记录：预约（待开始）会议不展示 -->
			<view v-if="status !== 'idle'" class="entry-card" :class="{ disabled: !hasRecords }" @click="onRecordCardClick">
				<view class="entry-icon entry-icon--record">
					<image class="entry-icon__img" src="@/static/icons/line/clipboard.svg" mode="aspectFit" />
				</view>
				<view class="entry-content">
					<text class="entry-title">会议记录</text>
					<text class="entry-subtitle">查看会议转写与聊天记录</text>
				</view>
				<view class="entry-status" :class="{ 'entry-status--ready': hasRecords }">
					<text>已生成</text>
				</view>
				<text class="entry-arrow">></text>
			</view>

			<!-- 记录展开区 -->
			<view v-if="showRecords && records.length" class="expand-panel">
				<view v-for="item in records" :key="item.id" class="result-block">
					<view class="block-meta">
						<text class="record-tag" :class="{ transcription: item.source === 'transcription' }">{{ sourceLabel(item.source) }}</text>
						<text class="block-time">{{ formatDateTime(item.createTime) }}</text>
					</view>
					<text class="block-text">{{ item.content }}</text>
				</view>
			</view>

		</view>

	</view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { deleteMeeting as deleteMeetingApi, getMeetingDetail } from '@/api/ai.js'

export default {
	components: { NavBar },
	data() {
		return {
			sessionId: '',
			title: '会议',
			roomCode: '',
			status: '',
			startTime: '',
			scheduledStartTime: '',
			participants: [],
				allParticipantNames: [],
			records: [],
			results: [],
			organizing: false,
			showAiResults: false,
			showRecords: false
		}
	},
	computed: {
		compactRoomCode() { return this.roomCode.replace(/\s+/g, '') },
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
		},
		displayedParticipants() {
			return this.allParticipantNames.slice(0, 4)
		},
		participantSummaryText() {
			const total = this.allParticipantNames.length
			if (total === 0) return '暂无参会人'
			const firstTwo = this.allParticipantNames.slice(0, 2).join('、')
			return `${firstTwo} 等 ${total} 人`
		},
		hasResults() {
			return this.results.length > 0
		},
		hasRecords() {
			return this.records.length > 0
		},
		/** Agent 2 (meeting_summary_agent) 的结果，从 results 数组中筛选 */
		aiMinutesResult() {
			if (!Array.isArray(this.results) || this.results.length === 0) return null
			return this.results.find(item => item && item.agentName === 'meeting_summary_agent') || null
		},
		/** AI 会议纪要状态：已生成 / 生成中 / 未生成 */
		aiMinutesStatus() {
			if (this.aiMinutesResult) return 'generated'
			if (this.status === 'ended') return 'generating'
			return 'empty'
		},
		aiMinutesStatusText() {
			const map = { generated: '已生成', generating: '生成中', empty: '未生成' }
			return map[this.aiMinutesStatus] || '未生成'
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
				this.applyDetail(res?.data || {})
			} catch (error) {}
		},
		applyDetail(detail) {
			const session = detail.session || {}
			this.title = session.title || this.title
			this.roomCode = session.roomCode || this.roomCode
			this.status = session.status || this.status
			this.startTime = session.startTime || ''
			this.scheduledStartTime = session.scheduledStartTime || ''
			this.participants = Array.isArray(detail.participants) ? detail.participants : []
						// 参会人数以最多人数为准：优先使用全部参会记录（含中途离开者），无记录时退回在线名单
						const records = Array.isArray(detail.participantRecords) ? detail.participantRecords : []
						this.allParticipantNames = records.length > 0 ? records.map(item => item && item.name).filter(Boolean) : this.participants
			this.records = Array.isArray(detail.records) ? detail.records : []
			this.results = Array.isArray(detail.results) ? detail.results : []
		},
		back() { uni.navigateBack() },
		copy(data) { uni.setClipboardData({ data, success: () => uni.showToast({ title: '已复制', icon: 'none' }) }) },
		copyMeetingLink() { this.copy('https://meeting.app/join/' + this.compactRoomCode) },
		shareMeeting() { this.copy(`会议：${this.title}\n会议号：${this.roomCode}\nhttps://meeting.app/join/${this.compactRoomCode}`) },
		goSchedule() {
			uni.redirectTo({
				url: '/subpackage_meeting/meetingSchedule/meetingSchedule'
			})
		},
		onAiCardClick() {
			// 只查询展示 Agent 2 结果，不触发 POST /ai-minutes
			this.showAiResults = !this.showAiResults
		},
		onRecordCardClick() {
			if (!this.hasRecords) return
			this.showRecords = !this.showRecords
		},
		deleteCurrentMeeting() {
			if (!this.sessionId) return
			uni.showModal({
				title: '删除会议',
				content: '删除后会议记录和AI整理结果都会移除，确定删除吗？',
				confirmText: '删除',
				confirmColor: '#ef3d34',
				success: async (res) => {
					if (!res.confirm) return
					try {
						await deleteMeetingApi(this.sessionId)
						uni.showToast({ title: '会议已删除', icon: 'none' })
						setTimeout(() => {
							uni.redirectTo({ url: '/subpackage_meeting/meetingRoom/meetingRoom' })
						}, 350)
					} catch (error) {
						uni.showToast({ title: '删除失败，请稍后重试', icon: 'none' })
					}
				}
			})
		},
		formatDateTime(value) {
			if (!value) return ''
			const date = new Date(value)
			if (Number.isNaN(date.getTime())) return ''
			const month = String(date.getMonth() + 1).padStart(2, '0')
			const day = String(date.getDate()).padStart(2, '0')
			const hour = String(date.getHours()).padStart(2, '0')
			const minute = String(date.getMinutes()).padStart(2, '0')
			return `${month}-${day} ${hour}:${minute}`
		},
		agentLabel(agentName) {
			const map = {
				meeting_transcription_agent: '转写整理',
				meeting_summary_agent: '会议纪要',
				meeting_controller_agent: '流程总控',
				meeting_member_analysis_agent: '成员分析',
				meeting_resource_recommendation_agent: '资源推荐',
				meeting_voice_broadcast_agent: '语音播报'
			}
			return map[agentName] || agentName || 'AI整理'
		},
		sourceLabel(source) {
			return source === 'transcription' ? '实时转写' : '手动记录'
		},
		goMeetingHistory() {
			uni.navigateTo({
				url: `/subpackage_meeting/participantRecord/participantRecord?sessionId=${encodeURIComponent(this.sessionId)}&title=${encodeURIComponent(this.title)}`
			})
		}
	}
}
</script>

<style lang="scss" scoped>
$primary: #3B82F6;
$primary-deep: #2563EB;
$primary-light: #EFF6FF;
$success: #22C55E;
$warning: #F59E0B;
$danger: #EF4444;
$text-main: #1F2937;
$text-secondary: #6B7280;
$text-muted: #9CA3AF;
$bg-page: #F8FAFC;
$card-radius: 24rpx;

.meeting-app-page {
	min-height: 100vh;
	background: $bg-page;
	color: $text-main;
}

.share-btn {
	width: 64rpx;
	height: 64rpx;
	display: flex;
	align-items: center;
	justify-content: center;
}

.share-icon {
	width: 36rpx;
	height: 36rpx;
}

.detail-body {
	padding: 24rpx 32rpx 48rpx;
}

/* 头部标题区 */
.detail-header {
	padding: 8rpx 8rpx 32rpx;
}

.detail-title-row {
	display: flex;
	justify-content: space-between;
	align-items: flex-start;
	gap: 20rpx;
	margin-bottom: 24rpx;
}

.detail-title {
	flex: 1;
	font-size: 40rpx;
	font-weight: 800;
	color: $text-main;
	line-height: 1.3;
}

.status-tag {
	flex-shrink: 0;
	height: 44rpx;
	padding: 0 20rpx;
	border-radius: 999rpx;
	background: #F3F4F6;
	color: $text-secondary;
	font-size: 22rpx;
	font-weight: 700;
	display: flex;
	align-items: center;

	&--active {
		background: #DCFCE7;
		color: #16A34A;
	}

	&--idle {
		background: #FEF3C7;
		color: #D97706;
	}

	&--ended {
		background: #F3F4F6;
		color: $text-secondary;
	}
}

.detail-meta {
	display: flex;
	flex-direction: column;
	gap: 16rpx;
}

.meta-item {
	display: flex;
	align-items: center;
	gap: 14rpx;
	font-size: 26rpx;
	color: $text-secondary;
}

.meta-icon {
	width: 32rpx;
	height: 32rpx;
}

/* 通用卡片 */
.section-card {
	margin-bottom: 24rpx;
	padding: 28rpx;
	border-radius: $card-radius;
	background: #fff;
	box-shadow: 0 8rpx 32rpx rgba(15, 23, 42, 0.04);
}

.section-title {
	font-size: 30rpx;
	font-weight: 800;
	color: $text-main;
	margin-bottom: 24rpx;
	display: flex;
	justify-content: space-between;
	align-items: center;
}

.more-link {
	font-size: 24rpx;
	color: $text-muted;
	font-weight: 500;
}

/* 参会人汇总卡片 */
.participant-summary-card {
	padding-bottom: 24rpx;
}

.participant-summary {
	display: flex;
	align-items: center;
	gap: 20rpx;
}

.participant-avatars {
	display: flex;
	align-items: center;
	flex-shrink: 0;
}

.summary-avatar {
	width: 64rpx;
	height: 64rpx;
	border-radius: 50%;
	background: #e5e7eb;
	color: #6b7280;
	display: flex;
	align-items: center;
	justify-content: center;
	font-size: 26rpx;
	font-weight: 700;
	border: 4rpx solid #fff;
	margin-left: -16rpx;
	box-sizing: border-box;

	&:first-child {
		margin-left: 0;
		background: #86c9a8;
		color: #fff;
	}
}

.participant-summary-text {
	flex: 1;
	min-width: 0;
	font-size: 26rpx;
	color: $text-main;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.record-link {
	display: flex;
	align-items: center;
	gap: 6rpx;
	font-size: 24rpx;
	color: #86c9a8;
	font-weight: 700;
	flex-shrink: 0;
}

.record-link-arrow {
	font-size: 22rpx;
}

/* 会议信息行 */
.info-row {
	display: flex;
	align-items: flex-start;
	justify-content: space-between;
	padding: 22rpx 0;
	border-bottom: 1rpx solid #F1F5F9;

	&:last-child {
		border-bottom: none;
		padding-bottom: 0;
	}

	&:first-child {
		padding-top: 0;
	}
}

.info-label {
	width: 140rpx;
	font-size: 26rpx;
	color: $text-secondary;
	flex-shrink: 0;
}

.info-value-wrap {
	flex: 1;
	display: flex;
	align-items: center;
	justify-content: space-between;
	min-width: 0;
}

.info-value {
	font-size: 26rpx;
	color: $text-main;
	font-weight: 600;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
	max-width: 360rpx;

	&.link {
		color: $text-main;
		font-weight: 500;
	}
}

.copy-text {
	font-size: 24rpx;
	color: $primary; // 蓝色
	font-weight: 700;
	flex-shrink: 0;
}

/* 参会人 */
.participant-list {
	display: flex;
	flex-direction: column;
	gap: 24rpx;
}

.participant-item {
	display: flex;
	align-items: center;
	gap: 20rpx;
}

.participant-avatar {
	width: 80rpx;
	height: 80rpx;
	border-radius: 50%;
	background: linear-gradient(135deg, #BFDBFE, #93C5FD);
	color: #fff;
	display: flex;
	align-items: center;
	justify-content: center;
	font-size: 30rpx;
	font-weight: 700;
	flex-shrink: 0;
}

.participant-info {
	flex: 1;
}

.participant-name-row {
	display: flex;
	align-items: center;
	gap: 12rpx;
	margin-bottom: 6rpx;
}

.participant-name {
	font-size: 28rpx;
	font-weight: 700;
	color: $text-main;
}

.host-tag {
	height: 32rpx;
	padding: 0 12rpx;
	border-radius: 6rpx;
	background: #DBEAFE;
	color: $primary;
	font-size: 20rpx;
	font-weight: 700;
	display: flex;
	align-items: center;
}

.participant-status {
	font-size: 24rpx;
	color: $text-muted;
}

/* 入口卡片 */
.entry-card {
	margin-bottom: 24rpx;
	padding: 28rpx;
	border-radius: $card-radius;
	background: #fff;
	box-shadow: 0 8rpx 32rpx rgba(15, 23, 42, 0.04);
	display: flex;
	align-items: center;
	gap: 22rpx;

	&.disabled {
		opacity: 0.7;
	}
}

.entry-icon {
	width: 80rpx;
	height: 80rpx;
	border-radius: 20rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;

	&--ai {
		background: #EDE9FE;
	}

	&--record {
		background: #DBEAFE;
	}
}

.entry-icon__img {
	width: 40rpx;
	height: 40rpx;
}

.entry-content {
	flex: 1;
	min-width: 0;
}

.entry-title {
	display: block;
	font-size: 30rpx;
	font-weight: 700;
	color: $text-main;
	margin-bottom: 8rpx;
}

.entry-subtitle {
	display: block;
	font-size: 24rpx;
	color: $text-secondary;
}

.entry-status {
	height: 40rpx;
	padding: 0 16rpx;
	border-radius: 999rpx;
	background: #F3F4F6;
	color: $text-secondary;
	font-size: 22rpx;
	font-weight: 700;
	display: flex;
	align-items: center;
	flex-shrink: 0;

	&--ready {
		background: #DCFCE7;
		color: #16A34A;
	}

	&--pending {
		background: #FEF3C7;
		color: #D97706;
	}
}

.entry-arrow {
	font-size: 24rpx;
	color: $text-muted;
	margin-left: 8rpx;
}

/* 展开面板 */
.expand-panel {
	margin: -12rpx 0 24rpx;
	padding: 24rpx;
	border-radius: 0 0 $card-radius $card-radius;
	background: #fff;
	box-shadow: 0 8rpx 32rpx rgba(15, 23, 42, 0.04);
	display: flex;
	flex-direction: column;
	gap: 20rpx;
}

.result-block {
	padding: 20rpx;
	border-radius: 16rpx;
	background: #F8FAFC;
}

.block-meta {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-bottom: 12rpx;
}

.result-tag,
.record-tag {
	height: 36rpx;
	padding: 0 14rpx;
	border-radius: 999rpx;
	background: #EDE9FE;
	color: #7C3AED;
	font-size: 20rpx;
	font-weight: 700;
	display: flex;
	align-items: center;
}

.record-tag {
	background: #F3F4F6;
	color: $text-secondary;

	&.transcription {
		background: #DBEAFE;
		color: $primary;
	}
}

.block-time {
	font-size: 20rpx;
	color: $text-muted;
}

.block-text {
	font-size: 25rpx;
	color: $text-main;
	line-height: 1.6;
	white-space: pre-wrap;
	word-break: break-word;
}

/* 底部操作 */
.bottom-actions {
	display: grid;
	grid-template-columns: 1fr 1fr;
	gap: 20rpx;
	margin-top: 16rpx;
}

.action-btn {
	height: 88rpx;
	border-radius: 999rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	font-size: 28rpx;
	font-weight: 700;

	&--primary {
		background: linear-gradient(90deg, $primary, $primary-deep);
		color: #fff;
		box-shadow: 0 12rpx 32rpx rgba(59, 130, 246, 0.28);

		&.disabled {
			opacity: 0.6;
		}
	}

	&--danger {
		background: #FEF2F2;
		color: $danger;
	}
}
</style>
