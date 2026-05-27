<template>
	<view class="meeting-page">
		<nav-bar
			title="会议空间"
			subtitle="Meeting Copilot"
			:showBack="true"
			fixed
			placeholder
			:border="false"
			background="linear-gradient(180deg, #e9f6f1 0%, rgba(233,246,241,0.72) 100%)"
		/>

		<view class="meeting-content">
			<view class="meeting-hero">
				<view class="meeting-hero__halo meeting-hero__halo--one"></view>
				<view class="meeting-hero__halo meeting-hero__halo--two"></view>
				<view class="meeting-hero__copy">
					<text class="meeting-hero__eyebrow">MEETING AGENTS</text>
					<text class="meeting-hero__title">把会议从“开完再说”变成“边开边沉淀”</text>
					<text class="meeting-hero__desc">总控、转写、总结、成员分析、资源推荐、语音播报，统一前缀的会议智能体已经就位。</text>
				</view>
				<view class="meeting-hero__orb">
					<view class="meeting-hero__ring"></view>
					<view class="meeting-hero__node meeting-hero__node--one">总</view>
					<view class="meeting-hero__node meeting-hero__node--two">记</view>
					<view class="meeting-hero__node meeting-hero__node--three">播</view>
				</view>
			</view>

			<view class="state-panel">
				<view class="state-panel__item">
					<text class="state-panel__label">状态</text>
					<text class="state-panel__value">{{ meetingStatusLabel }}</text>
				</view>
				<view class="state-panel__item">
					<text class="state-panel__label">智能体</text>
					<text class="state-panel__value">{{ agents.length }}</text>
				</view>
				<view class="state-panel__item">
					<text class="state-panel__label">成员</text>
					<text class="state-panel__value">{{ participants.length }}</text>
				</view>
			</view>

			<view class="meeting-card">
				<view class="section-heading">
					<view>
						<text class="section-heading__title">会议设置</text>
						<text class="section-heading__desc">先把主题和成员放进来，智能体回答会更稳。</text>
					</view>
					<view class="status-pill" :class="`status-pill--${meetingStatus}`">{{ meetingStatusLabel }}</view>
				</view>

				<view class="field-block">
					<text class="field-label">会议主题</text>
					<input
						v-model="meetingTitle"
						class="meeting-input"
						placeholder="例如：课程项目第 3 次推进会"
						placeholder-class="meeting-placeholder"
					/>
				</view>

				<view class="participant-row">
					<view
						v-for="member in participants"
						:key="member"
						class="participant-chip"
					>
						{{ member }}
					</view>
				</view>

				<view class="meeting-actions">
					<view class="primary-action" @click="toggleMeeting">{{ primaryActionText }}</view>
					<view class="ghost-action" @click="finishMeeting">结束会议</view>
				</view>
			</view>

			<view class="meeting-card">
				<view class="section-heading">
					<view>
						<text class="section-heading__title">选择会议智能体</text>
						<text class="section-heading__desc">统一 `meeting_` 前缀，不同后缀负责不同环节。</text>
					</view>
				</view>

				<scroll-view class="agent-scroll" scroll-x>
					<view class="agent-list">
						<view
							v-for="agent in agents"
							:key="agent.name"
							class="agent-card"
							:class="{ 'agent-card--active': currentAgentName === agent.name }"
							@click="currentAgentName = agent.name"
						>
							<view class="agent-card__mark">{{ agent.short }}</view>
							<text class="agent-card__label">{{ agent.label }}</text>
							<text class="agent-card__name">{{ agent.name }}</text>
							<text class="agent-card__desc">{{ agent.desc }}</text>
						</view>
					</view>
				</scroll-view>
			</view>

			<view class="meeting-card meeting-card--workspace">
				<view class="section-heading">
					<view>
						<text class="section-heading__title">会议记录</text>
						<text class="section-heading__desc">可以粘贴转写稿、人工纪要，或者边开会边补充。</text>
					</view>
				</view>

				<textarea
					v-model="meetingNotes"
					class="meeting-textarea"
					maxlength="3600"
					placeholder="例如：王老师提出需要明确小组分工；李同学负责数据整理；下周三前提交第一版..."
					placeholder-class="meeting-placeholder"
				/>

				<view class="workspace-footer">
					<text class="word-counter">{{ meetingNotes.length }}/3600</text>
					<view class="run-agent-btn" :class="{ 'run-agent-btn--disabled': !canRun }" @click="runCurrentAgent">
						{{ running ? '智能体处理中...' : `运行${currentAgent.label}` }}
					</view>
				</view>
			</view>

			<view class="meeting-card output-card">
				<view class="section-heading">
					<view>
						<text class="section-heading__title">智能体输出</text>
						<text class="section-heading__desc">当前输出来自：{{ currentAgent.name }}</text>
					</view>
					<view v-if="answer" class="copy-btn" @click="copyAnswer">复制</view>
				</view>

				<view v-if="!answer && !running" class="empty-output">
					<text class="empty-output__title">先选择一个智能体运行</text>
					<text class="empty-output__desc">如果只是想试一下，默认记录已经够总控智能体生成流程建议。</text>
				</view>
				<view v-else-if="running" class="loading-output">
					<view class="loading-dot"></view>
					<view class="loading-dot loading-dot--delay"></view>
					<view class="loading-dot loading-dot--late"></view>
					<text>正在组织会议结果</text>
				</view>
				<text v-else class="answer-text">{{ answer }}</text>
			</view>

			<view class="timeline-card">
				<text class="timeline-title">会议流程</text>
				<view
					v-for="item in timeline"
					:key="item.id"
					class="timeline-item"
				>
					<view class="timeline-dot"></view>
					<view class="timeline-body">
						<text class="timeline-body__title">{{ item.title }}</text>
						<text class="timeline-body__desc">{{ item.desc }}</text>
					</view>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { createMeeting, getMeetingDetail, runMeetingAgent, updateMeeting } from '@/api/ai.js'

const MEETING_AGENTS = [
	{
		name: 'meeting_controller_agent',
		label: '总控智能体',
		short: '总',
		desc: '会议状态管理、任务分发、流程调度',
		task: '请管理当前会议状态，拆解议程、风险、下一步流程，并给出任务分发建议。'
	},
	{
		name: 'meeting_transcription_agent',
		label: '转写智能体',
		short: '写',
		desc: '语音识别、说话人区分、发言整理',
		task: '请把会议记录整理为清晰的发言转写稿，区分说话人、动作项和待确认问题。'
	},
	{
		name: 'meeting_summary_agent',
		label: '总结智能体',
		short: '纪',
		desc: '核心观点、主要结论、任务计划',
		task: '请提炼会议核心观点、主要结论、任务分工、截止时间和后续计划。'
	},
	{
		name: 'meeting_member_analysis_agent',
		label: '成员分析智能体',
		short: '析',
		desc: '薄弱点、理解偏差、参与特征识别',
		task: '请分析不同成员的理解偏差、知识薄弱点、参与特征，并给出温和可执行的改进建议。'
	},
	{
		name: 'meeting_resource_recommendation_agent',
		label: '资源推荐智能体',
		short: '荐',
		desc: '学习资源选择与推送策略',
		task: '请基于会议内容，为不同成员推荐学习资源、推送节奏和复习策略。'
	},
	{
		name: 'meeting_voice_broadcast_agent',
		label: '播报智能体',
		short: '播',
		desc: '总结报告、建议与推荐内容播报稿',
		task: '请把会议总结、学习建议和推荐内容改写成自然清晰的语音播报稿。'
	}
]

export default {
	components: { NavBar },
	data() {
		return {
			sessionId: '',
			meetingStatus: 'idle',
			meetingTitle: '课程项目第 3 次推进会',
			participants: ['王老师', '李同学', '陈同学', '周同学'],
			currentAgentName: 'meeting_controller_agent',
			meetingNotes: '王老师：今天重点确认项目选题、分工和下周交付。\n李同学：数据整理已经完成一半，但对评价指标还不确定。\n陈同学：页面原型可以本周完成，需要后端接口字段说明。\n周同学：我负责答辩材料，但希望先拿到项目结构和核心亮点。\n会议决定：周三前完成第一版原型和数据样例，周五做一次内部演示。',
			answer: '',
			running: false,
			timeline: [
				{ id: 1, title: '准备会议', desc: '录入主题、成员和议程，会议总控智能体进入待命。' },
				{ id: 2, title: '沉淀记录', desc: '粘贴发言内容或实时补充关键事实，供后续智能体复用。' },
				{ id: 3, title: '生成结果', desc: '按需切换总结、分析、推荐、播报等会议智能体。' }
			],
			agents: MEETING_AGENTS
		}
	},
	onLoad(options) {
		if (options && options.sessionId) {
			this.sessionId = decodeURIComponent(options.sessionId)
			this.loadMeetingDetail()
		}
	},
	computed: {
		currentAgent() {
			return this.agents.find((agent) => agent.name === this.currentAgentName) || this.agents[0]
		},
		meetingStatusLabel() {
			const statusMap = {
				idle: '待开始',
				active: '会议中',
				paused: '已暂停',
				ended: '已结束'
			}
			return statusMap[this.meetingStatus] || '待开始'
		},
		primaryActionText() {
			if (this.meetingStatus === 'active') return '暂停会议'
			if (this.meetingStatus === 'paused') return '继续会议'
			if (this.meetingStatus === 'ended') return '重新开始'
			return '开始会议'
		},
		canRun() {
			return !this.running && this.meetingNotes.trim().length > 0
		}
	},
	methods: {
		async toggleMeeting() {
			const wasEnded = this.meetingStatus === 'ended'
			if (this.meetingStatus === 'active') {
				this.meetingStatus = 'paused'
				this.addTimeline('会议暂停', '可以继续补充记录，或先运行总结智能体生成阶段纪要。')
				this.persistMeetingSilently()
				return
			}
			this.meetingStatus = 'active'
			this.answer = ''
			this.addTimeline(wasEnded ? '会议重启' : '会议开始', '会议空间已进入工作状态，所有会议智能体可以随时调用。')
			await this.persistMeetingSilently(true)
		},
		finishMeeting() {
			if (this.meetingStatus === 'ended') return
			this.meetingStatus = 'ended'
			this.addTimeline('会议结束', '建议运行总结智能体，生成最终纪要和后续计划。')
			this.persistMeetingSilently()
		},
		addTimeline(title, desc) {
			this.timeline.unshift({
				id: Date.now() + Math.random(),
				title,
				desc
			})
		},
		async runCurrentAgent() {
			if (!this.canRun) {
				uni.showToast({ title: '请先填写会议记录', icon: 'none' })
				return
			}
			this.running = true
			this.answer = ''
			try {
				await this.ensureMeetingSession()
				const res = await runMeetingAgent(this.sessionId, {
					agentName: this.currentAgent.name,
					content: this.meetingNotes.trim()
				})
				const payload = res?.data || {}
				this.answer = payload.answer || '智能体没有返回可用内容，请稍后再试。'
				if (payload.detail) {
					this.applyMeetingDetail(payload.detail, false)
				}
				this.addTimeline('智能体已完成', `${this.currentAgent.label} 已输出会议结果。`)
			} catch (error) {
				this.answer = `这次没有顺利完成请求：${error?.message || error?.msg || '请稍后再试'}`
			} finally {
				this.running = false
			}
		},
		buildAgentInput() {
			return [
				`会议主题：${this.meetingTitle || '未命名会议'}`,
				`会议状态：${this.meetingStatusLabel}`,
				`参会成员：${this.participants.join('、')}`,
				`当前任务：${this.currentAgent.task}`,
				'会议记录：',
				this.meetingNotes.trim()
			].join('\n')
		},
		async loadMeetingDetail() {
			if (!this.sessionId) return
			try {
				const res = await getMeetingDetail(this.sessionId)
				this.applyMeetingDetail(res?.data || {}, true)
			} catch (error) {
				uni.showToast({ title: '会议详情加载失败', icon: 'none' })
			}
		},
		async ensureMeetingSession() {
			const payload = this.buildMeetingPayload()
			const res = this.sessionId
				? await updateMeeting(this.sessionId, payload)
				: await createMeeting(payload)
			this.applyMeetingDetail(res?.data || {}, false)
			return res?.data
		},
		async persistMeetingSilently(createIfMissing = false) {
			if (!this.sessionId && !createIfMissing) return
			try {
				await this.ensureMeetingSession()
			} catch (error) {
				console.error('同步会议失败:', error)
			}
		},
		buildMeetingPayload() {
			return {
				title: this.meetingTitle,
				status: this.meetingStatus,
				participants: this.participants,
				notes: this.meetingNotes
			}
		},
		applyMeetingDetail(detail, restoreAnswer) {
			const session = detail.session || {}
			if (session.sessionId) {
				this.sessionId = session.sessionId
			}
			if (session.title) {
				this.meetingTitle = session.title
			}
			if (session.status) {
				this.meetingStatus = session.status
			}
			if (Array.isArray(detail.participants) && detail.participants.length > 0) {
				this.participants = detail.participants
			}
			if (Array.isArray(detail.records) && detail.records.length > 0) {
				this.meetingNotes = detail.records[detail.records.length - 1].content || this.meetingNotes
			}
			if (restoreAnswer && Array.isArray(detail.results) && detail.results.length > 0) {
				this.answer = detail.results[0].answer || ''
				if (detail.results[0].agentName) {
					this.currentAgentName = detail.results[0].agentName
				}
			}
		},
		copyAnswer() {
			if (!this.answer) return
			uni.setClipboardData({
				data: this.answer,
				success: () => {
					uni.showToast({ title: '已复制会议结果', icon: 'none' })
				}
			})
		}
	}
}
</script>

<style lang="scss" scoped>
.meeting-page {
	min-height: 100vh;
	background:
		radial-gradient(circle at 10% 8%, rgba(255, 214, 125, 0.28), transparent 26%),
		linear-gradient(180deg, #e9f6f1 0%, #f8f3e6 52%, #f7f7f4 100%);
}

.meeting-content {
	padding: 24rpx 28rpx 64rpx;
}

.meeting-hero {
	position: relative;
	overflow: hidden;
	min-height: 320rpx;
	border-radius: 38rpx;
	padding: 34rpx 30rpx;
	background:
		linear-gradient(135deg, rgba(17, 60, 67, 0.96), rgba(30, 102, 95, 0.94) 54%, rgba(197, 137, 67, 0.94));
	box-shadow: 0 24rpx 52rpx rgba(27, 82, 80, 0.18);
}

.meeting-hero__halo {
	position: absolute;
	border-radius: 50%;
	border: 2rpx solid rgba(255, 255, 255, 0.18);
}

.meeting-hero__halo--one {
	width: 260rpx;
	height: 260rpx;
	right: -82rpx;
	top: -80rpx;
}

.meeting-hero__halo--two {
	width: 180rpx;
	height: 180rpx;
	right: 102rpx;
	bottom: -96rpx;
}

.meeting-hero__copy {
	position: relative;
	z-index: 2;
	max-width: 460rpx;
	display: flex;
	flex-direction: column;
}

.meeting-hero__eyebrow {
	align-self: flex-start;
	padding: 8rpx 16rpx;
	border-radius: 999rpx;
	background: rgba(255, 255, 255, 0.14);
	color: rgba(255, 255, 255, 0.78);
	font-size: 20rpx;
	font-weight: 900;
	letter-spacing: 2rpx;
}

.meeting-hero__title {
	margin-top: 18rpx;
	font-size: 44rpx;
	line-height: 1.22;
	color: #FFFFFF;
	font-weight: 900;
	letter-spacing: 1rpx;
}

.meeting-hero__desc {
	margin-top: 18rpx;
	font-size: 25rpx;
	line-height: 1.68;
	color: rgba(255, 255, 255, 0.84);
}

.meeting-hero__orb {
	position: absolute;
	right: 20rpx;
	bottom: 24rpx;
	width: 210rpx;
	height: 210rpx;
}

.meeting-hero__ring {
	position: absolute;
	inset: 24rpx;
	border-radius: 50%;
	border: 2rpx dashed rgba(255, 255, 255, 0.24);
	animation: meeting-spin 14s linear infinite;
}

.meeting-hero__node {
	position: absolute;
	width: 62rpx;
	height: 62rpx;
	border-radius: 24rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	background: rgba(255, 255, 255, 0.92);
	color: #153f49;
	font-size: 24rpx;
	font-weight: 900;
	box-shadow: 0 14rpx 28rpx rgba(0, 0, 0, 0.14);
}

.meeting-hero__node--one {
	left: 16rpx;
	top: 44rpx;
}

.meeting-hero__node--two {
	right: 24rpx;
	top: 8rpx;
	background: #fff4d8;
}

.meeting-hero__node--three {
	right: 48rpx;
	bottom: 18rpx;
	background: #dff6ed;
}

@keyframes meeting-spin {
	0% {
		transform: rotate(0deg);
	}
	100% {
		transform: rotate(360deg);
	}
}

.state-panel {
	display: grid;
	grid-template-columns: repeat(3, 1fr);
	gap: 16rpx;
	margin-top: 24rpx;
}

.state-panel__item {
	padding: 20rpx 18rpx;
	border-radius: 26rpx;
	background: rgba(255, 255, 255, 0.82);
	box-shadow: 0 12rpx 28rpx rgba(60, 90, 81, 0.08);
	display: flex;
	flex-direction: column;
	gap: 8rpx;
}

.state-panel__label {
	font-size: 21rpx;
	color: #82908a;
}

.state-panel__value {
	font-size: 31rpx;
	color: #183f43;
	font-weight: 900;
}

.meeting-card {
	margin-top: 24rpx;
	padding: 28rpx;
	border-radius: 32rpx;
	background: rgba(255, 255, 255, 0.9);
	box-shadow: 0 16rpx 38rpx rgba(60, 90, 81, 0.09);
	border: 1rpx solid rgba(255, 255, 255, 0.72);
}

.section-heading {
	display: flex;
	align-items: flex-start;
	justify-content: space-between;
	gap: 18rpx;
}

.section-heading__title {
	display: block;
	font-size: 31rpx;
	font-weight: 900;
	color: #183f43;
}

.section-heading__desc {
	display: block;
	margin-top: 8rpx;
	font-size: 23rpx;
	line-height: 1.52;
	color: #7c8982;
}

.status-pill {
	flex-shrink: 0;
	padding: 10rpx 18rpx;
	border-radius: 999rpx;
	font-size: 22rpx;
	font-weight: 800;
	background: #edf4f2;
	color: #1e665f;
}

.status-pill--active {
	background: #dbf6e7;
	color: #147a45;
}

.status-pill--paused {
	background: #fff1d2;
	color: #9a651c;
}

.status-pill--ended {
	background: #ececec;
	color: #6c6c6c;
}

.field-block {
	margin-top: 24rpx;
}

.field-label {
	display: block;
	margin-bottom: 12rpx;
	font-size: 24rpx;
	font-weight: 800;
	color: #47625f;
}

.meeting-input {
	height: 86rpx;
	padding: 0 24rpx;
	border-radius: 24rpx;
	background: #f4f8f5;
	color: #183f43;
	font-size: 28rpx;
	box-sizing: border-box;
}

.meeting-placeholder {
	color: #a0aaa4;
}

.participant-row {
	display: flex;
	flex-wrap: wrap;
	gap: 12rpx;
	margin-top: 20rpx;
}

.participant-chip {
	padding: 12rpx 18rpx;
	border-radius: 999rpx;
	background: #eff6ed;
	color: #40635c;
	font-size: 23rpx;
	font-weight: 700;
}

.meeting-actions {
	display: flex;
	gap: 16rpx;
	margin-top: 24rpx;
}

.primary-action,
.ghost-action,
.run-agent-btn,
.copy-btn {
	display: flex;
	align-items: center;
	justify-content: center;
	border-radius: 999rpx;
	font-size: 25rpx;
	font-weight: 900;
}

.primary-action {
	flex: 1;
	height: 82rpx;
	background: linear-gradient(135deg, #1e665f, #d39d51);
	color: #FFFFFF;
	box-shadow: 0 12rpx 26rpx rgba(30, 102, 95, 0.18);
}

.ghost-action {
	width: 180rpx;
	height: 82rpx;
	background: #f2f4f1;
	color: #52605c;
}

.agent-scroll {
	margin-top: 24rpx;
	width: 100%;
	white-space: nowrap;
}

.agent-list {
	display: inline-flex;
	gap: 16rpx;
	padding-bottom: 4rpx;
}

.agent-card {
	width: 248rpx;
	min-height: 230rpx;
	padding: 22rpx;
	border-radius: 28rpx;
	background: #f5f8f4;
	border: 2rpx solid transparent;
	box-sizing: border-box;
	display: inline-flex;
	flex-direction: column;
	vertical-align: top;
	white-space: normal;
}

.agent-card--active {
	background: linear-gradient(180deg, #fdf7e8, #edf8f1);
	border-color: rgba(211, 157, 81, 0.72);
	box-shadow: 0 14rpx 28rpx rgba(211, 157, 81, 0.16);
}

.agent-card__mark {
	width: 62rpx;
	height: 62rpx;
	border-radius: 22rpx;
	background: #183f43;
	color: #fff4d8;
	display: flex;
	align-items: center;
	justify-content: center;
	font-size: 25rpx;
	font-weight: 900;
}

.agent-card__label {
	margin-top: 16rpx;
	color: #183f43;
	font-size: 26rpx;
	font-weight: 900;
}

.agent-card__name {
	margin-top: 8rpx;
	color: #71807a;
	font-size: 19rpx;
	line-height: 1.35;
}

.agent-card__desc {
	margin-top: 12rpx;
	color: #596863;
	font-size: 22rpx;
	line-height: 1.5;
}

.meeting-card--workspace {
	background:
		radial-gradient(circle at top right, rgba(211, 157, 81, 0.14), transparent 30%),
		rgba(255, 255, 255, 0.92);
}

.meeting-textarea {
	margin-top: 24rpx;
	width: 100%;
	min-height: 300rpx;
	padding: 24rpx;
	border-radius: 28rpx;
	background: #f4f8f5;
	color: #1f3534;
	font-size: 27rpx;
	line-height: 1.62;
	box-sizing: border-box;
}

.workspace-footer {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 18rpx;
	margin-top: 20rpx;
}

.word-counter {
	color: #85928d;
	font-size: 22rpx;
}

.run-agent-btn {
	min-width: 250rpx;
	height: 76rpx;
	padding: 0 24rpx;
	background: #183f43;
	color: #FFFFFF;
	box-shadow: 0 12rpx 24rpx rgba(24, 63, 67, 0.18);
}

.run-agent-btn--disabled {
	opacity: 0.55;
}

.output-card {
	min-height: 260rpx;
}

.copy-btn {
	flex-shrink: 0;
	height: 58rpx;
	padding: 0 22rpx;
	background: #edf4f2;
	color: #1e665f;
}

.empty-output {
	margin-top: 28rpx;
	padding: 42rpx 24rpx;
	border-radius: 26rpx;
	background: #f6f8f5;
	text-align: center;
}

.empty-output__title {
	display: block;
	color: #183f43;
	font-size: 28rpx;
	font-weight: 900;
}

.empty-output__desc {
	display: block;
	margin-top: 12rpx;
	color: #7b8882;
	font-size: 24rpx;
	line-height: 1.58;
}

.loading-output {
	margin-top: 34rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 12rpx;
	color: #52605c;
	font-size: 25rpx;
}

.loading-dot {
	width: 12rpx;
	height: 12rpx;
	border-radius: 50%;
	background: #d39d51;
	animation: meeting-pulse 1.1s ease-in-out infinite;
}

.loading-dot--delay {
	animation-delay: 0.16s;
}

.loading-dot--late {
	animation-delay: 0.32s;
}

@keyframes meeting-pulse {
	0%,
	100% {
		opacity: 0.35;
		transform: translateY(0);
	}
	50% {
		opacity: 1;
		transform: translateY(-8rpx);
	}
}

.answer-text {
	display: block;
	margin-top: 26rpx;
	padding: 26rpx;
	border-radius: 26rpx;
	background: #f7f9f5;
	color: #233836;
	font-size: 26rpx;
	line-height: 1.72;
	white-space: pre-wrap;
	word-break: break-all;
}

.timeline-card {
	margin-top: 24rpx;
	padding: 28rpx;
	border-radius: 32rpx;
	background: rgba(24, 63, 67, 0.92);
	color: #FFFFFF;
	box-shadow: 0 18rpx 38rpx rgba(24, 63, 67, 0.18);
}

.timeline-title {
	display: block;
	margin-bottom: 24rpx;
	font-size: 31rpx;
	font-weight: 900;
}

.timeline-item {
	display: flex;
	gap: 18rpx;
	margin-bottom: 22rpx;
}

.timeline-item:last-child {
	margin-bottom: 0;
}

.timeline-dot {
	width: 18rpx;
	height: 18rpx;
	margin-top: 10rpx;
	border-radius: 50%;
	background: #ffd67d;
	box-shadow: 0 0 16rpx rgba(255, 214, 125, 0.72);
	flex-shrink: 0;
}

.timeline-body {
	display: flex;
	flex-direction: column;
}

.timeline-body__title {
	font-size: 26rpx;
	font-weight: 900;
	color: #FFFFFF;
}

.timeline-body__desc {
	margin-top: 8rpx;
	font-size: 23rpx;
	line-height: 1.58;
	color: rgba(255, 255, 255, 0.74);
}
</style>
