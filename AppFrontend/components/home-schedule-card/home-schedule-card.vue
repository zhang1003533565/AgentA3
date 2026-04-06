<template>
	<view class="home-schedule-card">
		<view class="home-schedule-top">
			<view class="home-day-spacer"></view>
			<view
				v-for="day in weekdays"
				:key="day.value"
				class="home-day-item"
				:class="{ active: currentDay === day.value }"
			>
				<text class="home-day-text">{{ day.label }}</text>
			</view>
		</view>

		<view class="home-schedule-board" :style="{ minHeight: boardHeight + 'rpx' }">
			<view class="home-period-col">
				<view v-for="period in visiblePeriods" :key="period" class="home-period-item">
					<text class="home-period-text">{{ period }}</text>
				</view>
			</view>
			<view class="home-course-col" :style="{ minHeight: boardHeight + 'rpx' }">
				<view
					v-for="(period, index) in visiblePeriods"
					:key="'h-' + period"
					class="home-h-divider"
					:style="{ top: index * periodStep + 'rpx' }"
				></view>
				<view class="home-h-divider" :style="{ top: visiblePeriods.length * periodStep + 'rpx' }"></view>
				<view
					v-for="i in 6"
					:key="'v-' + i"
					class="home-v-divider"
					:style="{ left: i * (100 / 7) + '%' }"
				></view>
				<view
					v-for="course in visibleCourses"
					:key="course.id"
					class="home-course-block"
					:class="`home-course-block--${course.theme}`"
					:style="courseStyle(course)"
					@click="goToSchedule"
				>
					<view class="home-course-content">
						<text class="home-course-title">{{ course.name }}@{{ course.location }}</text>
					</view>
				</view>
			</view>
		</view>

		<view class="home-schedule-footer" @click="goToSchedule">
			<view class="home-schedule-footer-text">
				<text class="home-schedule-week">课表 {{ currentDayLabel }}. 第{{ currentWeek }}周</text>
				<text class="home-schedule-desc">{{ currentDateText }}</text>
			</view>
			<view class="home-schedule-switch" @click.stop>
				<view class="home-schedule-switch-btn" :class="{ disabled: currentPeriodPage <= 0 }" @click.stop="switchPeriodPage(-1)">
					<text class="home-schedule-switch-icon">⌃</text>
				</view>
				<view class="home-schedule-switch-divider"></view>
				<view class="home-schedule-switch-btn" :class="{ disabled: currentPeriodPage >= periodWindows.length - 1 }" @click.stop="switchPeriodPage(1)">
					<text class="home-schedule-switch-icon home-schedule-switch-icon--down">⌃</text>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
import { getCurrentSchedule } from '@/api/schedule.js'

const HOME_PERIOD_HEIGHT = 144
const HOME_PERIOD_GAP = 4
const HOME_PERIOD_STEP = HOME_PERIOD_HEIGHT + HOME_PERIOD_GAP
const HOME_COURSE_THEMES = ['green', 'red', 'orange', 'yellow', 'green', 'red']

export default {
	name: 'HomeScheduleCard',
	data() {
		const today = new Date()
		return {
			currentDay: today.getDay() || 7,
			currentPeriodPage: 0,
			currentWeek: 1,
			weekdays: [
				{ value: 1, label: '一' },
				{ value: 2, label: '二' },
				{ value: 3, label: '三' },
				{ value: 4, label: '四' },
				{ value: 5, label: '五' },
				{ value: 6, label: '六' },
				{ value: 7, label: '日' }
			],
			periodWindows: [
				[1, 6],
				[7, 10]
			],
			courses: []
		}
	},
	mounted() {
		this.loadCurrentWeek()
	},
	computed: {
		currentPeriodRange() {
			return this.periodWindows[this.currentPeriodPage] || this.periodWindows[0]
		},
		visiblePeriods() {
			const [start, end] = this.currentPeriodRange
			return Array.from({ length: end - start + 1 }, (_, index) => start + index)
		},
		visibleCourses() {
			const [start, end] = this.currentPeriodRange
			return this.courses.filter((course) => course.start >= start && course.end <= end)
		},
		boardHeight() {
			return this.visiblePeriods.length * HOME_PERIOD_STEP
		},
		periodStep() {
			return HOME_PERIOD_STEP
		},
		currentDayLabel() {
			const map = { 1: 'Mon', 2: 'Tue', 3: 'Wed', 4: 'Thu', 5: 'Fri', 6: 'Sat', 7: 'Sun' }
			return map[this.currentDay] || 'Thu'
		},
		currentDateText() {
			const now = new Date()
			const year = now.getFullYear()
			const month = now.getMonth() + 1
			const day = now.getDate()
			return `${year}年${month}月${day}日`
		}
	},
	methods: {
		async loadCurrentWeek() {
			try {
				const res = await getCurrentSchedule()
				const payload = res?.data || {}
				const week = payload.currentWeek
				if (week) {
					this.currentWeek = week
				}
				this.courses = this.transformSchedule(payload.schedule || [])
				const hasCourseInCurrentPage = this.visibleCourses.length > 0
				const hasLateCourse = this.courses.some((course) => course.start >= 7)
				if (!hasCourseInCurrentPage && hasLateCourse) {
					this.currentPeriodPage = 1
				}
			} catch (error) {}
		},
		transformSchedule(scheduleList) {
			return scheduleList.flatMap((item, index) => {
				const sessions = this.parseClassSessions(item.classSessions)
				if (!sessions) return []
				const courseName = item.courseName || item.name || ''
				const location = item.location || ''
				const courseChunks = []

				for (let chunkStart = sessions.start; chunkStart <= sessions.end; chunkStart += 2) {
					const chunkEnd = Math.min(chunkStart + 1, sessions.end)
					courseChunks.push({
						id: `${item.id || index}-${chunkStart}-${chunkEnd}`,
						day: item.weekday || 1,
						name: courseName,
						location,
						start: chunkStart,
						end: chunkEnd,
						theme: HOME_COURSE_THEMES[index % HOME_COURSE_THEMES.length]
					})
				}

				return courseChunks
			})
		},
		parseClassSessions(classSessions) {
			if (!classSessions) return null
			const rangeMatch = classSessions.match(/(\d+)-(\d+)\s*节/)
			if (rangeMatch) {
				return {
					start: Number(rangeMatch[1]),
					end: Number(rangeMatch[2])
				}
			}
			const singleMatch = classSessions.match(/(\d+)\s*节/)
			if (singleMatch) {
				const section = Number(singleMatch[1])
				return {
					start: section,
					end: section
				}
			}
			return null
		},
		switchPeriodPage(step) {
			const nextPage = this.currentPeriodPage + step
			if (nextPage < 0 || nextPage >= this.periodWindows.length) return
			this.currentPeriodPage = nextPage
		},
		courseStyle(course) {
			const rowHeight = HOME_PERIOD_HEIGHT
			const gap = HOME_PERIOD_GAP
			const [visibleStart] = this.currentPeriodRange
			const top = (course.start - visibleStart) * rowHeight + (course.start - visibleStart) * gap
			const columnWidth = 100 / 7
			const width = columnWidth
			const left = (course.day - 1) * columnWidth
			const height = (course.end - course.start + 1) * rowHeight + (course.end - course.start) * gap
			return {
				top: `${top}rpx`,
				left: `${left}%`,
				width: `${width}%`,
				height: `${height}rpx`
			}
		},
		goToSchedule() {
			uni.navigateTo({
				url: '/subpackage_schedule/schedule/schedule'
			})
		}
	}
}
</script>

<style lang="scss" scoped>
.home-schedule-card {
	margin-top: 22rpx;
	padding: 24rpx 24rpx 24rpx;
	border-radius: 30rpx;
	background: #ffffff;
	box-shadow: 0 12rpx 28rpx rgba(188, 194, 203, 0.12);
}

.home-schedule-top {
	display: grid;
	grid-template-columns: 68rpx repeat(7, 1fr);
	align-items: center;
	margin-bottom: 18rpx;
}

.home-day-spacer {
	width: 68rpx;
}

.home-day-item {
	height: 56rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	position: relative;
}

.home-day-item.active::after {
	content: '';
	position: absolute;
	left: 16rpx;
	right: 16rpx;
	bottom: 2rpx;
	height: 5rpx;
	border-radius: 999rpx;
	background: #5f7dff;
}

.home-day-text {
	font-size: 24rpx;
	font-weight: 600;
	color: #6f7b88;
}

.home-day-item.active .home-day-text {
	color: #5f7dff;
}

.home-schedule-board {
	display: flex;
	align-items: flex-start;
	min-height: 690rpx;
	background: #ffffff;
	border-radius: 22rpx;
	padding: 6rpx 0 6rpx 0;
	overflow: hidden;
	box-sizing: border-box;
}

.home-period-col {
	width: 54rpx;
	padding-left: 8rpx;
	box-sizing: border-box;
}

.home-period-item {
	height: 148rpx;
	display: flex;
	align-items: center;
	justify-content: flex-end;
	padding-right: 8rpx;
	box-sizing: border-box;
}

.home-period-text {
	font-size: 28rpx;
	font-weight: 600;
	color: #8a94a3;
}

.home-course-col {
	position: relative;
	flex: 1;
	min-height: 690rpx;
	margin-left: 14rpx;
	background: transparent;
	overflow: hidden;
	box-sizing: border-box;
}

.home-h-divider {
	position: absolute;
	left: 0;
	right: 0;
	height: 1rpx;
	background: #f2f4f8;
	z-index: 1;
}

.home-v-divider {
	position: absolute;
	top: 0;
	bottom: 0;
	width: 1rpx;
	background: #f2f4f8;
	z-index: 1;
}

.home-course-block {
	position: absolute;
	padding: 0 10rpx;
	border-radius: 16rpx;
	box-shadow: inset 0 0 0 2rpx rgba(255, 255, 255, 0.95);
	overflow: hidden;
	display: flex;
	flex-direction: column;
	justify-content: center;
	align-items: center;
	z-index: 2;
	box-sizing: border-box;
	max-width: 100%;
}

.home-course-content {
	flex: 1;
	width: 100%;
	max-width: 100%;
	display: flex;
	align-items: center;
	justify-content: center;
	overflow: hidden;
	box-sizing: border-box;
}

.home-course-block--green {
	background: #edfdf7;
	color: #2aa98b;
}

.home-course-block--red {
	background: #fff1f3;
	color: #ef5a78;
}

.home-course-block--orange {
	background: #fff8e1;
	color: #f59e0b;
}

.home-course-block--yellow {
	background: #fefce8;
	color: #eab308;
}

.home-course-title {
	display: block;
	width: 100%;
	max-width: 100%;
	text-align: center;
	word-break: break-all;
	overflow-wrap: anywhere;
	line-height: 1.18;
	font-size: 22rpx;
	font-weight: 700;
	box-sizing: border-box;
	overflow: hidden;
}

.home-schedule-footer {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-top: 18rpx;
	padding: 0 10rpx 2rpx;
}

.home-schedule-footer-text {
	display: flex;
	flex-direction: column;
	align-items: flex-start;
	gap: 6rpx;
}

.home-schedule-week {
	font-size: 36rpx;
	font-weight: 700;
	color: #344255;
}

.home-schedule-desc {
	font-size: 28rpx;
	color: #95a1af;
}

.home-schedule-switch {
	width: 132rpx;
	height: 64rpx;
	border-radius: 18rpx;
	background: #ffffff;
	box-shadow: 0 10rpx 24rpx rgba(188, 194, 203, 0.18);
	border: 2rpx solid rgba(216, 221, 229, 0.9);
	display: flex;
	align-items: stretch;
	overflow: hidden;
	flex-shrink: 0;
}

.home-schedule-switch-btn {
	flex: 1;
	display: flex;
	align-items: center;
	justify-content: center;
}

.home-schedule-switch-btn.disabled {
	opacity: 0.35;
}

.home-schedule-switch-divider {
	width: 2rpx;
	background: rgba(216, 221, 229, 0.9);
}

.home-schedule-switch-icon {
	font-size: 36rpx;
	font-weight: 700;
	color: #6f7b88;
	line-height: 1;
}

.home-schedule-switch-icon--down {
	transform: rotate(180deg);
}
</style>
