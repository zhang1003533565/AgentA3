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

			<!-- 会议信息 -->
			<view class="section-card">
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
			<view class="section-card">
				<view class="section-title" @click="openAllMemberPopup">
					<text>参会人 ({{ participants.length }})</text>
					<text v-if="participants.length > 1" class="more-link">查看全部 ></text>
				</view>
				<view class="participant-list">
					<view v-for="(name, index) in displayedParticipants" :key="index" class="participant-item">
						<view class="participant-avatar">{{ name.slice(0, 1) }}</view>
						<view class="participant-info">
							<view class="participant-name-row">
								<text class="participant-name">{{ name }}</text>
								<view v-if="index === 0" class="host-tag">主持人</view>
							</view>
							<text class="participant-status">已参会</text>
						</view>
					</view>
				</view>
			</view>

			<!-- AI 会议纪要 -->
			<view class="entry-card" :class="{ disabled: !hasResults && status !== 'ended' }" @click="onAiCardClick">
				<view class="entry-icon entry-icon--ai">
					<image class="entry-icon__img" src="@/static/icons/line/sparkles.svg" mode="aspectFit" />
				</view>
				<view class="entry-content">
					<text class="entry-title">AI 会议纪要</text>
					<text class="entry-subtitle">{{ aiSubtitle }}</text>
				</view>
				<view class="entry-status" :class="{ 'entry-status--ready': hasResults }">
					<text>{{ hasResults ? '已生成' : '未生成' }}</text>
				</view>
				<text class="entry-arrow">></text>
			</view>

			<!-- AI 结果展开区 -->
			<view v-if="showAiResults && results.length" class="expand-panel">
				<view v-for="item in results" :key="item.id" class="result-block">
					<view class="block-meta">
						<text class="result-tag">{{ agentLabel(item.agentName) }}</text>
						<text class="block-time">{{ formatDateTime(item.createTime) }}</text>
					</view>
					<text class="block-text">{{ item.answer }}</text>
				</view>
			</view>

			<!-- 会议记录 -->
			<view class="entry-card" :class="{ disabled: !hasRecords }" @click="onRecordCardClick">
				<view class="entry-icon entry-icon--record">
					<image class="entry-icon__img" src="@/static/icons/line/clipboard.svg" mode="aspectFit" />
				</view>
				<view class="entry-content">
					<text class="entry-title">会议记录</text>
					<text class="entry-subtitle">{{ recordSubtitle }}</text>
				</view>
				<view class="entry-status" :class="{ 'entry-status--ready': hasRecords }">
					<text>{{ hasRecords ? '已生成' : '未生成' }}</text>
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

		<!-- 全部参会人弹窗 -->
		<view v-if="showMemberPopup" class="popup-mask" @click.self="closeMemberPopup">
			<view class="popup-box">
				<view class="popup-title">全部参会人</view>
				<view class="member-list">
					<view v-for="(name, index) in participants" :key="name" class="member-item">
						<view class="member-avatar">{{ name.slice(0,1) }}</view>
						<text class="member-name">{{ name }}</text>
						<view v-if="index === 0" class="host-tag">主持人</view>
					</view>
				</view>
				<view class="popup-btn-row">
					<view class="confirm-btn" @click="closeMemberPopup">关闭</view>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { deleteMeeting as deleteMeetingApi, getMeetingDetail, organizeMeeting as organizeMeetingApi } from '@/api/ai.js'

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
			records: [],
			results: [],
			organizing: false,
			showMemberPopup: false,
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
			return this.participants.slice(0, 3)
		},
		hasResults() {
			return this.results.length > 0
		},
		hasRecords() {
			return this.records.length > 0
		},
		aiSubtitle() {
			if (this.hasResults) return '查看 AI 生成的会议总结'
			return this.status === 'ended' ? '结束会议后自动生成，也可手动整理' : '会议结束后可生成 AI 纪要'
		},
		recordSubtitle() {
			if (this.hasRecords) return '查看会议转写与记录'
			return '暂无会议记录'
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
			if (!this.hasResults && this.status !== 'ended') return
			if (this.hasResults) {
				this.showAiResults = !this.showAiResults
			} else {
				this.organizeNow()
			}
		},
		onRecordCardClick() {
			if (!this.hasRecords) return
			this.showRecords = !this.showRecords
		},
		async organizeNow() {
			if (!this.sessionId || this.organizing) return
			this.organizing = true
			try {
				const res = await organizeMeetingApi(this.sessionId)
				this.applyDetail(res?.data || {})
				this.showAiResults = true
				uni.showToast({ title: '会议整理完成', icon: 'none' })
			} catch (error) {
				uni.showToast({ title: '整理失败，请检查模型配置', icon: 'none' })
			} finally {
				this.organizing = false
			}
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
		openAllMemberPopup() {
			if (this.participants.length <= 1) return
			this.showMemberPopup = true
		},
		closeMemberPopup() {
			this.showMemberPopup = false
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

/* 弹窗 */
.popup-mask {
	position: fixed;
	top: 0;
	left: 0;
	right: 0;
	bottom: 0;
	background: rgba(0,0,0,0.5);
	display: flex;
	align-items: center;
	justify-content: center;
	z-index: 999;
}

.popup-box {
	width: 660rpx;
	background: #fff;
	border-radius: $card-radius;
	overflow: hidden;
}

.popup-title {
	text-align: center;
	font-size: 30rpx;
	padding: 36rpx 0 20rpx;
	font-weight: 800;
}

.member-list {
	max-height: 60vh;
	padding: 0 40rpx 20rpx;
}

.member-item {
	display: flex;
	align-items: center;
	gap: 24rpx;
	height: 96rpx;
	border-bottom: 1rpx solid #F1F5F9;

	&:last-child {
		border-bottom: none;
	}
}

.member-avatar {
	width: 64rpx;
	height: 64rpx;
	border-radius: 50%;
	background: linear-gradient(135deg, #BFDBFE, #93C5FD);
	color: #fff;
	display: flex;
	align-items: center;
	justify-content: center;
	font-weight: 700;
}

.member-name {
	flex: 1;
	font-size: 28rpx;
	color: $text-main;
}

.popup-btn-row {
	border-top: 1rpx solid #F1F5F9;
}

.confirm-btn {
	height: 96rpx;
	text-align: center;
	line-height: 96rpx;
	font-size: 28rpx;
	color: $primary;
	font-weight: 700;
}
</style>
