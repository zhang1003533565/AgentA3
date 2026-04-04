<template>
	<view class="schedule-page">
		<view class="schedule-shell">
			<view class="header-bar">
				<view class="back-btn" @click="goBack">
					<text class="back-icon">‹</text>
					<text class="back-text">返回</text>
				</view>
				<text class="page-title">我的课表</text>
				<view class="import-btn" @click="importSchedule">
					<text class="import-icon">+</text>
				</view>
			</view>

			<!-- 星期栏 - 显示完整的星期，错位显示给节次列留空间 -->
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
					<view v-for="period in periods" :key="period" class="period-item">
						<text class="period-text">{{ period }}</text>
					</view>
				</view>

				<view class="course-board">
					<!-- 横向分割线 -->
					<view class="h-divider" v-for="i in periods.length" :key="'h'+i" :style="{ top: (i - 1) * 260 + 'rpx' }"></view>
					<!-- 底部边界线 -->
					<view class="h-divider" :style="{ top: periods.length * 260 + 'rpx' }"></view>

					<!-- 纵向分割线 -->
					<view class="v-divider" v-for="i in 6" :key="'v'+i" :style="{ left: (i * (100 / 7)) + '%' }"></view>

					<!-- 课程卡片 -->
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
		</view>
	</view>
</template>

<script>
export default {
	data() {
		return {
			currentWeek: 1,
			currentWeekday: 1,
			periods: [1, 2, 3, 4, 5],
			courses: [],
			loading: false,
			currentMonth: 3,
			weekDates: [] // 存储本周每天的日期
		}
	},
	onLoad() {
		this.loadSchedule()
		this.calculateWeekDates()
	},
	computed: {
		visibleCourses() {
			// 显示当前周的所有课程，按 weekday 分布到对应列
			return this.courses.filter((course) => course.week === this.currentWeek)
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
						this.loading = false
						if (res.statusCode === 200 && res.data.code === 200) {
							const scheduleData = res.data.data
							this.currentWeek = scheduleData.currentWeek || 1
							this.courses = this.transformScheduleData(scheduleData.schedule || [])
						} else {
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

		transformScheduleData(scheduleList) {
			const themes = ['green', 'red', 'orange', 'yellow', 'blue', 'purple']

			return scheduleList.map((item, index) => {
				let startSection = 1
				let endSection = 1

				if (item.classSessions) {
					const match = item.classSessions.match(/(\d+)-(\d+)\s*节/)
					if (match) {
						startSection = parseInt(match[1])
						endSection = parseInt(match[2])
					} else {
						const singleMatch = item.classSessions.match(/(\d+)\s*节/)
						if (singleMatch) {
							startSection = parseInt(singleMatch[1])
							endSection = startSection
						}
					}
				}

				// 每两节课为一行：1-2 节=第 1 行，3-4 节=第 2 行，5-6 节=第 3 行，7-8 节=第 4 行
				const startRow = Math.ceil(startSection / 2)
				const endRow = Math.ceil(endSection / 2)

				return {
					id: item.id,
					week: this.currentWeek,
					day: item.weekday || 1,
					name: item.courseName,
					campus: item.campus || '朝阳校区',
					location: item.location || '',
					start: startRow,
					end: endRow,
					theme: themes[index % themes.length]
				}
			})
		},

		getWeekDayName(day) {
			const names = ['日', '一', '二', '三', '四', '五', '六']
			return '星期' + names[day]
		},

		// 计算本周每天的日期
		calculateWeekDates() {
			const now = new Date()
			const currentDay = now.getDay() || 7 // 获取当前星期几，周日为 7
			const mondayOffset = currentDay - 1 // 计算与周一的差值

			// 计算本周一的日期
			const monday = new Date(now)
			monday.setDate(monday.getDate() - mondayOffset)

			// 根据当前周数调整日期（假设每两周之间有 7 天间隔）
			const weekOffset = (this.currentWeek - 1) * 7
			monday.setDate(monday.getDate() + weekOffset)

			// 计算本周每天的日期
			this.weekDates = []
			for (let i = 0; i < 7; i++) {
				const date = new Date(monday)
				date.setDate(monday.getDate() + i)
				this.weekDates.push({
					month: date.getMonth() + 1,
					day: date.getDate()
				})
			}

			// 设置当前月份
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
		courseStyle(course) {
			const rowHeight = 240  // 每节的高度
			const gap = 20         // 节间距
			const boardWidth = 100 // 课程面板宽度百分比
			const columnWidth = boardWidth / 7 // 每个星期列的宽度百分比

			// 课程顶部位置：(节次 -1) * 每节高度 (260rpx = 240 + 20) + 10rpx 上边距
			const top = (course.start - 1) * (rowHeight + gap) + 10

			// 课程高度：课程行数 * 每节高度 + 行数间距 - 8rpx 下边距
			const numRows = course.end - course.start + 1
			const height = numRows * rowHeight + (numRows - 1) * gap - 16

			// 同一列中同一时间段的课程处理
			const sameColumnCourses = this.courses.filter(
				(item) => item.day === course.day && item.start === course.start && item.end === course.end
			)
			const order = sameColumnCourses.findIndex((item) => item.id === course.id)

			// 课程宽度：如果同一位置有多个课程，则平分列宽
			const baseWidth = columnWidth - 2 // 每列基础宽度减去边距
			const width = sameColumnCourses.length > 1 ? (baseWidth - 2) / sameColumnCourses.length : baseWidth

			// 计算该列的起始位置
			const columnLeft = (course.day - 1) * columnWidth + 1
			// 同一列中多个课程的偏移
			const offset = order * (width + 2)
			const left = columnLeft + offset

			return {
				top: `${top}rpx`,
				left: `${left}%`,
				height: `${height}rpx`,
				width: `${width}%`
			}
		},
		goToDetail(course) {
			uni.navigateTo({
				url: `/subpackage_schedule/scheduleDetail/scheduleDetail?id=${course.id}`
			})
		},
		switchDay(day) {
			this.currentWeekday = day
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
						// 导入成功后，调用获取本周课表接口重新加载数据
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
		}
	}
}
</script>

<style lang="scss">
.schedule-page {
	position: relative;
	min-height: 100vh;
	background: #fff;
}

.schedule-shell {
	position: relative;
	z-index: 2;
	padding: 18rpx 18rpx 36rpx;
}

.header-bar {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 12rpx 0 24rpx;
}

.back-btn {
	display: flex;
	align-items: center;
	gap: 8rpx;
	padding: 12rpx 16rpx;
	background: #f5f5f5;
	border-radius: 20rpx;
}

.back-icon {
	font-size: 36rpx;
	color: #333;
	font-weight: 700;
	line-height: 1;
}

.back-text {
	font-size: 24rpx;
	color: #333;
	font-weight: 500;
}

.page-title {
	font-size: 32rpx;
	font-weight: 700;
	color: #333;
}

.placeholder {
	width: 100rpx;
}

.import-btn {
	display: flex;
	align-items: center;
	justify-content: center;
	width: 72rpx;
	height: 72rpx;
	background: #007aff;
	border-radius: 16rpx;
}

.import-icon {
	font-size: 48rpx;
	color: #fff;
	font-weight: 300;
	line-height: 1;
}

.weekday-bar {
	display: grid;
	grid-template-columns: 50rpx repeat(7, 1fr);
	align-items: center;
	background: #f5f5f5;
	border-radius: 22rpx 22rpx 0 0;
	padding: 12rpx 6rpx 6rpx;
}

.month-label {
	font-size: 24rpx;
	color: #999;
	text-align: center;
	font-weight: 500;
}

.weekday-item {
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	padding: 6rpx 0;
	gap: 2rpx;
}

.weekday-name {
	font-size: 28rpx;
	font-weight: 600;
	color: #666;
}

.weekday-date {
	font-size: 22rpx;
	color: #999;
	margin-top: 4rpx;
}

.weekday-item.active .weekday-name {
	color: #007aff;
	font-weight: 700;
}

.weekday-item.active .weekday-date {
	color: #007aff;
}

.board-card {
	display: flex;
	align-items: flex-start;
	background: #fff;
	padding: 18rpx 14rpx 18rpx 10rpx;
	border-radius: 0 0 24rpx 24rpx;
	min-height: 1350rpx;
}

.period-column {
	width: 50rpx;
	padding-top: 8rpx;
}

.period-item {
	height: 260rpx;
	display: flex;
	align-items: center;
	justify-content: center;
}

.period-text {
	font-size: 28rpx;
	color: #999;
	font-weight: 500;
}

.course-board {
	position: relative;
	flex: 1;
	min-height: 1300rpx;
	margin-left: 12rpx;
}

/* 横向分割线 */
.h-divider {
	position: absolute;
	left: 0;
	right: 0;
	height: 1rpx;
	background: #e5e5e5;
	z-index: 1;
}

/* 纵向分割线 */
.v-divider {
	position: absolute;
	top: 0;
	bottom: 0;
	width: 1rpx;
	background: #e5e5e5;
	z-index: 1;
}

.course-block {
	position: absolute;
	border-radius: 8rpx;
	padding: 12rpx 8rpx;
	overflow: hidden;
	display: flex;
	flex-direction: column;
	justify-content: flex-start;
	z-index: 2;
}

/* 课程颜色主题 - 柔和马卡龙色系 */
.course-block--green {
	background: linear-gradient(135deg, #c8f5dc 0%, #95e8b8 100%);
	color: #1a7f4b;
}
.course-block--red {
	background: linear-gradient(135deg, #ffd6d6 0%, #ffb3b3 100%);
	color: #c41e3a;
}
.course-block--orange {
	background: linear-gradient(135deg, #ffe5cc 0%, #ffcc99 100%);
	color: #cc6600;
}
.course-block--yellow {
	background: linear-gradient(135deg, #fff5cc 0%, #ffe680 100%);
	color: #b38f00;
}
.course-block--blue {
	background: linear-gradient(135deg, #d6ebff 0%, #99d6ff 100%);
	color: #0066cc;
}
.course-block--purple {
	background: linear-gradient(135deg, #e8d6ff 0%, #cc99ff 100%);
	color: #6633cc;
}

.course-title,
.course-meta {
	display: block;
	color: rgba(0, 0, 0, 0.75);
	line-height: 1.4;
	word-break: break-all;
	text-align: center;
}

.course-title {
	font-size: 24rpx;
	font-weight: 600;
}

.course-meta {
	margin-top: 4rpx;
	font-size: 18rpx;
	font-weight: 400;
}
</style>