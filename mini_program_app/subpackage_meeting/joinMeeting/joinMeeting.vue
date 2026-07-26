<template>
	<view class="meeting-app-page">
		<view class="status-bar"></view>
		<view class="top-bar">
			<view class="back" @click="back">‹</view>
			<text class="nav-title">加入会议</text>
			<view></view>
		</view>

		<view class="join-panel">
			<view class="field-block">
				<text class="field-title">请输入会议号</text>
				<view class="input-wrap">
					<input v-model="roomCode" class="join-input" maxlength="11" placeholder="请输入9–11位会议号" placeholder-class="placeholder" />
					<view v-if="roomCode" class="clear" @click="roomCode = ''">×</view>
				</view>
			</view>

			<view class="field-block field-block--name">
				<text class="field-title">入会名称（使用登录身份）</text>
				<input v-model="displayName" class="name-input" disabled placeholder="登录后自动读取" />
				<!-- 改为圆形勾选框，不再使用switch -->
				<view class="save-name-row" @click="saveDisplayName = !saveDisplayName">
					<view class="circle-check" :class="{active: saveDisplayName}">
						<text v-if="saveDisplayName">✓</text>
					</view>
					<text class="save-name-text">后续会议都使用此名称</text>
				</view>
			</view>

			<view class="option-title">入会选项</view>
			<view class="option-row">
				<text>开启麦克风</text>
				<switch :checked="micOn" color="#86C9A8" @change="micOn = $event.detail.value" />
			</view>
			<view class="option-row">
				<text>开启扬声器</text>
				<switch :checked="speakerOn" color="#86C9A8" @change="speakerOn = $event.detail.value" />
			</view>
			<view class="option-row">
				<text>开启视频</text>
				<switch :checked="videoOn" color="#86C9A8" @change="videoOn = $event.detail.value" />
			</view>

			<view class="device-link">♙ 从会议室设备加入</view>
		</view>

		<view class="bottom-button-wrap">
			<view class="main-button" @click="joinNow">加入会议</view>
		</view>
	</view>
</template>

<script>
import { joinMeeting } from '@/api/ai.js'
import { getCurrentDisplayName } from '@/utils/meetingUser.js'

export default {
	data() {
		return {
			roomCode: '',
			displayName: '',
			micOn: true,
			videoOn: false,
			speakerOn: true,
			saveDisplayName: true
		}
	},
	onLoad(options) {
		if (options?.roomCode) this.roomCode = decodeURIComponent(options.roomCode)
		this.displayName = getCurrentDisplayName()
	},
	methods: {
		back() { uni.navigateBack() },
		async joinNow() {
			const compactCode = this.roomCode.replace(/\s+/g, '')
			if (!compactCode) {
				uni.showToast({ title: '请输入会议号', icon: 'none' })
				return
			}
			try {
				const res = await joinMeeting({ roomCode: compactCode, displayName: this.displayName || getCurrentDisplayName() })
				const session = res?.data?.session || {}
				uni.redirectTo({
					url: `/subpackage_meeting/meetingLive/meetingLive?title=${encodeURIComponent(session.title || '会议')}&roomCode=${encodeURIComponent(session.roomCode || compactCode)}&sessionId=${encodeURIComponent(session.sessionId || '')}&micOn=${this.micOn ? '1' : '0'}&videoOn=${this.videoOn ? '1' : '0'}&speakerOn=${this.speakerOn ? '1' : '0'}`
				})
			} catch (error) {
				uni.showToast({ title: '加入会议失败，请检查会议号', icon: 'none' })
			}
		}
	}
}
</script>

<style lang="scss" scoped>
.meeting-app-page { min-height: 100vh; background: #fff; padding: 0 24rpx; box-sizing: border-box; color: #151f25; }
.status-bar { height: var(--status-bar-height); min-height: 42rpx; }
.top-bar { height: 88rpx; display: grid; grid-template-columns: 70rpx 1fr 70rpx; align-items: center; }
.back { font-size: 48rpx; line-height: 1; color: #111; }
.nav-title { text-align: center; font-size: 30rpx; font-weight: 800; }
.join-panel { margin-top: 24rpx; padding: 34rpx 28rpx 46rpx; border-radius: 24rpx; background: #fff; box-shadow: 0 20rpx 58rpx rgba(31,42,48,.08); }
.field-block { margin-bottom: 34rpx; }
.field-block--name { margin-top: 10rpx; }
.field-title { display: block; margin-bottom: 20rpx; font-size: 26rpx; color: #1c272d; font-weight: 750; }
.input-wrap { height: 88rpx; border-radius: 16rpx; background: #f7f7f7; display: flex; align-items: center; padding: 0 18rpx 0 24rpx; gap: 18rpx; }
.join-input { flex: 1; height: 88rpx; font-size: 26rpx; color: #1c272d; }
.placeholder { color: #b0b6ba; }
.clear { width: 30rpx; height: 30rpx; border-radius: 50%; border: 2rpx solid #a9b0b4; color: #a9b0b4; display: flex; align-items: center; justify-content: center; font-size: 26rpx; line-height: 1; }
.name-input { height: 88rpx; border-radius: 16rpx; background: #f7f7f7; padding: 0 24rpx; font-size: 27rpx; color: #1b252b; }

/* 自定义圆形勾选 */
.save-name-row {
	margin-top: 24rpx;
	display: flex;
	align-items: center;
	gap: 16rpx;
}
.circle-check {
	width: 36rpx;
	height: 36rpx;
	border-radius: 50%;
	border: 2rpx solid #c8cdcf;
	display: flex;
	align-items: center;
	justify-content: center;
	font-size: 24rpx;
	color: #fff;
	transition: all 0.2s;
}
.circle-check.active {
	background-color: #86C9A8;
	border-color: #86C9A8;
}
.save-name-text {
	font-size: 26rpx;
	color: #444;
}

.option-title { margin: 46rpx 0 24rpx; font-size: 27rpx; font-weight: 850; }
.option-row { height: 82rpx; display: flex; align-items: center; justify-content: space-between; font-size: 26rpx; }
.device-link { margin-top: 34rpx; text-align: center; color: #58636a; font-size: 25rpx; }

.bottom-button-wrap {
	position: fixed;
	left: 24rpx;
	right: 24rpx;
	bottom: calc(env(safe-area-inset-bottom) + 40rpx);
}
.main-button {
	height: 88rpx;
	border-radius: 16rpx;
	background: #86C9A8;
	color: #fff;
	display: flex;
	align-items: center;
	justify-content: center;
	font-size: 29rpx;
	font-weight: 900;
	box-shadow: 0 16rpx 34rpx rgba(134, 201, 168, .18);
}
</style>