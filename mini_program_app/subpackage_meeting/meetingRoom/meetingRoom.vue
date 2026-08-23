<template>
	<view class="meeting-app-page">
		<nav-bar title="会议" :showBack="true" fixed placeholder :border="false" background="#FFFFFF" />

		<scroll-view class="meeting-scroll" scroll-y>
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

				<!-- 即将开始 -->
				<view class="section">
					<view class="section-header">
						<text class="section-title">即将开始</text>
						<text class="section-date">{{ todayLabel }}</text>
					</view>
					<view v-if="loading" class="section-empty">正在加载...</view>
					<view v-else-if="upcomingMeetings.length === 0" class="section-empty">暂无即将开始的会议</view>
					<view
						v-for="meeting in upcomingMeetings"
						v-else
						:key="meeting.sessionId"
						class="meeting-item"
						@click="openDetail(meeting)"
					>
						<view class="meeting-info">
							<view class="meeting-title-row">
								<view class="status-dot" :class="`status-dot--${meeting.status}`" />
								<text class="meeting-name">{{ meeting.title || '未命名会议' }}</text>
							</view>
							<view class="meeting-meta">
								<image class="meta-icon" src="@/static/icons/line/clock.svg" mode="aspectFit" />
								<text>{{ meetingTime(meeting) }}</text>
							</view>
							<view class="meeting-meta">
								<image class="meta-icon" src="@/static/icons/line/id-card.svg" mode="aspectFit" />
								<text>会议号 {{ formatRoomCode(meeting.roomCode) }}</text>
							</view>
						</view>
						<!-- 右侧操作区 - 固定宽度保证对齐 -->
						<view class="action-side">
							<!-- 统一容器，所有内容在这个高度内靠右排列 -->
							<view class="action-content">
								<template v-if="meeting.status === 'active'">
									<!-- 状态标签 -->
									<view class="status-tag">进行中</view>
									<!-- 进入按钮 -->
									<view
										class="action-btn action-btn--primary"
										@click.stop="enterMeeting(meeting)"
									>
										<text>进入</text>
									</view>
								</template>
								<template v-else>
									<text class="action-text action-text--pending">待开始</text>
									<!-- 仅主持人可见：开始会议 -->
									<view
										v-if="isHost(meeting)"
										class="action-btn action-btn--pending"
										@click.stop="enterMeeting(meeting)"
									>
										<text>开始</text>
									</view>
								</template>
							</view>
						</view>
					</view>
				</view>

				<!-- 历史记录 -->
				<view class="section">
					<view class="section-title">历史记录</view>
					<view v-if="historyMeetings.length === 0" class="section-empty">暂无历史记录</view>
					<view
						v-for="meeting in historyMeetings"
						v-else
						:key="meeting.sessionId"
						class="meeting-item"
						@click="openDetail(meeting)"
					>
						<view class="meeting-info">
							<view class="meeting-title-row">
								<view class="status-dot status-dot--ended" />
								<text class="meeting-name">{{ meeting.title || '未命名会议' }}</text>
							</view>
							<view class="meeting-meta">
								<image class="meta-icon" src="@/static/icons/line/clock.svg" mode="aspectFit" />
								<text>{{ historyTime(meeting) }}</text>
							</view>
						</view>
						<!-- 右侧操作区 - 固定宽度保证对齐 -->
						<view class="action-side">
							<!-- 统一容器，所有内容在这个高度内居中 -->
							<view class="action-content">
								<text class="action-text action-text--ended">已结束</text>
							</view>
						</view>
					</view>
				</view>
			</view>
		</scroll-view>
	</view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getMeetings, startMeeting } from '@/api/ai.js'
import { getToken, getUserInfo } from '@/utils/storage.js'

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
		upcomingMeetings() {
			return this.meetings.filter(item => item.status !== 'ended')
		},
		historyMeetings() {
			return this.meetings.filter(item => item.status === 'ended')
		},
		todayLabel() {
			const date = new Date()
			const weekdays = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']
			return `${date.getMonth() + 1}月${date.getDate()}日 ${weekdays[date.getDay()]}`
		}
	},
	methods: {
		go(url) {
			uni.navigateTo({ url })
		},
		async loadMeetings() {
			this.loading = true
			try {
				const res = await getMeetings({ pageNum: 1, pageSize: 50 })
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
		// 仅主持人可见开始按钮：列表项 creatorId 与当前用户 id 一致即为主持人
		isHost(meeting) {
			const currentId = this.getCurrentUserId()
			return !!currentId && meeting?.creatorId != null && String(meeting.creatorId) === currentId
		},
		// 登录响应未返回用户 id：优先读本地缓存，缺失时解析 JWT 中的 userId（与 lostfound 模块同款实现）
		getCurrentUserId() {
			const user = getUserInfo()
			const storedId = user?.id || user?.userId
			if (storedId !== undefined && storedId !== null && storedId !== '') return String(storedId)
			const token = getToken()
			const payload = token ? token.split('.')[1] : ''
			if (!payload) return ''
			try {
				const decoded = JSON.parse(this.decodeBase64Url(payload))
				return decoded?.userId != null ? String(decoded.userId) : ''
			} catch (error) {
				return ''
			}
		},
		decodeBase64Url(value) {
			const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/='
			const input = `${value || ''}`.replace(/-/g, '+').replace(/_/g, '/')
			let output = ''
			let buffer = 0
			let bits = 0
			for (let i = 0; i < input.length; i += 1) {
				const char = input.charAt(i)
				if (char === '=') break
				const index = chars.indexOf(char)
				if (index < 0) continue
				buffer = (buffer << 6) | index
				bits += 6
				if (bits >= 8) {
					bits -= 8
					output += String.fromCharCode((buffer >> bits) & 0xff)
				}
			}
			return output
		},
		formatRoomCode(roomCode) {
			const code = roomCode || '未生成'
			return code.replace(/(.{3})/g, '$1 ').trim()
		},
		formatTime(source) {
			if (!source) return ''
			const date = new Date(source)
			if (Number.isNaN(date.getTime())) return ''
			const hh = String(date.getHours()).padStart(2, '0')
			const mm = String(date.getMinutes()).padStart(2, '0')
			return `${hh}:${mm}`
		},
		meetingTime(meeting) {
			const source = meeting.scheduledStartTime || meeting.startTime
			const time = this.formatTime(source)
			if (!time) return '时间待定'
			return `${time} 开始`
		},
		historyTime(meeting) {
			const source = meeting.endTime || meeting.startTime || meeting.createTime
			if (!source) return ''
			const date = new Date(source)
			if (Number.isNaN(date.getTime())) return ''
			const now = new Date()
			const isYesterday = date.getDate() === now.getDate() - 1 && date.getMonth() === now.getMonth() && date.getFullYear() === now.getFullYear()
			const hh = String(date.getHours()).padStart(2, '0')
			const mm = String(date.getMinutes()).padStart(2, '0')
			if (isYesterday) return `昨天 ${hh}:${mm}`
			return `${date.getMonth() + 1}月${date.getDate()}日 ${hh}:${mm}`
		}
	}
}
</script>

<style lang="scss" scoped>
$primary: #3B82F6;
$primary-deep: #2563EB;
$primary-light: #EFF6FF;
$success: #22C55E;
$text-main: #1F2937;
$text-secondary: #6B7280;
$text-muted: #9CA3AF;
$bg-page: #F8FAFC;
$card-radius: 24rpx;

.meeting-app-page {
	height: 100vh;
	background: $bg-page;
	color: $text-main;
}

.meeting-scroll {
	height: calc(100vh - var(--window-top, 0px));
}

.meeting-body {
	padding: 24rpx 32rpx 48rpx;
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

/* 分区 */
.section {
	margin-bottom: 40rpx;
}

.section-header {
	display: flex;
	align-items: baseline;
	gap: 16rpx;
	margin-bottom: 24rpx;
}

.section-title {
	font-size: 34rpx;
	font-weight: 800;
	color: $text-main;
}

.section-date {
	font-size: 24rpx;
	font-weight: 500;
	color: $text-secondary;
}

.section-empty {
	padding: 48rpx 0;
	text-align: center;
	font-size: 26rpx;
	color: $text-muted;
	background: #fff;
	border-radius: $card-radius;
}

/* 会议列表项 */
.meeting-item {
	display: flex;
	align-items: center; /* 【关键】整体垂直居中 - 左侧信息和右侧状态在卡片内垂直居中对齐 */
	gap: 18rpx;
	padding: 28rpx;
	margin-bottom: 20rpx;
	background: #fff;
	border-radius: $card-radius;
	box-shadow: 0 8rpx 28rpx rgba(15, 23, 42, 0.04);
}

.meeting-title-row {
	display: flex;
	align-items: center;
	gap: 14rpx;
	margin-bottom: 14rpx;
}

.status-dot {
	width: 16rpx;
	height: 16rpx;
	border-radius: 50%;
	background: $text-muted;
	flex-shrink: 0;

	&--active {
		background: $success;
		box-shadow: 0 0 0 6rpx rgba(34, 197, 94, 0.12);
	}

	&--idle,
	&--reserved {
		background: $primary;
	}

	&--ended {
		background: $text-muted;
	}
}

.meeting-info {
	flex: 1;
	min-width: 0;
}

.meeting-name {
	font-size: 30rpx;
	font-weight: 700;
	color: $text-main;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.meeting-meta {
	display: flex;
	align-items: center;
	gap: 10rpx;
	padding-left: 30rpx;
	font-size: 24rpx;
	color: $text-secondary;
	margin-bottom: 8rpx;

	&:last-child {
		margin-bottom: 0;
	}
}

.meta-icon {
	width: 24rpx;
	height: 24rpx;
}

/* 右侧操作区 - 固定宽度保证所有卡片对齐 */
.action-side {
	display: flex;
	flex-direction: column;
	align-items: center; /* 【关键】水平居中 */
	justify-content: center; /* 垂直居中 */
	width: 140rpx; /* 【关键】固定宽度，确保所有卡片右侧对齐在同一竖列 */
	flex-shrink: 0; /* 【关键】防止被压缩 */
}

/* 右侧内容的统一容器 - 控制内部元素的对齐 */
.action-content {
	display: flex;
	flex-direction: column;
	align-items: center; /* 【关键】内容水平居中（与图 2 一致）*/
	justify-content: flex-start; /* 【关键】内容从顶部开始排列 */
	gap: 16rpx; /* 【关键】增大间距，与图 2 更接近 */
}

/* "进行中" 状态标签 - 与 "待开始" 字体大小一致 */
.status-tag {
	font-size: 24rpx; /* 【关键】与 "待开始" 一致的字体大小 */
	font-weight: 400; /* 【关键】使用常规字重，不要太粗 */
	color: #16A34A; /* 绿色 */
	line-height: 1;
	margin-bottom: 8rpx; /* 【关键】给状态标签底部留出间距 */
}

.action-status {
	font-size: 24rpx; /* 保持与 status-tag 一致 */
	font-weight: 400;
	color: #16A34A;
}

.action-btn {
	height: 50rpx;
	padding: 0 24rpx; /* 固定 padding，避免过大 */
	border-radius: 12rpx;
	border: 2rpx solid #16A34A;
	background: transparent;
	display: flex;
	align-items: center;
	justify-content: center;
	font-size: 26rpx;
	font-weight: 400; /* 【关键】降低字重，更加克制 */
	color: #16A34A;
	min-width: 96rpx; /* 【关键】设置最小宽度，但不会无限扩大 */
	box-sizing: border-box; /* 【关键】包含 padding 在内的总宽度 */
	width: 100%; /* 【关键】按钮占满容器宽度 */
}

/* "开始" 按钮 - 边框与文字同“待开始”蓝色（仅主持人可见） */
.action-btn--pending {
	border-color: $primary;
	color: $primary;
}

.action-text {
	font-size: 24rpx;
	font-weight: 600;
	line-height: 1; /* 【关键】消除行高影响，保证垂直居中 */

	&--pending {
		color: $primary;
	}

	&--ended {
		color: $text-muted;
	}
}
</style>
