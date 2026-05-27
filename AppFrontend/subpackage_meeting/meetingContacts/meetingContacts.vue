<template>
	<view class="meeting-app-page">
		<view class="status-bar"></view>
		<view class="top-bar"><text class="page-title">通讯录</text></view>
		<view class="search-box">⌕  搜索联系人</view>
		<view class="shortcut-row">
			<view class="shortcut"><view class="shortcut-icon">♙</view><text>新建群组</text></view>
			<view class="shortcut"><view class="shortcut-icon">♙</view><text>我的群组</text></view>
			<view class="shortcut"><view class="shortcut-icon">▧</view><text>企业通讯录</text></view>
		</view>
		<text class="section-title">常用联系人</text>
		<view class="contact-list">
			<view v-for="person in contacts" :key="person.name" class="contact-item">
				<view class="avatar">{{ person.name.slice(0,1) }}</view>
				<view class="contact-copy"><text class="contact-name">{{ person.name }}</text><text class="contact-role">{{ person.role }}</text></view>
			</view>
		</view>
		<view class="letters"># A B C D E F G H I K R X Y Z</view>
		<view class="bottom-tabs">
			<view class="tab-item" @click="go('/subpackage_meeting/meetingRoom/meetingRoom')"><text class="tab-ico">▰</text><text>会议</text></view>
			<view class="tab-item" @click="go('/subpackage_meeting/meetingSchedule/meetingSchedule')"><text class="tab-ico">▣</text><text>日程</text></view>
			<view class="tab-item tab-item--active"><text class="tab-ico">♟</text><text>通讯录</text></view>
			<view class="tab-item" @click="go('/subpackage_meeting/meetingMine/meetingMine')"><text class="tab-ico">♙</text><text>我的</text></view>
		</view>
	</view>
</template>
<script>
export default { data() { return { contacts: [{ name: '张三', role: '产品经理' }, { name: '李四', role: '设计师' }, { name: '王五', role: '前端工程师' }, { name: '赵六', role: '测试工程师' }] } }, methods: { go(url) { uni.navigateTo({ url }) } } }
</script>
<style lang="scss" scoped>
.meeting-app-page { min-height: 100vh; background:#fff; padding:0 28rpx 150rpx; box-sizing:border-box; color:#111b21; position:relative; }
.status-bar{height:var(--status-bar-height);min-height:42rpx}.top-bar{height:88rpx;display:flex;align-items:center}.page-title{font-size:34rpx;font-weight:900}.search-box{height:58rpx;border-radius:14rpx;background:#f6f6f6;color:#9aa2a7;font-size:24rpx;display:flex;align-items:center;padding:0 24rpx}.shortcut-row{display:grid;grid-template-columns:repeat(3,1fr);margin:34rpx 0}.shortcut{display:flex;flex-direction:column;align-items:center;gap:12rpx;font-size:21rpx;color:#1a242a}.shortcut-icon{width:62rpx;height:62rpx;border-radius:22rpx;background:#e8f7ef;color:#1f7f68;display:flex;align-items:center;justify-content:center;font-size:29rpx}.section-title{display:block;font-size:24rpx;font-weight:850;margin:12rpx 0 18rpx}.contact-item{height:86rpx;display:flex;align-items:center;gap:18rpx}.avatar{width:50rpx;height:50rpx;border-radius:50%;background:linear-gradient(135deg,#e8eee9,#99cbb8);display:flex;align-items:center;justify-content:center;font-size:20rpx;font-weight:850;color:#173a32}.contact-copy{display:flex;flex-direction:column;gap:6rpx}.contact-name{font-size:27rpx;font-weight:800}.contact-role{font-size:21rpx;color:#8b9499}.letters{position:fixed;right:10rpx;top:330rpx;width:22rpx;text-align:center;font-size:18rpx;line-height:1.7;color:#1f2930}.bottom-tabs{position:fixed;left:0;right:0;bottom:0;height:112rpx;padding:10rpx 28rpx calc(env(safe-area-inset-bottom) + 8rpx);background:rgba(255,255,255,.96);box-shadow:0 -10rpx 35rpx rgba(20,33,40,.08);display:grid;grid-template-columns:repeat(4,1fr);box-sizing:content-box}.tab-item{display:flex;flex-direction:column;align-items:center;justify-content:center;gap:5rpx;color:#7c868b;font-size:21rpx}.tab-item--active{color:#147a64;font-weight:800}.tab-ico{font-size:32rpx}
</style>
