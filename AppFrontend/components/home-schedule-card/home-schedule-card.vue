<template>
	<view class="home-schedule-card">
		<view class="home-schedule-top">
			<view class="home-day-spacer"></view>
			<view
				v-for="day in weekdays"
				:key="day.value"
				class="home-day-item"
				:class="{ active: currentDay === day.value }"
				@click="currentDay = day.value"
			>
				<text class="home-day-text">{{ day.label }}</text>
			</view>
		</view>

		<view class="home-schedule-board">
			<view class="home-period-col">
				<view v-for="period in periods" :key="period" class="home-period-item">
					<text class="home-period-text">{{ period }}</text>
				</view>
			</view>
			<view class="home-course-col">
				<view
					v-for="course in visibleCourses"
					:key="course.id"
					class="home-course-block"
					:class="`home-course-block--${course.theme}`"
					:style="courseStyle(course)"
					@click="goToSchedule"
				>
					<view class="home-course-accent"></view>
					<view class="home-course-content">
						<text class="home-course-title">{{ course.name }}@{{ course.location }}</text>
					</view>
				</view>
			</view>
		</view>

		<view class="home-schedule-footer" @click="goToSchedule">
			<text class="home-schedule-week">课表 {{ currentDayLabel }}. 第5周</text>
			<text class="home-schedule-desc">今天有{{ visibleCourses.length }}门课程</text>
		</view>
	</view>
</template>

<script>
export default {
	name: 'HomeScheduleCard',
	data() {
		return {
			currentDay: 4,
			weekdays: [
				{ value: 1, label: '一' },
				{ value: 2, label: '二' },
				{ value: 3, label: '三' },
				{ value: 4, label: '四' },
				{ value: 5, label: '五' },
				{ value: 6, label: '六' },
				{ value: 7, label: '日' }
			],
			periods: [1, 2, 3, 4, 5, 6],
			courses: [
				{ id: 1, day: 4, name: '深度学习', location: '明德楼110', start: 1, end: 2, theme: 'green' },
				{ id: 2, day: 4, name: '网络编程', location: '明德楼505', start: 1, end: 2, theme: 'red' },
				{ id: 3, day: 4, name: 'Python', location: '明德楼110', start: 1, end: 2, theme: 'orange' },
				{ id: 4, day: 4, name: '深度学习', location: '图书馆机房', start: 3, end: 4, theme: 'green' },
				{ id: 5, day: 4, name: '软件工程', location: 'A414', start: 3, end: 4, theme: 'red' },
				{ id: 6, day: 4, name: 'Linux', location: '明德楼403', start: 5, end: 6, theme: 'yellow' },
				{ id: 7, day: 4, name: 'Linux', location: '明德楼403', start: 5, end: 6, theme: 'yellow', hidden: true }
			]
		}
	},
	computed: {
		visibleCourses() {
			return this.courses.filter((course) => course.day === this.currentDay && !course.hidden)
		},
		currentDayLabel() {
			const map = { 1: 'Mon', 2: 'Tue', 3: 'Wed', 4: 'Thu', 5: 'Fri', 6: 'Sat', 7: 'Sun' }
			return map[this.currentDay] || 'Thu'
		}
	},
	methods: {
		courseStyle(course) {
			const rowHeight = 112
			const gap = 6
			const top = (course.start - 1) * rowHeight + (course.start - 1) * gap
			const sameStartCourses = this.visibleCourses.filter((item) => item.start === course.start && item.end === course.end)
			const order = sameStartCourses.findIndex((item) => item.id === course.id)
			const columnWidth = 100 / 7
			const width = columnWidth * 0.74
			const startColumn = Math.floor((7 - sameStartCourses.length) / 2)
			const left = (startColumn + order) * columnWidth
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
}

.home-period-col {
	width: 54rpx;
	padding-left: 8rpx;
	box-sizing: border-box;
}

.home-period-item {
	height: 118rpx;
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
	background:
		linear-gradient(to right, #f4f5f8 1rpx, transparent 1rpx) 0 0 / calc(100% / 7) 100%,
		transparent;
}

.home-course-block {
	position: absolute;
	padding: 16rpx 8rpx 8rpx;
	border-radius: 26rpx;
	box-shadow: inset 0 0 0 2rpx rgba(255, 255, 255, 0.95);
	overflow: hidden;
	display: flex;
	flex-direction: column;
	justify-content: flex-start;
	align-items: center;
}

.home-course-accent {
	position: absolute;
	top: 0;
	left: 0;
	right: 0;
	height: 18rpx;
	border-radius: 26rpx 26rpx 0 0;
}

.home-course-content {
	flex: 1;
	width: 100%;
	display: flex;
	align-items: center;
	justify-content: center;
}

.home-course-block--green {
	background: #e8f5f0;
	color: #5cb8a3;
}
.home-course-block--green .home-course-accent { background: #7dd3c0; }

.home-course-block--red {
	background: #f5f5f5;
	color: #9ca3af;
}
.home-course-block--red .home-course-accent { background: #d1d5db; }

.home-course-block--orange {
	background: #fff8e1;
	color: #f59e0b;
}
.home-course-block--orange .home-course-accent { background: #fbbf24; }

.home-course-block--yellow {
	background: #fefce8;
	color: #eab308;
}
.home-course-block--yellow .home-course-accent { background: #facc15; }

.home-course-title {
	display: block;
	max-width: 100%;
	text-align: center;
	word-break: break-all;
	line-height: 1.18;
	font-size: 22rpx;
	font-weight: 700;
}

.home-schedule-footer {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-top: 18rpx;
	padding: 0 10rpx 2rpx;
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
</style>
