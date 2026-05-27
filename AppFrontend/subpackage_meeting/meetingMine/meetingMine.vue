<template>
	<view class="mine-page">
		<view class="status-bar"></view>
		<view class="profile-head">
			<view class="profile-avatar">张</view>
			<view><view><text class="profile-name">张三</text><text class="badge">企业版</text></view><text class="phone">138 0000 0000</text></view>
		</view>
		<view class="mine-card">
			<view class="mine-row"><text>♙  个人会议号</text><view><text>123 456 789</text><text class="copy"> □</text></view></view>
			<view class="mine-row" @click="go('/subpackage_meeting/meetingSettings/meetingSettings')"><text>⚙  设置</text><text>›</text></view>
			<view class="mine-row"><text>?  帮助与反馈</text><text>›</text></view>
			<view class="mine-row"><text>ⓘ  关于我们</text><text>V1.2.0</text></view>
		</view>
		<view class="logout">退出登录</view>
		<view class="bottom-tabs"><view class="tab-item" @click="go('/subpackage_meeting/meetingRoom/meetingRoom')"><text class="tab-ico">▰</text><text>会议</text></view><view class="tab-item" @click="go('/subpackage_meeting/meetingSchedule/meetingSchedule')"><text class="tab-ico">▣</text><text>日程</text></view><view class="tab-item" @click="go('/subpackage_meeting/meetingContacts/meetingContacts')"><text class="tab-ico">♟</text><text>通讯录</text></view><view class="tab-item tab-item--active"><text class="tab-ico">♙</text><text>我的</text></view></view>
	</view>
</template>
<script>export default { methods:{ go(url){ uni.navigateTo({url}) } } }</script>
<style lang="scss" scoped>
.mine-page{min-height:100vh;background:linear-gradient(135deg,#28836b,#76b78f 34%,#fff 34%,#fff 100%);padding:0 24rpx 150rpx;box-sizing:border-box;color:#111b21}.status-bar{height:var(--status-bar-height);min-height:42rpx}.profile-head{height:202rpx;display:flex;align-items:center;gap:22rpx;color:#fff}.profile-avatar{width:92rpx;height:92rpx;border-radius:24rpx;background:#eef4ef;color:#1b3630;display:flex;align-items:center;justify-content:center;font-size:34rpx;font-weight:900}.profile-name{font-size:32rpx;font-weight:900}.badge{margin-left:14rpx;padding:4rpx 12rpx;border-radius:999rpx;background:#d7f0c7;color:#276540;font-size:18rpx}.phone{display:block;margin-top:8rpx;font-size:25rpx}.mine-card{border-radius:20rpx;background:#fff;box-shadow:0 18rpx 52rpx rgba(31,42,48,.08);padding:8rpx 26rpx}.mine-row{height:86rpx;display:flex;align-items:center;justify-content:space-between;border-bottom:1rpx solid #f1f1f1;font-size:25rpx}.mine-row:last-child{border-bottom:0}.copy{color:#9aa3a8}.logout{margin:48rpx 24rpx 0;height:66rpx;border-radius:16rpx;background:#fff1f0;color:#e33b33;display:flex;align-items:center;justify-content:center;font-size:25rpx;font-weight:800}.bottom-tabs{position:fixed;left:0;right:0;bottom:0;height:112rpx;padding:10rpx 28rpx calc(env(safe-area-inset-bottom) + 8rpx);background:rgba(255,255,255,.96);box-shadow:0 -10rpx 35rpx rgba(20,33,40,.08);display:grid;grid-template-columns:repeat(4,1fr);box-sizing:content-box}.tab-item{display:flex;flex-direction:column;align-items:center;justify-content:center;gap:5rpx;color:#7c868b;font-size:21rpx}.tab-item--active{color:#147a64;font-weight:800}.tab-ico{font-size:32rpx}
</style>
