<template>
	<view class="meeting-app-page">
		<view class="status-bar"></view>
		<view class="top-bar"><view class="back" @click="back">‹</view><text class="nav-title">设置</text><view></view></view>
		<view class="settings-card">
			<view v-for="item in items" :key="item.label" class="setting-row"><view><text class="setting-icon">{{ item.icon }}</text><text>{{ item.label }}</text></view><text class="right-text">{{ item.version || '›' }}</text></view>
		</view>
		<view class="logout">退出登录</view>
	</view>
</template>
<script>
export default { data(){ return { items:[{icon:'▣',label:'账号与安全'},{icon:'⚙',label:'通用'},{icon:'▰',label:'会议'},{icon:'▰',label:'看视频'},{icon:'♧',label:'消息通知'},{icon:'▢',label:'隐私'},{icon:'ⓘ',label:'关于我们',version:'V1.2.0'}] } }, methods:{ back(){ uni.navigateBack() } } }
</script>
<style lang="scss" scoped>
.meeting-app-page{min-height:100vh;background:#fff;padding:0 24rpx;box-sizing:border-box;color:#111b21}.status-bar{height:var(--status-bar-height);min-height:42rpx}.top-bar{height:88rpx;display:grid;grid-template-columns:70rpx 1fr 70rpx;align-items:center}.back{font-size:48rpx;line-height:1}.nav-title{text-align:center;font-size:29rpx;font-weight:850}.settings-card{margin-top:24rpx;padding:8rpx 22rpx;border-radius:20rpx;background:#fff;box-shadow:0 18rpx 52rpx rgba(31,42,48,.08)}.setting-row{height:82rpx;border-bottom:1rpx solid #f1f1f1;display:flex;align-items:center;justify-content:space-between;font-size:25rpx}.setting-row:last-child{border-bottom:0}.setting-icon{display:inline-block;width:44rpx;color:#111b21}.right-text{color:#8d969b}.logout{margin:50rpx 20rpx 0;height:66rpx;border-radius:16rpx;background:#fff1f0;color:#e33b33;display:flex;align-items:center;justify-content:center;font-size:25rpx;font-weight:800}
</style>
