<template>
	<view class="live-page">
		<view class="status-bar"></view>
		<view class="live-top">
			<text class="speaker">◔</text>
			<view class="live-title-wrap">
				<text class="live-title">{{ title }}</text>
				<text class="live-time">{{ elapsedText }}</text>
			</view>
			<text class="end-text" @click="confirmEndMeeting">结束</text>
		</view>

		<view class="member-grid">
			<view v-for="member in visibleMembers" :key="member.name" class="member-card" :class="member.className">
				<view class="face">
					<view class="hair"></view>
					<view class="head"></view>
					<view class="body"></view>
				</view>
				<view class="name-chip">{{ member.name }}</view>
			</view>
		</view>

		<view class="asr-barrage-area">
			<view class="asr-head">
				<text class="asr-title">语音识别弹幕</text>
				<text class="asr-status" :class="{ 'asr-status--live': asrRecording }">{{ asrStatusText }}</text>
			</view>
			<view v-if="asrItems.length === 0" class="asr-empty">识别到发言后，会在这里显示每位成员说的话</view>
			<view v-else class="asr-stream">
				<view
					v-for="item in asrItems"
					:key="item.id"
					class="asr-bubble"
					:class="{ 'asr-bubble--partial': !item.isFinal }"
				>
					<text class="asr-speaker">{{ item.speaker }}</text>
					<text class="asr-text">{{ item.text }}</text>
				</view>
			</view>
		</view>

		<view class="live-bottom">
			<view class="handle"></view>
			<view class="control-row">
				<view class="control-item" :class="{ 'control-item--active': muted }" @click="toggleMute"><view class="control-icon">♩</view><text>{{ muted ? '解除静音' : '静音' }}</text></view>
				<view class="control-item" @click="showMembers"><view class="control-icon">♟</view><text>成员({{ members.length }})</text></view>
				<view class="control-item" @click="showMore"><view class="control-icon">•••</view><text>更多</text></view>
			</view>
		</view>

		<view v-if="panelVisible" class="panel-mask" @click="closePanel"></view>
		<view v-if="memberPanelVisible" class="sheet-panel">
			<view class="sheet-handle"></view>
			<view class="sheet-title-row">
				<text class="sheet-title">成员({{ members.length }})</text>
				<text class="sheet-close" @click="closePanel">×</text>
			</view>
			<view class="member-list">
				<view v-for="member in members" :key="member.name" class="member-row">
					<view class="member-avatar">{{ member.name.slice(0, 1) }}</view>
					<view class="member-info">
						<text class="member-name">{{ member.name }}</text>
						<text class="member-role">{{ member.isSelf ? '我' : '参会成员' }}</text>
					</view>
					<text class="member-mic">{{ muted && member.isSelf ? '已静音' : '麦克风开启' }}</text>
				</view>
			</view>
		</view>
		<view v-if="morePanelVisible" class="sheet-panel">
			<view class="sheet-handle"></view>
			<view class="sheet-title-row">
				<text class="sheet-title">更多</text>
				<text class="sheet-close" @click="closePanel">×</text>
			</view>
			<view class="more-list">
				<view class="more-row" @click="copyRoomCode"><text>复制会议号</text><text>{{ roomCode || '未生成' }}</text></view>
				<view class="more-row" @click="shareMeeting"><text>分享会议</text><text>复制邀请文案</text></view>
				<view class="more-row" @click="openMeetingDetail"><text>会议详情</text><text>查看会议号与参会人</text></view>
			</view>
		</view>
	</view>
</template>

<script>
import { endMeeting as finishMeetingApi, getMeetingDetail } from '@/api/ai.js'
import { getCurrentDisplayName, toMeetingMembers } from '@/utils/meetingUser.js'
import { BASE_URL } from '@/utils/config.js'
import { getToken } from '@/utils/storage.js'

export default {
	data() {
		return {
			sessionId: '',
			title: '项目进度同步会',
			roomCode: '',
			muted: false,
			elapsedSeconds: 0,
			timer: null,
			asrSocket: null,
			asrRecorder: null,
			asrRecording: false,
			asrSocketReady: false,
			asrStatusText: '等待连接',
			asrItems: [],
			asrSeq: 0,
			memberPanelVisible: false,
			morePanelVisible: false,
			members: []
		}
	},
	onLoad(options) {
		if (options?.sessionId) this.sessionId = decodeURIComponent(options.sessionId)
		if (options?.title) this.title = decodeURIComponent(options.title)
		if (options?.roomCode) this.roomCode = decodeURIComponent(options.roomCode)
		this.initCurrentMember()
		this.startTimer()
		this.loadMeeting()
		this.initAsr()
	},
	onUnload() {
		this.stopTimer()
		this.closeAsr()
	},
	computed: {
		elapsedText() {
			const hours = String(Math.floor(this.elapsedSeconds / 3600)).padStart(2, '0')
			const minutes = String(Math.floor((this.elapsedSeconds % 3600) / 60)).padStart(2, '0')
			const seconds = String(this.elapsedSeconds % 60).padStart(2, '0')
			return `${hours}:${minutes}:${seconds}`
		},
		panelVisible() {
			return this.memberPanelVisible || this.morePanelVisible
		},
		compactRoomCode() {
			return (this.roomCode || '').replace(/\s+/g, '')
		},
		visibleMembers() {
			return this.members.slice(0, 4)
		}
	},
	methods: {
		startTimer() {
			this.stopTimer()
			this.timer = setInterval(() => {
				this.elapsedSeconds += 1
			}, 1000)
		},
		stopTimer() {
			if (this.timer) {
				clearInterval(this.timer)
				this.timer = null
			}
		},
		async loadMeeting() {
			if (!this.sessionId) return
			try {
				const res = await getMeetingDetail(this.sessionId)
				const detail = res?.data || {}
				const session = detail.session || {}
				if (session.title) this.title = session.title
				if (session.roomCode) this.roomCode = session.roomCode
				if (Array.isArray(detail.participants) && detail.participants.length > 0) {
					this.members = toMeetingMembers(detail.participants.slice(0, 6))
				}
			} catch (error) {}
		},
		initCurrentMember() {
			const currentName = getCurrentDisplayName()
			this.members = currentName ? toMeetingMembers([currentName], currentName) : []
		},
		toggleMute() {
			this.muted = !this.muted
			if (this.muted) {
				this.stopAsrRecording()
			} else {
				this.startAsr()
			}
			uni.showToast({ title: this.muted ? '已静音' : '已解除静音', icon: 'none' })
		},
		initAsr() {
			if (!this.sessionId) {
				this.asrStatusText = '会议创建后开始识别'
				return
			}
			this.asrStatusText = '正在连接识别'
			this.openAsrSocket()
		},
		openAsrSocket() {
			if (this.asrSocket) return
			const token = getToken()
			if (!token) {
				this.asrStatusText = '请先登录后使用识别'
				return
			}
			const url = this.buildAsrSocketUrl(token)
			this.asrSocket = uni.connectSocket({ url, complete: () => {} })
			this.asrSocket.onOpen(() => {
				this.asrSocketReady = true
				this.asrStatusText = this.muted ? '已静音' : '识别已连接'
				if (!this.muted) this.startAsrRecording()
			})
			this.asrSocket.onMessage((event) => this.handleAsrMessage(event.data))
			this.asrSocket.onError(() => {
				this.asrStatusText = '识别连接异常'
				this.asrSocketReady = false
			})
			this.asrSocket.onClose(() => {
				this.asrStatusText = this.asrRecording ? '识别已断开' : '识别已停止'
				this.asrSocketReady = false
				this.asrSocket = null
			})
		},
		buildAsrSocketUrl(token) {
			const base = BASE_URL.replace(/^http/i, match => match.toLowerCase() === 'https' ? 'wss' : 'ws').replace(/\/$/, '')
			return `${base}/api/meetings/${encodeURIComponent(this.sessionId)}/asr/stream?token=${encodeURIComponent(token)}`
		},
		startAsr() {
			if (!this.sessionId) return
			if (!this.asrSocket) {
				this.openAsrSocket()
				return
			}
			if (this.asrSocketReady) this.startAsrRecording()
		},
		startAsrRecording() {
			if (this.asrRecording || !this.asrSocketReady || !this.asrSocket) return
			if (!this.asrRecorder) {
				this.asrRecorder = uni.getRecorderManager()
				this.asrRecorder.onFrameRecorded((res) => {
					if (this.asrSocketReady && this.asrSocket && res.frameBuffer) {
						this.asrSocket.send({ data: res.frameBuffer })
					}
				})
				this.asrRecorder.onError(() => {
					this.asrRecording = false
					this.asrStatusText = '录音权限或设备异常'
				})
				this.asrRecorder.onStop(() => {
					this.asrRecording = false
				})
			}
			try {
				this.asrRecorder.start({
					duration: 60 * 60 * 1000,
					sampleRate: 16000,
					numberOfChannels: 1,
					encodeBitRate: 256000,
					format: 'PCM',
					frameSize: 4
				})
				this.asrRecording = true
				this.asrStatusText = '正在识别'
			} catch (error) {
				this.asrStatusText = '录音启动失败'
			}
		},
		stopAsrRecording() {
			if (this.asrRecorder && this.asrRecording) {
				try {
					this.asrRecorder.stop()
				} catch (error) {}
			}
			if (this.asrSocketReady && this.asrSocket) {
				this.asrSocket.send({ data: JSON.stringify({ stop: true }) })
			}
			this.asrRecording = false
			this.asrStatusText = '已静音'
		},
		closeAsr() {
			if (this.asrRecorder && this.asrRecording) {
				try {
					this.asrRecorder.stop()
				} catch (error) {}
			}
			if (this.asrSocket) {
				try {
					this.asrSocket.send({ data: JSON.stringify({ stop: true }) })
					this.asrSocket.close()
				} catch (error) {}
			}
			this.asrRecording = false
			this.asrSocketReady = false
			this.asrSocket = null
		},
		handleAsrMessage(raw) {
			let payload = null
			try {
				payload = typeof raw === 'string' ? JSON.parse(raw) : raw
			} catch (error) {
				return
			}
			if (!payload) return
			if (payload.type === 'asr_ready') {
				this.asrStatusText = this.asrRecording ? '正在识别' : '识别已连接'
				return
			}
			if (payload.type === 'asr_error') {
				this.asrStatusText = payload.message || '识别异常'
				return
			}
			if (payload.type === 'asr_result') {
				this.upsertAsrItem({
					speaker: payload.speaker || '参会成员',
					text: payload.text || '',
					isFinal: !!payload.isFinal
				})
			}
		},
		upsertAsrItem(item) {
			const text = (item.text || '').trim()
			if (!text) return
			const partialId = `partial-${item.speaker}`
			if (!item.isFinal) {
				const existing = this.asrItems.find(asrItem => asrItem.id === partialId)
				if (existing) {
					existing.text = text
				} else {
					this.asrItems.push({ ...item, id: partialId })
				}
			} else {
				this.asrItems = this.asrItems.filter(asrItem => asrItem.id !== partialId)
				this.asrItems.push({ ...item, id: `asr-${Date.now()}-${this.asrSeq++}` })
			}
			if (this.asrItems.length > 8) {
				this.asrItems = this.asrItems.slice(this.asrItems.length - 8)
			}
		},
		showMembers() {
			this.morePanelVisible = false
			this.memberPanelVisible = true
		},
		showMore() {
			this.memberPanelVisible = false
			this.morePanelVisible = true
		},
		closePanel() {
			this.memberPanelVisible = false
			this.morePanelVisible = false
		},
		copyRoomCode() {
			if (!this.roomCode) {
				uni.showToast({ title: '会议号暂未生成', icon: 'none' })
				return
			}
			uni.setClipboardData({
				data: this.roomCode,
				success: () => uni.showToast({ title: '会议号已复制', icon: 'none' })
			})
		},
		shareMeeting() {
			const text = `会议：${this.title}\n会议号：${this.roomCode || '未生成'}${this.compactRoomCode ? `\nhttps://meeting.app/join/${this.compactRoomCode}` : ''}`
			uni.setClipboardData({
				data: text,
				success: () => uni.showToast({ title: '邀请信息已复制', icon: 'none' })
			})
		},
		openMeetingDetail() {
			this.closePanel()
			uni.navigateTo({
				url: `/subpackage_meeting/meetingDetail/meetingDetail?sessionId=${encodeURIComponent(this.sessionId || '')}&title=${encodeURIComponent(this.title)}&roomCode=${encodeURIComponent(this.roomCode || '')}`
			})
		},
		confirmEndMeeting() {
			uni.showModal({
				title: '结束会议',
				content: '确定要结束当前会议吗？',
				confirmText: '结束',
				confirmColor: '#ff5f55',
				success: (res) => {
					if (res.confirm) {
						this.endMeeting()
					}
				}
			})
		},
		async endMeeting() {
			if (this.sessionId) {
				try {
					await finishMeetingApi(this.sessionId)
				} catch (error) {}
			}
			this.stopTimer()
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
.member-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10rpx; margin-top: 18rpx; }
.member-card { position: relative; height: 230rpx; border-radius: 10rpx; overflow: hidden; background: linear-gradient(180deg, #e9eeef, #bec8ca); display: flex; align-items: flex-end; justify-content: center; }
.avatar-b { background: linear-gradient(180deg, #e2ecee, #a7c0c8); }
.avatar-c { background: linear-gradient(180deg, #e9ecec, #ccd2d3); }
.avatar-d { background: linear-gradient(180deg, #f0eeea, #d9c8bd); }
.asr-barrage-area { margin-top: 22rpx; min-height: 420rpx; padding: 22rpx 18rpx; border-radius: 24rpx; background: linear-gradient(180deg, rgba(255,255,255,.07), rgba(255,255,255,.025)); border: 1rpx solid rgba(255,255,255,.08); box-sizing: border-box; overflow: hidden; }
.asr-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 18rpx; }
.asr-title { color: rgba(255,255,255,.88); font-size: 24rpx; font-weight: 850; }
.asr-status { height: 34rpx; padding: 0 16rpx; border-radius: 999rpx; background: rgba(255,255,255,.08); color: rgba(255,255,255,.62); font-size: 19rpx; display: flex; align-items: center; }
.asr-status--live { background: rgba(92, 223, 179, .16); color: #79e6c2; }
.asr-empty { margin-top: 128rpx; text-align: center; color: rgba(255,255,255,.35); font-size: 23rpx; }
.asr-stream { display: flex; flex-direction: column; gap: 14rpx; }
.asr-bubble { max-width: 92%; align-self: flex-start; padding: 13rpx 17rpx; border-radius: 20rpx 20rpx 20rpx 8rpx; background: rgba(255,255,255,.12); box-shadow: 0 10rpx 24rpx rgba(0,0,0,.16); animation: asr-pop .24s ease-out both; }
.asr-bubble:nth-child(2n) { align-self: flex-end; border-radius: 20rpx 20rpx 8rpx 20rpx; background: rgba(97, 185, 153, .18); }
.asr-bubble--partial { opacity: .72; }
.asr-speaker { margin-right: 12rpx; color: #7ee2c0; font-size: 20rpx; font-weight: 900; }
.asr-text { color: rgba(255,255,255,.9); font-size: 23rpx; line-height: 1.45; }
@keyframes asr-pop { from { opacity: 0; transform: translateY(14rpx) scale(.98); } to { opacity: 1; transform: translateY(0) scale(1); } }
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
.control-row { display: grid; grid-template-columns: repeat(3, 1fr); }
.control-item { display: flex; flex-direction: column; align-items: center; gap: 8rpx; color: #fff; font-size: 19rpx; }
.control-item--active { color: #6ee0bc; }
.control-icon { width: 52rpx; height: 52rpx; display: flex; align-items: center; justify-content: center; font-size: 30rpx; }
.panel-mask { position: fixed; inset: 0; background: rgba(0, 0, 0, .24); z-index: 30; }
.sheet-panel { position: fixed; left: 0; right: 0; bottom: 0; padding: 16rpx 28rpx calc(env(safe-area-inset-bottom) + 34rpx); border-radius: 32rpx 32rpx 0 0; background: #171e29; box-shadow: 0 -18rpx 48rpx rgba(0,0,0,.28); z-index: 31; }
.sheet-handle { width: 72rpx; height: 8rpx; border-radius: 999rpx; background: rgba(255,255,255,.12); margin: 0 auto 24rpx; }
.sheet-title-row { display: flex; align-items: center; justify-content: space-between; margin-bottom: 18rpx; }
.sheet-title { color: #fff; font-size: 30rpx; font-weight: 900; }
.sheet-close { color: rgba(255,255,255,.72); font-size: 36rpx; }
.member-list, .more-list { display: flex; flex-direction: column; gap: 12rpx; }
.member-row, .more-row { min-height: 78rpx; border-radius: 18rpx; background: rgba(255,255,255,.06); display: flex; align-items: center; padding: 0 20rpx; }
.member-row { gap: 18rpx; }
.member-avatar { width: 48rpx; height: 48rpx; border-radius: 50%; background: #2b8d75; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 22rpx; font-weight: 900; }
.member-info { flex: 1; display: flex; flex-direction: column; gap: 4rpx; }
.member-name { color: #fff; font-size: 25rpx; font-weight: 800; }
.member-role, .member-mic { color: rgba(255,255,255,.58); font-size: 21rpx; }
.more-row { justify-content: space-between; color: #fff; font-size: 25rpx; }
.more-row text:last-child { color: rgba(255,255,255,.58); font-size: 22rpx; }
</style>
