<template>
	<view class="schedule-page"
		@touchstart="handleTouchStart"
		@touchmove="handleTouchMove"
		@touchend="handleTouchEnd">
		<!-- Header Bar（独立页面时显示） -->
		<view v-if="showHeader" class="header-shell">
			<nav-bar title="我的课表" :showBack="true" fixed placeholder />
			<view class="header-module">
				<view class="header-main">
					<view class="header-left">
						<view class="week-info-wrapper">
							<view class="week-info" :class="{ 'week-info--current': isCurrentRealWeek }" @click="showWeekSelector">
								<text class="week-text">第{{ currentWeek }}周</text>
								<text class="week-caret">▼</text>
							</view>
							<view class="semester-info" @click="showSemesterSelector">
								<text class="semester-text">{{ semester }}</text>
								<text class="semester-caret">切换</text>
							</view>
						</view>
					</view>
					<view class="header-actions">
						<view class="manage-btn" @click="openScheduleSettings">
							<text class="manage-text">学期</text>
						</view>
						<view class="manage-btn" @click="openScheduleGeneralSettings">
							<text class="manage-text">设置</text>
						</view>
						<view class="share-btn" @click="shareSchedule">
							<view class="utility-copy">
								<view class="copy-back"></view>
								<view class="copy-front"></view>
							</view>
							<text class="share-text">分享</text>
						</view>
						<view class="import-btn" @click="showImportMenu">
							<text class="import-plus">+</text>
						</view>
					</view>
				</view>
			</view>
		</view>

		<!-- 导入菜单弹窗 -->
		<view v-if="showImportPopup" class="import-popup" @click="showImportPopup = false">
			<view class="popup-content" @click.stop>
				<view class="popup-item" @click="importCurrentSemester">
					<text class="popup-title">导入本学期</text>
					<text class="popup-desc">{{ academicYear }} 第{{ semesterTerm }}学期</text>
				</view>
				<view class="popup-item" @click="importOtherSemester">
					<text class="popup-title">导入其他学期</text>
					<text class="popup-desc">前往学期管理选择导入</text>
				</view>
				<view class="popup-item" @click="openImportCodePopup">
					<text class="popup-title">分享码导入</text>
					<text class="popup-desc">粘贴好友的本学期分享码</text>
				</view>
			</view>
		</view>

		<!-- 选择导入学期弹窗 -->
		<view v-if="showImportSemesterPopup" class="import-semester-popup" @click="showImportSemesterPopup = false">
			<view class="import-semester-content" @click.stop>
				<view class="popup-header">
					<text class="popup-title-text">选择导入学期</text>
					<text class="popup-close" @click="showImportSemesterPopup = false">×</text>
				</view>
				<view class="import-semester-list">
					<view
						v-for="item in importSemesterOptions"
						:key="semesterOptionKey(item)"
						class="import-semester-item"
						:class="{ 'import-semester-item--current': item.selected }"
						@click="selectImportSemester(item)"
					>
						<view class="import-semester-main">
							<text class="import-semester-title">{{ formatSemesterOption(item) }}</text>
							<text class="import-semester-desc">开学日期 {{ item.semesterStart || '未设置' }}</text>
						</view>
						<text v-if="item.selected" class="import-semester-badge">当前</text>
					</view>
				</view>
				<view class="import-semester-footer">
					<button class="manage-semester-btn" @click="openScheduleSettings">学期管理</button>
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
					<text class="input-label">请输入好友的本学期分享码</text>
					<text class="input-tip">带学期信息的分享码会导入对应学期</text>
					<input
						class="share-code-input"
						type="text"
						v-model="shareCodeInput"
						placeholder="例如：SCH260405A1B2#2025-2026#2"
						maxlength="50"
					/>
				</view>
				<view class="popup-footer">
					<button class="cancel-btn" @click="showImportCodePopup = false">取消</button>
					<button class="confirm-btn" @click="confirmImportShareCode">导入课表</button>
				</view>
			</view>
		</view>

		<import-progress
			:visible="showImportProgress"
			:title="importProgressTitle"
			:message="importProgressMessage"
			:status="importProgressStatus"
			:steps="importProgressSteps"
		/>

		<view class="schedule-shell">
			<!-- 星期栏 -->
			<view class="weekday-bar">
				<view class="month-label">{{ currentMonth }}月</view>
				<view class="weekday-item" :class="{ active: shouldHighlightWeekday(1), today: isTodayWeekday(1) }" @click="switchDay(1)">
					<text class="weekday-name">一</text>
					<text class="weekday-date">{{ getWeekDayDate(1) }}</text>
				</view>
				<view class="weekday-item" :class="{ active: shouldHighlightWeekday(2), today: isTodayWeekday(2) }" @click="switchDay(2)">
					<text class="weekday-name">二</text>
					<text class="weekday-date">{{ getWeekDayDate(2) }}</text>
				</view>
				<view class="weekday-item" :class="{ active: shouldHighlightWeekday(3), today: isTodayWeekday(3) }" @click="switchDay(3)">
					<text class="weekday-name">三</text>
					<text class="weekday-date">{{ getWeekDayDate(3) }}</text>
				</view>
				<view class="weekday-item" :class="{ active: shouldHighlightWeekday(4), today: isTodayWeekday(4) }" @click="switchDay(4)">
					<text class="weekday-name">四</text>
					<text class="weekday-date">{{ getWeekDayDate(4) }}</text>
				</view>
				<view class="weekday-item" :class="{ active: shouldHighlightWeekday(5), today: isTodayWeekday(5) }" @click="switchDay(5)">
					<text class="weekday-name">五</text>
					<text class="weekday-date">{{ getWeekDayDate(5) }}</text>
				</view>
				<view class="weekday-item" :class="{ active: shouldHighlightWeekday(6), today: isTodayWeekday(6) }" @click="switchDay(6)">
					<text class="weekday-name">六</text>
					<text class="weekday-date">{{ getWeekDayDate(6) }}</text>
				</view>
				<view class="weekday-item" :class="{ active: shouldHighlightWeekday(7), today: isTodayWeekday(7) }" @click="switchDay(7)">
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
import NavBar from '@/components/nav-bar/nav-bar.vue'
import ImportProgress from '@/components/import-progress/import-progress.vue'
import { getScheduleImportProgress, getSchedulePeriods } from '@/api/schedule.js'
import { BASE_URL } from '@/utils/config.js'

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

const normalizePeriods = (periods) => {
	const source = Array.isArray(periods) && periods.length ? periods : PERIODS
	return source
		.map((item, index) => ({
			index: Number(item.index || item.periodIndex || index + 1),
			start: item.start || item.startTime || '',
			end: item.end || item.endTime || ''
		}))
		.filter((item) => item.index && item.start && item.end)
		.sort((a, b) => a.index - b.index)
}

const semesterCodeForTerm = (term) => (Number(term) === 2 ? '12' : '3')

const normalizeAcademicYear = (value) => {
	const text = String(value || '').trim()
	if (/^\d{4}-\d{4}$/.test(text)) return text
	if (/^\d{4}$/.test(text)) return `${text}-${Number(text) + 1}`
	const now = new Date()
	const year = now.getFullYear()
	const startYear = now.getMonth() + 1 >= 8 ? year : year - 1
	return `${startYear}-${startYear + 1}`
}

const defaultSemesterStarts = (academicYear) => {
	const year = Number(normalizeAcademicYear(academicYear).slice(0, 4))
	return {
		1: `${year}-09-01`,
		2: `${year + 1}-03-01`
	}
}

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
	components: { NavBar, ImportProgress },
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
			actualCurrentWeek: 1,
			currentWeekday: new Date().getDay() || 7,
			periods: normalizePeriods(PERIODS),
			periodHeight: PERIOD_HEIGHT,
			periodGap: PERIOD_GAP,
			rawSchedules: [],
			courses: [],
			loading: false,
			currentMonth: 3,
			weekDates: [], // 存储本周每天的日期
			semester: '2025-2026 第 1 学期',
			academicYear: normalizeAcademicYear(),
			semesterTerm: 1,
			semesterCode: '3',
			availableSemesters: [],
			semesterStarts: defaultSemesterStarts(),
			showImportPopup: false,
			showImportSemesterPopup: false,
			importSemesterOptions: [],
			showImportCodePopup: false,
			showImportProgress: false,
			importProgressTitle: '正在导入课表',
			importProgressMessage: '正在准备导入',
			importProgressStatus: 'running',
			importProgressSteps: [],
			importProgressPollTimer: null,
			importProgressStartedAt: 0,
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
			isSwiping: false,
			hasMounted: false
		}
	},
	mounted() {
		this.hasMounted = true
		this.loadPeriodSettings()
		this.loadSchedule()
		this.calculateWeekDates()
	},
	onShow() {
		if (this.hasMounted) {
			this.loadPeriodSettings()
			this.loadSchedule()
		}
	},
	onUnload() {
		this.stopImportProgressPolling()
	},
	beforeDestroy() {
		this.stopImportProgressPolling()
	},
	computed: {
		isCurrentRealWeek() {
			return this.currentWeek === this.actualCurrentWeek
		},
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
				return {
					...(currentCourse || slotCourses[0]),
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
					url: `${BASE_URL}/api/browser/jwx/schedule/current`,
					method: 'GET',
					header: {
						'Authorization': 'Bearer ' + token
					},
					success: (res) => {
						if (res.statusCode === 200 && res.data.code === 200) {
							const scheduleData = res.data.data
							this.currentWeek = scheduleData.currentWeek || 1
							this.actualCurrentWeek = scheduleData.currentWeek || 1
							this.applyScheduleMeta(scheduleData)
							this.loadScheduleSettings(token)
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
		async loadPeriodSettings() {
			try {
				const res = await getSchedulePeriods()
				this.periods = normalizePeriods(res.data)
			} catch (error) {
				if (!this.periods.length) {
					this.periods = normalizePeriods(PERIODS)
				}
			}
		},
		applyScheduleMeta(scheduleData = {}) {
			this.academicYear = normalizeAcademicYear(scheduleData.academicYear || this.academicYear)
			this.semesterTerm = Number(scheduleData.semesterTerm || this.semesterTerm || 1)
			this.semesterCode = scheduleData.semesterCode || semesterCodeForTerm(this.semesterTerm)
			this.semester = scheduleData.semester || `${this.academicYear} 第 ${this.semesterTerm} 学期`
			if (scheduleData.semesterStart) {
				this.semesterStart = scheduleData.semesterStart
				this.semesterStarts = {
					...this.semesterStarts,
					[this.semesterTerm]: scheduleData.semesterStart
				}
			}
		},
		buildSemesterQuery() {
			const params = []
			if (this.academicYear) {
				params.push(`academicYear=${encodeURIComponent(this.academicYear)}`)
			}
			if (this.semesterTerm) {
				params.push(`semesterTerm=${encodeURIComponent(this.semesterTerm)}`)
			}
			return params.length ? `?${params.join('&')}` : ''
		},
		loadAllSchedules(token) {
			uni.request({
				url: `${BASE_URL}/api/schedule${this.buildSemesterQuery()}`,
				method: 'GET',
				header: {
					'Authorization': 'Bearer ' + token
				},
				success: (res) => {
					this.loading = false
					if (res.statusCode === 200 && res.data.code === 200) {
						this.rawSchedules = res.data.data || []
						this.courses = this.transformScheduleData(this.rawSchedules)
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
		loadScheduleSettings(token = uni.getStorageSync('token') || '') {
			return new Promise((resolve) => {
				uni.request({
					url: `${BASE_URL}/api/schedule/settings`,
					method: 'GET',
					header: {
						'Authorization': 'Bearer ' + token
					},
					success: (res) => {
						if (res.statusCode === 200 && res.data.code === 200) {
							const data = res.data.data || {}
							this.availableSemesters = this.normalizeSemesterOptions(data)
							this.semesterStarts = this.getSemesterStartsForYear(this.academicYear)
						}
						resolve()
					},
					fail: () => resolve()
				})
			})
		},
		normalizeSemesterOptions(data = {}) {
			const list = Array.isArray(data.semesters) ? data.semesters : []
			const selectedYear = normalizeAcademicYear(data.academicYear || this.academicYear)
			const byKey = new Map()

			list.forEach((item) => {
				if (!item) return
				const academicYear = normalizeAcademicYear(item.academicYear || selectedYear)
				const semesterTerm = Number(item.semesterTerm || 1)
				if (semesterTerm !== 1 && semesterTerm !== 2) return
				byKey.set(`${academicYear}-${semesterTerm}`, {
					academicYear,
					semesterTerm,
					semesterCode: item.semesterCode || semesterCodeForTerm(semesterTerm),
					semesterStart: item.semesterStart || defaultSemesterStarts(academicYear)[semesterTerm],
					courseCount: Number(item.courseCount || 0),
					currentWeek: Number(item.currentWeek || 1),
					selected: Boolean(item.selected)
				})
			})

			;[1, 2].forEach((term) => {
				const key = `${selectedYear}-${term}`
				if (!byKey.has(key)) {
					byKey.set(key, {
						academicYear: selectedYear,
						semesterTerm: term,
						semesterCode: semesterCodeForTerm(term),
						semesterStart: defaultSemesterStarts(selectedYear)[term],
						courseCount: 0,
						currentWeek: 1,
						selected: selectedYear === this.academicYear && term === this.semesterTerm
					})
				}
			})

			return Array.from(byKey.values()).sort((a, b) => {
				if (a.academicYear !== b.academicYear) return b.academicYear.localeCompare(a.academicYear)
				return Number(b.semesterTerm) - Number(a.semesterTerm)
			})
		},
		getSemesterStartsForYear(academicYear) {
			const year = normalizeAcademicYear(academicYear)
			const starts = defaultSemesterStarts(year)
			this.availableSemesters
				.filter((item) => item.academicYear === year)
				.forEach((item) => {
					starts[item.semesterTerm] = item.semesterStart || starts[item.semesterTerm]
				})
			return starts
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
			const startWeekday = startDate.getDay() || 7
			const firstWeekMonday = new Date(startDate)
			firstWeekMonday.setDate(startDate.getDate() - (startWeekday - 1))

			// 计算当前周的周一，而不是直接把学期开始日当成周一
			const weekStart = new Date(firstWeekMonday)
			weekStart.setDate(firstWeekMonday.getDate() + (this.currentWeek - 1) * 7)

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
		isTodayWeekday(weekday) {
			const targetDate = this.weekDates[weekday - 1]
			if (!targetDate) return false
			const now = new Date()
			return targetDate.month === now.getMonth() + 1 && targetDate.day === now.getDate()
		},
		shouldHighlightWeekday(weekday) {
			return this.isCurrentRealWeek && this.currentWeekday === weekday
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
		async showSemesterSelector() {
			await this.loadScheduleSettings()
			const options = this.availableSemesters.length
				? this.availableSemesters
				: [1, 2].map((term) => ({
					academicYear: this.academicYear,
					semesterTerm: term,
					semesterCode: semesterCodeForTerm(term),
					semesterStart: defaultSemesterStarts(this.academicYear)[term],
					courseCount: 0
				}))
			uni.showActionSheet({
				itemList: options.map((item) => this.formatSemesterOption(item)),
				success: (res) => {
					const selected = options[res.tapIndex]
					if (selected) {
						this.switchSemester(selected)
					}
				}
			})
		},
		formatSemesterOption(item) {
			const countText = Number(item.courseCount || 0) > 0 ? ` · ${item.courseCount}门课` : ''
			return `${item.academicYear} 第${item.semesterTerm}学期${countText}`
		},
		semesterOptionKey(item) {
			return `${normalizeAcademicYear(item?.academicYear || this.academicYear)}-${Number(item?.semesterTerm || 1)}`
		},
		getImportSemesterOptions() {
			const options = this.availableSemesters.length
				? this.availableSemesters
				: [1, 2].map((term) => ({
					academicYear: this.academicYear,
					semesterTerm: term,
					semesterCode: semesterCodeForTerm(term),
					semesterStart: defaultSemesterStarts(this.academicYear)[term],
					courseCount: 0,
					selected: term === this.semesterTerm
				}))
			const byKey = new Map()
			options.forEach((item) => {
				const academicYear = normalizeAcademicYear(item.academicYear || this.academicYear)
				const semesterTerm = Number(item.semesterTerm || 1)
				if (semesterTerm !== 1 && semesterTerm !== 2) return
				const key = `${academicYear}-${semesterTerm}`
				byKey.set(key, {
					academicYear,
					semesterTerm,
					semesterCode: item.semesterCode || semesterCodeForTerm(semesterTerm),
					semesterStart: item.semesterStart || defaultSemesterStarts(academicYear)[semesterTerm],
					courseCount: Number(item.courseCount || 0),
					currentWeek: Number(item.currentWeek || 1),
					selected: Boolean(item.selected) || (academicYear === this.academicYear && semesterTerm === this.semesterTerm)
				})
			})
			return Array.from(byKey.values()).sort((a, b) => {
				if (a.academicYear !== b.academicYear) return b.academicYear.localeCompare(a.academicYear)
				return Number(b.semesterTerm) - Number(a.semesterTerm)
			})
		},
		switchSemester(semester) {
			const academicYear = normalizeAcademicYear(semester.academicYear || this.academicYear)
			const semesterTerm = Number(semester.semesterTerm || 1)
			const starts = this.getSemesterStartsForYear(academicYear)
			const semesterStart = semester.semesterStart || starts[semesterTerm] || defaultSemesterStarts(academicYear)[semesterTerm]
			const token = uni.getStorageSync('token') || ''

			uni.showLoading({ title: '切换中...' })
			uni.request({
				url: `${BASE_URL}/api/schedule/settings`,
				method: 'PUT',
				header: {
					'Authorization': 'Bearer ' + token,
					'Content-Type': 'application/json'
				},
				data: {
					academicYear,
					semesterTerm,
					semesterStart,
					selected: true,
					semesters: [1, 2].map((term) => ({
						academicYear,
						semesterTerm: term,
						semesterCode: semesterCodeForTerm(term),
						semesterStart: starts[term] || defaultSemesterStarts(academicYear)[term]
					}))
				},
				success: (res) => {
					uni.hideLoading()
					if (res.statusCode === 200 && res.data.code === 200) {
						this.academicYear = academicYear
						this.semesterTerm = semesterTerm
						this.semesterCode = semesterCodeForTerm(semesterTerm)
						this.semesterStart = semesterStart
						this.semesterStarts = {
							...starts,
							[semesterTerm]: semesterStart
						}
						this.semester = `${academicYear} 第 ${semesterTerm} 学期`
						this.loadSchedule()
						return
					}
					uni.showToast({ title: res.data.message || '切换失败', icon: 'none' })
				},
				fail: () => {
					uni.hideLoading()
					uni.showToast({ title: '网络错误', icon: 'none' })
				}
			})
		},
		loadWeekSchedule(week) {
			this.currentWeek = week
			this.courses = this.transformScheduleData(this.rawSchedules)
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
			return !course.isCurrentWeek
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
						url: `${BASE_URL}/api/browser/jwx/schedule/${item.id}`,
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
		createImportProgressSteps(semesterTerm) {
			return [
				{ key: 'connect', title: '连接教务系统', desc: '正在打开教务系统登录页', status: 'active' },
				{ key: 'login', title: '登录教务账号', desc: '等待账号验证完成', status: 'waiting' },
				{ key: 'query', title: '进入课表查询', desc: '打开个人课表查询页面', status: 'waiting' },
				{ key: 'read', title: `读取第 ${semesterTerm} 学期`, desc: '获取课程、时间和教室信息', status: 'waiting' },
				{ key: 'save', title: '保存课程数据', desc: '同步到我的课表', status: 'waiting' }
			]
		},
		startImportProgress(semesterTerm) {
			this.stopImportProgressPolling()
			this.importProgressTitle = `导入第 ${semesterTerm} 学期课表`
			this.importProgressMessage = '正在准备导入'
			this.importProgressStatus = 'running'
			this.importProgressSteps = this.createImportProgressSteps(semesterTerm)
			this.importProgressStartedAt = Date.now()
			this.showImportProgress = true
			this.importProgressPollTimer = setInterval(() => {
				this.pollImportProgress()
			}, 900)
			this.pollImportProgress()
		},
		stopImportProgressPolling() {
			if (this.importProgressPollTimer) {
				clearInterval(this.importProgressPollTimer)
				this.importProgressPollTimer = null
			}
		},
		async pollImportProgress() {
			try {
				const res = await getScheduleImportProgress()
				this.applyImportProgress(res.data || {})
			} catch (error) {
				// 轮询失败不打断导入请求，保留当前展示。
			}
		},
		applyImportProgress(progress) {
			const stepOrder = ['connect', 'login', 'query', 'read', 'save']
			const step = progress.step || 'connect'
			const status = progress.status || 'running'
			if (progress.updatedAt && progress.updatedAt < this.importProgressStartedAt - 1000) {
				return
			}
			const activeIndex = stepOrder.includes(step) ? stepOrder.indexOf(step) : 0
			this.importProgressMessage = progress.message || this.importProgressMessage
			this.importProgressStatus = status === 'failed' ? 'failed' : (status === 'done' ? 'done' : 'running')
			this.importProgressSteps = this.importProgressSteps.map((item, index) => {
				if (status === 'done') {
					return { ...item, status: 'done' }
				}
				if (status === 'failed') {
					return {
						...item,
						status: index < activeIndex ? 'done' : (index === activeIndex ? 'failed' : 'waiting')
					}
				}
				return {
					...item,
					status: index < activeIndex ? 'done' : (index === activeIndex ? 'active' : 'waiting'),
					desc: item.key === step && progress.message ? progress.message : item.desc
				}
			})
		},
		finishImportProgress(message) {
			this.stopImportProgressPolling()
			this.importProgressStatus = 'done'
			this.importProgressMessage = message
			this.importProgressSteps = this.importProgressSteps.map((item) => ({ ...item, status: 'done' }))
			setTimeout(() => {
				this.showImportProgress = false
			}, 900)
		},
		failImportProgress(message) {
			this.stopImportProgressPolling()
			this.importProgressStatus = 'failed'
			this.importProgressMessage = message || '导入失败，请稍后重试'
			if (!this.importProgressSteps.some((item) => item.status === 'failed')) {
				this.importProgressSteps = this.importProgressSteps.map((item, index) => ({
					...item,
					status: index === 0 ? 'failed' : 'waiting'
				}))
			}
			setTimeout(() => {
				this.showImportProgress = false
			}, 1800)
		},
		// 导入课表
		importSchedule(semester = null) {
			const target = semester || {
				academicYear: this.academicYear,
				semesterTerm: this.semesterTerm,
				semesterStart: this.semesterStarts[this.semesterTerm] || this.semesterStart
			}
			const academicYear = normalizeAcademicYear(target.academicYear || this.academicYear)
			const semesterTerm = Number(target.semesterTerm || this.semesterTerm || 1)
			const defaultStarts = defaultSemesterStarts(academicYear)
			const semesterStart = target.semesterStart || this.semesterStarts[semesterTerm] || defaultStarts[semesterTerm]
			const token = uni.getStorageSync('token') || ''
			this.startImportProgress(semesterTerm)

			uni.request({
				url: `${BASE_URL}/api/browser/jwx/schedule/auto`,
				method: 'POST',
				header: {
					'Authorization': 'Bearer ' + token,
					'Content-Type': 'application/json'
				},
				data: {
					academicYear,
					selectedSemesterTerm: semesterTerm,
					importBothTerms: false,
					semesterStarts: {
						[String(semesterTerm)]: semesterStart
					}
				},
				success: (res) => {
					if (res.statusCode === 200 && res.data.code === 200) {
						const count = res.data.data.count || 0
						this.loadSchedule()
						this.finishImportProgress(`导入完成，共 ${count} 门课程`)
						uni.showToast({
							title: `成功导入 ${count} 门课程`,
							icon: 'success'
						})
					} else {
						this.failImportProgress(res.data.message || '导入失败')
						uni.showToast({
							title: res.data.message || '导入失败',
							icon: 'none'
						})
					}
				},
				fail: (err) => {
					console.error('导入课表失败:', err)
					this.failImportProgress('网络错误，导入失败')
					uni.showToast({
						title: '网络错误',
						icon: 'none'
					})
				}
			})
		},
		selectImportSemester(item) {
			this.showImportSemesterPopup = false
			this.importSchedule(item)
		},
		// 打开分享码导入弹窗
		openImportCodePopup() {
			this.showImportPopup = false
			this.showImportCodePopup = true
			this.shareCodeInput = ''
		},
		buildSemesterShareCode() {
			return `${this.myShareCode}#${this.academicYear}#${this.semesterTerm}`
		},
		parseSemesterShareCode(value) {
			const text = String(value || '').trim()
			const parts = text.split('#').map((item) => item.trim()).filter(Boolean)
			return {
				shareCode: parts[0] || text,
				academicYear: parts[1] || this.academicYear,
				semesterTerm: Number(parts[2] || this.semesterTerm || 1)
			}
		},
		showShareCopiedModal(shareCode) {
			uni.showModal({
				title: '本学期分享码已复制',
				content: `分享学期：${this.semester}\n分享码：${shareCode}\n\n好友粘贴后只会导入这个学期的课表。`,
				showCancel: false,
				confirmText: '我知道了'
			})
		},
		// 确认导入分享码
		confirmImportShareCode() {
			if (!this.shareCodeInput || !this.shareCodeInput.trim()) {
				uni.showToast({ title: '请输入分享码', icon: 'none' })
				return
			}

			const parsedShare = this.parseSemesterShareCode(this.shareCodeInput)
			const token = uni.getStorageSync('token') || ''
			uni.showLoading({ title: '正在导入课表...' })

			uni.request({
				url: `${BASE_URL}/api/schedule/copy`,
				method: 'POST',
				header: {
					'Authorization': 'Bearer ' + token,
					'Content-Type': 'application/json'
				},
				data: {
					shareCode: parsedShare.shareCode,
					academicYear: parsedShare.academicYear,
					semesterTerm: parsedShare.semesterTerm
				},
				success: (res) => {
					uni.hideLoading()
					if (res.statusCode === 200 && res.data.code === 200) {
						this.showImportCodePopup = false
						this.shareCodeInput = ''
						this.loadSchedule()
						uni.showToast({ title: '本学期课表导入成功', icon: 'success' })
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
				const semesterShareCode = this.buildSemesterShareCode()
				uni.setClipboardData({
					data: semesterShareCode,
					success: () => {
						this.showShareCopiedModal(semesterShareCode)
					}
				})
				return
			}

			// 否则先获取用户信息
			const token = uni.getStorageSync('token') || ''
			uni.showLoading({ title: '获取中...' })

			uni.request({
				url: `${BASE_URL}/api/auth/current-user`,
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
							const semesterShareCode = this.buildSemesterShareCode()
							uni.setClipboardData({
								data: semesterShareCode,
								success: () => {
									this.showShareCopiedModal(semesterShareCode)
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
		checkJwxBindThen(next) {
			const token = uni.getStorageSync('token') || ''
			uni.request({
				url: `${BASE_URL}/api/browser/jwx/user/check-jwx-bind`,
				method: 'GET',
				header: {
					'Authorization': 'Bearer ' + token
				},
				success: (res) => {
					if (res.statusCode === 200 && res.data.code === 200 && res.data.data.binded) {
						next()
						return
					}
					uni.showModal({
						title: '需要先设置账号',
						content: '导入课表前需要先设置教务系统账号。设置完成后再导入本学期。',
						confirmText: '去设置',
						success: (modalRes) => {
							if (modalRes.confirm) {
								uni.navigateTo({
									url: '/subpackage_schedule/scheduleAccountSettings/scheduleAccountSettings'
								})
							}
						}
					})
				},
				fail: () => {
					uni.showToast({ title: '网络错误', icon: 'none' })
				}
			})
		},
		importCurrentSemester() {
			this.showImportPopup = false
			this.checkJwxBindThen(() => {
				this.importSchedule({
					academicYear: this.academicYear,
					semesterTerm: this.semesterTerm,
					semesterStart: this.semesterStarts[this.semesterTerm] || this.semesterStart
				})
			})
		},
		importOtherSemester() {
			this.showImportPopup = false
			uni.navigateTo({
				url: '/subpackage_schedule/scheduleSettings/scheduleSettings?mode=import'
			})
		},
		openScheduleSettings(options = {}) {
			this.showImportPopup = false
			this.showImportSemesterPopup = false
			const query = options.mode ? `?mode=${options.mode}` : ''
			uni.navigateTo({
				url: `/subpackage_schedule/scheduleSettings/scheduleSettings${query}`
			})
		},
		openScheduleGeneralSettings() {
			this.showImportPopup = false
			this.showImportSemesterPopup = false
			uni.navigateTo({
				url: '/subpackage_schedule/scheduleGeneralSettings/scheduleGeneralSettings'
			})
		},
		openScheduleAccountSettings() {
			this.showImportPopup = false
			this.showImportSemesterPopup = false
			uni.navigateTo({
				url: '/subpackage_schedule/scheduleAccountSettings/scheduleAccountSettings'
			})
		},
		// 从教务系统导入
		importFromJwx() {
			this.showImportPopup = false
			this.importOtherSemester()
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

.header-shell {
	position: relative;
	z-index: 3;
}

.header-module {
	position: relative;
	background: linear-gradient(180deg, #dff0ff 0%, #eaf5ff 100%);
	border-bottom: 1rpx solid rgba(177, 208, 235, 0.55);
	padding: 18rpx 28rpx 18rpx;
}

.header-main {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 8rpx 0 6rpx;
}

.header-left {
	display: flex;
	align-items: center;
	gap: 14rpx;
	min-width: 0;
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

.week-info--current .week-text,
.week-info--current .week-caret {
	color: #ef6b5f;
}

.week-text {
	font-size: 42rpx;
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
	gap: 10rpx;
	cursor: pointer;
}

.semester-text {
	font-size: 20rpx;
	color: #8f97a3;
	max-width: 260rpx;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.semester-caret {
	font-size: 20rpx;
	color: #4b80ef;
}

.header-actions {
	display: flex;
	align-items: center;
	gap: 8rpx;
	flex-shrink: 0;
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

.utility-btn,
.share-btn {
	display: flex;
	align-items: center;
	justify-content: center;
	height: 52rpx;
	border-radius: 14rpx;
	background: rgba(255, 255, 255, 0.42);
}

.utility-btn {
	width: 52rpx;
}

.share-btn {
	gap: 7rpx;
	padding: 0 14rpx;
	border: 1rpx solid rgba(29, 29, 31, 0.1);
	box-sizing: border-box;
}

.manage-btn {
	display: flex;
	align-items: center;
	justify-content: center;
	height: 52rpx;
	padding: 0 14rpx;
	border-radius: 16rpx;
	background: rgba(255, 255, 255, 0.7);
	border: 1rpx solid rgba(29, 29, 31, 0.12);
	box-sizing: border-box;
}

.manage-icon {
	font-size: 22rpx;
	line-height: 1;
	color: #1d1d1f;
}

.manage-text {
	font-size: 23rpx;
	font-weight: 700;
	color: #1d1d1f;
	line-height: 1;
}

.utility-copy {
	position: relative;
	width: 28rpx;
	height: 28rpx;
	flex-shrink: 0;
}

.utility-gear {
	width: 34rpx;
	height: 34rpx;
	border: 3rpx solid #1d1d1f;
	border-radius: 10rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	box-sizing: border-box;
}

.gear-icon {
	font-size: 22rpx;
	line-height: 1;
	color: #1d1d1f;
}

.copy-back,
.copy-front {
	position: absolute;
	border: 3rpx solid #1d1d1f;
	border-radius: 8rpx;
	background: transparent;
}

.copy-back {
	width: 20rpx;
	height: 20rpx;
	left: 8rpx;
	top: 0;
	opacity: 0.7;
}

.copy-front {
	width: 20rpx;
	height: 20rpx;
	left: 2rpx;
	top: 7rpx;
}

.share-text {
	font-size: 23rpx;
	font-weight: 700;
	color: #1d1d1f;
	line-height: 1;
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
	width: 56rpx;
	height: 56rpx;
	padding: 0;
	background: #1D1D1F;
	border-radius: 16rpx;
	box-sizing: border-box;
	box-shadow: 0 8rpx 18rpx rgba(29, 29, 31, 0.18);
}

.import-plus {
	color: #fff;
	font-size: 34rpx;
	font-weight: 700;
	line-height: 1;
	margin-top: -2rpx;
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

// 选择导入学期弹窗
.import-semester-popup {
	position: fixed;
	top: 0;
	left: 0;
	right: 0;
	bottom: 0;
	background: rgba(0, 0, 0, 0.5);
	z-index: 1000;
	display: flex;
	justify-content: center;
	align-items: flex-end;
	padding: 28rpx;
	box-sizing: border-box;
}

.import-semester-content {
	width: 100%;
	max-width: 640rpx;
	background: #fff;
	border-radius: 28rpx;
	box-shadow: 0 18rpx 44rpx rgba(0, 0, 0, 0.16);
	overflow: hidden;
}

.import-semester-list {
	max-height: 620rpx;
	padding: 14rpx 26rpx 8rpx;
	box-sizing: border-box;
}

.import-semester-item {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 18rpx;
	padding: 24rpx 0;
	border-bottom: 1rpx solid #f0f2f5;
}

.import-semester-item:last-child {
	border-bottom: none;
}

.import-semester-item--current .import-semester-title {
	color: #3f7df2;
}

.import-semester-main {
	flex: 1;
	min-width: 0;
}

.import-semester-title {
	display: block;
	font-size: 30rpx;
	font-weight: 700;
	color: #222;
	line-height: 1.35;
}

.import-semester-desc {
	display: block;
	margin-top: 8rpx;
	font-size: 23rpx;
	color: #8a96a6;
}

.import-semester-badge {
	flex-shrink: 0;
	padding: 10rpx 18rpx;
	border-radius: 999rpx;
	background: #eaf2ff;
	color: #3f7df2;
	font-size: 22rpx;
	font-weight: 700;
}

.import-semester-footer {
	padding: 18rpx 26rpx 28rpx;
	box-sizing: border-box;
}

.manage-semester-btn {
	width: 100%;
	height: 78rpx;
	border: none;
	border-radius: 18rpx;
	background: #f2f6fb;
	color: #536b87;
	font-size: 27rpx;
	font-weight: 700;
	line-height: 78rpx;
}

.manage-semester-btn::after {
	border: none;
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
	margin-bottom: 8rpx;
}

.input-tip {
	display: block;
	font-size: 22rpx;
	color: #7d8fa5;
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

.weekday-item.today.active .weekday-date {
	background: #ef6b5f;
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
	background: #edfdf7;
	color: #2aa98b;
}
.course-block--green .course-accent {
	background: #43c6a5;
}
.course-block--red {
	background: #fff1f3;
	color: #ef5a78;
}
.course-block--red .course-accent {
	background: #fb7185;
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
	background: #c8ced8 !important;
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
