<template>
	<view class="schedule-page">
		<view class="status-bar"></view>
		<view class="schedule-header">
			<view class="header-back" @click="uni.navigateBack()">
				<text>〈</text>
			</view>
			<text class="header-title">日程</text>
			<view class="header-actions">
				<view class="icon-add" @click="goCreateMeeting">+</view>
			</view>
		</view>

		<view class="calendar-wrap">
			<view class="year-month-row">
				<view class="month-btn" @click="prevMonth">‹</view>
				<text class="year-month-text" @click="openYearMonthPopup">{{ currentYear }}年{{ currentMonth }}月</text>
				<view class="month-btn" @click="nextMonth">›</view>
			</view>
			<scroll-view scroll-x class="date-scroll" scroll-with-animation :scroll-into-view="scrollTargetId">
				<view class="date-list">
					<view
						v-for="item in monthAllDateList"
						:key="item.fullDate"
						:id="'date-' + item.fullDate"
						class="date-item"
						:class="{ 'date-active': item.fullDate === selectDate }"
						@click="selectDay(item)"
					>
						<text class="week-text">{{ item.week }}</text>
						<text class="day-text">{{ item.day }}</text>
					</view>
				</view>
			</scroll-view>
		</view>

		<scroll-view class="meeting-scroll" scroll-y>
			<view v-if="loading" class="empty-tip">加载中...</view>
			<view v-else-if="filteredMeetingList.length === 0" class="empty-tip">当日暂无会议日程</view>
			<view v-else class="meeting-list">
				<view v-for="item in filteredMeetingList" :key="item.sessionId" class="meeting-card">
					<view class="meeting-time">{{ formatTime(item.scheduledStartTime) }}</view>
					<view class="meeting-info">
						<text class="meeting-name">{{ item.title }}</text>
						<view class="meeting-sub">
							<text class="status-text" :class="getStatusClass(item.status)">{{ getStatusText(item.status) }}</text>
							<text> · 主持人：{{ getHostName(item) }} · {{ formatRoomCode(item.roomCode) }}</text>
						</view>
					</view>
					<view class="detail-btn" @click="goMeetingDetail(item)">详情</view>
				</view>
			</view>
		</scroll-view>

		<!-- 年月简易弹窗 -->
		<view v-if="showPopup" class="popup-mask" @click.self="showPopup=false">
			<view class="popup-box">
				<view class="popup-title">选择年月</view>
				<view class="ym-select-row">
					<view class="ym-btn" @click="tempYear--">‹</view>
					<text class="ym-text">{{ tempYear }}年</text>
					<view class="ym-btn" @click="tempYear++">›</view>

					<view class="ym-btn" @click="tempMonth--">‹</view>
					<text class="ym-text">{{ tempMonth }}月</text>
					<view class="ym-btn" @click="tempMonth++">›</view>
				</view>
				<view class="popup-btn-row">
					<view class="btn-cancel" @click="showPopup=false">取消</view>
					<view class="btn-confirm" @click="confirmSelectMonth">确定</view>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
import { getMeetings } from '@/api/ai.js'
export default {
	data() {
		return {
			currentYear: 2026,
			currentMonth: 7,
			selectDate: '',
			monthAllDateList: [],
			scrollTargetId: '',
			loading: false,
			allMeetingList: [], // 接口拉取全部会议（待开始、进行中、已结束）
			showPopup: false,
			tempYear: 2026,
			tempMonth: 7
		}
	},
	computed: {
		filteredMeetingList() {
			// 根据选中日期，筛选当天所有会议，依靠举办时间scheduledStartTime匹配
			return this.allMeetingList.filter(row => {
				if (!row.scheduledStartTime) return false
				const dayStr = row.scheduledStartTime.split('T')[0]
				return dayStr === this.selectDate
			})
		}
	},
	onLoad() {
		this.initPage()
	},
	onShow() {
		// 每次切回页面重新拉取最新会议，和历史会议逻辑保持一致【核心实时刷新】
		this.initPage()
	},
	methods: {
		async initPage() {
			await this.loadAllMeetings()
			this.renderMonth()
		},
		// 和历史会议共用同一个接口，拉取全部会议
		async loadAllMeetings() {
			this.loading = true
			try {
				const res = await getMeetings({ pageNum: 1, pageSize: 999 })
				this.allMeetingList = res?.data?.records || []
			} catch (err) {
				this.allMeetingList = []
			} finally {
				this.loading = false
			}
		},
		renderMonth() {
			const weekMap = ['日', '一', '二', '三', '四', '五', '六']
			this.monthAllDateList = []
			const y = this.currentYear
			const m = this.currentMonth
			const lastDay = new Date(y, m, 0).getDate()
			for (let d = 1; d <= lastDay; d++) {
				const fullDate = `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`
				const dateObj = new Date(y, m - 1, d)
				const week = weekMap[dateObj.getDay()]
				this.monthAllDateList.push({
					fullDate,
					week,
					day: d
				})
			}

			const now = new Date()
			const todayY = now.getFullYear()
			const todayM = now.getMonth() + 1
			const todayD = now.getDate()
			const todayStr = `${todayY}-${String(todayM).padStart(2, '0')}-${String(todayD).padStart(2, '0')}`

			if (todayY === y && todayM === m) {
				this.selectDate = todayStr
			} else {
				this.selectDate = `${y}-${String(m).padStart(2, '0')}-01`
			}

			this.$nextTick(() => {
				setTimeout(() => {
					this.scrollTargetId = 'date-' + this.selectDate
				}, 180)
			})
		},
		selectDay(item) {
			this.selectDate = item.fullDate
		},
		prevMonth() {
			let y = this.currentYear
			let m = this.currentMonth - 1
			if (m < 1) {
				m = 12
				y -= 1
			}
			this.currentYear = y
			this.currentMonth = m
			this.renderMonth()
		},
		nextMonth() {
			let y = this.currentYear
			let m = this.currentMonth + 1
			if (m > 12) {
				m = 1
				y += 1
			}
			this.currentYear = y
			this.currentMonth = m
			this.renderMonth()
		},
		openYearMonthPopup() {
			this.tempYear = this.currentYear
			this.tempMonth = this.currentMonth
			this.showPopup = true
		},
		confirmSelectMonth() {
			if (this.tempMonth < 1) this.tempMonth = 1
			if (this.tempMonth > 12) this.tempMonth = 12
			this.currentYear = this.tempYear
			this.currentMonth = this.tempMonth
			this.showPopup = false
			this.$nextTick(() => {
				this.renderMonth()
			})
		},
		goCreateMeeting() {
			uni.navigateTo({
				url: '/subpackage_meeting/reserveMeeting/reserveMeeting'
			})
		},
		// 【已修改】使用redirectTo，替换当前页面，不新增路由栈
		goMeetingDetail(item) {
			uni.redirectTo({
				url: `/subpackage_meeting/meetingDetail/meetingDetail?sessionId=${encodeURIComponent(item.sessionId)}&title=${encodeURIComponent(item.title)}&roomCode=${encodeURIComponent(item.roomCode)}`
			})
		},
		// 格式化时间，只展示时分
		formatTime(timeStr) {
			if (!timeStr) return ''
			return timeStr.split('T')[1].substring(0, 5)
		},
		formatRoomCode(code) {
			if (!code) return ''
			return code.replace(/(.{3})/g, '$1 ').trim()
		},
		getHostName(item) {
			if (Array.isArray(item.participants) && item.participants.length > 0) {
				return item.participants[0]
			}
			if (item.hostName) return item.hostName
			return '未知'
		},
		getStatusText(status) {
			if (status === 'pending' || status === '待开始') return '待开始'
			if (status === 'ongoing' || status === '进行中') return '进行中'
			return '已结束'
		},
		getStatusClass(status) {
			if (status === 'pending' || status === '待开始' || status === 'ongoing' || status === '进行中') return 'wait'
			return 'end'
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

.schedule-page {
	min-height: 100vh;
	background: #ffffff;
	color: $text-dark;
}
.status-bar {
	height: var(--status-bar-height);
	min-height: 42rpx;
}
.schedule-header {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 0 22rpx;
	height: 92rpx;
	.header-back {
		font-size: 36rpx;
		width: 60rpx;
	}
	.header-title {
		font-size: 32rpx;
		font-weight: bold;
	}
	.header-actions {
		display: flex;
		gap: 24rpx;
		align-items: center;
		.icon-add {
			width: 42rpx;
			height: 42rpx;
			border-radius: 50%;
			background: $main-color;
			color: #fff;
			text-align: center;
			line-height: 42rpx;
			font-size: 32rpx;
			display: flex;
			align-items: center;
			justify-content: center;
		}
	}
}

.calendar-wrap {
	padding: 0 22rpx;
	.year-month-row {
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 30rpx;
		margin-bottom: 16rpx;
		.month-btn {
			font-size: 32rpx;
			color: #666;
			padding: 4rpx 12rpx;
		}
		.year-month-text {
			font-size: 28rpx;
			font-weight: 500;
		}
	}
	.date-scroll {
		width: calc(84rpx * 7 + 12rpx * 6);
	}
	.date-list {
		display: flex;
		gap: 12rpx;
		.date-item {
			width: 84rpx;
			height: 100rpx;
			border-radius: 16rpx;
			background: #f7f7f7;
			display: flex;
			flex-direction: column;
			align-items: center;
			justify-content: center;
			flex-shrink: 0;
			.week-text {
				font-size: 22rpx;
				color: $text-gray;
			}
			.day-text {
				font-size: 28rpx;
				margin-top: 6rpx;
			}
		}
		.date-active {
			background: $main-color;
			color: #fff;
			.week-text {
				color: #fff;
			}
		}
	}
}

.meeting-scroll {
	margin-top: 24rpx;
	padding: 0 22rpx;
	height: calc(100vh - 440rpx);
	.empty-tip {
		text-align: center;
		margin-top: 120rpx;
		color: #999;
		font-size: 26rpx;
	}
	.meeting-list {
		display: flex;
		flex-direction: column;
		gap: 16rpx;
		.meeting-card {
			background: #f7f7f7;
			border-radius: 20rpx;
			padding: 24rpx;
			display: flex;
			align-items: flex-start;
			gap: 20rpx;
			.meeting-time {
				font-size: 26rpx;
				min-width: 96rpx;
			}
			.meeting-info {
				flex: 1;
				.meeting-name {
					font-size: 28rpx;
					font-weight: bold;
					display: block;
					margin-bottom: 8rpx;
				}
				.meeting-sub {
					font-size: 22rpx;
					color: $text-gray;
					.wait {
						color: $main-color;
					}
					.end {
						color: $text-end;
					}
				}
			}
			.detail-btn {
				padding: 6rpx 18rpx;
				border-radius: 20rpx;
				background: $main-light;
				color: $main-color;
				font-size: 24rpx;
			}
		}
	}
}

/* 年月弹窗 */
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
	width: 620rpx;
	background: #fff;
	border-radius: 24rpx;
	overflow: hidden;
}
.popup-title {
	text-align: center;
	font-size: 30rpx;
	padding: 30rpx 0;
	font-weight: bold;
}
.ym-select-row {
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 20rpx;
	padding: 20rpx 0 40rpx;
	.ym-btn {
		width: 60rpx;
		height: 60rpx;
		line-height: 60rpx;
		text-align: center;
		font-size: 32rpx;
		background: #f4f4f4;
		border-radius: 50%;
	}
	.ym-text {
		font-size: 30rpx;
		min-width: 140rpx;
		text-align: center;
	}
}
.popup-btn-row {
	display: flex;
	border-top: 1rpx solid #eee;
	.btn-cancel,.btn-confirm {
		flex:1;
		text-align:center;
		line-height: 90rpx;
		font-size:28rpx;
	}
	.btn-cancel {
		color:#666;
		border-right:1rpx solid #eee;
	}
	.btn-confirm {
		color:$main-color;
	}
}
</style>