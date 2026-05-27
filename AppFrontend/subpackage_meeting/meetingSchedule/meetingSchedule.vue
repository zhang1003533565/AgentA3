<template>
	<view class="meeting-app-page">
		<view class="status-bar"></view>
		<view class="top-bar">
			<text class="page-title">日程</text>
			<view class="top-actions"><view class="calendar-mini">▣</view><view class="round-plus" @click="go('/subpackage_meeting/startMeeting/startMeeting')">+</view></view>
		</view>

		<text class="month-title">2024年 3月</text>
		<view class="week-row">
			<view v-for="day in days" :key="day.num" class="day-cell" :class="{ active: day.num === '20' }">
				<text class="weekday">{{ day.week }}</text>
				<text class="daynum">{{ day.num }}</text>
			</view>
		</view>

		<view class="schedule-list">
			<view class="schedule-item active" @click="openDetail('产品需求评审会', '876 543 210')">
				<view class="time-block"><text>09:30</text><text>10:30</text></view>
				<view class="schedule-main"><text class="schedule-name">产品需求评审会</text><text class="schedule-meta">张三 · 876 543 210</text></view>
				<view class="pill live">进行中</view>
			</view>
			<view class="schedule-item" @click="openDetail('设计方案讨论会', '123 456 789')">
				<view class="time-block"><text>14:00</text><text>15:30</text></view>
				<view class="schedule-main"><text class="schedule-name">设计方案讨论会</text><text class="schedule-meta">李四 · 123 456 789</text></view>
				<view class="pill">加入</view>
			</view>
			<view class="schedule-item" @click="openDetail('团队周会', '987 654 321')">
				<view class="time-block"><text>16:00</text><text>17:00</text></view>
				<view class="schedule-main"><text class="schedule-name">团队周会</text><text class="schedule-meta">王五 · 987 654 321</text></view>
				<view class="pill">加入</view>
			</view>
		</view>

		<view class="bottom-tabs">
			<view class="tab-item" @click="go('/subpackage_meeting/meetingRoom/meetingRoom')"><text class="tab-ico">▰</text><text>会议</text></view>
			<view class="tab-item tab-item--active"><text class="tab-ico">▣</text><text>日程</text></view>
			<view class="tab-item" @click="go('/subpackage_meeting/meetingContacts/meetingContacts')"><text class="tab-ico">♟</text><text>通讯录</text></view>
			<view class="tab-item" @click="go('/subpackage_meeting/meetingMine/meetingMine')"><text class="tab-ico">♙</text><text>我的</text></view>
		</view>
	</view>
</template>

<script>
export default {
	data() {
		return { days: [{ week: '日', num: '17' }, { week: '一', num: '18' }, { week: '二', num: '19' }, { week: '三', num: '20' }, { week: '四', num: '21' }, { week: '五', num: '22' }, { week: '六', num: '23' }] }
	},
	methods: {
		go(url) { uni.navigateTo({ url }) },
		openDetail(title, roomCode) { uni.navigateTo({ url: `/subpackage_meeting/meetingDetail/meetingDetail?title=${encodeURIComponent(title)}&roomCode=${encodeURIComponent(roomCode)}` }) }
	}
}
</script>

<style lang="scss" scoped>
.meeting-app-page { min-height: 100vh; background: #fff; padding: 0 28rpx 150rpx; color: #101820; box-sizing: border-box; }
.status-bar { height: var(--status-bar-height); min-height: 42rpx; }
.top-bar { height: 88rpx; display: flex; justify-content: space-between; align-items: center; }
.page-title { font-size: 34rpx; font-weight: 900; }
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
.schedule-item { min-height: 112rpx; display: grid; grid-template-columns: 92rpx 1fr 82rpx; align-items: center; gap: 10rpx; padding: 20rpx 22rpx; background: #fff; border-bottom: 1rpx solid #f0f2f2; box-sizing: border-box; }
.schedule-item.active { background: linear-gradient(90deg, rgba(229,246,236,.95), #fff); }
.time-block, .schedule-main { display: flex; flex-direction: column; gap: 9rpx; }
.time-block { font-size: 25rpx; color: #1e2930; }
.schedule-name { font-size: 25rpx; font-weight: 850; }
.schedule-meta { color: #68747a; font-size: 22rpx; }
.pill { height: 42rpx; border-radius: 999rpx; background: #eef8f3; color: #1f7f68; display: flex; align-items: center; justify-content: center; font-size: 21rpx; font-weight: 750; }
.pill.live { background: #c8efd9; }
.bottom-tabs { position: fixed; left: 0; right: 0; bottom: 0; height: 112rpx; padding: 10rpx 28rpx calc(env(safe-area-inset-bottom) + 8rpx); background: rgba(255,255,255,.96); box-shadow: 0 -10rpx 35rpx rgba(20,33,40,.08); display: grid; grid-template-columns: repeat(4,1fr); box-sizing: content-box; }
.tab-item { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 5rpx; color: #7c868b; font-size: 21rpx; }
.tab-item--active { color: #147a64; font-weight: 800; }
.tab-ico { font-size: 32rpx; }
</style>
