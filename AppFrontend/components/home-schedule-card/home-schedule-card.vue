<template>
	<view class="home-schedule-card">
		<view class="home-schedule-top">
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
					@click="goToDetail(course)"
				>
					<text class="home-course-title">{{ course.name }}</text>
					<text class="home-course-meta">{{ course.location }}</text>
				</view>
			</view>
		</view>

		<view class="home-schedule-footer">
			<text class="home-schedule-week">{{ currentDayLabel }}. 第5周</text>
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
				{ id: 7, day: 4, name: 'Linux', location: '明德楼403', start: 5, end: 6, theme: 'yellow' }
			]
		}
	},
	computed: {
		visibleCourses() {
			return this.courses.filter((course) => course.day === this.currentDay)
		},
		currentDayLabel() {
			const map = { 1: 'Mon', 2: 'Tue', 3: 'Wed', 4: 'Thu', 5: 'Fri', 6: 'Sat', 7: 'Sun' }
			return map[this.currentDay] || 'Thu'
		}
	},
	methods: {
		courseStyle(course) {
			const rowHeight = 104
			const gap = 10
			const top = (course.start - 1) * rowHeight + (course.start - 1) * gap
			const sameStartCourses = this.visibleCourses.filter((item) => item.start === course.start && item.end === course.end)
			const order = sameStartCourses.findIndex((item) => item.id === course.id)
			const width = sameStartCourses.length > 1 ? 118 : 132
			const left = order * (width + 12)
			const height = (course.end - course.start + 1) * rowHeight + (course.end - course.start) * gap
			return {
				top: `${top}rpx`,
				left: `${left}rpx`,
				width: `${width}rpx`,
				height: `${height}rpx`
			}
		},
		goToDetail(course) {
			uni.navigateTo({
				url: `/subpackage_schedule/scheduleDetail/scheduleDetail?id=${course.id}`
			})
		}
	}
}
</script>

<style lang="scss" scoped>
.home-schedule-card {
	margin-top: 22rpx;
	padding: 20rpx 18rpx 22rpx;
	border-radius: 28rpx;
	background: rgba(255, 255, 255, 0.74);
	box-shadow: 0 16rpx 30rpx rgba(193, 198, 182, 0.14);
}

.home-schedule-top {
	display: grid;
	grid-template-columns: repeat(7, 1fr);
	align-items: center;
	margin-bottom: 14rpx;
}

.home-day-item {
	height: 58rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	position: relative;
}

.home-day-item.active::after {
	content: '';
	position: absolute;
	left: 18rpx;
	right: 18rpx;
	bottom: 4rpx;
	height: 5rpx;
	border-radius: 999rpx;
	background: #5b8ef4;
}

.home-day-text {
	font-size: 24rpx;
	font-weight: 600;
	color: rgba(74, 88, 103, 0.8);
}

.home-day-item.active .home-day-text {
	color: #4b7cf3;
}

.home-schedule-board {
	display: flex;
	align-items: flex-start;
	min-height: 690rpx;
	background: rgba(255, 255, 255, 0.28);
	border-radius: 20rpx;
	padding: 10rpx 10rpx 12rpx 4rpx;
}

.home-period-col {
	width: 34rpx;
}

.home-period-item {
	height: 114rpx;
	display: flex;
	align-items: center;
	justify-content: center;
}

.home-period-text {
	font-size: 28rpx;
	font-weight: 600;
	color: rgba(90, 100, 114, 0.72);
}

.home-course-col {
	position: relative;
	flex: 1;
	min-height: 650rpx;
	margin-left: 10rpx;
}

.home-course-block {
	position: absolute;
	padding: 14rpx 12rpx;
	border-radius: 22rpx;
	box-shadow: 0 10rpx 18rpx rgba(111, 124, 144, 0.08);
}

.home-course-block--green { background: linear-gradient(180deg, #a5ef55, #8ce53c); }
.home-course-block--red { background: linear-gradient(180deg, #f59aa0, #f07c82); }
.home-course-block--orange { background: linear-gradient(180deg, #f8ae7a, #f5965f); }
.home-course-block--yellow { background: linear-gradient(180deg, #f8e06f, #f2d94d); }

.home-course-title,
.home-course-meta {
	display: block;
	color: rgba(255, 255, 255, 0.96);
	line-height: 1.24;
	word-break: break-all;
}

.home-course-title {
	font-size: 21rpx;
	font-weight: 700;
}

.home-course-meta {
	margin-top: 6rpx;
	font-size: 18rpx;
	font-weight: 600;
}

.home-schedule-footer {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-top: 16rpx;
	padding: 0 8rpx;
}

.home-schedule-week {
	font-size: 30rpx;
	font-weight: 700;
	color: #314051;
}

.home-schedule-desc {
	font-size: 24rpx;
	color: #80909a;
}
</style>
