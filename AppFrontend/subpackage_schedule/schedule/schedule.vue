<template>
	<view class="schedule-page"
		@touchstart="handleTouchStart"
		@touchmove="handleTouchMove"
		@touchend="handleTouchEnd">
		<!-- Header Bar（独立页面时显示） -->
		<view v-if="showHeader" class="header-bar">
			<view class="header-left">
				<view class="menu-btn" @click="goBack">
					<view class="menu-line"></view>
					<view class="menu-line"></view>
					<view class="menu-line"></view>
				</view>
				<view class="week-info-wrapper">
					<view class="week-info" @click="showWeekSelector">
						<text class="week-text">第{{ currentWeek }}周</text>
						<text class="week-caret">▼</text>
					</view>
					<view class="semester-info">
						<text class="semester-text">{{ semester }}</text>
					</view>
				</view>
			</view>
			<view class="header-actions">
				<view class="utility-btn" @click="shareSchedule">
					<view class="utility-copy">
						<view class="copy-back"></view>
						<view class="copy-front"></view>
					</view>
				</view>
				<view class="utility-btn" @click="showImportMenu">
					<view class="utility-expand">
						<text class="expand-arrow">↗</text>
					</view>
				</view>
				<view class="import-btn" @click="showImportMenu">
					<text class="import-plus">+</text>
				</view>
			</view>
		</view>

		<!-- 导入菜单弹窗 -->
		<view v-if="showImportPopup" class="import-popup" @click="showImportPopup = false">
			<view class="popup-content" @click.stop>
				<view class="popup-item" @click="importFromJwx">
					<text class="popup-title">教务系统导课</text>
					<text class="popup-desc">导入至{{ semester }}</text>
				</view>
				<view class="popup-item" @click="openImportCodePopup">
					<text class="popup-title">分享码导入</text>
					<text class="popup-desc">使用好友分享码导入课表</text>
				</view>
			</view>
		</view>

		<!-- 分享码导入弹窗 -->
		<view v-if="showImportCodePopup" class="import-code-popup" @click="showImportCodePopup = false">
			<view class="import-code-content" @click.stop>
				<view class="popup-header">
					<text class="popup-title-text">分享码导入</text>
					<text class="popup-close" @click="showImportCodePopup = false">×</text>
				</view>
				<view class="popup-body">
					<text class="input-label">请输入好友的课表分享码</text>
					<input
						class="share-code-input"
						type="text"
						v-model="shareCodeInput"
						placeholder="例如：SCH260405A1B2"
						maxlength="20"
					/>
				</view>
				<view class="popup-footer">
					<button class="cancel-btn" @click="showImportCodePopup = false">取消</button>
					<button class="confirm-btn" @click="confirmImportShareCode">导入课表</button>
				</view>
			</view>
		</view>

		<view class="schedule-shell">
			<!-- 星期栏 -->
			<view class="weekday-bar">
				<view class="month-label">{{ currentMonth }}月</view>
				<view class="weekday-item" :class="{ active: currentWeekday === 1 }" @click="switchDay(1)">
					<text class="weekday-name">一</text>
					<text class="weekday-date">{{ getWeekDayDate(1) }}</text>
				</view>
				<view class="weekday-item" :class="{ active: currentWeekday === 2 }" @click="switchDay(2)">
					<text class="weekday-name">二</text>
					<text class="weekday-date">{{ getWeekDayDate(2) }}</text>
				</view>
				<view class="weekday-item" :class="{ active: currentWeekday === 3 }" @click="switchDay(3)">
					<text class="weekday-name">三</text>
					<text class="weekday-date">{{ getWeekDayDate(3) }}</text>
				</view>
				<view class="weekday-item" :class="{ active: currentWeekday === 4 }" @click="switchDay(4)">
					<text class="weekday-name">四</text>
					<text class="weekday-date">{{ getWeekDayDate(4) }}</text>
				</view>
				<view class="weekday-item" :class="{ active: currentWeekday === 5 }" @click="switchDay(5)">
					<text class="weekday-name">五</text>
					<text class="weekday-date">{{ getWeekDayDate(5) }}</text>
				</view>
				<view class="weekday-item" :class="{ active: currentWeekday === 6 }" @click="switchDay(6)">
					<text class="weekday-name">六</text>
					<text class="weekday-date">{{ getWeekDayDate(6) }}</text>
				</view>
				<view class="weekday-item" :class="{ active: currentWeekday === 7 }" @click="switchDay(7)">
					<text class="weekday-name">日</text>
					<text class="weekday-date">{{ getWeekDayDate(7) }}</text>
				</view>
			</view>

			<view class="board-card">
				<view class="period-column">
					<view v-for="period in periods" :key="period.index" class="period-item">
						<text class="period-text">{{ period.index }}</text>
						<text class="period-time">{{ period.start }}</text>
						<text class="period-time">{{ period.end }}</text>
					</view>
				</view>

				<view class="course-board">
					<!-- 横向分割线 -->
					<view
						class="h-divider"
						v-for="i in periods.length"
						:key="'h'+i"
						:style="{ top: (i - 1) * (periodHeight + periodGap) + 'rpx' }"
					></view>
					<!-- 底部边界线 -->
					<view class="h-divider" :style="{ top: periods.length * (periodHeight + periodGap) + 'rpx' }"></view>

					<!-- 纵向分割线 -->
					<view class="v-divider" v-for="i in 6" :key="'v'+i" :style="{ left: (i * (100 / 7)) + '%' }"></view>

					<!-- 课程卡片 -->
					<view
						v-for="course in displayCourses"
						:key="course.renderId || course.id"
						class="course-block"
						:class="[
							`course-block--${course.theme}`,
							{
								'course-block--inactive': !course.isCurrentWeek,
								'course-block--with-banner': shouldShowNonCurrentFlag(course)
							}
						]"
						:style="courseStyle(course)"
						@click="goToDetail(course)"
					>
						<view
							class="course-accent"
							:class="{ 'course-accent--hidden': shouldShowNonCurrentFlag(course) }"
						></view>
						<view v-if="getSlotCourseCount(course) > 1" class="course-badge">
							{{ getSlotCourseCount(course) }}
						</view>
						<view v-if="shouldShowNonCurrentFlag(course)" class="course-week-banner">非本周</view>
						<view class="course-content">
							<text class="course-title">{{ course.name }}@{{ course.location }}</text>
						</view>
					</view>
				</view>
			</view>
		</view>

		<view v-if="showCoursePopup" class="course-popup-mask" @click="closeCoursePopup">
			<view class="course-popup-stack" @click.stop>
				<view v-if="courseDetailLoading" class="course-popup course-popup--loading">
					<view class="course-popup-loading">加载中...</view>
				</view>
				<view
					v-else
					v-for="course in popupCourses"
					:key="`popup-${course.id}`"
					class="course-popup"
				>
					<view class="course-popup-header">
						<view class="course-popup-title-wrap">
							<text class="course-popup-title">{{ course.courseName || course.name }}</text>
						</view>
						<view class="course-popup-edit" @click="closeCoursePopup">关闭</view>
					</view>
					<view class="course-popup-body">
						<text class="course-popup-weeks">{{ course.weekRange || '周次待同步' }}</text>
						<text class="course-popup-line">{{ formatPopupSchedule(course) }}</text>
						<text class="course-popup-line">教室: {{ course.location || '-' }}</text>
						<text class="course-popup-line">老师: {{ course.teacherName || '-' }}</text>
					</view>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
const PERIOD_HEIGHT = 140
const PERIOD_GAP = 8
const PERIODS = [
	{ index: 1, start: '08:00', end: '08:45' },
	{ index: 2, start: '08:55', end: '09:40' },
	{ index: 3, start: '10:00', end: '10:45' },
	{ index: 4, start: '10:55', end: '11:40' },
	{ index: 5, start: '14:30', end: '15:15' },
	{ index: 6, start: '15:25', end: '16:10' },
	{ index: 7, start: '16:20', end: '17:05' },
	{ index: 8, start: '17:15', end: '18:00' },
	{ index: 9, start: '18:30', end: '19:15' },
	{ index: 10, start: '19:25', end: '20:10' }
]

const isWeekInRange = (weekRange, currentWeek) => {
	if (!weekRange || !currentWeek) return false
	const normalized = String(weekRange).replace(/\s+/g, '')
	const parts = normalized.split(/[，,]/).filter(Boolean)

	return parts.some((part) => {
		const oddOnly = part.includes('单')
		const evenOnly = part.includes('双')
		const purePart = part.replace(/\(单\)|\(双\)|单|双|周/g, '')

		if (purePart.includes('-')) {
			const [start, end] = purePart.split('-').map(Number)
			if (!start || !end || currentWeek < start || currentWeek > end) return false
			if (oddOnly && currentWeek % 2 === 0) return false
			if (evenOnly && currentWeek % 2 !== 0) return false
			return true
		}

		const week = Number(purePart)
		if (!week || week !== currentWeek) return false
		if (oddOnly && currentWeek % 2 === 0) return false
		if (evenOnly && currentWeek % 2 !== 0) return false
		return true
	})
}

export default {
	name: 'ScheduleBoard',
	props: {
		// 是否显示 Header Bar（独立页面时显示）
		showHeader: {
			type: Boolean,
			default: true
		}
	},
	data() {
		return {
			currentWeek: 1,
			currentWeekday: new Date().getDay() || 7,
			periods: PERIODS,
			periodHeight: PERIOD_HEIGHT,
			periodGap: PERIOD_GAP,
			courses: [],
			loading: false,
			currentMonth: 3,
			weekDates: [], // 存储本周每天的日期
			semester: '2025-2026 第 1 学期',
			showImportPopup: false,
			showImportCodePopup: false,
			shareCodeInput: '',
			semesterStart: '',
			myShareCode: '',
			showCoursePopup: false,
			courseDetailLoading: false,
			selectedCourse: {},
			popupCourses: [],
			// 触摸滑动相关
			touchStartX: 0,
			touchStartY: 0,
			touchMoveX: 0,
			isSwiping: false
		}
	},
	mounted() {
		this.loadSchedule()
		this.calculateWeekDates()
	},
	computed: {
		displayCourses() {
			const grouped = new Map()
			this.courses.forEach((course) => {
				const key = this.getSlotKey(course)
				if (!grouped.has(key)) {
					grouped.set(key, [])
				}
				grouped.get(key).push(course)
			})

			return Array.from(grouped.values()).map((slotCourses) => {
				const currentCourse = slotCourses.find((item) => item.isCurrentWeek)
				const representative = currentCourse || slotCourses[0]
				return {
					...representative,
					slotCount: slotCourses.length
				}
			})
		}
	},
	methods: {
		async loadSchedule() {
			this.loading = true
			try {
				const token = uni.getStorageSync('token') || ''
				uni.request({
					url: 'http://localhost:8080/api/browser/jwx/schedule/current',
					method: 'GET',
					header: {
						'Authorization': 'Bearer ' + token
					},
					success: (res) => {
						if (res.statusCode === 200 && res.data.code === 200) {
							const scheduleData = res.data.data
							this.currentWeek = scheduleData.currentWeek || 1
							if (scheduleData.semester) {
								this.semester = scheduleData.semester
							}
							if (scheduleData.semesterStart) {
								this.semesterStart = scheduleData.semesterStart
							}
							this.loadAllSchedules(token)
						} else {
							this.loading = false
							uni.showToast({
								title: res.data.message || '获取课表失败',
								icon: 'none'
							})
						}
					},
					fail: (err) => {
						this.loading = false
						console.error('加载课表失败:', err)
						uni.showToast({
							title: '加载失败',
							icon: 'none'
						})
					}
				})
			} catch (error) {
				this.loading = false
				console.error('加载课表失败:', error)
				uni.showToast({
					title: '加载失败',
					icon: 'none'
				})
			}
		},
		loadAllSchedules(token) {
			uni.request({
				url: 'http://localhost:8080/api/schedule',
				method: 'GET',
				header: {
					'Authorization': 'Bearer ' + token
				},
				success: (res) => {
					this.loading = false
					if (res.statusCode === 200 && res.data.code === 200) {
						this.courses = this.transformScheduleData(res.data.data || [])
						this.calculateWeekDates()
						return
					}
					uni.showToast({
						title: res.data.message || '获取课表失败',
						icon: 'none'
					})
				},
				fail: (err) => {
					this.loading = false
					console.error('加载全部课表失败:', err)
					uni.showToast({
						title: '加载失败',
						icon: 'none'
					})
				}
			})
		},

		transformScheduleData(scheduleList) {
			const themes = ['green', 'red', 'orange', 'yellow', 'blue', 'purple']
			return scheduleList.flatMap((item, index) => {
				const courseName = item.courseName || item.name || ''
				const teacherName = item.teacherName || ''
				const location = item.location || ''
				const classSessions = item.classSessions || ''
				const weekRange = item.weekRange || ''
				let startSection = 1
				let endSection = 1

				if (classSessions) {
					const match = classSessions.match(/(\d+)-(\d+)\s*节/)
					if (match) {
						startSection = parseInt(match[1])
						endSection = parseInt(match[2])
					} else {
						const singleMatch = classSessions.match(/(\d+)\s*节/)
						if (singleMatch) {
							startSection = parseInt(singleMatch[1])
							endSection = startSection
						}
					}
				}

				const courseChunks = []
				for (let chunkStart = startSection; chunkStart <= endSection; chunkStart += 2) {
					const chunkEnd = Math.min(chunkStart + 1, endSection)
					courseChunks.push({
						id: item.id,
						renderId: `${item.id}-${chunkStart}-${chunkEnd}`,
						weekday: item.weekday || 1,
						name: courseName,
						courseName,
						teacherName,
						location,
						classSessions,
						weekRange,
						start: chunkStart,
						end: chunkEnd,
						theme: themes[index % themes.length],
						isCurrentWeek: isWeekInRange(weekRange, this.currentWeek)
					})
				}

				return courseChunks
			})
		},

		getWeekDayName(day) {
			const names = ['日', '一', '二', '三', '四', '五', '六']
			return '星期' + names[day]
		},

		// 计算本周每天的日期
		calculateWeekDates() {
			if (!this.semesterStart) {
				// 如果没有学期开始日期，使用当前日期计算
				this.calculateWeekDatesFromNow()
				return
			}

			// 解析学期开始日期
			const startDate = new Date(this.semesterStart)

			// 计算当前周的第一天（学期开始日期 + (currentWeek - 1) * 7 天）
			const weekStart = new Date(startDate)
			weekStart.setDate(startDate.getDate() + (this.currentWeek - 1) * 7)

			// 计算本周每天的日期
			this.weekDates = []
			for (let i = 0; i < 7; i++) {
				const date = new Date(weekStart)
				date.setDate(weekStart.getDate() + i)
				this.weekDates.push({
					month: date.getMonth() + 1,
					day: date.getDate()
				})
			}

			// 设置当前月份
			this.currentMonth = this.weekDates[0]?.month || new Date().getMonth() + 1
		},
		// 使用当前日期计算（兼容模式）
		calculateWeekDatesFromNow() {
			const now = new Date()
			const currentDay = now.getDay() || 7
			const mondayOffset = currentDay - 1

			const monday = new Date(now)
			monday.setDate(monday.getDate() - mondayOffset)

			this.weekDates = []
			for (let i = 0; i < 7; i++) {
				const date = new Date(monday)
				date.setDate(monday.getDate() + i)
				this.weekDates.push({
					month: date.getMonth() + 1,
					day: date.getDate()
				})
			}

			this.currentMonth = now.getMonth() + 1
		},

		// 获取指定星期几的日期显示
		getWeekDayDate(weekday) {
			if (!this.weekDates[weekday - 1]) return ''
			const { month, day } = this.weekDates[weekday - 1]
			return day
		},

		goBack() {
			uni.navigateBack()
		},
		showWeekSelector() {
			const weeks = Array.from({ length: 20 }, (_, i) => i + 1)
			uni.showActionSheet({
				itemList: weeks.map(w => `第${w}周`),
				success: (res) => {
					const selectedWeek = res.tapIndex + 1
					this.loadWeekSchedule(selectedWeek)
				}
			})
		},
		loadWeekSchedule(week) {
			this.currentWeek = week
			this.courses = this.transformScheduleData(this.courses)
			this.calculateWeekDates()
		},
		getSlotKey(course) {
			return [course.weekday, course.start, course.end].join('-')
		},
		getSlotCourses(course) {
			const key = this.getSlotKey(course)
			return this.courses.filter((item) => this.getSlotKey(item) === key)
		},
		getSlotCourseCount(course) {
			return course.slotCount || this.getSlotCourses(course).length
		},
		shouldShowNonCurrentFlag(course) {
			// 只有当前选中日的课程槽，本周无课但存在跨周课程时，才显示“非本周”
			return course.weekday === this.currentWeekday && !course.isCurrentWeek
		},
		courseStyle(course) {
			const rowHeight = this.periodHeight
			const gap = this.periodGap

			const top = (course.start - 1) * (rowHeight + gap)

			const numRows = course.end - course.start + 1
			// 完全铺满，不留间隙
			const height = numRows * rowHeight + (numRows - 1) * gap

			// 使用 calc 计算精确宽度：100% / 7 列，不留边距
			const columnIndex = course.weekday - 1
			const left = `calc(${columnIndex} * (100% / 7))`
			const width = `calc(100% / 7)`

			return {
				top: `${top}rpx`,
				left: left,
				height: `${height}rpx`,
				width: width
			}
		},
		async goToDetail(course) {
			this.showCoursePopup = true
			this.courseDetailLoading = true
			const slotCourses = this.getSlotCourses(course)
			this.selectedCourse = { ...course }
			this.popupCourses = slotCourses.map((item) => ({ ...item }))

			try {
				const token = uni.getStorageSync('token') || ''
				const detailRequests = slotCourses.map((item) => new Promise((resolve) => {
					uni.request({
						url: `http://localhost:8080/api/browser/jwx/schedule/${item.id}`,
						method: 'GET',
						header: {
							'Authorization': 'Bearer ' + token
						},
						success: (res) => {
							if (res.statusCode === 200 && res.data.code === 200 && res.data.data?.course) {
								resolve({
									...item,
									...res.data.data.course
								})
								return
							}
							resolve(item)
						},
						fail: () => resolve(item)
					})
				}))

				const detailCourses = await Promise.all(detailRequests)
				this.popupCourses = detailCourses
				this.selectedCourse = detailCourses[0] || { ...course }
			} finally {
				this.courseDetailLoading = false
			}
		},
		closeCoursePopup() {
			this.showCoursePopup = false
			this.popupCourses = []
		},
		switchDay(day) {
			this.currentWeekday = day
		},
		getWeekdayLabel(day) {
			const names = ['日', '一', '二', '三', '四', '五', '六']
			return `周${names[day] || ''}`
		},
		formatPopupSchedule(course) {
			if (!course) return '-'
			const weekText = this.getWeekdayLabel(course.weekday || 1)
			const sessionText = course.classSessions || `${course.start || '-'}-${course.end || '-'}节`
			const timeText = this.getTimeRangeBySessions(course.classSessions, course.start, course.end)
			return `${weekText} | ${sessionText}${timeText ? ` | ${timeText}` : ''}`
		},
		getTimeRangeBySessions(classSessions, startSection, endSection) {
			let start = startSection
			let end = endSection
			if (classSessions) {
				const match = classSessions.match(/(\d+)-(\d+)\s*节/)
				if (match) {
					start = Number(match[1])
					end = Number(match[2])
				} else {
					const singleMatch = classSessions.match(/(\d+)\s*节/)
					if (singleMatch) {
						start = Number(singleMatch[1])
						end = Number(singleMatch[1])
					}
				}
			}
			const startPeriod = this.periods.find((item) => item.index === Number(start))
			const endPeriod = this.periods.find((item) => item.index === Number(end))
			if (!startPeriod || !endPeriod) return ''
			return `${startPeriod.start}-${endPeriod.end}`
		},
		// 导入课表
		importSchedule() {
			const token = uni.getStorageSync('token') || ''
			uni.showLoading({
				title: '正在导入课表...'
			})

			uni.request({
				url: 'http://localhost:8080/api/browser/jwx/schedule/auto',
				method: 'POST',
				header: {
					'Authorization': 'Bearer ' + token
				},
				success: (res) => {
					uni.hideLoading()
					if (res.statusCode === 200 && res.data.code === 200) {
						const count = res.data.data.count || 0
						this.loadSchedule()
						uni.showToast({
							title: `成功导入 ${count} 门课程`,
							icon: 'success'
						})
					} else {
						uni.showToast({
							title: res.data.message || '导入失败',
							icon: 'none'
						})
					}
				},
				fail: (err) => {
					uni.hideLoading()
					console.error('导入课表失败:', err)
					uni.showToast({
						title: '网络错误',
						icon: 'none'
					})
				}
			})
		},
		// 打开分享码导入弹窗
		openImportCodePopup() {
			this.showImportPopup = false
			this.showImportCodePopup = true
			this.shareCodeInput = ''
		},
		// 确认导入分享码
		confirmImportShareCode() {
			if (!this.shareCodeInput || !this.shareCodeInput.trim()) {
				uni.showToast({ title: '请输入分享码', icon: 'none' })
				return
			}

			const token = uni.getStorageSync('token') || ''
			uni.showLoading({ title: '正在导入课表...' })

			uni.request({
				url: 'http://localhost:8080/api/schedule/copy',
				method: 'POST',
				header: {
					'Authorization': 'Bearer ' + token,
					'Content-Type': 'application/json'
				},
				data: {
					shareCode: this.shareCodeInput.trim()
				},
				success: (res) => {
					uni.hideLoading()
					if (res.statusCode === 200 && res.data.code === 200) {
						this.showImportCodePopup = false
						this.shareCodeInput = ''
						this.loadSchedule()
						uni.showToast({ title: '课表导入成功', icon: 'success' })
					} else {
						uni.showToast({ title: res.data.message || '导入失败', icon: 'none' })
					}
				},
				fail: (err) => {
					uni.hideLoading()
					console.error('导入课表失败:', err)
					uni.showToast({ title: '网络错误', icon: 'none' })
				}
			})
		},
		// 分享课程表
		shareSchedule() {
			// 如果已经有分享码，直接复制
			if (this.myShareCode) {
				uni.setClipboardData({
					data: this.myShareCode,
					success: () => {
						uni.showModal({
							title: '复制成功',
							content: '课表分享码已复制到剪贴板，好友可以使用该分享码导入你的课表',
							showCancel: false,
							confirmText: '我知道了'
						})
					}
				})
				return
			}

			// 否则先获取用户信息
			const token = uni.getStorageSync('token') || ''
			uni.showLoading({ title: '获取中...' })

			uni.request({
				url: 'http://localhost:8080/api/auth/current-user',
				method: 'GET',
				header: {
					'Authorization': 'Bearer ' + token
				},
				success: (res) => {
					uni.hideLoading()
					if (res.statusCode === 200 && res.data.code === 200 && res.data.data) {
						const userInfo = res.data.data
						if (userInfo.shareCode) {
							this.myShareCode = userInfo.shareCode
							uni.setClipboardData({
								data: this.myShareCode,
								success: () => {
									uni.showModal({
										title: '复制成功',
										content: `你的课表分享码是：${this.myShareCode}\n\n已复制到剪贴板，好友可以使用该分享码导入你的课表`,
										showCancel: false,
										confirmText: '我知道了'
									})
								}
							})
						} else {
							uni.showToast({ title: '未找到分享码', icon: 'none' })
						}
					} else {
						uni.showToast({ title: '获取失败', icon: 'none' })
					}
				},
				fail: (err) => {
					uni.hideLoading()
					console.error('获取用户信息失败:', err)
					uni.showToast({ title: '网络错误', icon: 'none' })
				}
			})
		},
		// 显示导入菜单
		showImportMenu() {
			this.showImportPopup = true
		},
		// 从教务系统导入
		importFromJwx() {
			this.showImportPopup = false
			const token = uni.getStorageSync('token') || ''
			uni.request({
				url: 'http://localhost:8080/api/browser/jwx/user/check-jwx-bind',
				method: 'GET',
				header: {
					'Authorization': 'Bearer ' + token
				},
				success: (res) => {
					if (res.statusCode === 200 && res.data.code === 200 && res.data.data.binded) {
						this.importSchedule()
					} else {
						uni.showModal({
							title: '提示',
							content: '您还未绑定教务系统账号，请先绑定后再导入课表',
							confirmText: '去绑定',
							success: (modalRes) => {
								if (modalRes.confirm) {
									uni.navigateTo({
										url: '/pages/jwxBind/jwxBind'
									})
								}
							}
						})
					}
				},
				fail: () => {
					uni.showToast({ title: '网络错误', icon: 'none' })
				}
			})
		},
		// 通过分享导入
		importFromShare() {
			uni.showToast({ title: '功能开发中', icon: 'none' })
		},
		// 触摸滑动相关方法
		handleTouchStart(e) {
			this.touchStartX = e.touches[0].clientX
			this.touchStartY = e.touches[0].clientY
			this.touchMoveX = 0
			this.isSwiping = false
		},
		handleTouchMove(e) {
			const diffX = e.touches[0].clientX - this.touchStartX
			const diffY = e.touches[0].clientY - this.touchStartY

			// 只有水平滑动距离大于垂直距离时才认为是滑动
			if (Math.abs(diffX) > Math.abs(diffY) && Math.abs(diffX) > 10) {
				this.isSwiping = true
				this.touchMoveX = diffX
			}
		},
		handleTouchEnd(e) {
			if (!this.isSwiping) return

			const threshold = 50 // 滑动距离阈值
			if (this.touchMoveX > threshold) {
				// 向右滑动 - 上一周
				if (this.currentWeek > 1) {
					this.loadWeekSchedule(this.currentWeek - 1)
				} else {
					uni.showToast({ title: '已经是第一周', icon: 'none' })
				}
			} else if (this.touchMoveX < -threshold) {
				// 向左滑动 - 下一周
				if (this.currentWeek < 20) {
					this.loadWeekSchedule(this.currentWeek + 1)
				} else {
					uni.showToast({ title: '已经是最后一周', icon: 'none' })
				}
			}
		},
	}
}
</script>

<style lang="scss">
.schedule-page {
	position: relative;
	min-height: 100vh;
	background: #ffffff;
}

.schedule-shell {
	position: relative;
	z-index: 2;
	padding: 0 6rpx 20rpx;
}

.header-bar {
	position: relative;
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 14rpx 20rpx 14rpx;
}

.header-left {
	display: flex;
	align-items: center;
	gap: 14rpx;
}

.week-info-wrapper {
	display: flex;
	flex-direction: column;
	align-items: flex-start;
	gap: 4rpx;
}

.week-info {
	display: flex;
	align-items: center;
	gap: 8rpx;
	cursor: pointer;
}

.week-text {
	font-size: 21px;
	font-weight: 800;
	color: #1D1D1F;
	line-height: 1;
}

.week-caret {
	font-size: 22rpx;
	color: #333;
	margin-top: 4rpx;
}

.semester-info {
	display: flex;
	align-items: center;
}

.semester-text {
	font-size: 20rpx;
	color: #b0b4bb;
}

.header-actions {
	display: flex;
	align-items: center;
	gap: 18rpx;
}

.menu-btn {
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	width: 44rpx;
	height: 44rpx;
	gap: 6rpx;
}

.menu-line {
	width: 26rpx;
	height: 3rpx;
	border-radius: 999rpx;
	background: #1d1d1f;
}

.utility-btn {
	display: flex;
	align-items: center;
	justify-content: center;
	width: 48rpx;
	height: 48rpx;
}

.utility-copy {
	position: relative;
	width: 32rpx;
	height: 32rpx;
}

.copy-back,
.copy-front {
	position: absolute;
	border: 3rpx solid #1d1d1f;
	border-radius: 8rpx;
	background: transparent;
}

.copy-back {
	width: 22rpx;
	height: 22rpx;
	left: 10rpx;
	top: 0;
	opacity: 0.7;
}

.copy-front {
	width: 22rpx;
	height: 22rpx;
	left: 2rpx;
	top: 8rpx;
}

.utility-expand {
	width: 34rpx;
	height: 34rpx;
	border: 3rpx solid #1d1d1f;
	border-radius: 10rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	box-sizing: border-box;
}

.expand-arrow {
	font-size: 24rpx;
	font-weight: 700;
	color: #1d1d1f;
	line-height: 1;
}

.import-btn {
	display: flex;
	align-items: center;
	justify-content: center;
	width: 52rpx;
	height: 52rpx;
	background: #1D1D1F;
	border-radius: 12rpx;
}

.import-plus {
	color: #fff;
	font-size: 40rpx;
	font-weight: 500;
	line-height: 1;
	margin-top: -4rpx;
}

// 导入弹窗
.import-popup {
	position: fixed;
	top: 0;
	left: 0;
	right: 0;
	bottom: 0;
	background: rgba(0, 0, 0, 0.5);
	z-index: 1000;
	display: flex;
	justify-content: flex-end;
	align-items: flex-start;
	padding-top: 140rpx;
	padding-right: 30rpx;
}

.popup-content {
	background: #fff;
	border-radius: 16rpx;
	width: 360rpx;
	padding: 16rpx 0;
	box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.15);
	height: auto;
}

.popup-item {
	padding: 16rpx 28rpx;
	border-bottom: 1rpx solid #f0f0f0;
	&:last-child {
		border-bottom: none;
	}
}

.popup-title {
	display: block;
	font-size: 30rpx;
	font-weight: 600;
	color: #333;
	margin-bottom: 4rpx;
}

.popup-desc {
	display: block;
	font-size: 22rpx;
	color: #999;
}

// 分享码导入弹窗
.import-code-popup {
	position: fixed;
	top: 0;
	left: 0;
	right: 0;
	bottom: 0;
	background: rgba(0, 0, 0, 0.5);
	z-index: 1000;
	display: flex;
	justify-content: center;
	align-items: center;
}

.import-code-content {
	background: #fff;
	border-radius: 24rpx;
	width: 560rpx;
	padding: 0;
	box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.15);
	overflow: hidden;
}

.popup-header {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 24rpx 32rpx;
	border-bottom: 1rpx solid #f0f0f0;
}

.popup-title-text {
	font-size: 32rpx;
	font-weight: 600;
	color: #333;
}

.popup-close {
	font-size: 48rpx;
	color: #999;
	line-height: 1;
}

.popup-body {
	padding: 32rpx;
	box-sizing: border-box;
}

.input-label {
	display: block;
	font-size: 28rpx;
	color: #666;
	margin-bottom: 16rpx;
}

.share-code-input {
	width: 100%;
	height: 80rpx;
	background: #f5f5f5;
	border-radius: 12rpx;
	padding: 0 20rpx;
	font-size: 28rpx;
	color: #333;
	box-sizing: border-box;
}

.popup-footer {
	display: flex;
	padding: 0 32rpx 32rpx;
	gap: 20rpx;
	box-sizing: border-box;
}

.cancel-btn,
.confirm-btn {
	flex: 1;
	height: 76rpx;
	line-height: 76rpx;
	text-align: center;
	border-radius: 12rpx;
	font-size: 28rpx;
	border: none;
}

.cancel-btn {
	background: #f5f5f5;
	color: #666;
}

.confirm-btn {
	background: #1D1D1F;
	color: #fff;
}

.weekday-bar {
	display: grid;
	grid-template-columns: 52rpx repeat(7, 1fr);
	align-items: center;
	background: #ffffff;
	padding: 0 0 6rpx;
	border-bottom: 1rpx solid #f3f4f7;
}

.month-label {
	font-size: 20rpx;
	color: #9ca2aa;
	text-align: center;
	font-weight: 600;
	line-height: 1.15;
}

.weekday-item {
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	padding: 4rpx 0;
	gap: 4rpx;
}

.weekday-name {
	font-size: 20rpx;
	font-weight: 600;
	color: #747981;
}

.weekday-date {
	width: 42rpx;
	height: 42rpx;
	border-radius: 10rpx;
	background: transparent;
	font-size: 20rpx;
	color: #a8adb5;
	display: flex;
	align-items: center;
	justify-content: center;
	font-weight: 600;
}

.weekday-item.active .weekday-name {
	color: #1d1d1f;
}

.weekday-item.active .weekday-date {
	color: #ffffff;
	background: #111111;
}

.board-card {
	display: flex;
	align-items: flex-start;
	background: #fff;
	padding: 2rpx 0 10rpx;
	min-height: 1280rpx;
}

.period-column {
	width: 52rpx;
	padding-top: 0;
}

.period-item {
	height: 148rpx;
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: flex-start;
	gap: 4rpx;
	padding-top: 8rpx;
	box-sizing: border-box;
	border-bottom: 1rpx solid #f0f0f0;
}

.period-text {
	font-size: 24rpx;
	color: #6b7280;
	font-weight: 700;
	line-height: 1;
}

.period-time {
	font-size: 16rpx;
	color: #9ca3af;
	line-height: 1.3;
}

.course-board {
	position: relative;
	flex: 1;
	min-height: 1280rpx;
	margin-left: 2rpx;
	background:
		linear-gradient(to right, #f4f5f8 1rpx, transparent 1rpx) 0 0 / calc(100% / 7) 100%,
		transparent;
}

/* 横向分割线 */
.h-divider {
	position: absolute;
	left: 0;
	right: 0;
	height: 1rpx;
	background: #f5f6f9;
	z-index: 1;
}

/* 纵向分割线 */
.v-divider {
	position: absolute;
	top: 0;
	bottom: 0;
	width: 1rpx;
	background: #f4f5f8;
	z-index: 1;
}

.course-block {
	position: absolute;
	border-radius: 16rpx;
	padding: 14rpx 4rpx 12rpx;
	overflow: hidden;
	display: flex;
	flex-direction: column;
	justify-content: flex-start;
	align-items: center;
	z-index: 2;
	box-sizing: border-box;
	box-shadow: inset 0 0 0 2rpx rgba(255, 255, 255, 0.95);
	background-clip: padding-box;
}

.course-block--with-banner {
	padding-top: 32rpx;
}

.course-content {
	width: 100%;
	display: flex;
	align-items: center;
	justify-content: center;
	padding-top: 0;
	box-sizing: border-box;
	flex: 1;
}

.course-accent {
	position: absolute;
	top: 0;
	left: 0;
	right: 0;
	height: 20rpx;
	border-radius: 16rpx 16rpx 0 0;
	opacity: 1;
}

.course-accent--hidden {
	display: none;
}

.course-badge {
	position: absolute;
	top: -2rpx;
	right: 0rpx;
	min-width: 20rpx;
	height: 22rpx;
	padding: 0 2rpx;
	border-radius: 999rpx;
	background: #8f7cf7;
	color: #ffffff;
	font-size: 13rpx;
	font-weight: 700;
	display: flex;
	align-items: center;
	justify-content: center;
	border: 3rpx solid #ffffff;
	box-shadow: 0 2rpx 6rpx rgba(143, 124, 247, 0.12);
	z-index: 4;
	line-height: 1;
}

.course-week-banner {
	position: absolute;
	top: 0;
	left: 0;
	right: 0;
	height: 34rpx;
	background: #aeb4bc;
	color: #ffffff;
	font-size: 16rpx;
	font-weight: 600;
	display: flex;
	align-items: center;
	justify-content: center;
	border-radius: 16rpx 16rpx 0 0;
	letter-spacing: 0;
	z-index: 2;
}

/* 课程颜色主题 - 贴近参考图 */
.course-block--green {
	background: #e8f5f0;
	color: #5cb8a3;
}
.course-block--green .course-accent {
	background: #7dd3c0;
}
.course-block--red {
	background: #f5f5f5;
	color: #9ca3af;
}
.course-block--red .course-accent {
	background: #d1d5db;
}
.course-block--orange {
	background: #fff8e1;
	color: #f59e0b;
}
.course-block--orange .course-accent {
	background: #fbbf24;
}
.course-block--yellow {
	background: #fefce8;
	color: #eab308;
}
.course-block--yellow .course-accent {
	background: #facc15;
}
.course-block--blue {
	background: #eff6ff;
	color: #3b82f6;
}
.course-block--blue .course-accent {
	background: #60a5fa;
}
.course-block--purple {
	background: #faf5ff;
	color: #a855f7;
}
.course-block--purple .course-accent {
	background: #c084fc;
}

.course-block--inactive {
	background: #f3f4f6 !important;
	color: #9ca3af !important;
}

.course-block--inactive .course-accent {
	background: #d1d5db !important;
}

.course-title {
	display: block;
	line-height: 1.3;
	word-break: break-all;
	text-align: center;
	max-width: 100%;
	writing-mode: horizontal-tb;
	text-orientation: mixed;
	letter-spacing: 0;
	white-space: normal;
}

.course-title {
	font-size: 22rpx;
	font-weight: 700;
	margin-top: 0;
	line-height: 1.22;
}

.course-popup-mask {
	position: fixed;
	inset: 0;
	background: rgba(255, 255, 255, 0.62);
	backdrop-filter: blur(6px);
	-webkit-backdrop-filter: blur(6px);
	z-index: 1200;
	display: flex;
	align-items: center;
	justify-content: center;
	padding: 24rpx;
	box-sizing: border-box;
}

.course-popup-stack {
	width: 100%;
	max-width: 620rpx;
	display: flex;
	flex-direction: column;
	gap: 22rpx;
}

.course-popup {
	width: 100%;
	background: rgba(240, 243, 247, 0.96);
	border-radius: 28rpx;
	padding: 28rpx 30rpx;
	box-sizing: border-box;
	box-shadow: 0 18rpx 40rpx rgba(31, 35, 41, 0.12);
}

.course-popup--loading {
	min-height: 180rpx;
	display: flex;
	align-items: center;
	justify-content: center;
}

.course-popup-header {
	display: flex;
	align-items: flex-start;
	justify-content: space-between;
	gap: 16rpx;
	margin-bottom: 18rpx;
}

.course-popup-title-wrap {
	flex: 1;
}

.course-popup-title {
	display: block;
	font-size: 38rpx;
	font-weight: 700;
	color: #1f2329;
	line-height: 1.3;
}

.course-popup-edit {
	flex-shrink: 0;
	min-width: 90rpx;
	height: 54rpx;
	border-radius: 14rpx;
	background: #ffffff;
	color: #8a8f98;
	font-size: 24rpx;
	display: flex;
	align-items: center;
	justify-content: center;
}

.course-popup-body {
	display: flex;
	flex-direction: column;
	gap: 16rpx;
}

.course-popup-weeks {
	font-size: 30rpx;
	color: #50555d;
	font-weight: 500;
}

.course-popup-line {
	font-size: 29rpx;
	color: #737983;
	line-height: 1.55;
}

.course-popup-loading {
	font-size: 28rpx;
	color: #737983;
	padding: 20rpx 0;
}
</style>
