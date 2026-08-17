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
			<text class="end-text" @click="openEndPanel">结束</text>
		</view>

		<!-- 中间主区域：参会画面居中（核心视觉） -->
		<view class="main-view-area">
			<!-- AI 实时摘要画布：开关打开后显示在成员画布上方，成员自然向下压缩，其余元素（弹幕/字幕/底部导航）原位保留；用 v-show 纯隐藏，保留已生成摘要数据与滚动位置 -->
			<view v-show="agentEnabled" class="ai-summary-canvas">
				<view class="ai-summary-canvas-head">
					<text class="ai-summary-canvas-title">AI 实时摘要</text>
					<text class="ai-summary-canvas-status" :class="{ 'ai-summary-canvas-status--live': aiSummaryRunning }">{{ aiSummaryStatusText }}</text>
				</view>
				<scroll-view class="ai-summary-canvas-scroll" scroll-y>
					<view v-if="aiSummaryItems.length === 0" class="ai-summary-canvas-empty">{{ livePanelEmptyText }}</view>
					<view v-else class="ai-summary-canvas-list">
						<view v-for="item in aiSummaryItems" :key="item.id" class="ai-summary-canvas-item">
							<view class="ai-summary-canvas-meta">
								<text>AI 总结</text>
								<text>{{ item.time }}</text>
							</view>
							<text class="ai-summary-canvas-text">{{ item.text }}</text>
						</view>
					</view>
				</scroll-view>
			</view>
			<view class="member-slider" @touchstart="handleSwipeStart" @touchend="handleSwipeEnd" @mousedown="handleSwipeStart" @mouseup="handleSwipeEnd">
				<!-- 顶部弹幕滚动层：扣字消息从右向左飘过，不阻挡成员滑动 -->
				<view class="danmaku-layer">
					<text v-for="item in danmakuItems" :key="item.id" class="danmaku-item">{{ item.text }}</text>
				</view>
				<view class="member-pages" :style="{ transform: 'translateX(-' + memberPageIndex * 100 + '%)' }">
					<view
						v-for="(page, pageIndex) in pagedMembers"
						:key="pageIndex"
						class="member-grid"
						:class="{ 'member-grid--compact': agentEnabled }"
					>
						<view
							v-for="member in page"
							:key="member.name"
							class="member-card"
							:class="{ 'member-card--speaking': member.speaking }"
						>
							<view v-if="member.speaking" class="speaking-tag">正在发言</view>
							<view v-if="member.name === hostName" class="host-tag">主持人</view>
							<view class="member-avatar">
								<svg class="member-avatar-icon" viewBox="0 0 96 96" fill="currentColor">
									<circle cx="48" cy="30" r="20"/>
									<path d="M14 90c0-19 15-30 34-30s34 11 34 30z"/>
								</svg>
							</view>
							<view class="member-info-row">
								<svg
									class="member-mic-icon"
									:class="{ 'member-mic-icon--speaking': member.speaking }"
									viewBox="0 0 24 24"
									fill="none"
									stroke="currentColor"
									stroke-width="2"
									stroke-linecap="round"
									stroke-linejoin="round"
								>
									<path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"/>
									<path d="M19 10v2a7 7 0 0 1-14 0v-2"/>
									<line x1="12" y1="19" x2="12" y2="23"/>
									<line x1="8" y1="23" x2="16" y2="23"/>
								</svg>
								<text class="member-name">{{ member.name }}</text>
							</view>
						</view>
					</view>
				</view>
			</view>
			<!-- 分页行：左侧扣字聊天浮窗 + 居中分页指示器（多页时显示） -->
			<view class="pager-chat-row">
				<view class="chat-float">
					<input
						class="chat-input"
						v-model="chatDraft"
						confirm-type="send"
						@confirm="sendChatMessage"
					/>
					<view class="chat-send" @click="sendChatMessage">发送</view>
				</view>
				<view v-if="pagedMembers.length > 1" class="member-pager">
					<view class="member-pager-dots">
						<view
							v-for="(page, pageIndex) in pagedMembers"
							:key="pageIndex"
							class="member-pager-dot"
							:class="{ 'member-pager-dot--active': pageIndex === memberPageIndex }"
						></view>
					</view>
					<text class="member-pager-text">{{ memberPageIndex + 1 }} / {{ pagedMembers.length }}</text>
				</view>
			</view>

			<!-- 实时字幕区域 -->
			<view class="subtitle-card">
				<view class="subtitle-header">
					<text class="subtitle-title">实时字幕</text>
					<view class="subtitle-summary-toggle" @click="setAgentSummary(!agentEnabled)">
						<text class="subtitle-summary-text">AI 总结</text>
						<view class="subtitle-summary-switch" :class="{ 'subtitle-summary-switch--active': agentEnabled }">
							<view class="subtitle-summary-knob"></view>
						</view>
					</view>
				</view>
				<scroll-view class="subtitle-body" scroll-y>
					<view v-if="subtitleLines.length === 0" class="subtitle-empty"></view>
					<view v-else class="subtitle-list">
						<text v-for="(item, index) in subtitleLines" :key="item.id || index" class="subtitle-item">{{ item.speaker }}：{{ item.text }}</text>
					</view>
				</scroll-view>
			</view>
		</view>

		<!-- 底部固定操作栏 -->
		<view class="live-bottom">
			<view class="control-row">
				<view class="control-item" :class="{ 'control-item--active': !muted }" @click="toggleMute">
					<view class="control-icon">
						<svg class="control-icon-svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
							<path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"/>
							<path d="M19 10v2a7 7 0 0 1-14 0v-2"/>
							<line x1="12" y1="19" x2="12" y2="23"/>
							<line x1="8" y1="23" x2="16" y2="23"/>
						</svg>
					</view>
					<text class="control-label">麦克风</text>
				</view>
				<view class="control-item" @click="showMembers">
					<view class="control-icon">
						<svg class="control-icon-svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
							<path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
							<circle cx="12" cy="7" r="4"/>
						</svg>
					</view>
					<text class="control-label">成员({{ members.length }})</text>
				</view>
				<view class="control-item" :class="{ 'control-item--active': subtitlePanelVisible }" @click="openSubtitlePanel">
					<view class="control-icon">
						<svg class="control-icon-svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
							<path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
							<circle cx="9" cy="10" r="1" fill="currentColor"/>
							<circle cx="12" cy="10" r="1" fill="currentColor"/>
							<circle cx="15" cy="10" r="1" fill="currentColor"/>
						</svg>
					</view>
					<text class="control-label">记录</text>
				</view>
				<view class="control-item" @click="shareMeeting">
					<view class="control-icon">
						<svg class="control-icon-svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
							<path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
							<circle cx="9" cy="7" r="4"/>
							<line x1="20" y1="8" x2="20" y2="14"/>
							<line x1="23" y1="11" x2="17" y2="11"/>
						</svg>
					</view>
					<text class="control-label">邀请</text>
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

		<!-- 实时字幕半屏弹窗 -->
		<view v-if="subtitlePanelVisible" class="panel-mask" @click="closeSubtitlePanel"></view>
		<view v-if="subtitlePanelVisible" class="sheet-panel subtitle-panel">
			<view class="sheet-handle"></view>
			<view class="sheet-title-row">
				<text class="sheet-close" @click="closeSubtitlePanel">×</text>
			</view>
			<scroll-view
				class="subtitle-record-scroll"
				scroll-y
				:scroll-into-view="subtitleRecordIntoView"
				lower-threshold="60"
				@scroll="handleSubtitleRecordScroll"
				@scrolltolower="handleSubtitleRecordScrollToLower"
			>
				<view v-if="subtitleRecords.length === 0" class="subtitle-record-empty">暂无字幕记录</view>
				<view v-else class="subtitle-record-list">
					<view
						v-for="(item, index) in subtitleRecords"
						:key="index"
						:id="'subtitle-record-item-' + index"
						class="subtitle-record-item"
					>
						<view class="subtitle-record-avatar">{{ (item.speaker || '').slice(0,1) }}</view>
						<view class="subtitle-record-main">
							<view class="subtitle-record-meta">
								<text class="subtitle-record-name">{{ item.speaker }}</text>
								<text v-if="item.isDanmaku" class="subtitle-record-tag">（弹幕）</text>
								<text class="subtitle-record-time">{{ item.time }}</text>
							</view>
							<text class="subtitle-record-text">{{ item.text }}</text>
						</view>
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

		<!-- 结束会议操作面板：对齐图二设计，主持人可全员结束，普通参会人仅可离开 -->
		<view v-if="endPanelVisible" class="panel-mask" @click="closeEndPanel"></view>
		<view v-if="endPanelVisible" class="end-panel-wrap">
			<view class="end-panel">
				<view v-if="isHost" class="end-action end-action--danger" @click="handleEndAll">全员结束会议</view>
				<view class="end-action end-action--leave" @click="handleLeaveMeeting">离开会议</view>
				<view v-if="isHost" class="end-action end-action--ai" @click="handleAiHost">
					<text>AI 托管</text>
					<svg class="end-action-ai-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
						<line x1="7" y1="17" x2="17" y2="7"/>
						<polyline points="7 7 17 7 17 17"/>
					</svg>
				</view>
			</view>
			<view class="end-cancel" @click="closeEndPanel">取消</view>
		</view>

		<!-- 主持人离开操作选择弹窗 -->
		<view v-if="leaveActionVisible" class="panel-mask" @click="closeLeaveAction"></view>
		<view v-if="leaveActionVisible" class="end-panel-wrap">
			<view class="end-panel">
				<view class="end-action end-action--danger" @click="onLeaveActionEndAll">全员结束会议</view>
				<view class="end-action end-action--transfer" @click="openTransferHost">转交主持人并离开</view>
				<view class="end-action end-action--cancel-leave" @click="closeLeaveAction">暂不离开</view>
			</view>
			<view class="end-cancel" @click="closeLeaveAction">取消</view>
		</view>

		<!-- 转交主持人成员选择弹窗 -->
		<view v-if="transferHostVisible" class="panel-mask" @click="closeTransferHost"></view>
		<view v-if="transferHostVisible" class="sheet-panel">
			<view class="sheet-handle"></view>
			<view class="sheet-title-row">
				<text class="sheet-title">选择新主持人</text>
				<text class="sheet-close" @click="closeTransferHost">×</text>
			</view>
			<view class="member-list">
				<view v-for="member in transferableMembers" :key="member.name" class="member-row" @click="selectTransferMember(member)">
					<view class="member-avatar">{{ member.name.slice(0, 1) }}</view>
					<view class="member-info">
						<text class="member-name">{{ member.name }}</text>
						<text class="member-role">参会成员</text>
					</view>
					<text v-if="selectedTransferMember === member.name" class="transfer-check">✓</text>
				</view>
			</view>
			<view class="transfer-confirm-row">
				<view class="end-action end-action--transfer" @click="confirmTransferHost">确认转交并离开</view>
			</view>
		</view>
	</view>
</template>

<script>
import { endMeeting as finishMeetingApi, getMeetingDetail, leaveMeeting as leaveMeetingApi, streamLlmChat, transferHost } from '@/api/ai.js'
import { getCurrentDisplayName, toMeetingMembers } from '@/utils/meetingUser.js'
import { BASE_URL } from '@/utils/config.js'
import { getToken, getUserInfo, getCurrentUserId } from '@/utils/storage.js'

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
			subtitlePanelVisible: false,
			endPanelVisible: false,
			leaveActionVisible: false,
			transferHostVisible: false,
			selectedTransferMember: '',
			isHost: false,
			hostName: '',
			subtitleRecords: [],
			subtitleRecordIntoView: '',
			subtitleRecordAtBottom: true,
			subtitleRecordScrollTopValue: 0,
			chatDraft: '',
			danmakuItems: [],
			danmakuSeq: 0,
			members: [],
			memberPageIndex: 0,
			swipeStartX: 0,
			refreshTimer: null,
			meetingEndedHandled: false
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
		this.restoreSubtitleRecords()
		this.startTimer()
		this.loadMeeting()
		this.initAsr()
		this.startRefreshTimer()
	},
	onUnload() {
		this.stopTimer()
		this.closeAsr()
		this.stopRefreshTimer()
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
			return this.members
		},
		// 成员分页：AI 摘要画布展开时每页 2人（一行两列），收起时每页 2列×3行 共6人，左右滑动翻页
		pagedMembers() {
			const pages = []
			const list = this.visibleMembers
			const pageSize = this.agentEnabled ? 2 : 6
			for (let i = 0; i < list.length; i += pageSize) {
				pages.push(list.slice(i, i + pageSize))
			}
			return pages.length ? pages : [[]]
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
		subtitleLines() {
			return this.asrItems.filter(item => item.isFinal).slice(-3)
		},
		asrReconnectVisible() {
			return !this.asrSocketReady && !this.muted && !!this.sessionId
		},
		transferableMembers() {
			return this.members.filter(m => !m.isSelf)
		}
	},
	watch: {
		'subtitleRecords.length'() {
			if (this.subtitlePanelVisible && this.subtitleRecordAtBottom) {
				this.$nextTick(() => {
					this.scrollSubtitleRecordToBottom()
				})
			}
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
		// 新增：打开实时字幕半屏弹窗
		openSubtitlePanel() {
			this.closePanel()
			this.subtitlePanelVisible = true
			this.subtitleRecordAtBottom = true
			this.$nextTick(() => {
				this.scrollSubtitleRecordToBottom()
			})
		},
		closeSubtitlePanel() {
			this.subtitlePanelVisible = false
		},
		scrollSubtitleRecordToBottom() {
			if (!this.subtitleRecords.length) return
			this.subtitleRecordIntoView = ''
			this.$nextTick(() => {
				this.subtitleRecordIntoView = 'subtitle-record-item-' + (this.subtitleRecords.length - 1)
			})
		},
		handleSubtitleRecordScroll(e) {
			const scrollTop = e?.detail?.scrollTop || 0
			if (scrollTop < this.subtitleRecordScrollTopValue - 10) {
				this.subtitleRecordAtBottom = false
			}
			this.subtitleRecordScrollTopValue = scrollTop
		},
		handleSubtitleRecordScrollToLower() {
			this.subtitleRecordAtBottom = true
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
		startRefreshTimer() {
			this.stopRefreshTimer()
			this.refreshTimer = setInterval(() => {
				this.loadMeeting()
			}, 3000)
		},
		stopRefreshTimer() {
			if (this.refreshTimer) {
				clearInterval(this.refreshTimer)
				this.refreshTimer = null
			}
		},
		async loadMeeting() {
			if (!this.sessionId) return
			try {
				const res = await getMeetingDetail(this.sessionId)
				this.applyMeetingDetail(res?.data || {})
			} catch (error) {}
		},
		applyMeetingDetail(detail) {
			const session = detail.session || {}
			if (session.title) this.title = session.title
			if (session.roomCode) this.roomCode = session.roomCode
			if (Array.isArray(detail.participants) && detail.participants.length > 0) {
				this.members = toMeetingMembers(detail.participants)
				// 与 meetingDetail 页一致：participants[0] 为主持人姓名，用于卡片标注展示
				this.hostName = String(detail.participants[0] || '').trim()
			}
			// 与 meetingRoom 主持人逻辑对齐：creatorId 与当前用户 id 一致即为主持人
			const creatorId = session?.creatorId
			const currentId = getCurrentUserId()
			this.isHost = !!currentId && creatorId != null && String(creatorId) === String(currentId)
			// 参会人轮询发现会议已被主持人结束：提示并自动退出会议现场（主持人自身在 endMeeting 中已跳转）
			if (session.status === 'ended' && !this.isHost) {
				this.handleMeetingEndedByHost()
			}
		},
		initCurrentMember() {
			const currentName = getCurrentDisplayName()
			this.members = currentName ? toMeetingMembers([currentName], currentName) : []
		},
		toggleMute() {
			// 关麦时不断开 ASR Socket，只停止录音（但仍能接收广播）
			this.muted = !this.muted
			if (this.muted) {
				// 停止录音但不关闭 socket，仍然能收其他客户端的识别广播
				this.stopAsrRecording()
				this.asrStatusText = '已静音'
			} else {
				// 开麦时启动录音并继续监听广播
				this.startAsr()
				this.asrStatusText = '已解除静音'
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
			console.log('[ASR-Socket] connecting', url)
			this.asrSocket = uni.connectSocket({ url, complete: () => {} })
			this.asrSocket.onOpen(() => {
				console.log('[ASR-Socket] opened')
				this.asrSocketReady = true
				this.asrReconnectAttempts = 0
				this.asrStatusText = this.muted ? '已静音' : '识别服务连接中'
			})
			this.asrSocket.onMessage((event) => {
				console.log('[ASR-Socket] message', event.data?.slice(0, 200))
				this.handleAsrMessage(event.data)
			})
			this.asrSocket.onError((error) => {
				console.log('[ASR-Socket] error', error)
				this.asrLastError = error?.errMsg || '识别连接异常'
				this.asrStatusText = this.asrLastError
				this.asrSocketReady = false
			})
			this.asrSocket.onClose(() => {
				console.log('[ASR-Socket] closed')
				// 关播/会议结束时才不再重连，平时断线都要自动重连以维持广播通道
				const shouldReconnect = !this.asrManualClosing && !!this.sessionId
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
			// socket 已存在且 ready，直接启动录音（如果之前是静音状态）
			if (this.asrSocketReady && this.asrServiceReady && !this.muted) {
				this.startAsrRecording()
			}
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
			// 关麦时停止录音和当前音频流，但保持 ASR WebSocket 连接以接收广播
			this.asrManualClosing = false
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
				this.asrStatusText = `正在重连识别 (${this.asrReconnectAttempts}/3)`
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
				// 关麦时也显示连接成功，但不启动录音
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
				console.log('[ASR-Receive]', item.speaker, item.isFinal ? 'final' : 'partial', item.text)
				this.upsertAsrItem(item)
				if (item.isFinal) {
					this.appendTranscriptLine(item)
				}
			}
			// 弹幕广播：来自其他参会成员，上屏滚动并写入记录
			if (payload.type === 'danmaku') {
				const user = getUserInfo()
				const currentId = user?.id || user?.userId || ''
				// 去重：自己发送的弹幕本地已上屏，跳过回环
				if (currentId && payload.speakerUserId && String(currentId) === String(payload.speakerUserId)) return
				const speaker = payload.speaker || '参会成员'
				const text = (payload.text || '').trim()
				if (!text) return
				const danmaku = { id: `dm-${Date.now()}-${this.danmakuSeq++}`, text: `${speaker}：${text}` }
				this.danmakuItems.push(danmaku)
				setTimeout(() => {
					this.danmakuItems = this.danmakuItems.filter(d => d.id !== danmaku.id)
				}, 6000)
				const now = new Date()
				const time = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`
				this.subtitleRecords.push({
					speaker,
					text,
					time,
					isSelf: false,
					isDanmaku: true,
					timestamp: Date.now()
				})
				if (this.subtitleRecords.length > 200) {
					this.subtitleRecords = this.subtitleRecords.slice(-200)
				}
				this.persistSubtitleRecords()
			}
		},
		isSelfSpeaker(payload) {
			const user = getUserInfo()
			const currentId = user?.id || user?.userId || ''
			const currentName = getCurrentDisplayName()
			if (currentId && payload.speakerUserId && String(currentId) === String(payload.speakerUserId)) {
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
			const now = new Date()
			const time = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`
			this.subtitleRecords.push({
				speaker: item.speaker || '参会成员',
				text,
				time,
				isSelf: item.isSelf,
				timestamp: Date.now()
			})
			if (this.subtitleRecords.length > 200) {
				this.subtitleRecords = this.subtitleRecords.slice(-200)
			}
			this.persistSubtitleRecords()
			if (this.agentEnabled) {
				this.scheduleAiSummary()
			}
		},
		toggleAgentSummary(event) {
			this.setAgentSummary(!!event.detail.value, true)
		},
		setAgentSummary(enabled, shouldClosePanel = false) {
			this.agentEnabled = !!enabled
			this.memberPageIndex = 0
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
		getSwipeClientX(event) {
			const touch = event?.changedTouches?.[0]
			return touch ? touch.clientX : (event?.clientX || 0)
		},
		handleSwipeStart(event) {
			this.swipeStartX = this.getSwipeClientX(event)
		},
		// 左右滑动翻页查看其他成员
		handleSwipeEnd(event) {
			if (!this.swipeStartX) return
			const delta = this.getSwipeClientX(event) - this.swipeStartX
			this.swipeStartX = 0
			const maxIndex = this.pagedMembers.length - 1
			if (delta <= -40 && this.memberPageIndex < maxIndex) {
				this.memberPageIndex += 1
			} else if (delta >= 40 && this.memberPageIndex > 0) {
				this.memberPageIndex -= 1
			}
		},
		// 扣字弹幕：顶部滚动飘过 + 写入下方记录（带（弹幕）后缀标注），不进入实时字幕与AI总结源
		sendChatMessage() {
			const text = (this.chatDraft || '').trim()
			if (!text) return
			const speaker = getCurrentDisplayName() || '参会成员'
			// 顶部弹幕：从右向左滚动，动画结束后自动移除
			const danmaku = { id: `dm-${Date.now()}-${this.danmakuSeq++}`, text: `${speaker}：${text}` }
			this.danmakuItems.push(danmaku)
			setTimeout(() => {
				this.danmakuItems = this.danmakuItems.filter(d => d.id !== danmaku.id)
			}, 6000)
			// 写入下方记录：isDanmaku 标记用于追加（弹幕）后缀，与语音字幕区分
			const now = new Date()
			const time = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`
			this.subtitleRecords.push({
				speaker,
				text,
				time,
				isSelf: true,
				isDanmaku: true,
				timestamp: Date.now()
			})
			if (this.subtitleRecords.length > 200) {
				this.subtitleRecords = this.subtitleRecords.slice(-200)
			}
			this.persistSubtitleRecords()
			this.chatDraft = ''
			// 跨账号广播：通过 ASR WebSocket 将弹幕转发给会议其他在线成员
			this.sendDanmakuViaSocket(speaker, text)
		},
		// 通过 ASR WebSocket 发送弹幕消息，由后端广播给同会议其他在线成员
		sendDanmakuViaSocket(speaker, text) {
			if (!this.asrSocket || !this.asrSocketReady) return
			try {
				this.asrSocket.send({ data: JSON.stringify({ type: 'danmaku', speaker, text }) })
			} catch (error) {}
		},
		// 字幕记录本地持久化：按会议 sessionId 存储，托管/离开页面后再次进入可恢复
		persistSubtitleRecords() {
			if (!this.sessionId) return
			try {
				uni.setStorageSync(`meeting_danmaku_${this.sessionId}`, this.subtitleRecords.slice(-200))
			} catch (error) {}
		},
		// 进入会议时恢复该会议的历史字幕记录（包含弹幕与 ASR 语音字幕）
		restoreSubtitleRecords() {
			if (!this.sessionId) return
			try {
				const list = uni.getStorageSync(`meeting_danmaku_${this.sessionId}`)
				if (Array.isArray(list) && list.length > 0) {
					const validList = list.filter(item => item && (item.text || item.speaker))
					validList.forEach(item => {
						if (!item.timestamp) item.timestamp = 0
					})
					validList.sort((a, b) => a.timestamp - b.timestamp)
					this.subtitleRecords = validList.slice(-200)
				}
			} catch (error) {}
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
			if (!this.roomCode) {
				uni.showToast({ title: '会议号暂未生成', icon: 'none' })
				return
			}
			uni.setClipboardData({
				data: this.roomCode,
				success: () => uni.showToast({ title: '会议号已复制', icon: 'none' })
			})
		},
		openMeetingDetail() {
			this.closePanel()
			uni.navigateTo({
				url: `/subpackage_meeting/meetingDetail/meetingDetail?sessionId=${encodeURIComponent(this.sessionId || '')}&title=${encodeURIComponent(this.title)}&roomCode=${encodeURIComponent(this.roomCode || '')}`
			})
		},
		openEndPanel() {
			this.endPanelVisible = true
		},
		closeEndPanel() {
			this.endPanelVisible = false
		},
		// 主持人：全员结束会议
		handleEndAll() {
			this.closeEndPanel()
			this.endMeeting()
		},
		// 普通参会人：仅自己离开，不结束会议
		handleLeaveMeeting() {
			if (this.isHost) {
				this.closeEndPanel()
				this.leaveActionVisible = true
			} else {
				this.closeEndPanel()
				this.leaveMeeting()
			}
		},
		// 主持人离开操作弹窗
		closeLeaveAction() {
			this.leaveActionVisible = false
		},
		onLeaveActionEndAll() {
			this.leaveActionVisible = false
			this.handleEndAll()
		},
		openTransferHost() {
			this.leaveActionVisible = false
			this.transferHostVisible = true
			this.selectedTransferMember = ''
		},
		closeTransferHost() {
			this.transferHostVisible = false
		},
		selectTransferMember(member) {
			this.selectedTransferMember = member.name
		},
		async confirmTransferHost() {
			if (!this.selectedTransferMember) {
				uni.showToast({ title: '请选择新主持人', icon: 'none' })
				return
			}
			this.transferHostVisible = false
			uni.showLoading({ title: '转交中...', mask: true })
			try {
				const res = await transferHost(this.sessionId, this.selectedTransferMember)
				this.applyMeetingDetail(res?.data || {})
				uni.hideLoading()
				this.leaveMeeting()
			} catch (error) {
				uni.hideLoading()
				const message = error?.msg || error?.message || '转交主持人失败'
				uni.showToast({ title: message, icon: 'none' })
			}
		},
		// TODO 前端模拟，正式环境接入 AI 托管接口
		handleAiHost() {
			this.closeEndPanel()
			uni.showToast({ title: '已开启 AI 托管', icon: 'none' })
			this.stopTimer()
			this.stopRefreshTimer()
			uni.redirectTo({ url: '/subpackage_meeting/meetingRoom/meetingRoom' })
		},
		async leaveMeeting() {
			this.closeEndPanel()
			this.stopTimer()
			this.stopRefreshTimer()
			let leaveError = ''
			if (this.sessionId) {
				try {
					await leaveMeetingApi(this.sessionId)
				} catch (error) {
					leaveError = error?.msg || error?.message || '离开会议失败'
				}
			}
			if (leaveError) {
				uni.showToast({ title: leaveError, icon: 'none' })
			}
			uni.redirectTo({ url: '/subpackage_meeting/meetingRoom/meetingRoom' })
		},
		async endMeeting() {
			if (!this.sessionId) return
			this.closeEndPanel()
			uni.showLoading({ title: '结束中...', mask: true })
			try {
				await finishMeetingApi(this.sessionId)
				uni.hideLoading()
				uni.showToast({ title: '会议已结束，AI正在整理', icon: 'none' })
				this.stopTimer()
				this.stopRefreshTimer()
				this.closeAsr()
				setTimeout(() => {
					uni.redirectTo({ url: '/subpackage_meeting/meetingRoom/meetingRoom' })
				}, 800)
			} catch (error) {
				uni.hideLoading()
				const message = error?.msg || error?.message || '结束会议失败'
				uni.showToast({ title: message, icon: 'none' })
			}
		},
		// 主持人已结束会议：参会人端提示并退出，后端 endMeeting 已为在线参会人写入 leaveTime，无需再调 leave 接口
		handleMeetingEndedByHost() {
			if (this.meetingEndedHandled) return
			this.meetingEndedHandled = true
			uni.showToast({ title: '主持人已结束会议', icon: 'none' })
			this.stopTimer()
			this.stopRefreshTimer()
			this.closeAsr()
			setTimeout(() => {
				uni.redirectTo({ url: '/subpackage_meeting/meetingRoom/meetingRoom' })
			}, 800)
		},
	}
}
</script>

<style lang="scss" scoped>
.live-page {
	height: 100vh;
	min-height: 100vh;
	background: #ffffff;
	color: #151f25;
	display: flex;
	flex-direction: column;
	overflow: hidden;
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
	flex: 1;
	min-height: 0;
	display: flex;
	flex-direction: column;
	justify-content: center;
	padding: 40rpx 40rpx 24rpx;
	box-sizing: border-box;
	overflow: hidden;
}
/* AI 实时摘要画布（位于成员画布上方，成员自然向下压缩，其余元素原位保留） */
.ai-summary-canvas {
	flex: 2;
	min-height: 0;
	margin-bottom: 12rpx;
	border-radius: 24rpx;
	background: #ffffff;
	border: 2rpx solid #f0f0f0;
	box-shadow: 0 8rpx 24rpx rgba(31,42,48,.04);
	overflow: hidden;
	display: flex;
	flex-direction: column;
}
.ai-summary-canvas-head {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 20rpx 24rpx 12rpx;
}
.ai-summary-canvas-title {
	color: #151f25;
	font-size: 28rpx;
	font-weight: 900;
}
.ai-summary-canvas-status {
	padding: 4rpx 16rpx;
	border-radius: 999rpx;
	background: #e9e9e9;
	color: #666;
	font-size: 20rpx;
}
.ai-summary-canvas-status--live {
	background: rgba(134, 201, 168, .14);
	color: #86C9A8;
}
.ai-summary-canvas-scroll {
	flex: 1;
	min-height: 0;
}
.ai-summary-canvas-empty {
	flex: 1;
	min-height: 0;
	display: flex;
	align-items: center;
	justify-content: center;
	color: #999;
	font-size: 23rpx;
}
.ai-summary-canvas-list {
	display: flex;
	flex-direction: column;
	gap: 16rpx;
	padding: 0 24rpx 24rpx;
}
.ai-summary-canvas-item {
	padding: 18rpx 20rpx;
	border-radius: 22rpx;
	background: rgba(134, 201, 168, .08);
	border: 1rpx solid rgba(134, 201, 168, .16);
}
.ai-summary-canvas-meta {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-bottom: 10rpx;
	color: #86C9A8;
	font-size: 20rpx;
	font-weight: 900;
}
.ai-summary-canvas-text {
	color: #151f25;
	font-size: 24rpx;
	line-height: 1.55;
	white-space: pre-wrap;
}
.member-slider {
	position: relative;
	flex: 1;
	min-height: 0;
	width: 100%;
	overflow: hidden;
	user-select: none;
}
/* 顶部弹幕滚动层 */
.danmaku-layer {
	position: absolute;
	top: 0;
	left: 0;
	right: 0;
	height: 56rpx;
	overflow: hidden;
	pointer-events: none;
	z-index: 3;
}
.danmaku-item {
	position: absolute;
	top: 10rpx;
	left: 100%;
	white-space: nowrap;
	padding: 6rpx 18rpx;
	border-radius: 999rpx;
	background: rgba(21, 31, 37, .55);
	color: #ffffff;
	font-size: 22rpx;
	animation: danmaku-scroll 6s linear forwards;
}
@keyframes danmaku-scroll {
	to { transform: translateX(calc(-100vw - 100%)); }
}
.member-pages {
	display: flex;
	height: 100%;
	transition: transform .25s ease;
}
.member-grid {
	height: 100%;
	width: 100%;
	flex-shrink: 0;
	display: grid;
	grid-template-columns: repeat(2, 1fr);
	grid-template-rows: repeat(3, 1fr);
	gap: 24rpx;
	padding: 0 30rpx;
	box-sizing: border-box;
}
.member-grid--compact {
	grid-template-rows: 1fr;
	align-content: stretch;
}
.member-card {
	position: relative;
	min-height: 0;
	aspect-ratio: 1 / 1;
	border-radius: 24rpx;
	overflow: hidden;
	background: #ffffff;
	border: 2rpx solid #e0e0e0;
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
}
.member-card--speaking {
	border-color: #86C9A8;
}
.speaking-tag {
	position: absolute;
	top: 16rpx;
	right: 16rpx;
	color: #86C9A8;
	font-size: 20rpx;
	font-weight: 500;
}
.host-tag {
	position: absolute;
	top: 16rpx;
	left: 16rpx;
	color: #86C9A8;
	font-size: 20rpx;
	font-weight: 500;
}
.member-avatar {
	width: 220rpx;
	height: 220rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	color: #d3d8dd;
}
.member-avatar-icon {
	width: 100%;
	height: 100%;
}
.member-info-row {
	position: absolute;
	left: 20rpx;
	bottom: 16rpx;
	display: flex;
	align-items: center;
	gap: 8rpx;
}
.member-mic-icon {
	width: 28rpx;
	height: 28rpx;
	color: #b0b8bf;
	flex-shrink: 0;
}
.member-mic-icon--speaking {
	color: #86C9A8;
}
.member-name {
	color: #151f25;
	font-size: 24rpx;
	font-weight: 500;
}
/* 成员分页指示器 */
.member-pager {
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: 8rpx;
}
.member-pager-dots {
	display: flex;
	align-items: center;
	gap: 10rpx;
}
.member-pager-dot {
	width: 12rpx;
	height: 12rpx;
	border-radius: 50%;
	background: #d8dde2;
}
.member-pager-dot--active {
	background: #86C9A8;
}
.member-pager-text {
	color: #8a9299;
	font-size: 20rpx;
}
/* 分页行容器：浮窗绝对定位于左侧，分页指示器保持居中 */
.pager-chat-row {
	position: relative;
	margin-top: 20rpx;
	min-height: 64rpx;
	display: flex;
	align-items: center;
	justify-content: center;
}
/* 扣字聊天浮窗：限制最大宽度，不遮挡右侧分页圆点 */
.chat-float {
	position: absolute;
	left: 0;
	top: 50%;
	transform: translateY(-50%);
	display: flex;
	align-items: center;
	gap: 8rpx;
	max-width: 300rpx;
	padding: 8rpx 8rpx 8rpx 20rpx;
	border-radius: 999rpx;
	background: #ffffff;
	border: 2rpx solid #f0f0f0;
	box-shadow: 0 8rpx 24rpx rgba(31,42,48,.06);
	box-sizing: border-box;
	z-index: 2;
}
.chat-input {
	width: 150rpx;
	font-size: 22rpx;
	color: #151f25;
}
.chat-send {
	flex-shrink: 0;
	padding: 6rpx 16rpx;
	border-radius: 999rpx;
	background: rgba(134, 201, 168, .14);
	color: #86C9A8;
	font-size: 20rpx;
	font-weight: 850;
}
/* 实时字幕区域 */
.subtitle-card {
	margin-top: 24rpx;
	padding: 24rpx;
	border-radius: 24rpx;
	background: #ffffff;
	border: 2rpx solid #f0f0f0;
	box-shadow: 0 8rpx 24rpx rgba(31,42,48,.04);
}
.subtitle-header {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-bottom: 16rpx;
}
.subtitle-title {
	color: #151f25;
	font-size: 28rpx;
	font-weight: 900;
}
.subtitle-summary-toggle {
	display: flex;
	align-items: center;
	gap: 10rpx;
}
.subtitle-summary-text {
	color: #86C9A8;
	font-size: 22rpx;
	font-weight: 850;
}
.subtitle-summary-switch {
	width: 88rpx;
	height: 48rpx;
	padding: 5rpx;
	border-radius: 999rpx;
	background: #e4e4e4;
	box-sizing: border-box;
	display: flex;
	justify-content: flex-start;
}
.subtitle-summary-switch--active {
	justify-content: flex-end;
	background: #86C9A8;
}
.subtitle-summary-knob {
	width: 38rpx;
	height: 38rpx;
	border-radius: 50%;
	background: #fff;
	box-shadow: 0 5rpx 12rpx rgba(0,0,0,.18);
}
.subtitle-body {
	height: 120rpx;
}
.subtitle-list {
	display: flex;
	flex-direction: column;
	gap: 12rpx;
}
.subtitle-item {
	color: #151f25;
	font-size: 22rpx;
	line-height: 1.5;
	word-break: break-all;
}
.subtitle-empty {
	height: 100%;
}
/* 实时字幕半屏弹窗 */
.subtitle-panel {
	height: 55%;
}
/* 无标题行时关闭按钮靠右 */
.subtitle-panel .sheet-title-row {
	justify-content: flex-end;
}
.subtitle-record-scroll {
	flex: 1;
	min-height: 0;
}
.subtitle-record-empty {
	margin-top: 128rpx;
	text-align: center;
	color: #999;
	font-size: 23rpx;
}
.subtitle-record-list {
	display: flex;
	flex-direction: column;
	gap: 24rpx;
	padding: 8rpx 0 24rpx;
}
.subtitle-record-item {
	display: flex;
	gap: 18rpx;
}
.subtitle-record-avatar {
	width: 56rpx;
	height: 56rpx;
	border-radius: 50%;
	background: #86C9A8;
	color: #fff;
	display: flex;
	align-items: center;
	justify-content: center;
	font-size: 22rpx;
	font-weight: 900;
	flex-shrink: 0;
}
.subtitle-record-main {
	flex: 1;
	display: flex;
	flex-direction: column;
	gap: 10rpx;
}
.subtitle-record-meta {
	display: flex;
	align-items: center;
	gap: 12rpx;
}
.subtitle-record-name {
	color: #151f25;
	font-size: 25rpx;
	font-weight: 800;
}
.subtitle-record-tag {
	color: #86C9A8;
	font-size: 20rpx;
}
.subtitle-record-time {
	color: #999;
	font-size: 20rpx;
}
.subtitle-record-text {
	color: #151f25;
	font-size: 24rpx;
	line-height: 1.5;
	word-break: break-all;
}

/* 底部操作栏 */
.live-bottom {
	padding: 20rpx 22rpx calc(env(safe-area-inset-bottom) + 20rpx);
}
.control-row {
	display: grid;
	grid-template-columns: repeat(4, 1fr);
	gap: 8rpx;
}
.control-item {
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: 10rpx;
	color: #8a9299;
	font-size: 20rpx;
}
.control-item--active { color: #86C9A8; }
.control-icon {
	width: 56rpx;
	height: 56rpx;
	display: flex;
	align-items: center;
	justify-content: center;
}
.control-icon-svg {
	width: 44rpx;
	height: 44rpx;
}
.control-label {
	font-size: 20rpx;
	line-height: 1;
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

/* 结束会议操作面板：对齐图二 */
.end-panel-wrap {
	position: fixed;
	top: calc(var(--status-bar-height) + 108rpx);
	right: 24rpx;
	z-index: 31;
	display: flex;
	flex-direction: column;
	align-items: flex-end;
	gap: 16rpx;
}
.end-panel {
	width: 340rpx;
	padding: 20rpx;
	border-radius: 24rpx;
	background: #ffffff;
	box-shadow: 0 18rpx 48rpx rgba(31,42,48,.16);
	display: flex;
	flex-direction: column;
	gap: 16rpx;
	box-sizing: border-box;
}
.end-action {
	height: 84rpx;
	border-radius: 16rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 8rpx;
	font-size: 26rpx;
	font-weight: 700;
}
.end-action--danger { background: #ff5f55; color: #ffffff; }
.end-action--leave { background: #e5e7eb; color: #151f25; }
.end-action--ai { background: #ffffff; border: 2rpx solid #d1d5db; color: #151f25; box-sizing: border-box; }
.end-action-ai-icon { width: 24rpx; height: 24rpx; }
.end-cancel {
	height: 72rpx;
	padding: 0 44rpx;
	border-radius: 20rpx;
	background: #e5e7eb;
	color: #151f25;
	font-size: 26rpx;
	font-weight: 700;
	display: flex;
	align-items: center;
	justify-content: center;
	box-shadow: 0 12rpx 28rpx rgba(31,42,48,.10);
}
.end-action--transfer { background: #86C9A8; color: #ffffff; }
.end-action--cancel-leave { background: #ffffff; border: 2rpx solid #d1d5db; color: #151f25; box-sizing: border-box; }
.transfer-check { color: #86C9A8; font-size: 28rpx; font-weight: 900; }
.transfer-confirm-row { margin-top: 24rpx; }
</style>