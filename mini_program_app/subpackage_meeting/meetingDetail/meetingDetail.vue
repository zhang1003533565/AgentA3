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
				<!-- 绑定点击事件 -->
				<text v-if="participants.length > 1" class="all-link" @click="openAllMemberPopup">查看全部</text>
			</view>
			<view class="avatar-row">
				<view v-for="(item, index) in avatars" :key="`${item}-${index}`" class="mini-avatar">{{ item }}</view>
			</view>
		</view>

		<view class="section-card">
			<view class="section-head">
				<view>
					<text class="section-title">AI会后整理</text>
					<text class="section-subtitle">{{ results.length ? `${results.length}份整理结果` : '结束会议后自动生成，也可手动整理' }}</text>
				</view>
				<view class="section-button" :class="{ disabled: organizing }" @click="organizeNow">{{ organizing ? '整理中' : 'AI整理' }}</view>
			</view>
			<view v-if="results.length" class="result-list">
				<view v-for="item in results" :key="item.id" class="result-block">
					<view class="block-meta">
						<text class="result-tag">{{ agentLabel(item.agentName) }}</text>
						<text>{{ formatDateTime(item.createTime) }}</text>
					</view>
					<text class="block-text">{{ item.answer }}</text>
				</view>
			</view>
			<view v-else class="empty-block">暂无AI整理结果。会议结束后会自动生成，如果模型刚配置好，可以点右上角手动整理。</view>
		</view>

		<view class="section-card">
			<view class="section-head">
				<view>
					<text class="section-title">会议记录</text>
					<text class="section-subtitle">{{ records.length ? `${records.length}条记录` : '暂无记录' }}</text>
				</view>
			</view>
			<view v-if="records.length" class="record-list">
				<view v-for="item in records" :key="item.id" class="result-block">
					<view class="block-meta">
						<text class="record-tag" :class="{ transcription: item.source === 'transcription' }">{{ sourceLabel(item.source) }}</text>
						<text>{{ formatDateTime(item.createTime) }}</text>
					</view>
					<text class="block-text">{{ item.content }}</text>
				</view>
			</view>
			<view v-else class="empty-block">会议现场的实时转写或手动记录会沉淀在这里。</view>
		</view>

		<view class="action-bar">
			<view class="detail-action green" @click="shareMeeting"><text>⇧</text><text>分享会议</text></view>
			<view class="detail-action" @click="goSchedule"><text>▣</text><text>会议日程</text></view>
			<view class="detail-action" @click="organizeNow"><text>✦</text><text>AI整理</text></view>
			<view class="detail-action red" @click="deleteCurrentMeeting"><text>⌫</text><text>删除会议</text></view>
		</view>

		<!-- 全部参会人弹窗 -->
		<view v-if="showMemberPopup" class="popup-mask" @click.self="closeMemberPopup">
			<view class="popup-box">
				<view class="popup-title">全部参会人</view>
				<view class="member-list">
					<view v-for="name in participants" :key="name" class="member-item">
						<view class="member-avatar">{{ name.slice(0,1) }}</view>
						<text>{{ name }}</text>
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
import { deleteMeeting as deleteMeetingApi, getMeetingDetail, organizeMeeting as organizeMeetingApi } from '@/api/ai.js'

export default {
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
			showMemberPopup: false // 参会人弹窗开关
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
		// 【已修改】使用 redirectTo，替换当前页面，不新增路由栈
		goSchedule() {
			uni.redirectTo({
				url: '/subpackage_meeting/meetingSchedule/meetingSchedule'
			})
		},
		async organizeNow() {
			if (!this.sessionId || this.organizing) return
			this.organizing = true
			try {
				const res = await organizeMeetingApi(this.sessionId)
				this.applyDetail(res?.data || {})
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
		// 打开全部参会人弹窗
		openAllMemberPopup() {
			this.showMemberPopup = true
		},
		closeMemberPopup() {
			this.showMemberPopup = false
		}
	}
}
</script>

<style lang="scss" scoped>
$main-color: #86C9A8;
$main-light: rgba(134, 201, 168, 0.12);
$text-dark: #151f25;
$text-gray: #666;
$text-end: #999;

.meeting-app-page {
	min-height: 100vh;
	background: #fff;
	color: $text-dark;
}
.status-bar {
	height: var(--status-bar-height);
	min-height: 42rpx;
}
.top-bar {
	height: 88rpx;
	display: grid;
	grid-template-columns: 70rpx 1fr 70rpx;
	align-items: center;
	padding: 0 24rpx;
}
.back {
	font-size: 48rpx;
	line-height: 1;
}
.nav-title {
	text-align: center;
	font-size: 29rpx;
	font-weight: 850;
}
.detail-card {
	margin: 0 24rpx;
	margin-top: 26rpx;
	padding: 30rpx;
	border-radius: 24rpx;
	background: #fff;
	box-shadow: 0 18rpx 54rpx rgba(31,42,48,.08);
}
.detail-head {
	display: flex;
	justify-content: space-between;
	align-items: flex-start;
	margin-bottom: 30rpx;
}
.detail-title {
	font-size: 34rpx;
	font-weight: 900;
	color: #0f1a20;
}
.pill {
	height: 38rpx;
	padding: 0 18rpx;
	border-radius: 999rpx;
	background: #E8F8F2;
	color: #57A77D;
	font-size: 20rpx;
	display: flex;
	align-items: center;
}
.info-row {
	height: 62rpx;
	display: grid;
	grid-template-columns: 38rpx 100rpx 1fr 32rpx;
	align-items: center;
	color: #2e3b42;
	font-size: 24rpx;
}
.info-icon {
	color: #6b767c;
}
.copy {
	color: #9aa3a8;
}
.link-row {
	min-height: 92rpx;
	display: grid;
	grid-template-columns: 38rpx 1fr 32rpx 32rpx;
	gap: 8rpx;
	align-items: center;
	color: #2e3b42;
	font-size: 24rpx;
}
.link-copy {
	display: flex;
	flex-direction: column;
	gap: 8rpx;
}
.url {
	color: #5c6470;
	text-decoration: underline;
	font-size: 23rpx;
}
.member-head {
	margin-top: 28rpx;
	display: flex;
	justify-content: space-between;
	font-size: 25rpx;
}
.all-link {
	color: #8d989e;
	font-size: 23rpx;
}
.avatar-row {
	margin-top: 18rpx;
	display: flex;
	gap: 18rpx;
}
.mini-avatar {
	width: 48rpx;
	height: 48rpx;
	border-radius: 50%;
	background: #E8F8F2;
	display: flex;
	align-items: center;
	justify-content: center;
	font-size: 19rpx;
	color: #57A77D;
	font-weight: 850;
}
.section-card {
	margin: 0 24rpx;
	margin-top: 24rpx;
	padding: 28rpx;
	border-radius: 24rpx;
	background: #fff;
	box-shadow: 0 18rpx 54rpx rgba(31,42,48,.08);
}
.section-head {
	display: flex;
	align-items: flex-start;
	justify-content: space-between;
	gap: 20rpx;
	margin-bottom: 22rpx;
}
.section-title {
	display: block;
	color: #0f1a20;
	font-size: 28rpx;
	font-weight: 900;
}
.section-subtitle {
	display: block;
	margin-top: 8rpx;
	color: #8b969b;
	font-size: 22rpx;
}
.section-button {
	min-width: 112rpx;
	height: 48rpx;
	padding: 0 18rpx;
	border-radius: 999rpx;
	background: #86C9A8;
	color: #fff;
	display: flex;
	align-items: center;
	justify-content: center;
	font-size: 22rpx;
	font-weight: 850;
}
.section-button.disabled {
	opacity: .58;
}
.result-list, .record-list {
	display: flex;
	flex-direction: column;
	gap: 18rpx;
}
.result-block {
	padding: 20rpx;
	border-radius: 18rpx;
	background: #f8faf9;
	border: 1rpx solid #edf2ef;
}
.block-meta {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 18rpx;
	margin-bottom: 14rpx;
	color: #8b969b;
	font-size: 20rpx;
}
.result-tag, .record-tag {
	max-width: 220rpx;
	height: 34rpx;
	padding: 0 14rpx;
	border-radius: 999rpx;
	background: #E8F8F2;
	color: #57A77D;
	display: flex;
	align-items: center;
	font-size: 20rpx;
	font-weight: 850;
}
.record-tag {
	background: #f0f2f4;
	color: #59646b;
}
.record-tag.transcription {
	background: #e8f1ff;
	color: #2869c6;
}
.block-text {
	display: block;
	color: #263239;
	font-size: 23rpx;
	line-height: 1.62;
	white-space: pre-wrap;
	word-break: break-word;
}
.empty-block {
	padding: 28rpx 20rpx;
	border-radius: 18rpx;
	background: #f8faf9;
	color: #7d898f;
	font-size: 23rpx;
	line-height: 1.55;
}
.action-bar {
	position: fixed;
	left: 24rpx;
	right: 24rpx;
	bottom: calc(env(safe-area-inset-bottom) + 20rpx);
	min-height: 110rpx;
	border-radius: 24rpx;
	background: rgba(255,255,255,.96);
	box-shadow: 0 -8rpx 36rpx rgba(31,42,48,.08);
	display: grid;
	grid-template-columns: repeat(4, 1fr);
	align-items: center;
}
.detail-action {
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: 8rpx;
	color: #2a353b;
	font-size: 22rpx;
}
.green {
	color: #57A77D;
}
.red {
	color: #ef3d34;
}

/* 参会人弹窗样式 */
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
	border-radius: 24rpx;
	overflow: hidden;
}
.popup-title {
	text-align: center;
	font-size: 30rpx;
	padding: 36rpx 0 20rpx;
	font-weight: bold;
}
.member-list {
	max-height: 60vh;
	padding: 0 40rpx 20rpx;
}
.member-item {
	display: flex;
	align-items: center;
	gap: 24rpx;
	height: 88rpx;
	border-bottom: 1rpx solid #f0f0f0;
	font-size: 26rpx;
}
.member-avatar {
	width: 64rpx;
	height: 64rpx;
	border-radius: 50%;
	background: #E8F8F2;
	color: #57A77D;
	display: flex;
	align-items: center;
	justify-content: center;
	font-weight: bold;
}
.popup-btn-row {
	border-top: 1rpx solid #eee;
}
.confirm-btn {
	height: 96rpx;
	text-align: center;
	line-height: 96rpx;
	font-size:28rpx;
	color:$main-color;
}
</style>