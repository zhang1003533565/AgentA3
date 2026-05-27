<template>
	<view class="meeting-app-page">
		<view class="status-bar"></view>
		<view class="top-bar top-bar--home">
			<text class="page-title">会议</text>
			<view class="top-actions">
				<view class="scan-icon"></view>
				<view class="round-plus" @click="go('/subpackage_meeting/startMeeting/startMeeting')">+</view>
			</view>
		</view>

		<view class="home-hero">
			<view class="hero-close">×</view>
			<view class="hero-copy">
				<text class="hero-title">高效会议\n从这里开始</text>
				<text class="hero-desc">智能协作 · 实时共享</text>
			</view>
			<view class="hero-scene">
				<view class="board"></view>
				<view class="chair chair--one"></view>
				<view class="chair chair--two"></view>
				<view class="chair chair--three"></view>
				<view class="table"></view>
			</view>
		</view>

		<view class="quick-row">
			<view class="quick-item" @click="go('/subpackage_meeting/startMeeting/startMeeting')">
				<view class="quick-icon">▶</view>
				<text>发起会议</text>
			</view>
			<view class="quick-item" @click="go('/subpackage_meeting/joinMeeting/joinMeeting')">
				<view class="quick-icon">+</view>
				<text>加入会议</text>
			</view>
			<view class="quick-item" @click="go('/subpackage_meeting/meetingSchedule/meetingSchedule')">
				<view class="quick-icon">▣</view>
				<text>预约会议</text>
			</view>
		</view>

		<view class="date-line">
			<text>今天 · 3月20日 星期三</text>
		</view>

		<view class="meeting-list">
			<view class="meeting-item meeting-item--active" @click="openDetail('产品需求评审会', '876 543 210')">
				<view>
					<text class="meeting-name">产品需求评审会</text>
					<text class="meeting-time">09:30 - 11:00</text>
					<text class="meeting-code">会议号：876 543 210</text>
				</view>
				<view class="pill pill--live">进行中</view>
			</view>
			<view class="meeting-item" @click="openDetail('设计方案讨论会', '123 456 789')">
				<view>
					<text class="meeting-name">设计方案讨论会</text>
					<text class="meeting-time">14:00 - 15:30</text>
					<text class="meeting-code">会议号：123 456 789</text>
				</view>
				<view class="pill" @click.stop="go('/subpackage_meeting/joinMeeting/joinMeeting?roomCode=123456789')">加入</view>
			</view>
			<view class="meeting-item" @click="openDetail('团队周会', '987 654 321')">
				<view>
					<text class="meeting-name">团队周会</text>
					<text class="meeting-time">16:00 - 17:00</text>
					<text class="meeting-code">会议号：987 654 321</text>
				</view>
				<view class="pill" @click.stop="go('/subpackage_meeting/joinMeeting/joinMeeting?roomCode=987654321')">加入</view>
			</view>
		</view>

		<view class="bottom-tabs">
			<view class="tab-item tab-item--active"><text class="tab-ico">▰</text><text>会议</text></view>
			<view class="tab-item" @click="go('/subpackage_meeting/meetingSchedule/meetingSchedule')"><text class="tab-ico">▣</text><text>日程</text></view>
			<view class="tab-item" @click="go('/subpackage_meeting/meetingContacts/meetingContacts')"><text class="tab-ico">♟</text><text>通讯录</text></view>
			<view class="tab-item" @click="go('/subpackage_meeting/meetingMine/meetingMine')"><text class="tab-ico">♙</text><text>我的</text></view>
		</view>
	</view>
</template>

<script>
export default {
	methods: {
		go(url) {
			uni.navigateTo({ url })
		},
		openDetail(title, roomCode) {
			uni.navigateTo({
				url: `/subpackage_meeting/meetingDetail/meetingDetail?title=${encodeURIComponent(title)}&roomCode=${encodeURIComponent(roomCode)}`
			})
		}
	}
}
</script>

<style lang="scss" scoped>
.meeting-app-page { min-height: 100vh; background: #ffffff; padding: 0 28rpx 150rpx; box-sizing: border-box; color: #101820; }
.status-bar { height: var(--status-bar-height); min-height: 42rpx; }
.top-bar { height: 88rpx; display: flex; align-items: center; justify-content: space-between; }
.top-bar--home { margin-bottom: 8rpx; }
.page-title { font-size: 34rpx; font-weight: 800; }
.top-actions { display: flex; align-items: center; gap: 30rpx; }
.round-plus { width: 38rpx; height: 38rpx; border-radius: 50%; background: #1f7f68; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 32rpx; line-height: 1; }
.scan-icon { width: 30rpx; height: 30rpx; border: 3rpx solid #101820; border-radius: 6rpx; position: relative; box-sizing: border-box; }
.scan-icon::after { content: ''; position: absolute; inset: 8rpx; background: #fff; }
.home-hero { height: 258rpx; border-radius: 28rpx; overflow: hidden; position: relative; background: linear-gradient(135deg, #24735f 0%, #8cc7aa 100%); box-shadow: 0 20rpx 40rpx rgba(31, 127, 104, 0.15); }
.hero-close { position: absolute; right: 18rpx; top: 14rpx; color: rgba(255,255,255,.85); font-size: 34rpx; z-index: 3; }
.hero-copy { position: absolute; left: 28rpx; top: 44rpx; z-index: 2; display: flex; flex-direction: column; }
.hero-title { color: #fff; font-size: 40rpx; line-height: 1.28; font-weight: 900; white-space: pre-line; }
.hero-desc { margin-top: 22rpx; color: rgba(255,255,255,.9); font-size: 23rpx; }
.hero-scene { position: absolute; right: 12rpx; bottom: 4rpx; width: 270rpx; height: 205rpx; }
.board { position: absolute; width: 102rpx; height: 74rpx; left: 52rpx; top: 28rpx; border: 5rpx solid rgba(255,255,255,.75); border-radius: 8rpx; transform: rotate(-2deg); }
.table { position: absolute; width: 112rpx; height: 38rpx; left: 82rpx; bottom: 38rpx; border-radius: 50%; background: #65b696; box-shadow: 0 10rpx 0 rgba(34, 110, 90, .36); }
.chair { position: absolute; width: 58rpx; height: 64rpx; border-radius: 20rpx 20rpx 14rpx 14rpx; background: #7fc4a5; box-shadow: 0 10rpx 18rpx rgba(22,77,64,.18); }
.chair--one { left: 16rpx; bottom: 32rpx; transform: rotate(-12deg); }
.chair--two { right: 24rpx; bottom: 46rpx; transform: rotate(9deg); }
.chair--three { right: 66rpx; top: 60rpx; transform: rotate(-6deg); }
.quick-row { display: grid; grid-template-columns: repeat(3, 1fr); margin: 48rpx 8rpx 46rpx; }
.quick-item { display: flex; flex-direction: column; align-items: center; gap: 16rpx; color: #121b22; font-size: 24rpx; font-weight: 700; }
.quick-icon { width: 72rpx; height: 72rpx; border-radius: 20rpx; background: linear-gradient(135deg,#198468,#49aa88); color: #fff; display: flex; align-items: center; justify-content: center; font-size: 34rpx; box-shadow: 0 12rpx 24rpx rgba(31,127,104,.16); }
.date-line { margin-bottom: 18rpx; font-size: 26rpx; color: #1e2930; }
.meeting-list { border-radius: 26rpx; overflow: hidden; box-shadow: 0 22rpx 54rpx rgba(22,35,42,.08); }
.meeting-item { min-height: 126rpx; padding: 24rpx 28rpx; background: #fff; display: flex; align-items: flex-start; justify-content: space-between; border-bottom: 1rpx solid #f0f2f2; box-sizing: border-box; }
.meeting-item--active { background: linear-gradient(90deg, rgba(229,246,236,.95), rgba(255,255,255,.98)); }
.meeting-name { display: block; font-size: 27rpx; color: #121b22; font-weight: 800; }
.meeting-time, .meeting-code { display: block; margin-top: 9rpx; color: #68747a; font-size: 22rpx; }
.pill { min-width: 76rpx; height: 44rpx; border-radius: 999rpx; background: #eef8f3; color: #1f7f68; display: flex; align-items: center; justify-content: center; font-size: 22rpx; font-weight: 700; }
.pill--live { background: #c8efd9; }
.bottom-tabs { position: fixed; left: 0; right: 0; bottom: 0; height: 112rpx; padding: 10rpx 28rpx calc(env(safe-area-inset-bottom) + 8rpx); background: rgba(255,255,255,.96); box-shadow: 0 -10rpx 35rpx rgba(20,33,40,.08); display: grid; grid-template-columns: repeat(4,1fr); box-sizing: content-box; z-index: 20; }
.tab-item { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 5rpx; color: #7c868b; font-size: 21rpx; }
.tab-item--active { color: #147a64; font-weight: 800; }
.tab-ico { font-size: 32rpx; line-height: 1; }
</style>
