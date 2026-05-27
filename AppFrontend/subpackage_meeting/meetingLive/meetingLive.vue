<template>
	<view class="live-page">
		<view class="status-bar"></view>
		<view class="live-top">
			<text class="speaker">◔</text>
			<view class="live-title-wrap">
				<text class="live-title">{{ title }}</text>
				<text class="live-time">00:24:36</text>
			</view>
			<text class="end-text" @click="endMeeting">结束</text>
		</view>

		<view class="video-grid">
			<view v-for="member in members" :key="member.name" class="video-card" :class="member.className">
				<view class="face">
					<view class="hair"></view>
					<view class="head"></view>
					<view class="body"></view>
				</view>
				<view class="name-chip">{{ member.name }}</view>
			</view>
		</view>

		<view class="live-bottom">
			<view class="handle"></view>
			<view class="control-row">
				<view class="control-item"><view class="control-icon">♩</view><text>静音</text></view>
				<view class="control-item"><view class="control-icon">▰</view><text>关闭视频</text></view>
				<view class="control-item"><view class="control-icon">▭</view><text>共享屏幕</text></view>
				<view class="control-item"><view class="control-icon">♟</view><text>成员(6)</text></view>
				<view class="control-item"><view class="control-icon">•••</view><text>更多</text></view>
			</view>
		</view>
	</view>
</template>

<script>
export default {
	data() {
		return {
			title: '项目进度同步会',
			roomCode: '',
			members: [
				{ name: '我', className: 'avatar-a' },
				{ name: '李明', className: 'avatar-b' },
				{ name: '王磊', className: 'avatar-c' },
				{ name: '陈晨', className: 'avatar-d' }
			]
		}
	},
	onLoad(options) {
		if (options?.title) this.title = decodeURIComponent(options.title)
		if (options?.roomCode) this.roomCode = decodeURIComponent(options.roomCode)
	},
	methods: {
		endMeeting() {
			uni.redirectTo({ url: '/subpackage_meeting/meetingRoom/meetingRoom' })
		}
	}
}
</script>

<style lang="scss" scoped>
.live-page { min-height: 100vh; background: radial-gradient(circle at 20% 8%, #222a38 0, #111725 34%, #080d18 100%); padding: 0 22rpx 160rpx; box-sizing: border-box; color: #fff; }
.status-bar { height: var(--status-bar-height); min-height: 42rpx; }
.live-top { height: 98rpx; display: grid; grid-template-columns: 70rpx 1fr 70rpx; align-items: center; }
.speaker { font-size: 34rpx; color: #fff; }
.live-title-wrap { display: flex; flex-direction: column; align-items: center; gap: 6rpx; }
.live-title { font-size: 27rpx; font-weight: 850; color: #fff; }
.live-time { font-size: 20rpx; color: rgba(255,255,255,.72); }
.end-text { color: #ff5f55; font-size: 25rpx; text-align: right; }
.video-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10rpx; margin-top: 18rpx; }
.video-card { position: relative; height: 230rpx; border-radius: 10rpx; overflow: hidden; background: linear-gradient(180deg, #e9eeef, #bec8ca); display: flex; align-items: flex-end; justify-content: center; }
.avatar-b { background: linear-gradient(180deg, #e2ecee, #a7c0c8); }
.avatar-c { background: linear-gradient(180deg, #e9ecec, #ccd2d3); }
.avatar-d { background: linear-gradient(180deg, #f0eeea, #d9c8bd); }
.name-chip { position: absolute; left: 12rpx; bottom: 12rpx; min-width: 48rpx; height: 28rpx; padding: 0 10rpx; border-radius: 14rpx; background: rgba(22, 29, 36, .48); color: #fff; font-size: 18rpx; display: flex; align-items: center; justify-content: center; }
.face { position: relative; width: 138rpx; height: 190rpx; margin-bottom: 0; }
.head { position: absolute; left: 38rpx; top: 28rpx; width: 64rpx; height: 76rpx; border-radius: 34rpx 34rpx 28rpx 28rpx; background: #f4c9a6; z-index: 2; }
.hair { position: absolute; left: 32rpx; top: 18rpx; width: 76rpx; height: 54rpx; border-radius: 42rpx 42rpx 18rpx 18rpx; background: #242424; z-index: 3; }
.body { position: absolute; left: 8rpx; bottom: 0; width: 122rpx; height: 86rpx; border-radius: 38rpx 38rpx 0 0; background: #f7f7f7; z-index: 1; }
.avatar-b .body { background: #316f8e; }
.avatar-c .hair { background: #1e1d1c; }
.avatar-c .body { background: #dfe6ea; }
.avatar-d .hair { background: #3c2d26; }
.avatar-d .body { background: #efe7df; }
.live-bottom { position: fixed; left: 0; right: 0; bottom: 0; height: 150rpx; padding: 16rpx 22rpx calc(env(safe-area-inset-bottom) + 18rpx); background: rgba(23, 28, 36, .92); border-radius: 30rpx 30rpx 0 0; box-sizing: content-box; }
.handle { width: 74rpx; height: 8rpx; border-radius: 999rpx; background: rgba(255,255,255,.08); margin: 0 auto 20rpx; }
.control-row { display: grid; grid-template-columns: repeat(5, 1fr); }
.control-item { display: flex; flex-direction: column; align-items: center; gap: 8rpx; color: #fff; font-size: 19rpx; }
.control-icon { width: 52rpx; height: 52rpx; display: flex; align-items: center; justify-content: center; font-size: 30rpx; }
</style>
