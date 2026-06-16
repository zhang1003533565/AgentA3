<template>
	<view class="ai-assistant-root">
		<view
			v-if="panelVisible"
			class="ai-assistant-mask"
			@click="closePanel"
		></view>

		<view
			v-if="panelVisible"
			class="ai-assistant-panel"
			:class="{ 'ai-assistant-panel--active': panelVisible }"
			:style="panelStyle"
		>
			<view class="ai-assistant-panel__header">
				<view>
					<text class="ai-assistant-panel__eyebrow">Campus Copilot</text>
					<text class="ai-assistant-panel__title">校园对话助手</text>
				</view>
				<view class="ai-assistant-panel__actions">
					<view class="ai-assistant-panel__icon" @click.stop="openFullConversation">展开</view>
					<view class="ai-assistant-panel__icon" @click="resetSession">新会话</view>
					<view class="ai-assistant-panel__close" @click="closePanel">×</view>
				</view>
			</view>

			<scroll-view
				class="ai-assistant-panel__messages"
				scroll-y
				enhanced
				show-scrollbar="false"
				:scroll-into-view="scrollAnchor"
			>
				<view
					v-for="(message, index) in messages"
					:key="message.id"
					:id="`ai-message-${index}`"
					class="ai-message-row"
					:class="message.role === 'user' ? 'ai-message-row--user' : 'ai-message-row--assistant'"
				>
					<view class="ai-message-bubble" :class="message.role === 'user' ? 'ai-message-bubble--user' : 'ai-message-bubble--assistant'">
						<view v-if="message.type === 'thinking'" class="ai-thinking-indicator">
							<text class="ai-thinking-text">思考中</text>
							<view class="ai-thinking-dots">
								<text></text>
								<text></text>
								<text></text>
							</view>
							</view>
							<view v-else-if="message.role === 'assistant'" class="ai-message-content">
								<view v-if="getOutputTypeTags(message).length" class="ai-output-type-list">
									<text
										v-for="type in getOutputTypeTags(message)"
										:key="`${message.id}-type-${type}`"
										class="ai-output-type-tag"
									>{{ getOutputTypeLabel(type) }}</text>
								</view>
								<image
									v-for="(image, imageIndex) in getMessageImages(message)"
									:key="`${message.id}-image-${imageIndex}`"
									class="ai-message-image"
									:src="image.url"
									mode="aspectFill"
									@click="previewMessageImage(image, message)"
								/>
								<view
									v-for="(line, lineIndex) in formatAnswerLines(getDisplayContent(message))"
								:key="`${message.id}-line-${lineIndex}`"
								class="ai-message-line"
								:class="{
									'ai-message-line--bullet': line.type === 'bullet',
									'ai-message-line--empty': line.type === 'empty'
								}"
							>
								<text v-if="line.type === 'bullet'" class="ai-message-line__dot"></text>
								<text class="ai-message-line__text">{{ line.text }}</text>
							</view>
						</view>
						<text v-else class="ai-message-content">{{ message.content }}</text>

					</view>
				</view>

				<view id="ai-message-anchor"></view>
			</scroll-view>

			<view class="ai-assistant-panel__quick">
				<view
					v-for="prompt in quickPrompts"
					:key="prompt"
					class="ai-assistant-panel__quick-chip"
					@click="useQuickPrompt(prompt)"
				>
					{{ prompt }}
				</view>
			</view>

			<view class="ai-assistant-panel__composer">
				<textarea
					v-model="inputValue"
					class="ai-assistant-panel__textarea"
					placeholder="直接问 Leader 智能助手"
					maxlength="300"
					auto-height
					confirm-type="send"
					:confirm-hold="true"
					:disabled="sending"
					@confirm="handleInputConfirm"
					@keydown.enter="handleEnterKey"
				/>
				<view class="ai-assistant-panel__composer-bottom">
					<text class="ai-assistant-panel__tip">{{ sending ? '正在思考...' : '输入问题后按回车发送' }}</text>
					<view
						class="ai-assistant-panel__send"
						:class="{ 'ai-assistant-panel__send--disabled': !canSend }"
						@click="sendMessage"
					>
						发送
					</view>
				</view>
			</view>
		</view>

		<view
			class="ai-assistant-fab"
			:class="{
				'ai-assistant-fab--collapsed': fabCollapsed,
				'ai-assistant-fab--left': fabDockSide === 'left',
				'ai-assistant-fab--right': fabDockSide === 'right'
			}"
			:style="fabStyle"
			@tap.stop="handleFabTap"
			@touchstart.stop="onTouchStart"
			@touchmove.stop.prevent="onTouchMove"
			@touchend.stop="onTouchEnd"
			@mousedown.stop="onMouseDown"
		>
			<view class="ai-assistant-fab__halo"></view>
			<view class="ai-assistant-fab__core">
				<text class="ai-assistant-fab__label">AI</text>
			</view>
		</view>
	</view>
</template>

<script>
import { getLeaderSessionDetail, queryLeaderAgent, streamLeaderAgent } from '@/api/ai.js'

const STORAGE_KEY = 'aiAssistantSessionId'

export default {
	data() {
		return {
			panelVisible: false,
			sending: false,
			inputValue: '',
			sessionId: '',
			scrollAnchor: 'ai-message-anchor',
			screenWidth: 375,
			screenHeight: 667,
			fabSize: 56,
			fabBottomSpacing: 110,
			defaultFabLeft: 0,
			defaultFabTop: 0,
			fabLeft: 0,
			fabTop: 0,
			fabCollapsed: false,
			fabDockSide: 'right',
			collapseTimer: null,
			dragging: false,
			dragMoved: false,
			dragStartX: 0,
			dragStartY: 0,
			originLeft: 0,
			originTop: 0,
			lastPointerType: '',
			suppressNextTap: false,
			quickPrompts: ['推荐今天的食堂', '我今天有什么课', '校园里有哪些优惠'],
			messages: [
				{
					id: 'welcome',
					role: 'assistant',
					content: '你好，我是 Leader 智能助手。你可以直接问我校园服务、课表、论坛、知识库和常用问题。'
				}
			]
		}
	},
	computed: {
		canSend() {
			return !this.sending && this.inputValue.trim().length > 0
		},
		fabStyle() {
			return {
				left: `${this.fabLeft}px`,
				top: `${this.fabTop}px`,
				zIndex: 1202
			}
		},
		panelStyle() {
			const panelWidth = Math.min(this.screenWidth - 24, 360)
			const panelHeight = Math.min(this.screenHeight - 120, 540)
			const preferredLeft = this.fabLeft + this.fabSize - panelWidth
			const minLeft = 12
			const maxLeft = Math.max(12, this.screenWidth - panelWidth - 12)
			const left = Math.min(Math.max(preferredLeft, minLeft), maxLeft)

			let top = this.fabTop - panelHeight - 16
			if (top < 16) {
				top = Math.min(this.fabTop + this.fabSize + 12, this.screenHeight - panelHeight - 16)
			}

			return {
				width: `${panelWidth}px`,
				height: `${panelHeight}px`,
				left: `${left}px`,
				top: `${Math.max(16, top)}px`,
				zIndex: 1201
			}
		}
	},
	created() {
		this.ensureSessionId()
	},
	mounted() {
		this.initFloatingPosition()
		this.scheduleFabCollapse()
	},
	beforeDestroy() {
		this.clearFabCollapseTimer()
		this.removeMouseListeners()
	},
	beforeUnmount() {
		this.clearFabCollapseTimer()
		this.removeMouseListeners()
	},
	methods: {
		initFloatingPosition() {
			const systemInfo = uni.getSystemInfoSync()
			this.screenWidth = systemInfo.windowWidth || 375
			this.screenHeight = systemInfo.windowHeight || 667
			this.fabSize = 56
			const safeAreaBottomInset = systemInfo.safeArea
				? Math.max(0, this.screenHeight - systemInfo.safeArea.bottom)
				: 0
			this.fabBottomSpacing = Math.max(140, safeAreaBottomInset + 112)
			this.defaultFabLeft = Math.max(12, this.screenWidth - this.fabSize - 16)
			this.defaultFabTop = Math.max(120, this.screenHeight - this.fabSize - this.fabBottomSpacing)
			this.fabLeft = this.defaultFabLeft
			this.fabTop = this.defaultFabTop
			this.updateFabDockSide()
		},
		ensureSessionId() {
			const saved = uni.getStorageSync(STORAGE_KEY)
			if (saved) {
				this.sessionId = saved
				return
			}
			this.sessionId = this.createSessionId()
			uni.setStorageSync(STORAGE_KEY, this.sessionId)
		},
		createSessionId() {
			return `app-ai-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
		},
		togglePanel() {
			this.resetFabPosition()
			const willOpen = !this.panelVisible
			this.panelVisible = willOpen
			this.fabCollapsed = false
			if (willOpen) {
				this.clearFabCollapseTimer()
				this.syncCurrentSession()
			} else {
				this.scheduleFabCollapse()
			}
			this.$nextTick(() => {
				this.scrollToBottom()
			})
		},
		closePanel() {
			this.resetFabPosition()
			this.panelVisible = false
			this.scheduleFabCollapse()
		},
		openFullConversation() {
			this.ensureSessionId()
			uni.setStorageSync(STORAGE_KEY, this.sessionId)
			this.panelVisible = false
			this.fabCollapsed = false
			this.scheduleFabCollapse()
			uni.navigateTo({
				url: `/subpackage_ai/aiConversation/aiConversation?sessionId=${encodeURIComponent(this.sessionId)}`
			})
		},
		resetSession() {
			this.sessionId = this.createSessionId()
			uni.setStorageSync(STORAGE_KEY, this.sessionId)
			this.messages = [
				{
					id: `welcome-${Date.now()}`,
					role: 'assistant',
					content: '新会话已经开始了。你可以继续问我校园服务、课表、论坛或知识库问题。'
				}
			]
			this.inputValue = ''
		},
		async syncCurrentSession() {
			const saved = uni.getStorageSync(STORAGE_KEY)
			if (!saved) {
				return
			}
			const shouldReload = saved !== this.sessionId || this.messages.length <= 1
			if (!shouldReload) {
				return
			}
			this.sessionId = saved
			try {
				const res = await getLeaderSessionDetail(saved)
				const records = res?.data?.messages || []
				if (!records.length) {
					return
				}
				this.messages = records.map((item) => ({
					id: `${item.role}-${item.id}`,
					role: item.role,
						content: item.content,
						answerType: item.answerType || '',
						outputType: item.outputType || item.answerType || 'text',
						outputTypes: item.outputTypes || [],
						outputMeta: item.outputMeta || {},
						attachments: item.attachments || []
				}))
				this.scrollToBottom()
			} catch (error) {
				// 会话可能还未产生首条消息，保持当前输入面板即可。
			}
		},
		useQuickPrompt(prompt) {
			this.inputValue = prompt
			this.sendMessage()
		},
		appendMessage(message) {
			const item = {
				id: `${message.role}-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
				...message
			}
			this.messages.push(item)
			this.scrollToBottom()
			return item
		},
		updateMessage(messageId, patch) {
			const index = this.messages.findIndex(item => item.id === messageId)
			if (index === -1) {
				return
			}
			this.messages.splice(index, 1, {
				...this.messages[index],
				...patch
			})
			this.scrollToBottom()
		},
		appendMessageContent(messageId, content) {
			const index = this.messages.findIndex(item => item.id === messageId)
			if (index === -1) {
				return
			}
			const current = this.messages[index]
			this.messages.splice(index, 1, {
				...current,
				type: '',
				content: `${current.type === 'thinking' ? '' : current.content || ''}${content}`
			})
			this.scrollToBottom()
		},
		scrollToBottom() {
			this.$nextTick(() => {
				this.scrollAnchor = ''
				this.$nextTick(() => {
					this.scrollAnchor = 'ai-message-anchor'
				})
			})
		},
			formatAnswerLines(content) {
				const normalized = String(content || '').replace(/\*\*(.*?)\*\*/g, '$1')
				if (!normalized) {
					return []
				}
				return normalized.split('\n').map(line => line.trim()).map((line) => {
					if (!line) {
						return { type: 'empty', text: '' }
				}
				if (line.startsWith('- ')) {
					return { type: 'bullet', text: line.slice(2).trim() }
					}
					return { type: 'text', text: line }
				})
			},
			getDisplayContent(message) {
				const content = String(message?.content || '')
				try {
					const parsed = JSON.parse(content)
					if (parsed && Array.isArray(parsed.images)) return String(parsed.message || '')
				} catch (error) {
					// Keep non-JSON image descriptions visible.
				}
				return content
			},
			getOutputTypeTags(message) {
				return [this.detectMessageType(message)]
			},
			detectMessageType(message) {
				const attachments = this.getMessageAttachments(message)
				if (attachments.some(item => item.type === 'image')) return 'image'
				const content = String(message?.content || '')
				if (this.containsFormula(content)) return 'formula'
				return 'text'
			},
			containsFormula(content) {
				return /```(?:math|latex|tex)\b|\$\$[\s\S]+?\$\$|\\\[[\s\S]+?\\\]|\\\([\s\S]+?\\\)|(?:公式|方程)\s*[:：]/i.test(String(content || ''))
			},
			getOutputTypeLabel(type) {
				const labels = {
					text: '文本', image: '照片', formula: '公式'
				}
				return labels[type] || '文本'
			},
			getMessageImages(message) {
				return this.getMessageAttachments(message).filter(item => item.type === 'image')
			},
			getMessageAttachments(message) {
				const files = []
				const structured = Array.isArray(message?.attachments) ? message.attachments : []
				structured.forEach(item => {
					const normalized = this.normalizeMessageAttachment(item)
					if (normalized) files.push(normalized)
				})

				const content = String(message?.content || '')
				try {
					const parsed = JSON.parse(content)
					if (Array.isArray(parsed?.images)) {
						parsed.images.forEach(item => {
							const normalized = this.normalizeMessageAttachment(
								typeof item === 'string' ? { url: item, type: 'image' } : { ...item, type: item?.type || 'image' }
							)
							if (normalized) files.push(normalized)
						})
					}
				} catch (error) {
					// Plain text responses are inspected below.
				}

				const imageUrls = content.match(/https?:\/\/[^\s"'<>，。！？；、]+?\.(?:png|jpe?g|gif|webp|bmp)(?:\?[^\s"'<>，。！？；、]*)?/gi) || []
				imageUrls.forEach(url => files.push({ type: 'image', url }))

				const seen = new Set()
				return files.filter(item => {
					if (!item?.url || seen.has(item.url)) return false
					seen.add(item.url)
					return true
				})
			},
			normalizeMessageAttachment(item) {
				if (!item) return null
				const url = String(item.url || item.fileUrl || item.path || item.href || '').trim()
				if (!url) return null
				const hint = String(item.type || item.fileType || item.mimeType || '').toLowerCase()
				const cleanUrl = url.split('?')[0].toLowerCase()
				let type = hint.includes('image') || /\.(?:png|jpe?g|gif|webp|bmp)$/.test(cleanUrl) ? 'image' : ''
				if (!type && (hint.includes('video') || /\.(?:mp4|mov|m4v|webm|ogg)$/.test(cleanUrl))) type = 'video'
				if (!type && /\.(?:pdf|docx?|pptx?)$/.test(cleanUrl)) type = cleanUrl.endsWith('.pdf') ? 'pdf' : cleanUrl.match(/\.docx?$/) ? 'docx' : 'ppt'
				return type ? { ...item, type, url } : null
			},
			previewMessageImage(image, message) {
				const urls = this.getMessageImages(message).map(item => item.url)
				uni.previewImage({ urls, current: image.url })
			},
		getTouchPoint(event) {
			const touch = event.touches && event.touches[0]
			if (touch) {
				return { x: touch.clientX, y: touch.clientY }
			}
			const changedTouch = event.changedTouches && event.changedTouches[0]
			if (changedTouch) {
				return { x: changedTouch.clientX, y: changedTouch.clientY }
			}
			return { x: 0, y: 0 }
		},
		startDrag(x, y) {
			this.clearFabCollapseTimer()
			this.fabCollapsed = false
			this.dragging = true
			this.dragMoved = false
			this.dragStartX = x
			this.dragStartY = y
			this.originLeft = this.fabLeft
			this.originTop = this.fabTop
		},
		updateDrag(x, y) {
			if (!this.dragging) {
				return
			}
			const deltaX = x - this.dragStartX
			const deltaY = y - this.dragStartY
			if (Math.abs(deltaX) > 3 || Math.abs(deltaY) > 3) {
				this.dragMoved = true
			}
			this.fabLeft = this.clamp(this.originLeft + deltaX, 12, this.screenWidth - this.fabSize - 12)
			this.fabTop = this.clamp(this.originTop + deltaY, 80, this.screenHeight - this.fabSize - this.fabBottomSpacing)
			this.updateFabDockSide()
		},
		endDrag() {
			if (!this.dragging) {
				return
			}
			if (this.dragMoved) {
				this.suppressNextTap = true
				const edgeLeft = 12
				const edgeRight = this.screenWidth - this.fabSize - 12
				this.fabLeft = this.fabLeft + this.fabSize / 2 < this.screenWidth / 2 ? edgeLeft : edgeRight
				this.updateFabDockSide()
				if (!this.panelVisible) {
					this.scheduleFabCollapse()
				}
			}
			this.dragging = false
			this.dragMoved = false
		},
		handleFabTap() {
			if (this.suppressNextTap) {
				this.suppressNextTap = false
				return
			}
			if (this.fabCollapsed) {
				this.fabCollapsed = false
				this.scheduleFabCollapse()
				return
			}
			this.togglePanel()
		},
		onTouchStart(event) {
			this.lastPointerType = 'touch'
			const point = this.getTouchPoint(event)
			this.startDrag(point.x, point.y)
		},
		onTouchMove(event) {
			const point = this.getTouchPoint(event)
			this.updateDrag(point.x, point.y)
		},
		onTouchEnd() {
			const moved = this.dragMoved
			this.endDrag()
			if (!moved) {
				this.suppressNextTap = false
			}
		},
		onMouseDown(event) {
			this.lastPointerType = 'mouse'
			this.startDrag(event.clientX, event.clientY)
			this.suppressNextTap = false
			document.addEventListener('mousemove', this.onMouseMove)
			document.addEventListener('mouseup', this.onMouseUp)
		},
		onMouseMove(event) {
			this.updateDrag(event.clientX, event.clientY)
		},
		onMouseUp() {
			this.removeMouseListeners()
			this.endDrag()
		},
		removeMouseListeners() {
			if (typeof document === 'undefined') {
				return
			}
			document.removeEventListener('mousemove', this.onMouseMove)
			document.removeEventListener('mouseup', this.onMouseUp)
		},
		clearFabCollapseTimer() {
			if (!this.collapseTimer) {
				return
			}
			clearTimeout(this.collapseTimer)
			this.collapseTimer = null
		},
		scheduleFabCollapse() {
			this.clearFabCollapseTimer()
			if (this.panelVisible || this.dragging) {
				return
			}
			this.collapseTimer = setTimeout(() => {
				this.fabCollapsed = true
				this.collapseTimer = null
			}, 1800)
		},
		updateFabDockSide() {
			this.fabDockSide = this.fabLeft + this.fabSize / 2 < this.screenWidth / 2 ? 'left' : 'right'
		},
		resetFabPosition() {
			this.fabLeft = this.defaultFabLeft
			this.fabTop = this.defaultFabTop
			this.updateFabDockSide()
		},
		clamp(value, min, max) {
			return Math.min(Math.max(value, min), max)
		},
		handleInputConfirm() {
			this.sendMessage()
		},
		handleEnterKey(event) {
			if (event?.shiftKey) {
				return
			}
			event?.preventDefault?.()
			this.sendMessage()
		},
		async sendMessage() {
			const text = this.inputValue.trim()
			if (!text || this.sending || !this.canSend) {
				return
			}

			this.appendMessage({
				role: 'user',
				content: text
			})
			this.inputValue = ''
			this.sending = true
			const assistantMessage = this.appendMessage({
				role: 'assistant',
				type: 'thinking',
				content: '思考中'
			})
			let streamStarted = false
			let streamTouched = false

			try {
				const requestPayload = {
					sessionId: this.sessionId,
					prompt: '你是智慧校园 App 的 Leader 智能助手，只负责意图识别、路由和汇总回答。',
					agentName: 'leader_agent',
					input: text
				}

				await streamLeaderAgent(requestPayload, {
					onEvent: () => {
						streamTouched = true
					},
					onSession: (payload) => {
						streamTouched = true
						this.syncSessionId(payload?.sessionId)
					},
					onDelta: (content) => {
						if (!content) return
						streamTouched = true
						streamStarted = true
						this.appendMessageContent(assistantMessage.id, content)
					},
					onDone: (payload) => {
						streamTouched = true
						this.syncSessionId(payload?.sessionId)
						const finalAnswer = payload?.answer || ''
							const current = this.messages.find(item => item.id === assistantMessage.id)
							this.updateMessage(assistantMessage.id, {
								type: '',
								content: finalAnswer || current?.content || 'Leader 这次没有返回可用答案，请换一种问法再试。',
								answerType: payload?.answerType || 'text',
								outputType: payload?.outputType || payload?.answerType || 'text',
								outputTypes: payload?.outputTypes || [],
								outputMeta: payload?.outputMeta || {},
								attachments: payload?.attachments || []
							})
					},
					onError: (payload) => {
						streamTouched = true
						throw new Error(payload?.message || '流式请求失败')
					}
				})
			} catch (error) {
				if (streamStarted || streamTouched) {
					const message = (error && (error.msg || error.message)) || '流式回复中断，请稍后再试'
					this.updateMessage(assistantMessage.id, {
						type: '',
						content: `这次流式回复中断了：${message}`
					})
				} else if (error?.fallbackToNormalRequest) {
					await this.sendMessageFallback(text, assistantMessage.id, error)
				} else {
					const message = (error && (error.msg || error.message)) || '对话失败，请稍后再试'
					this.updateMessage(assistantMessage.id, {
						type: '',
						content: `这次没有顺利完成请求：${message}`
					})
				}
			} finally {
				this.sending = false
			}
		},
		async sendMessageFallback(text, assistantMessageId, streamError) {
			try {
				const res = await queryLeaderAgent({
					sessionId: this.sessionId,
					prompt: '你是智慧校园 App 的 Leader 智能助手，只负责意图识别、路由和汇总回答。',
					agentName: 'leader_agent',
					input: text
				})
				const payload = res.data || {}
				this.syncSessionId(payload.sessionId)
					this.updateMessage(assistantMessageId, {
						type: '',
						content: payload.answer || 'Leader 这次没有返回可用答案，请换一种问法再试。',
						answerType: payload.answerType || 'text',
						outputType: payload.outputType || payload.answerType || 'text',
						outputTypes: payload.outputTypes || [],
						outputMeta: payload.outputMeta || {},
						attachments: payload.attachments || []
					})
			} catch (error) {
				const message = (error && (error.msg || error.message)) || streamError?.message || '对话失败，请稍后再试'
				this.updateMessage(assistantMessageId, {
					type: '',
					content: `这次没有顺利完成请求：${message}`
				})
			}
		},
		syncSessionId(sessionId) {
			if (!sessionId || sessionId === this.sessionId) {
				return
			}
			this.sessionId = sessionId
			uni.setStorageSync(STORAGE_KEY, sessionId)
		},
	}
}
</script>

<style lang="scss">
.ai-assistant-root {
	position: relative;
	z-index: 1200;
}

.ai-assistant-mask {
	position: fixed;
	inset: 0;
	background: rgba(20, 32, 48, 0.18);
	backdrop-filter: blur(8px);
	z-index: 1200;
}

.ai-assistant-fab {
	position: fixed;
	width: 112rpx;
	height: 112rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	cursor: pointer;
	transition: transform 0.22s ease, opacity 0.22s ease;
}

.ai-assistant-fab--collapsed.ai-assistant-fab--right {
	transform: translateX(52rpx);
}

.ai-assistant-fab--collapsed.ai-assistant-fab--left {
	transform: translateX(-52rpx);
}

.ai-assistant-fab--collapsed .ai-assistant-fab__halo {
	opacity: 0.9;
	box-shadow: 0 14rpx 30rpx rgba(52, 120, 246, 0.2);
}

.ai-assistant-fab--collapsed .ai-assistant-fab__core {
	background: rgba(255, 255, 255, 0.22);
}

.ai-assistant-fab__halo {
	position: absolute;
	inset: 0;
	border-radius: 50%;
	background:
		radial-gradient(circle at 30% 30%, rgba(255, 255, 255, 0.85), rgba(255, 255, 255, 0) 38%),
		linear-gradient(145deg, #3478f6, #5a9bff 58%, #8fd3ff);
	box-shadow: 0 18rpx 40rpx rgba(52, 120, 246, 0.28);
	animation: ai-fab-pulse 2.8s ease-in-out infinite;
}

.ai-assistant-fab__core {
	position: relative;
	width: 84rpx;
	height: 84rpx;
	border-radius: 50%;
	background: rgba(255, 255, 255, 0.18);
	backdrop-filter: blur(12px);
	display: flex;
	align-items: center;
	justify-content: center;
	border: 2rpx solid rgba(255, 255, 255, 0.32);
}

.ai-assistant-fab__label {
	font-size: 32rpx;
	font-weight: 700;
	color: #ffffff;
	letter-spacing: 2rpx;
}

.ai-assistant-panel {
	position: fixed;
	background:
		linear-gradient(180deg, rgba(248, 251, 255, 0.98), rgba(241, 247, 255, 0.98)),
		#ffffff;
	border-radius: 36rpx;
	border: 2rpx solid rgba(69, 126, 243, 0.1);
	box-shadow: 0 24rpx 56rpx rgba(45, 86, 170, 0.18);
	display: flex;
	flex-direction: column;
	overflow: hidden;
	transform: translateY(18rpx) scale(0.96);
	opacity: 0;
	transition: all 0.22s ease;
	min-height: 0;
}

.ai-assistant-panel--active {
	transform: translateY(0) scale(1);
	opacity: 1;
}

.ai-assistant-panel__header {
	padding: 26rpx 28rpx 22rpx;
	display: flex;
	align-items: flex-start;
	justify-content: space-between;
	background:
		radial-gradient(circle at top right, rgba(255, 255, 255, 0.14), transparent 30%),
		linear-gradient(135deg, #3d7df5, #5a93ff 78%);
	color: #fff;
	flex-shrink: 0;
}

.ai-assistant-panel__eyebrow {
	display: block;
	font-size: 20rpx;
	opacity: 0.76;
	text-transform: uppercase;
	letter-spacing: 2rpx;
}

.ai-assistant-panel__title {
	display: block;
	margin-top: 8rpx;
	font-size: 34rpx;
	font-weight: 700;
}

.ai-assistant-panel__actions {
	display: flex;
	align-items: center;
	gap: 16rpx;
}

.ai-assistant-panel__icon,
.ai-assistant-panel__close {
	min-width: 72rpx;
	height: 56rpx;
	padding: 0 18rpx;
	border-radius: 28rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	background: rgba(255, 255, 255, 0.2);
	font-size: 22rpx;
}

.ai-assistant-panel__close {
	font-size: 34rpx;
	padding-bottom: 4rpx;
}

.ai-assistant-panel__messages {
	flex: 1;
	min-height: 0;
	padding: 24rpx 22rpx 18rpx;
	box-sizing: border-box;
	background:
		linear-gradient(180deg, rgba(255, 255, 255, 0.38), rgba(255, 255, 255, 0)),
		transparent;
}

.ai-message-row {
	display: flex;
	margin-bottom: 18rpx;
}

.ai-message-row--user {
	justify-content: flex-end;
}

.ai-message-row--assistant {
	justify-content: flex-start;
}

.ai-message-bubble {
	max-width: 82%;
	padding: 20rpx 22rpx;
	border-radius: 26rpx;
}

.ai-message-bubble--assistant {
	background: rgba(255, 255, 255, 0.96);
	border: 2rpx solid rgba(61, 125, 245, 0.06);
	box-shadow: 0 10rpx 24rpx rgba(72, 103, 163, 0.08);
}

.ai-message-bubble--user {
	background: linear-gradient(135deg, #3d7df5, #69a6ff);
	color: #ffffff;
	box-shadow: 0 12rpx 26rpx rgba(61, 125, 245, 0.2);
}

.ai-message-content {
	display: block;
	font-size: 27rpx;
	line-height: 1.72;
	word-break: break-all;
}

.ai-output-type-list {
	display: flex;
	flex-wrap: wrap;
	gap: 8rpx;
	margin-bottom: 10rpx;
}

.ai-output-type-tag {
	padding: 4rpx 10rpx;
	border-radius: 999rpx;
	background: #eef3fa;
	color: #496d9c;
	font-size: 19rpx;
	font-weight: 700;
}

.ai-message-image {
	width: 360rpx;
	max-width: 100%;
	height: 220rpx;
	margin-bottom: 12rpx;
	border-radius: 16rpx;
	background: #eef2f7;
}

.ai-message-line {
	display: flex;
	align-items: flex-start;
	min-height: 34rpx;
}

.ai-message-line + .ai-message-line {
	margin-top: 8rpx;
}

.ai-message-line--empty {
	min-height: 18rpx;
}

.ai-message-line__dot {
	flex: 0 0 10rpx;
	width: 10rpx;
	height: 10rpx;
	margin-top: 18rpx;
	margin-right: 12rpx;
	border-radius: 50%;
	background: #4a88f7;
}

.ai-message-line__text {
	flex: 1;
	font-size: 27rpx;
	line-height: 1.72;
	color: inherit;
	word-break: break-all;
}

.ai-thinking-indicator {
	display: flex;
	align-items: center;
	gap: 14rpx;
	min-height: 38rpx;
}

.ai-thinking-text {
	font-size: 25rpx;
	color: #6f82a0;
}

.ai-thinking-dots {
	display: flex;
	align-items: center;
	gap: 8rpx;
}

.ai-thinking-dots text {
	width: 10rpx;
	height: 10rpx;
	border-radius: 50%;
	background: #7faeff;
	animation: ai-thinking-bounce 1s ease-in-out infinite;
}

.ai-thinking-dots text:nth-child(2) {
	animation-delay: 0.16s;
}

.ai-thinking-dots text:nth-child(3) {
	animation-delay: 0.32s;
}

.ai-assistant-panel__quick {
	padding: 0 22rpx 10rpx;
	display: flex;
	flex-wrap: nowrap;
	gap: 12rpx;
	flex-shrink: 0;
	align-items: center;
}

.ai-assistant-panel__quick-chip {
	flex: 1 1 0;
	min-width: 0;
	padding: 10rpx 12rpx;
	border-radius: 999rpx;
	background: rgba(229, 240, 255, 0.96);
	color: #496d9c;
	font-size: 21rpx;
	text-align: center;
	white-space: nowrap;
	overflow: hidden;
	text-overflow: ellipsis;
}

.ai-assistant-panel__composer {
	padding: 16rpx 22rpx 22rpx;
	background: rgba(255, 255, 255, 0.9);
	border-top: 2rpx solid rgba(61, 125, 245, 0.06);
	backdrop-filter: blur(14px);
	flex-shrink: 0;
}

.ai-assistant-panel__textarea {
	width: 100%;
	min-height: 92rpx;
	max-height: 220rpx;
	padding: 18rpx 20rpx;
	box-sizing: border-box;
	background: linear-gradient(180deg, #ffffff, #f7faff);
	border-radius: 22rpx;
	border: 2rpx solid rgba(61, 125, 245, 0.08);
	font-size: 28rpx;
	line-height: 1.5;
}

.ai-assistant-panel__composer-bottom {
	margin-top: 16rpx;
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 18rpx;
}

.ai-assistant-panel__tip {
	flex: 1;
	font-size: 20rpx;
	line-height: 1.5;
	color: #6f82a0;
}

.ai-assistant-panel__send {
	min-width: 132rpx;
	height: 72rpx;
	border-radius: 999rpx;
	background: linear-gradient(135deg, #4a82f7, #6baeff);
	color: #fff;
	display: flex;
	align-items: center;
	justify-content: center;
	font-size: 26rpx;
	font-weight: 700;
	box-shadow: 0 14rpx 24rpx rgba(74, 130, 247, 0.24);
}

.ai-assistant-panel__send--disabled {
	opacity: 0.45;
}

@keyframes ai-fab-pulse {
	0%, 100% {
		transform: scale(1);
	}
	50% {
		transform: scale(1.05);
	}
}

@keyframes ai-thinking-bounce {
	0%, 80%, 100% {
		opacity: 0.35;
		transform: translateY(0);
	}
	40% {
		opacity: 1;
		transform: translateY(-6rpx);
	}
}
</style>
