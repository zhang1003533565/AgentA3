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
						<view v-if="message.role === 'assistant'" class="ai-message-content">
							<view
								v-for="(line, lineIndex) in formatAnswerLines(message.content)"
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

						<view v-if="message.searchKeyword" class="ai-message-debug">
							<text class="ai-message-debug__label">关键词</text>
							<text class="ai-message-debug__value">{{ message.searchKeyword }}</text>
						</view>

						<view v-if="message.matchedResults && message.matchedResults.length" class="ai-message-result-list">
							<view
								v-for="group in groupMatchedResults(message.matchedResults)"
								:key="`${message.id}-${group.type}`"
								class="ai-message-result-group"
							>
								<view class="ai-message-result-group__header">
									<text class="ai-message-result-group__title">{{ group.label }}</text>
									<text class="ai-message-result-group__count">{{ group.items.length }} 条</text>
								</view>

								<view
									v-for="item in group.items"
									:key="`${message.id}-${group.type}-${item.id || item.name}`"
									class="ai-message-result-card"
									:class="{ 'ai-message-result-card--schedule': item.type === 'course_schedule' }"
									@click="openResultDetail(item)"
								>
									<view class="ai-message-result-card__top">
										<text class="ai-message-result-card__type">{{ formatResultType(item.type) }}</text>
										<text class="ai-message-result-card__name">{{ item.name || item.title || '未命名结果' }}</text>
									</view>

									<view v-if="item.type === 'course_schedule'" class="ai-message-result-card__schedule-table">
										<view
											v-for="detail in getResultDetails(item)"
											:key="`${message.id}-${group.type}-${item.id || item.name}-${detail.label}`"
											class="ai-message-result-card__schedule-row"
										>
											<text class="ai-message-result-card__schedule-label">{{ detail.label }}</text>
											<text class="ai-message-result-card__schedule-value">{{ detail.value }}</text>
										</view>
									</view>

									<view v-if="getResultHighlights(item).length" class="ai-message-result-card__highlights">
										<text
											v-for="highlight in getResultHighlights(item)"
											:key="`${message.id}-${group.type}-${item.id || item.name}-${highlight}`"
											class="ai-message-result-card__highlight"
										>
											{{ highlight }}
										</text>
									</view>

									<view v-if="item.type !== 'course_schedule' && getResultDetails(item).length" class="ai-message-result-card__detail-table">
										<view
											v-for="detail in getResultDetails(item)"
											:key="`${message.id}-${group.type}-${item.id || item.name}-${detail.label}`"
											class="ai-message-result-card__detail-row"
										>
											<text class="ai-message-result-card__detail-label">{{ detail.label }}</text>
											<text class="ai-message-result-card__detail-value">{{ detail.value }}</text>
										</view>
									</view>

									<text v-if="item.description" class="ai-message-result-card__desc">{{ item.description }}</text>
								</view>
							</view>
						</view>
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
					placeholder="问我食堂、菜品、优惠券都可以"
					maxlength="300"
					auto-height
					:disabled="sending"
				/>
				<view class="ai-assistant-panel__composer-bottom">
					<text class="ai-assistant-panel__tip">{{ sending ? 'AI 正在思考...' : '支持结合你的登录态与会话上下文回答' }}</text>
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
import { chatWithAi, streamChatWithAi } from '@/api/ai.js'

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
			quickPrompts: ['推荐今天的食堂', '麻辣烫在哪家', '看看有什么优惠券'],
			messages: [
				{
					id: 'welcome',
					role: 'assistant',
					content: '你好，我是校园助手。你可以直接问我食堂、档口、菜品和优惠券。'
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
			this.panelVisible = !this.panelVisible
			this.fabCollapsed = false
			if (this.panelVisible) {
				this.clearFabCollapseTimer()
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
		resetSession() {
			this.sessionId = this.createSessionId()
			uni.setStorageSync(STORAGE_KEY, this.sessionId)
			this.messages = [
				{
					id: `welcome-${Date.now()}`,
					role: 'assistant',
					content: '新会话已经开始了。你可以继续问我校园里的食堂和优惠信息。'
				}
			]
			this.inputValue = ''
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
		scrollToBottom() {
			this.$nextTick(() => {
				this.scrollAnchor = ''
				this.$nextTick(() => {
					this.scrollAnchor = 'ai-message-anchor'
				})
			})
		},
		formatResultType(type) {
			const map = {
				restaurant: '食堂',
				stall: '档口',
				dish: '菜品',
				coupon: '优惠券',
				course_schedule: '课程'
			}
			return map[type] || '结果'
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
		groupMatchedResults(results) {
			const grouped = {
				restaurant: [],
				stall: [],
				dish: [],
				coupon: [],
				course_schedule: []
			}
			;(Array.isArray(results) ? results : []).forEach((item) => {
				if (item && grouped[item.type]) {
					grouped[item.type].push(item)
				}
			})
			return ['course_schedule', 'restaurant', 'stall', 'dish', 'coupon']
				.filter(type => grouped[type].length)
				.map(type => ({
					type,
					label: this.formatResultType(type),
					items: grouped[type]
				}))
		},
		formatCurrency(value) {
			const amount = Number(value)
			if (!Number.isFinite(amount)) {
				return ''
			}
			return `¥${amount % 1 === 0 ? amount.toFixed(0) : amount.toFixed(2)}`
		},
		formatScore(value) {
			const score = Number(value)
			if (!Number.isFinite(score)) {
				return ''
			}
			return score.toFixed(2)
		},
		getResultHighlights(item) {
			const highlights = []
			if (item.type === 'stall') {
				if (item.category) highlights.push(item.category)
				if (item.avgPrice !== undefined && item.avgPrice !== null) highlights.push(`均价 ${this.formatCurrency(item.avgPrice)}`)
				if (item.score !== undefined && item.score !== null) highlights.push(`评分 ${this.formatScore(item.score)}`)
			} else if (item.type === 'course_schedule') {
				if (item.requestedWeekdayText) highlights.push(item.requestedWeekdayText)
				else if (item.weekdayText) highlights.push(item.weekdayText)
				if (item.requestedWeek) highlights.push(`第${item.requestedWeek}周`)
				if (item.classSessions) highlights.push(item.classSessions)
			} else if (item.type === 'dish') {
				if (item.category) highlights.push(item.category)
				if (item.taste) highlights.push(item.taste)
				if (item.price !== undefined && item.price !== null) highlights.push(`价格 ${this.formatCurrency(item.price)}`)
				if (item.rating !== undefined && item.rating !== null) highlights.push(`评分 ${this.formatScore(item.rating)}`)
			} else if (item.type === 'coupon') {
				if (item.tagType) highlights.push(this.formatCouponTag(item.tagType))
				if (item.startDate && item.endDate) highlights.push(`${item.startDate} - ${item.endDate}`)
			} else if (item.type === 'restaurant') {
				if (item.location) highlights.push(item.location)
			}
			return highlights
		},
		getResultDetails(item) {
			const details = []
			if (item.type === 'restaurant') {
				if (item.location) details.push({ label: '位置', value: item.location })
			}
			if (item.type === 'course_schedule') {
				if (item.requestedWeek || item.weekRange) details.push({ label: '周次', value: item.requestedWeek ? `第${item.requestedWeek}周` : item.weekRange })
				if (item.weekdayText) details.push({ label: '星期', value: item.weekdayText })
				if (item.classSessions) details.push({ label: '时间', value: item.classSessions })
				if (item.location) details.push({ label: '地点', value: item.location })
				if (item.teacherName) details.push({ label: '教师', value: item.teacherName })
				if (item.assessmentType) details.push({ label: '考核', value: item.assessmentType })
				if (item.credit !== undefined && item.credit !== null && `${item.credit}` !== '') details.push({ label: '学分', value: String(item.credit) })
			}
			if (item.type === 'stall') {
				if (item.restaurantId) details.push({ label: '所属食堂', value: `餐厅 #${item.restaurantId}` })
			}
			if (item.type === 'dish') {
				if (item.stallId) details.push({ label: '所属档口', value: `档口 #${item.stallId}` })
			}
			if (item.type === 'coupon') {
				if (item.facilityName) details.push({ label: '所属食堂', value: item.facilityName })
				if (item.stallName) details.push({ label: '所属档口', value: item.stallName })
				if (item.pickupLocation) details.push({ label: '领取地点', value: item.pickupLocation })
			}
			return details
		},
		formatCouponTag(tagType) {
			const map = {
				hot: '热门',
				new: '上新',
				recommend: '推荐'
			}
			return map[tagType] || tagType
		},
		openResultDetail(item) {
			const url = this.getResultDetailUrl(item)
			if (!url) {
				return
			}
			uni.navigateTo({ url })
		},
		getResultDetailUrl(item) {
			if (!item || !item.type || !item.id) {
				return ''
			}
			if (item.type === 'restaurant') {
				return `/subpackage_facility/restaurantDetail/restaurantDetail?id=${item.id}`
			}
			if (item.type === 'stall') {
				return `/subpackage_facility/stallDetail/stallDetail?stallId=${item.id}`
			}
			if (item.type === 'dish') {
				return `/subpackage_facility/dishDetail/dishDetail?dishId=${item.id}`
			}
			if (item.type === 'coupon') {
				return `/subpackage_promotion/promotionDetail/promotionDetail?id=${item.id}`
			}
			if (item.type === 'course_schedule') {
				return `/subpackage_schedule/scheduleDetail/scheduleDetail?id=${item.id}`
			}
			return ''
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
				content: '正在整理你的问题...',
				searchKeyword: '',
				matchedResults: []
			})

			try {
				const requestPayload = {
					sessionId: this.sessionId,
					prompt: '你是智慧校园助手，请优先依据系统返回的食堂、档口、菜品和优惠券结果回答，语气自然简洁。',
					input: text
				}

				const isH5 = typeof window !== 'undefined' && typeof fetch === 'function'
				if (isH5) {
					let answerBuffer = ''
					await streamChatWithAi(requestPayload, {
						session: (payload) => {
							if (payload.sessionId && payload.sessionId !== this.sessionId) {
								this.sessionId = payload.sessionId
								uni.setStorageSync(STORAGE_KEY, payload.sessionId)
							}
						},
						search: (payload) => {
							this.updateMessage(assistantMessage.id, {
								content: answerBuffer || '正在整理匹配结果...',
								searchKeyword: payload.searchKeyword || '',
								matchedResults: Array.isArray(payload.matchedResults) ? payload.matchedResults.slice(0, 6) : []
							})
						},
						delta: (payload) => {
							answerBuffer += payload.content || ''
							this.updateMessage(assistantMessage.id, {
								content: answerBuffer
							})
						},
						done: (payload) => {
							this.updateMessage(assistantMessage.id, {
								content: payload.answer || answerBuffer || '我这次没有整理出可用答案，你可以换一种问法试试。',
								searchKeyword: payload.searchKeyword || '',
								matchedResults: Array.isArray(payload.matchedResults) ? payload.matchedResults.slice(0, 6) : []
							})
						},
						error: (payload) => {
							throw new Error(payload.message || '流式请求失败')
						}
					})
				} else {
					const res = await chatWithAi(requestPayload)
					const payload = res.data || {}
					if (payload.sessionId && payload.sessionId !== this.sessionId) {
						this.sessionId = payload.sessionId
						uni.setStorageSync(STORAGE_KEY, payload.sessionId)
					}
					this.updateMessage(assistantMessage.id, {
						content: payload.answer || '我这次没有整理出可用答案，你可以换一种问法试试。',
						searchKeyword: payload.searchKeyword || '',
						matchedResults: Array.isArray(payload.matchedResults) ? payload.matchedResults.slice(0, 6) : []
					})
				}
			} catch (error) {
				const message = (error && (error.msg || error.message)) || '对话失败，请稍后再试'
				this.updateMessage(assistantMessage.id, {
					content: `这次没有顺利完成请求：${message}`
				})
			} finally {
				this.sending = false
			}
		}
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

.ai-message-debug {
	margin-top: 18rpx;
	padding: 14rpx 18rpx;
	border-radius: 20rpx;
	background: #edf4ff;
}

.ai-message-debug__label {
	font-size: 20rpx;
	color: #6b7f9b;
	margin-right: 10rpx;
}

.ai-message-debug__value {
	font-size: 22rpx;
	color: #2d6ce0;
	font-weight: 600;
}

.ai-message-result-list {
	margin-top: 18rpx;
	display: flex;
	flex-direction: column;
	gap: 14rpx;
}

.ai-message-result-group {
	padding: 18rpx;
	background: rgba(236, 244, 255, 0.84);
	border-radius: 22rpx;
}

.ai-message-result-group__header {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-bottom: 14rpx;
}

.ai-message-result-group__title {
	font-size: 24rpx;
	font-weight: 700;
	color: #284f96;
}

.ai-message-result-group__count {
	font-size: 20rpx;
	color: #7488a3;
}

.ai-message-result-group + .ai-message-result-group {
	margin-top: 8rpx;
}

.ai-message-result-card {
	padding: 18rpx;
	background: linear-gradient(180deg, #f8fbff, #eef4ff);
	border-radius: 20rpx;
	cursor: pointer;
}

.ai-message-result-card--schedule {
	background: linear-gradient(180deg, #ffffff, #f4f8ff);
	border: 2rpx solid rgba(85, 133, 240, 0.12);
	box-shadow: 0 10rpx 22rpx rgba(72, 109, 184, 0.08);
}

.ai-message-result-card + .ai-message-result-card {
	margin-top: 12rpx;
}

.ai-message-result-card__top {
	display: flex;
	align-items: center;
	gap: 12rpx;
	flex-wrap: wrap;
}

.ai-message-result-card__type {
	padding: 6rpx 14rpx;
	border-radius: 999rpx;
	background: #dfeaff;
	color: #356fdb;
	font-size: 20rpx;
}

.ai-message-result-card__name {
	font-size: 26rpx;
	font-weight: 700;
	color: #20323c;
}

.ai-message-result-card__highlights {
	display: flex;
	flex-wrap: wrap;
	gap: 10rpx;
	margin-top: 12rpx;
}

.ai-message-result-card__highlight {
	padding: 6rpx 12rpx;
	border-radius: 999rpx;
	background: #e8f1ff;
	color: #476b9d;
	font-size: 20rpx;
}

.ai-message-result-card__detail-table {
	margin-top: 14rpx;
	padding: 12rpx 14rpx;
	background: rgba(255, 255, 255, 0.86);
	border-radius: 16rpx;
}

.ai-message-result-card__schedule-table {
	margin-top: 14rpx;
	border-radius: 18rpx;
	overflow: hidden;
	background: rgba(255, 255, 255, 0.92);
	border: 2rpx solid rgba(83, 129, 234, 0.1);
}

.ai-message-result-card__schedule-row {
	display: flex;
	align-items: flex-start;
	padding: 14rpx 16rpx;
	gap: 14rpx;
}

.ai-message-result-card__schedule-row + .ai-message-result-card__schedule-row {
	border-top: 2rpx solid rgba(83, 129, 234, 0.08);
}

.ai-message-result-card__schedule-label {
	flex: 0 0 76rpx;
	font-size: 21rpx;
	font-weight: 700;
	color: #4d75bd;
}

.ai-message-result-card__schedule-value {
	flex: 1;
	font-size: 22rpx;
	line-height: 1.55;
	color: #274261;
	word-break: break-all;
}

.ai-message-result-card__detail-row {
	display: flex;
	align-items: flex-start;
	gap: 12rpx;
}

.ai-message-result-card__detail-row + .ai-message-result-card__detail-row {
	margin-top: 8rpx;
}

.ai-message-result-card__detail-label {
	flex: 0 0 92rpx;
	font-size: 21rpx;
	color: #7183a0;
}

.ai-message-result-card__detail-value {
	flex: 1;
	font-size: 21rpx;
	line-height: 1.5;
	color: #375170;
	word-break: break-all;
}

.ai-message-result-card__desc,
.ai-message-result-card__meta {
	display: block;
	margin-top: 10rpx;
	font-size: 22rpx;
	line-height: 1.6;
	color: #60728d;
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
</style>
