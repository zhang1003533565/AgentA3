<template>
	<view class="schedule-page">
		<image class="schedule-bg" :src="backgroundImage" mode="aspectFill" />
		<view class="schedule-mask"></view>

		<view class="schedule-shell">
			<view class="weekday-bar">
				<view
					v-for="day in weekdays"
					:key="day.value"
					class="weekday-item"
					:class="{ active: currentWeekday === day.value }"
					@click="currentWeekday = day.value"
				>
					<text class="weekday-text">{{ day.label }}</text>
				</view>
			</view>

			<view class="board-card">
				<view class="period-column">
					<view v-for="period in periods" :key="period" class="period-item">
						<text class="period-text">{{ period }}</text>
					</view>
				</view>

				<view class="course-board">
					<view
						v-for="course in visibleCourses"
						:key="course.id"
						class="course-block"
						:class="`course-block--${course.theme}`"
						:style="courseStyle(course)"
						@click="goToDetail(course)"
					>
						<text class="course-title">{{ course.name }}</text>
						<text class="course-meta">@{{ course.campus }}</text>
						<text class="course-meta">{{ course.location }}</text>
					</view>
				</view>
			</view>

			<view class="footer-card">
				<view class="footer-main">
					<text class="footer-day">{{ currentDayLabel }}.</text>
					<text class="footer-week">第5周</text>
				</view>
				<text class="footer-desc">今天有{{ visibleCourses.length }}门课程</text>
				<view class="footer-actions">
					<view class="footer-btn" @click="switchPrevDay">〈</view>
					<view class="footer-btn" @click="switchNextDay">〉</view>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
export default {
	data() {
		return {
			backgroundImage: 'https://images.unsplash.com/photo-1517048676732-d65bc937f952?auto=format&fit=crop&w=1400&q=80',
			currentWeekday: 4,
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
				{ id: 1, day: 4, name: '深度学习', campus: '朝阳校区', location: '明德楼阶梯110', start: 1, end: 2, theme: 'green' },
				{ id: 2, day: 4, name: '网络编程', campus: '朝阳校区', location: '明德楼505', start: 1, end: 2, theme: 'red' },
				{ id: 3, day: 4, name: 'Python', campus: '朝阳校区', location: '明德楼阶梯110', start: 1, end: 2, theme: 'orange' },
				{ id: 4, day: 4, name: '深度学习', campus: '朝阳校区', location: '图书馆松公共享机房4', start: 3, end: 4, theme: 'green' },
				{ id: 5, day: 4, name: '软件工程', campus: '朝阳校区', location: 'A414', start: 3, end: 4, theme: 'red' },
				{ id: 6, day: 4, name: 'Linux基础', campus: '朝阳校区', location: '明德楼403', start: 5, end: 6, theme: 'yellow' },
				{ id: 7, day: 4, name: 'Linux基础', campus: '朝阳校区', location: '明德楼403', start: 5, end: 6, theme: 'yellow' },
				{ id: 8, day: 3, name: '高等数学', campus: '朝阳校区', location: '教一101', start: 1, end: 2, theme: 'blue' },
				{ id: 9, day: 5, name: '数据库系统', campus: '朝阳校区', location: '实训楼202', start: 3, end: 4, theme: 'purple' }
			]
		}
	},
	computed: {
		visibleCourses() {
			return this.courses.filter((course) => course.day === this.currentWeekday)
		},
		currentDayLabel() {
			const map = { 1: 'Mon', 2: 'Tue', 3: 'Wed', 4: 'Thu', 5: 'Fri', 6: 'Sat', 7: 'Sun' }
			return map[this.currentWeekday] || 'Thu'
		}
	},
	methods: {
		courseStyle(course) {
			const rowHeight = 156
			const gap = 12
			const top = (course.start - 1) * rowHeight + (course.start - 1) * gap
			const height = (course.end - course.start + 1) * rowHeight + (course.end - course.start) * gap
			const sameStartCourses = this.visibleCourses.filter((item) => item.start === course.start && item.end === course.end)
			const order = sameStartCourses.findIndex((item) => item.id === course.id)
			const width = sameStartCourses.length > 1 ? 128 : 138
			const leftBase = 0
			const left = leftBase + order * (width + 14)
			return {
				top: `${top}rpx`,
				left: `${left}rpx`,
				height: `${height}rpx`,
				width: `${width}rpx`
			}
		},
		goToDetail(course) {
			uni.navigateTo({
				url: `/subpackage_schedule/scheduleDetail/scheduleDetail?id=${course.id}`
			})
		},
		switchPrevDay() {
			this.currentWeekday = this.currentWeekday === 1 ? 7 : this.currentWeekday - 1
		},
		switchNextDay() {
			this.currentWeekday = this.currentWeekday === 7 ? 1 : this.currentWeekday + 1
		}
	}
}
</script>

<style lang="scss">
.schedule-page {
	position: relative;
	min-height: 100vh;
	overflow: hidden;
}

.schedule-bg,
.schedule-mask {
	position: absolute;
	inset: 0;
	width: 100%;
	height: 100%;
}

.schedule-bg {
	z-index: 0;
}

.schedule-mask {
	z-index: 1;
	background: linear-gradient(180deg, rgba(222, 236, 255, 0.16) 0%, rgba(227, 237, 255, 0.44) 100%);
	backdrop-filter: blur(4rpx);
}

.schedule-shell {
	position: relative;
	z-index: 2;
	padding: 18rpx 18rpx 36rpx;
}

.weekday-bar {
	display: grid;
	grid-template-columns: repeat(7, 1fr);
	align-items: center;
	background: rgba(255, 255, 255, 0.72);
	border-radius: 22rpx 22rpx 0 0;
	padding: 18rpx 10rpx 0;
}

.weekday-item {
	display: flex;
	align-items: center;
	justify-content: center;
	height: 70rpx;
	position: relative;
}

.weekday-item.active::after {
	content: '';
	position: absolute;
	left: 22rpx;
	right: 22rpx;
	bottom: 6rpx;
	height: 6rpx;
	border-radius: 999rpx;
	background: #5b8ef4;
}

.weekday-text {
	font-size: 26rpx;
	font-weight: 600;
	color: rgba(65, 73, 86, 0.76);
}

.weekday-item.active .weekday-text {
	color: #4b7cf3;
}

.board-card {
	display: flex;
	align-items: flex-start;
	background: rgba(255, 255, 255, 0.58);
	padding: 18rpx 14rpx 18rpx 10rpx;
	border-radius: 0 0 24rpx 24rpx;
	min-height: 1010rpx;
}

.period-column {
	width: 36rpx;
	padding-top: 8rpx;
}

.period-item {
	height: 168rpx;
	display: flex;
	align-items: center;
	justify-content: center;
}

.period-text {
	font-size: 30rpx;
	color: rgba(92, 102, 118, 0.72);
	font-weight: 600;
}

.course-board {
	position: relative;
	flex: 1;
	min-height: 1010rpx;
	margin-left: 12rpx;
}

.course-block {
	position: absolute;
	border-radius: 24rpx;
	padding: 18rpx 14rpx;
	box-shadow: 0 12rpx 20rpx rgba(111, 124, 144, 0.1);
	overflow: hidden;
}

.course-block--green { background: linear-gradient(180deg, #a5ef55, #8ce53c); }
.course-block--red { background: linear-gradient(180deg, #f59aa0, #f07c82); }
.course-block--orange { background: linear-gradient(180deg, #f8ae7a, #f5965f); }
.course-block--yellow { background: linear-gradient(180deg, #f8e06f, #f2d94d); }
.course-block--blue { background: linear-gradient(180deg, #8bc6ff, #65aef0); }
.course-block--purple { background: linear-gradient(180deg, #b9a6ff, #9b86ef); }

.course-title,
.course-meta {
	display: block;
	color: rgba(255, 255, 255, 0.96);
	line-height: 1.22;
	word-break: break-all;
}

.course-title {
	font-size: 22rpx;
	font-weight: 700;
}

.course-meta {
	margin-top: 6rpx;
	font-size: 20rpx;
	font-weight: 600;
}

.footer-card {
	margin-top: 16rpx;
	background: rgba(255, 255, 255, 0.7);
	border-radius: 26rpx;
	padding: 18rpx 22rpx;
	display: flex;
	align-items: center;
}

.footer-main {
	display: flex;
	align-items: baseline;
}

.footer-day {
	font-size: 54rpx;
	font-weight: 800;
	color: #1f2834;
}

.footer-week {
	margin-left: 6rpx;
	font-size: 36rpx;
	font-weight: 600;
	color: #4e5969;
}

.footer-desc {
	margin-left: 16rpx;
	font-size: 26rpx;
	color: #748192;
	flex: 1;
}

.footer-actions {
	display: flex;
	align-items: center;
	gap: 14rpx;
}

.footer-btn {
	width: 62rpx;
	height: 62rpx;
	border-radius: 14rpx;
	background: rgba(255, 255, 255, 0.86);
	border: 2rpx solid rgba(111, 124, 144, 0.18);
	display: flex;
	align-items: center;
	justify-content: center;
	font-size: 34rpx;
	color: #4d596a;
}
</style>
