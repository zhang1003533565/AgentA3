<template>
	<view class="live-page">
		<view class="status-bar"></view>

		<!-- 顶部导航栏 对齐参考布局 -->
		<view class="live-top">
			<view class="top-left-empty"></view>
			<view class="live-title-wrap">
				<text class="live-title">{{ title }}</text>
				<text class="live-time">{{ elapsedText }}</text>
			</view>
			<text class="end-text" @click="confirmEndMeeting">结束</text>
		</view>

		<!-- 中间主区域：参会画面居中（核心视觉） -->
		<view class="main-view-area">
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
		</view>

		<!-- 底部固定操作栏 -->
		<view class="live-bottom">
			<view class="control-row">
				<view class="control-item" :class="{ 'control-item--active': muted }" @click="toggleMute">
					<view class="control-icon">♩</view>
					<text>{{ muted ? '解除静音' : '静音' }}</text>
				</view>
				<view class="control-item" :class="{ 'control-item--active': cameraOpen }" @click="toggleCamera">
					<view class="control-icon">◎</view>
					<text>{{ cameraOpen ? '关闭视频' : '开启视频' }}</text>
				</view>
				<view class="control-item" :class="{ 'control-item--active': shareScreenOpen }" @click="toggleShareScreen">
					<view class="control-icon">▣</view>
					<text>{{ shareScreenOpen ? '停止共享' : '共享屏幕' }}</text>
				</view>
				<view class="control-item" @click="showMembers">
					<view class="control-icon">♟</view>
					<text>成员({{ members.length }})</text>
				</view>
				<view class="control-item" @click="showMore">
					<view class="control-icon">•••</view>
					<text>更多</text>
				</view>
			</view>
		</view>

		<!-- 语音/AI总结 独立底部弹窗，不再常驻页面 -->
		<view v-if="asrPanelVisible" class="panel-mask" @click="closeAsrPanel"></view>
		<view v-if="asrPanelVisible" class="sheet-panel asr-panel">
			<view class="sheet-handle"></view>
			<view class="sheet-title-row">
				<text class="sheet-title">{{ livePanelTitle }}</text>
				<text class="sheet-close" @click="closeAsrPanel">×</text>
			</view>
			<view class="asr-mode-row">
				<view class="asr-mode-pill" :class="{ 'asr-mode-pill--active': !agentEnabled }" @click="setAgentSummary(false)">语音弹幕</view>
				<view class="asr-mode-pill" :class="{ 'asr-mode-pill--active': agentEnabled }" @click="setAgentSummary(true)">AI总结</view>
			</view>
			<view class="agent-top-toggle" :class="{ 'agent-top-toggle--active': agentEnabled }" @click="setAgentSummary(!agentEnabled)">
				<view class="agent-top-copy">
					<text class="agent-top-title">智能体会议总结</text>
					<text class="agent-top-desc">{{ agentEnabled ? '已开启：下方显示 AI 实时总结流' : '未开启：点击切换为 AI 实时总结' }}</text>
				</view>
				<view class="agent-top-switch" :class="{ 'agent-top-switch--active': agentEnabled }">
					<view class="agent-top-knob"></view>
				</view>
			</view>
			<view class="asr-status-line">
				<text class="asr-title">{{ livePanelTitle }}</text>
				<text class="asr-status" :class="{ 'asr-status--live': asrRecording || agentEnabled }">{{ livePanelStatus }}</text>
			</view>
			<view v-if="asrReconnectVisible" class="asr-reconnect" @click="reconnectAsr">重新连接识别</view>

			<scroll-view class="asr-scroll" scroll-y>
				<view v-if="agentEnabled && aiSummaryItems.length === 0" class="asr-empty">{{ livePanelEmptyText }}</view>
				<view v-else-if="agentEnabled" class="ai-summary-stream">
					<view v-for="item in aiSummaryItems" :key="item.id" class="ai-summary-card">
						<view class="ai-summary-meta">
							<text>AI 总结</text>
							<text>{{ item.time }}</text>
						</view>
						<text class="ai-summary-text">{{ item.text }}</text>
					</view>
				</view>
				<view v-else-if="asrItems.length === 0" class="asr-empty">{{ livePanelEmptyText }}</view>
				<view v-else class="asr-stream">
					<view
						v-for="item in asrItems"
						:key="item.id"
						class="asr-bubble"
						:class="{ 'asr-bubble--partial': !item.isFinal, 'asr-bubble--self': item.isSelf }"
					>
						<text class="asr-speaker">{{ item.speaker }}</text>
						<text class="asr-text">{{ item.text }}</text>
					</view>
				</view>
			</scroll-view>
		</view>

		<!-- 原有底部弹窗：成员 / 更多 -->
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
				<view class="more-row" @click="openAsrPanel">
					<text>语音识别 & AI总结</text>
					<text>打开实时转写面板</text>
				</view>
				<view class="more-row more-row--switch"><text>智能体总结</text><switch :checked="agentEnabled" color="#86C9A8" @change="toggleAgentSummary" /></view>
				<view class="more-row" @click="copyRoomCode"><text>复制会议号</text><text>{{ roomCode || '未生成' }}</text></view>
				<view class="more-row" @click="shareMeeting"><text>分享会议</text><text>复制邀请文案</text></view>
				<view class="more-row" @click="openMeetingDetail"><text>会议详情</text><text>查看会议号与参会人</text></view>
			</view>
		</view>
	</view>
</template>

<script>
import { endMeeting as finishMeetingApi, getMeetingDetail, streamLlmChat } from '@/api/ai.js'
import { getCurrentDisplayName, toMeetingMembers } from '@/utils/meetingUser.js'
import { BASE_URL } from '@/utils/config.js'
import { getToken, getUserInfo } from '@/utils/storage.js'

export default {
	data() {
		return {
			sessionId: '',
			title: '项目进度同步会',
			roomCode: '',
			muted: false,
			cameraOpen: false,
			shareScreenOpen: false,
			elapsedSeconds: 0,
			timer: null,
			asrSocket: null,
			asrRecorder: null,
			asrBrowserStream: null,
			asrAudioContext: null,
			asrAudioSource: null,
			asrAudioProcessor: null,
			asrAudioSilence: null,
			asrAudioWorkletUrl: '',
			asrPcmBuffer: null,
			asrRecording: false,
			asrSocketReady: false,
			asrServiceReady: false,
			asrStatusText: '等待连接',
			asrLastError: '',
			asrManualClosing: false,
			asrReconnectTimer: null,
			asrReconnectAttempts: 0,
			asrItems: [],
			asrSeq: 0,
			agentEnabled: false,
			aiSummaryItems: [],
			aiSummarySeq: 0,
			aiSummaryStatusText: '等待发言',
			aiSummaryTimer: null,
			aiSummaryRunning: false,
			aiSummaryPending: false,
			aiSummaryActiveId: '',
			lastSummaryInput: '',
			lastSummaryErrorInput: '',
			meetingTranscriptLines: [],
			memberPanelVisible: false,
			morePanelVisible: false,
			asrPanelVisible: false,
			members: []
		}
	},
	onLoad(options) {
		if (options?.sessionId) this.sessionId = decodeURIComponent(options.sessionId)
		if (options?.title) this.title = decodeURIComponent(options.title)
		if (options?.roomCode) this.roomCode = decodeURIComponent(options.roomCode)
		if (options?.micOn === '0' || options?.micOn === 'false') this.muted = true
		if (options?.cameraOn === '1') this.cameraOpen = true
		if (options?.shareScreen === '1') this.shareScreenOpen = true

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
			return this.memberPanelVisible || this.morePanelVisible || this.asrPanelVisible
		},
		compactRoomCode() {
			return (this.roomCode || '').replace(/\s+/g, '')
		},
		visibleMembers() {
			return this.members.slice(0, 4)
		},
		livePanelTitle() {
			return this.agentEnabled ? 'AI 实时总结流' : '语音识别弹幕'
		},
		livePanelStatus() {
			return this.agentEnabled ? this.aiSummaryStatusText : this.asrStatusText
		},
		livePanelEmptyText() {
			return this.agentEnabled ? '开启后会根据实时识别内容生成滚动会议总结' : '识别到发言后，会在这里显示每位成员说的话'
		},
		asrReconnectVisible() {
			return !this.asrSocketReady && !this.muted && !!this.sessionId
		}
	},
	methods: {
		// 新增：打开ASR弹窗
		openAsrPanel() {
			this.closePanel()
			this.asrPanelVisible = true
		},
		closeAsrPanel() {
			this.asrPanelVisible = false
		},
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
		toggleCamera() {
			this.cameraOpen = !this.cameraOpen
			if(this.cameraOpen){
				uni.showToast({title: '正在启动摄像头', icon:'none'})
			}else{
				uni.showToast({title: '已关闭视频', icon:'none'})
			}
		},
		toggleShareScreen() {
			this.shareScreenOpen = !this.shareScreenOpen
			if(this.shareScreenOpen){
				uni.showToast({title: '请求开启屏幕共享', icon:'none'})
			}else{
				uni.showToast({title: '停止屏幕共享', icon:'none'})
			}
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
			this.clearAsrReconnectTimer()
			const token = getToken()
			if (!token) {
				this.asrStatusText = '请先登录后使用识别'
				return
			}
			this.asrManualClosing = false
			this.asrLastError = ''
			const url = this.buildAsrSocketUrl(token)
			this.asrSocket = uni.connectSocket({ url, complete: () => {} })
			this.asrSocket.onOpen(() => {
				this.asrSocketReady = true
				this.asrReconnectAttempts = 0
				this.asrStatusText = this.muted ? '已静音' : '识别服务连接中'
			})
			this.asrSocket.onMessage((event) => this.handleAsrMessage(event.data))
			this.asrSocket.onError((error) => {
				this.asrLastError = error?.errMsg || '识别连接异常'
				this.asrStatusText = this.asrLastError
				this.asrSocketReady = false
			})
			this.asrSocket.onClose(() => {
				const shouldReconnect = !this.asrManualClosing && !this.muted && !!this.sessionId
				this.asrSocketReady = false
				this.asrServiceReady = false
				this.asrSocket = null
				this.asrRecording = false
				this.stopBrowserAsrRecording()
				if (shouldReconnect) {
					this.asrStatusText = this.asrLastError || '识别已断开，正在重连'
					this.scheduleAsrReconnect()
				} else {
					this.asrStatusText = this.asrLastError || '识别已停止'
				}
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
			if (this.asrSocketReady && this.asrServiceReady) this.startAsrRecording()
		},
		startAsrRecording() {
			if (this.asrRecording || !this.asrSocketReady || !this.asrServiceReady || !this.asrSocket) return
			if (typeof uni.getRecorderManager !== 'function') {
				this.startBrowserAsrRecording()
				return
			}
			if (!this.asrRecorder) {
				try {
					this.asrRecorder = uni.getRecorderManager()
				} catch (error) {
					this.asrRecorder = null
					this.startBrowserAsrRecording()
					return
				}
				if (!this.asrRecorder || typeof this.asrRecorder.onFrameRecorded !== 'function') {
					this.asrRecorder = null
					this.startBrowserAsrRecording()
					return
				}
				this.asrRecorder.onFrameRecorded((res) => {
					if (this.asrSocketReady && this.asrServiceReady && this.asrSocket && res.frameBuffer) {
						this.asrSocket.send({ data: res.frameBuffer })
					}
				})
				this.asrRecorder.onError(() => {
					this.asrRecording = false
					this.asrStatusText = '录音权限或设备异常'
					this.startBrowserAsrRecording()
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
				this.startBrowserAsrRecording()
			}
		},
		async startBrowserAsrRecording() {
			if (this.asrRecording || !this.asrSocketReady || !this.asrServiceReady || !this.asrSocket) return
			if (typeof navigator === 'undefined' || !navigator.mediaDevices?.getUserMedia) {
				this.asrStatusText = '当前端不支持录音，仅接收弹幕'
				return
			}
			const AudioContextClass = typeof window !== 'undefined' && (window.AudioContext || window.webkitAudioContext)
			if (!AudioContextClass) {
				this.asrStatusText = '当前浏览器不支持音频采集'
				return
			}
			try {
				this.asrBrowserStream = await navigator.mediaDevices.getUserMedia({ audio: true })
				this.asrAudioContext = new AudioContextClass()
				this.asrAudioSource = this.asrAudioContext.createMediaStreamSource(this.asrBrowserStream)
				await this.connectBrowserAudioProcessor()
				this.asrRecording = true
				this.asrStatusText = '正在识别'
			} catch (error) {
				this.asrRecording = false
				this.asrStatusText = '浏览器录音权限未开启'
				this.stopBrowserAsrRecording()
			}
		},
		async connectBrowserAudioProcessor() {
			if (await this.connectAudioWorkletProcessor()) return
			this.asrAudioProcessor = this.asrAudioContext.createScriptProcessor(2048, 1, 1)
			this.asrAudioProcessor.onaudioprocess = (event) => {
				this.sendBrowserPcmFrame(event.inputBuffer.getChannelData(0))
			}
			this.asrAudioSource.connect(this.asrAudioProcessor)
			this.asrAudioProcessor.connect(this.asrAudioContext.destination)
		},
		async connectAudioWorkletProcessor() {
			if (
				!this.asrAudioContext?.audioWorklet ||
				typeof AudioWorkletNode === 'undefined' ||
				typeof Blob === 'undefined' ||
				typeof URL === 'undefined'
			) {
				return false
			}
			try {
				const workletCode = `
					class AsrPcmProcessor extends AudioWorkletProcessor {
						process(inputs) {
							const input = inputs[0]
							if (input && input[0]) {
								this.port.postMessage(input[0].slice(0))
							}
							return true
						}
					}
					registerProcessor('asr-pcm-processor', AsrPcmProcessor)
				`
				this.asrAudioWorkletUrl = URL.createObjectURL(new Blob([workletCode], { type: 'application/javascript' }))
				await this.asrAudioContext.audioWorklet.addModule(this.asrAudioWorkletUrl)
				this.asrAudioProcessor = new AudioWorkletNode(this.asrAudioContext, 'asr-pcm-processor')
				this.asrAudioProcessor.port.onmessage = (event) => {
					this.sendBrowserPcmFrame(event.data)
				}
				this.asrAudioSilence = this.asrAudioContext.createGain()
				this.asrAudioSilence.gain.value = 0
				this.asrAudioSource.connect(this.asrAudioProcessor)
				this.asrAudioProcessor.connect(this.asrAudioSilence)
				this.asrAudioSilence.connect(this.asrAudioContext.destination)
				return true
			} catch (error) {
				this.cleanupAudioWorkletUrl()
				this.asrAudioProcessor = null
				this.asrAudioSilence = null
				return false
			}
		},
		sendBrowserPcmFrame(input) {
			const sampleRate = this.asrAudioContext?.sampleRate
			if (!input || !sampleRate) return
			const pcm = this.floatTo16kPcm(input, sampleRate)
			this.sendAlignedPcmFrames(pcm)
		},
		sendAlignedPcmFrames(pcm) {
			if (!pcm || !this.asrSocketReady || !this.asrServiceReady || !this.asrSocket) return
			const frameSize = 1280
			const incoming = new Uint8Array(pcm)
			const pending = this.asrPcmBuffer || new Uint8Array(0)
			const merged = new Uint8Array(pending.length + incoming.length)
			merged.set(pending)
			merged.set(incoming, pending.length)
			let offset = 0
			while (offset + frameSize <= merged.length) {
				const frame = merged.slice(offset, offset + frameSize)
				this.asrSocket.send({ data: frame.buffer })
				offset += frameSize
			}
			this.asrPcmBuffer = merged.slice(offset)
		},
		flushPendingPcmFrame() {
			if (!this.asrPcmBuffer || !this.asrPcmBuffer.length || !this.asrSocketReady || !this.asrServiceReady || !this.asrSocket) return
			const frameSize = 1280
			const frame = new Uint8Array(frameSize)
			frame.set(this.asrPcmBuffer)
			this.asrSocket.send({ data: frame.buffer })
			this.asrPcmBuffer = null
		},
		floatTo16kPcm(input, inputSampleRate) {
			const outputSampleRate = 16000
			const ratio = inputSampleRate / outputSampleRate
			const outputLength = Math.floor(input.length / ratio)
			if (outputLength <= 0) return null
			const buffer = new ArrayBuffer(outputLength * 2)
			const view = new DataView(buffer)
			for (let i = 0; i < outputLength; i++) {
				const sampleIndex = Math.floor(i * ratio)
				const sample = Math.max(-1, Math.min(1, input[sampleIndex]))
				view.setInt16(i * 2, sample < 0 ? sample * 0x8000 : sample * 0x7fff, true)
			}
			return buffer
		},
		stopAsrRecording() {
			this.asrManualClosing = true
			this.clearAsrReconnectTimer()
			if (this.asrRecorder && this.asrRecording) {
				try {
					this.asrRecorder.stop()
				} catch (error) {}
			}
			if (this.asrSocketReady && this.asrSocket) {
				this.flushPendingPcmFrame()
				this.asrSocket.send({ data: JSON.stringify({ stop: true }) })
			}
			this.stopBrowserAsrRecording()
			this.asrRecording = false
			this.asrStatusText = '已静音'
		},
		closeAsr() {
			this.clearAiSummaryTimer()
			this.asrManualClosing = true
			this.clearAsrReconnectTimer()
			if (this.asrRecorder && this.asrRecording) {
				try {
					this.asrRecorder.stop()
				} catch (error) {}
			}
			if (this.asrSocket) {
				try {
					this.flushPendingPcmFrame()
					this.asrSocket.send({ data: JSON.stringify({ stop: true }) })
					this.asrSocket.close()
				} catch (error) {}
			}
			this.stopBrowserAsrRecording()
			this.asrRecording = false
			this.asrSocketReady = false
			this.asrServiceReady = false
			this.asrSocket = null
		},
		reconnectAsr() {
			this.asrLastError = ''
			this.asrStatusText = '正在重新连接'
			this.asrManualClosing = false
			this.clearAsrReconnectTimer()
			if (this.asrSocket) {
				try {
					this.asrSocket.close()
				} catch (error) {}
				this.asrSocket = null
			}
			this.openAsrSocket()
		},
		scheduleAsrReconnect() {
			if (this.asrReconnectAttempts >= 3) {
				this.asrStatusText = this.asrLastError || '识别已断开，请手动重连'
				return
			}
			this.clearAsrReconnectTimer()
			this.asrReconnectAttempts += 1
			const delay = Math.min(1000 * this.asrReconnectAttempts, 3000)
			this.asrReconnectTimer = setTimeout(() => {
				this.asrStatusText = `正在重连识别(${this.asrReconnectAttempts}/3)`
				this.openAsrSocket()
			}, delay)
		},
		clearAsrReconnectTimer() {
			if (this.asrReconnectTimer) {
				clearTimeout(this.asrReconnectTimer)
				this.asrReconnectTimer = null
			}
		},
		stopBrowserAsrRecording() {
			if (this.asrAudioProcessor) {
				try {
					if (this.asrAudioProcessor.port) {
						this.asrAudioProcessor.port.onmessage = null
					}
					this.asrAudioProcessor.disconnect()
					this.asrAudioProcessor.onaudioprocess = null
				} catch (error) {}
			}
			if (this.asrAudioSilence) {
				try {
					this.asrAudioSilence.disconnect()
				} catch (error) {}
			}
			if (this.asrAudioSource) {
				try {
					this.asrAudioSource.disconnect()
				} catch (error) {}
			}
			if (this.asrAudioContext) {
				try {
					this.asrAudioContext.close()
				} catch (error) {}
			}
			if (this.asrBrowserStream) {
				this.asrBrowserStream.getTracks().forEach(track => track.stop())
			}
			this.cleanupAudioWorkletUrl()
			this.asrPcmBuffer = null
			this.asrAudioProcessor = null
			this.asrAudioSilence = null
			this.asrAudioSource = null
			this.asrAudioContext = null
			this.asrBrowserStream = null
		},
		cleanupAudioWorkletUrl() {
			if (this.asrAudioWorkletUrl && typeof URL !== 'undefined') {
				try {
					URL.revokeObjectURL(this.asrAudioWorkletUrl)
				} catch (error) {}
			}
			this.asrAudioWorkletUrl = ''
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
				this.asrServiceReady = true
				this.asrStatusText = this.muted ? '已静音' : (this.asrRecording ? '正在识别' : '识别已连接')
				if (!this.muted) this.startAsrRecording()
				return
			}
			if (payload.type === 'asr_error') {
				this.asrLastError = payload.message || '识别异常'
				this.asrStatusText = this.asrLastError
				this.asrServiceReady = false
				return
			}
			if (payload.type === 'asr_result') {
				const item = {
					speakerUserId: payload.speakerUserId || '',
					speaker: payload.speaker || '参会成员',
					text: payload.text || '',
					isFinal: !!payload.isFinal,
					isSelf: this.isSelfSpeaker(payload)
				}
				this.upsertAsrItem(item)
				if (item.isFinal) {
					this.appendTranscriptLine(item)
				}
			}
		},
		isSelfSpeaker(payload) {
			const user = getUserInfo()
			const currentId = user?.id || user?.userId || ''
			const currentName = getCurrentDisplayName()
			if (currentId && payload.speakerUserId && String(currentId) === String(currentId)) {
				return true
			}
			return !!currentName && payload.speaker === currentName
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
		appendTranscriptLine(item) {
			const text = (item.text || '').trim()
			if (!text) return
			this.meetingTranscriptLines.push(`${item.speaker}：${text}`)
			if (this.meetingTranscriptLines.length > 80) {
				this.meetingTranscriptLines = this.meetingTranscriptLines.slice(this.meetingTranscriptLines.length - 80)
			}
			if (this.agentEnabled) {
				this.scheduleAiSummary()
			}
		},
		toggleAgentSummary(event) {
			this.setAgentSummary(!!event.detail.value, true)
		},
		setAgentSummary(enabled, shouldClosePanel = false) {
			this.agentEnabled = !!enabled
			if (shouldClosePanel) this.closePanel()
			if (this.agentEnabled) {
				this.aiSummaryStatusText = this.hasSummarySourceText() ? '准备总结' : '等待发言'
				this.scheduleAiSummary(0)
			} else {
				this.clearAiSummaryTimer()
				this.aiSummaryStatusText = '等待发言'
			}
		},
		scheduleAiSummary(delay = 0) {
			if (!this.agentEnabled) return
			this.clearAiSummaryTimer()
			this.aiSummaryTimer = setTimeout(() => {
				this.runAiSummary()
			}, delay)
		},
		clearAiSummaryTimer() {
			if (this.aiSummaryTimer) {
				clearTimeout(this.aiSummaryTimer)
				this.aiSummaryTimer = null
			}
		},
		async runAiSummary() {
			if (!this.agentEnabled || !this.hasSummarySourceText()) {
				this.aiSummaryStatusText = this.agentEnabled ? '等待发言' : '等待发言'
				return
			}
			if (!this.sessionId) {
				this.aiSummaryStatusText = '会议未就绪'
				return
			}
			const content = this.buildSummaryInput()
			if (!content || content === this.lastSummaryInput) {
				this.aiSummaryStatusText = '已是最新'
				return
			}
			if (this.aiSummaryRunning) {
				this.aiSummaryPending = true
				return
			}
			this.aiSummaryRunning = true
			this.aiSummaryPending = false
			this.aiSummaryStatusText = '智能体总结中'
			this.lastSummaryInput = content
			this.lastSummaryErrorInput = ''
			const streamItem = this.createAiSummaryItem('正在生成总结...')
			let streamText = ''
			let streamError = ''
			try {
				await streamLlmChat({
					sessionId: `meeting-${this.sessionId}-summary`,
					agentName: 'meeting_summary_agent',
					input: content
				}, {
					onDelta: (delta) => {
						if (!delta) return
						streamText += delta
						this.updateAiSummaryItem(streamItem.id, streamText)
					},
					onDone: (payload) => {
						const answer = (payload?.answer || '').trim()
						if (answer && !streamText.trim()) {
							streamText = answer
							this.updateAiSummaryItem(streamItem.id, streamText)
						}
					},
					onError: (payload) => {
						streamError = payload?.message || '流式总结失败'
					}
				})
				if (streamError) {
					throw new Error(streamError)
				}
				if (streamText.trim()) {
					this.aiSummaryStatusText = '总结已更新'
				} else {
					this.aiSummaryStatusText = '智能体未返回内容'
					this.updateAiSummaryItem(streamItem.id, '已尝试调用会议总结智能体，但本次没有返回可用总结。请继续发言后我会再次尝试。')
				}
			} catch (error) {
				this.aiSummaryStatusText = '智能体暂不可用'
				if (this.lastSummaryErrorInput !== content) {
					this.lastSummaryErrorInput = content
					const message = error?.msg || error?.message || '请求失败'
					const tip = message.includes('模型') || message.includes('配置')
						? '请先在后台 AI 模块完成语言模型配置并测试成功'
						: message
					this.updateAiSummaryItem(streamItem.id, `已尝试调用会议总结智能体，但请求失败：${tip}`)
				}
			} finally {
				this.aiSummaryRunning = false
				if (this.aiSummaryPending && this.agentEnabled) {
					this.scheduleAiSummary(0)
				}
			}
		},
		buildSummaryInput() {
			const liveLines = this.asrItems
				.filter(item => !item.isFinal)
				.filter(item => item.text && item.text.trim())
				.map(item => `${item.speaker}：${item.text.trim()}`)
			const transcript = [...this.meetingTranscriptLines, ...liveLines].slice(-40).join('\n')
			if (!transcript.trim()) return ''
			const input = [
				`会议主题：${this.title}`,
				'请根据以下实时转写生成一段不超过120字的阶段性会议总结，包含已讨论内容、最新进展和待跟进事项；不要输出未在转写中出现的事实。',
				'实时转写：',
				transcript
			].join('\n')
			return input.length > 3900 ? input.slice(input.length - 3900) : input
		},
		hasSummarySourceText() {
			return this.meetingTranscriptLines.length > 0 || this.asrItems.some(item => item.text && item.text.trim())
		},
		createAiSummaryItem(text) {
			const now = new Date()
			const time = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}:${String(now.getSeconds()).padStart(2, '0')}`
			const item = {
				id: `ai-summary-${Date.now()}-${this.aiSummarySeq++}`,
				text,
				time
			}
			this.aiSummaryItems.push({
				...item
			})
			if (this.aiSummaryItems.length > 5) {
				this.aiSummaryItems = this.aiSummaryItems.slice(this.aiSummaryItems.length - 5)
			}
			this.aiSummaryActiveId = item.id
			return item
		},
		updateAiSummaryItem(id, text) {
			const item = this.aiSummaryItems.find(summary => summary.id === id)
			if (item) {
				item.text = text
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
					uni.showToast({ title: '会议已结束，AI正在整理', icon: 'none' })
				} catch (error) {}
			}
			this.stopTimer()
			uni.redirectTo({ url: '/subpackage_meeting/meetingRoom/meetingRoom' })
		}
	}
}
</script>

<style lang="scss" scoped>
.live-page {
	min-height: 100vh;
	background: #ffffff;
	color: #151f25;
}
.status-bar { height: var(--status-bar-height); min-height: 42rpx; }

/* 顶部导航 */
.live-top {
	display: grid;
	grid-template-columns: 70rpx 1fr 70rpx;
	height: 98rpx;
	align-items: center;
	padding: 0 22rpx;
}
.top-left-empty{}
.live-title-wrap {
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: 6rpx;
}
.live-title { font-size: 27rpx; font-weight: 850; color: #0f1a20; }
.live-time { font-size: 20rpx; color: #5c6470; }
.end-text { color: #ff5f55; font-size: 25rpx; text-align: right; }

/* 中间主画面区域 */
.main-view-area {
	flex:1;
	display: flex;
	justify-content: center;
	padding: 60rpx 40rpx;
	min-height: calc(100vh - 320rpx);
	box-sizing: border-box;
}
.member-grid {
	display: grid;
	grid-template-columns: repeat(2, 1fr);
	gap: 24rpx;
	width: 100%;
	max-width: 620rpx;
}
.member-card {
	position: relative;
	height: 280rpx;
	border-radius: 24rpx;
	overflow: hidden;
	background: linear-gradient(180deg, #e9eeef, #bec8ca);
	display: flex;
	align-items: flex-end;
	justify-content: center;
}
.avatar-b { background: linear-gradient(180deg, #e2ecee, #a7c0c8); }
.avatar-c { background: linear-gradient(180deg, #e9ecec, #ccd2d3); }
.avatar-d { background: linear-gradient(180deg, #f0eeea, #d9c8bd); }

.name-chip {
	position: absolute;
	left: 16rpx;
	bottom: 16rpx;
	min-width: 48rpx;
	height: 32rpx;
	padding: 0 14rpx;
	border-radius: 16rpx;
	background: rgba(22, 29, 36, .48);
	color: #fff;
	font-size: 20rpx;
	display: flex;
	align-items: center;
	justify-content: center;
}
.face { position: relative; width: 160rpx; height: 220rpx; margin-bottom: 0; }
.head {
	position: absolute;
	left: 44rpx;
	top: 32rpx;
	width: 72rpx;
	height: 84rpx;
	border-radius: 38rpx 38rpx 32rpx 32rpx;
	background: #f4c9a6;
	z-index: 2;
}
.hair {
	position: absolute;
	left: 36rpx;
	top: 20rpx;
	width: 84rpx;
	height: 60rpx;
	border-radius: 46rpx 46rpx 20rpx 20rpx;
	background: #242424;
	z-index: 3;
}
.body {
	position: absolute;
	left: 10rpx;
	bottom: 0;
	width: 136rpx;
	height: 96rpx;
	border-radius: 42rpx 42rpx 0 0;
	background: #f7f7f7;
	z-index: 1;
}
.avatar-b .body { background: #316f8e; }
.avatar-c .hair { background: #1e1d1c; }
.avatar-c .body { background: #dfe6ea; }
.avatar-d .hair { background: #3c2d26; }
.avatar-d .body { background: #efe7df; }

/* 底部操作栏 */
.live-bottom {
	padding: 20rpx 22rpx calc(env(safe-area-inset-bottom) + 20rpx);
}
.control-row {
	display: grid;
	grid-template-columns: repeat(5, 1fr);
	gap: 8rpx;
}
.control-item {
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: 10rpx;
	color: #1c272d;
	font-size: 20rpx;
}
.control-item--active { color: #86C9A8; }
.control-icon {
	width: 56rpx;
	height: 56rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	font-size: 34rpx;
}

/* 通用底部弹窗 */
.panel-mask {
	position: fixed;
	inset: 0;
	background: rgba(0, 0, 0, .24);
	z-index: 30;
}
.sheet-panel {
	position: fixed;
	left: 0;
	right: 0;
	bottom: 0;
	padding: 16rpx 28rpx calc(env(safe-area-inset-bottom) + 34rpx);
	border-radius: 32rpx 32rpx 0 0;
	background: #ffffff;
	box-shadow: 0 -18rpx 48rpx rgba(31,42,48,.12);
	z-index: 31;
	max-height: 75vh;
	display: flex;
	flex-direction: column;
}
.asr-panel {
	min-height: 60vh;
}
.sheet-handle {
	width: 72rpx;
	height: 8rpx;
	border-radius: 999rpx;
	background: rgba(134, 201, 168, .12);
	margin: 0 auto 24rpx;
}
.sheet-title-row {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-bottom: 18rpx;
}
.sheet-title { color: #151f25; font-size: 30rpx; font-weight: 900; }
.sheet-close { color: #666; font-size: 36rpx; }

/* ASR弹窗内样式 */
.agent-top-toggle {
	margin: 12rpx 0 16rpx;
	min-height: 86rpx;
	padding: 16rpx 18rpx;
	border-radius: 22rpx;
	background: #f7f7f7;
	border: 2rpx solid #efefef;
	display: flex;
	align-items: center;
	justify-content: space-between;
	box-shadow: 0 14rpx 30rpx rgba(31,42,48,.06);
}
.agent-top-toggle--active {
	background: rgba(134, 201, 168, .12);
	border-color: rgba(134, 201, 168, .35);
}
.agent-top-copy { display: flex; flex-direction: column; gap: 8rpx; }
.agent-top-title { color: #151f25; font-size: 27rpx; font-weight: 950; }
.agent-top-desc { color: #58636a; font-size: 21rpx; }
.agent-top-switch {
	width: 88rpx;
	height: 48rpx;
	padding: 5rpx;
	border-radius: 999rpx;
	background: #e4e4e4;
	box-sizing: border-box;
	display: flex;
	justify-content: flex-start;
}
.agent-top-switch--active {
	justify-content: flex-end;
	background: #86C9A8;
}
.agent-top-knob {
	width: 38rpx;
	height: 38rpx;
	border-radius: 50%;
	background: #fff;
	box-shadow: 0 5rpx 12rpx rgba(0,0,0,.18);
}

.asr-mode-row {
	display: grid;
	grid-template-columns: repeat(2, 1fr);
	gap: 10rpx;
	margin-bottom: 18rpx;
	padding: 6rpx;
	border-radius: 18rpx;
	background: #ececec;
}
.asr-mode-pill {
	height: 50rpx;
	border-radius: 14rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	color: #777;
	font-size: 22rpx;
	font-weight: 850;
}
.asr-mode-pill--active {
	background: #fff;
	color: #86C9A8;
	box-shadow: 0 8rpx 18rpx rgba(31,42,48,.06);
}
.asr-status-line {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-bottom: 16rpx;
}
.asr-title { color: #1c272d; font-size: 24rpx; font-weight: 850; }
.asr-status {
	height: 34rpx;
	padding: 0 16rpx;
	border-radius: 999rpx;
	background: #e9e9e9;
	color: #666;
	font-size: 19rpx;
	display: flex;
	align-items: center;
}
.asr-status--live {
	background: rgba(134, 201, 168, .14);
	color: #86C9A8;
}
.asr-reconnect {
	height: 54rpx;
	margin-bottom: 18rpx;
	border-radius: 16rpx;
	background: rgba(255, 95, 85, .12);
	color: #e05046;
	display: flex;
	align-items: center;
	justify-content: center;
	font-size: 22rpx;
	font-weight: 900;
}
.asr-scroll {
	flex: 1;
}
.asr-empty {
	margin-top: 128rpx;
	text-align: center;
	color: #999;
	font-size: 23rpx;
}
.asr-stream { display: flex; flex-direction: column; gap: 14rpx; }
.ai-summary-stream { display: flex; flex-direction: column; gap: 16rpx; }
.ai-summary-card {
	padding: 18rpx 20rpx;
	border-radius: 22rpx;
	background: rgba(134, 201, 168, .08);
	border: 1rpx solid rgba(134, 201, 168, .16);
	box-shadow: 0 12rpx 28rpx rgba(31,42,48,.05);
}
.ai-summary-meta {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-bottom: 10rpx;
	color: #86C9A8;
	font-size: 20rpx;
	font-weight: 900;
}
.ai-summary-text {
	color: #151f25;
	font-size: 24rpx;
	line-height: 1.55;
	white-space: pre-wrap;
}
.asr-bubble {
	max-width: 92%;
	align-self: flex-start;
	padding: 13rpx 17rpx;
	border-radius: 20rpx 20rpx 20rpx 8rpx;
	background: #ebebeb;
}
.asr-bubble--self {
	align-self: flex-end;
	border-radius: 20rpx 20rpx 8rpx 20rpx;
	background: rgba(134, 201, 168, .16);
}
.asr-bubble--partial { opacity: .72; }
.asr-speaker {
	margin-right: 12rpx;
	color: #86C9A8;
	font-size: 20rpx;
	font-weight: 900;
}
.asr-text {
	color: #151f25;
	font-size: 23rpx;
	line-height: 1.45;
}

/* 成员弹窗样式 */
.member-list, .more-list { display: flex; flex-direction: column; gap: 12rpx; }
.member-row, .more-row { min-height: 78rpx; border-radius: 18rpx; background: #f7f7f7; display: flex; align-items: center; padding: 0 20rpx; }
.member-row { gap: 18rpx; }
.member-avatar { width: 48rpx; height: 48rpx; border-radius: 50%; background: #86C9A8; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 22rpx; font-weight: 900; }
.member-info { flex: 1; display: flex; flex-direction: column; gap: 4rpx; }
.member-name { color: #151f25; font-size: 25rpx; font-weight: 800; }
.member-role, .member-mic { color: #666; font-size: 21rpx; }
.more-row { justify-content: space-between; color: #151f25; font-size: 25rpx; }
.more-row--switch { min-height: 88rpx; }
.more-row text:last-child { color: #666; font-size: 22rpx; }
</style>