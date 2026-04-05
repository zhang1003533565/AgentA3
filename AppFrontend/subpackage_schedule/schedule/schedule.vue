<template>
	<view class="schedule-page"
		@touchstart="handleTouchStart"
		@touchmove="handleTouchMove"
		@touchend="handleTouchEnd">
		<!-- Header Bar（独立页面时显示） -->
		<view v-if="showHeader" class="header-bar">
			<view class="header-left">
				<view class="back-btn" @click="goBack">
					<image class="back-icon" src="/static/icons/back.png" mode="aspectFit" />
				</view>
				<view class="week-info-wrapper">
					<view class="week-info" @click="showWeekSelector">
						<text class="week-text">第{{ currentWeek }}周</text>
					</view>
					<view class="semester-info">
						<text class="semester-text">{{ semester }}</text>
					</view>
				</view>
			</view>
			<view class="header-actions">
				<view class="share-btn" @click="shareSchedule">
					<image class="share-icon" src="/static/icons/share.png" mode="aspectFit" />
				</view>
				<view class="import-btn" @click="showImportMenu">
					<image class="import-icon" src="/static/icons/add.png" mode="aspectFit" />
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
						<text class="course-meta"></text>
						<text class="course-meta">{{ course.location }}</text>
					</view>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
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
			periods: [1, 2, 3, 4, 5],
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
		visibleCourses() {
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
							if (scheduleData.semester) {
								this.semester = scheduleData.semester
							}
							if (scheduleData.semesterStart) {
								this.semesterStart = scheduleData.semesterStart
							}
							this.calculateWeekDates()
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
					weekday: item.weekday || 1,
					name: item.courseName,
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
			uni.showLoading({
				title: '加载中...'
			})
			const token = uni.getStorageSync('token') || ''
			uni.request({
				url: `http://localhost:8080/api/browser/jwx/schedule/week/${week}`,
				method: 'GET',
				header: {
					'Authorization': 'Bearer ' + token
				},
				success: (res) => {
					uni.hideLoading()
					if (res.statusCode === 200 && res.data.code === 200) {
						const scheduleData = res.data.data
						this.currentWeek = scheduleData.currentWeek || week
						this.courses = this.transformScheduleData(scheduleData.schedule || [])
						if (scheduleData.semester) {
							this.semester = scheduleData.semester
						}
						this.calculateWeekDates()
					} else {
						uni.showToast({
							title: res.data.message || '获取课表失败',
							icon: 'none'
						})
					}
				},
				fail: (err) => {
					uni.hideLoading()
					console.error('加载课表失败:', err)
					uni.showToast({
						title: '加载失败',
						icon: 'none'
					})
				}
			})
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
				(item) => item.weekday === course.weekday && item.start === course.start && item.end === course.end
			)
			const order = sameColumnCourses.findIndex((item) => item.id === course.id)

			// 课程宽度：如果同一位置有多个课程，则平分列宽
			const baseWidth = columnWidth - 2 // 每列基础宽度减去边距
			const width = sameColumnCourses.length > 1 ? (baseWidth - 2) / sameColumnCourses.length : baseWidth

			// 计算该列的起始位置
			const columnLeft = (course.weekday - 1) * columnWidth + 1
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
	background: #fff;
}

.schedule-shell {
	position: relative;
	z-index: 2;
	padding: 18rpx 18rpx 36rpx;
}

.header-bar {
	position: relative;
	display: flex;
	flex-direction: column;
	padding: 12rpx 30rpx 24rpx;
}

.header-left {
	display: flex;
	align-items: center;
	justify-content: flex-start;
}

.header-actions {
	position: absolute;
	right: 30rpx;
	top: 50%;
	transform: translateY(-50%);
	display: flex;
	align-items: center;
}

.back-btn {
	display: flex;
	align-items: center;
	justify-content: center;
	width: 72rpx;
	height: 72rpx;
}

.back-icon {
	width: 56rpx;
	height: 56rpx;
}

.header-left {
	display: flex;
	align-items: center;
	gap: 16rpx;
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
	gap: 4rpx;
	cursor: pointer;
	opacity: 0.9;
	transition: opacity 0.2s;
}

.week-info:active {
	opacity: 0.6;
}

.week-text {
	font-size: 40rpx;
	font-weight: 800;
	color: #1D1D1F;
}

.semester-info {
	display: flex;
	align-items: center;
}

.semester-text {
	font-size: 22rpx;
	color: #999;
}

.header-actions {
	position: absolute;
	right: 30rpx;
	top: 50%;
	transform: translateY(-50%);
	display: flex;
	align-items: center;
	gap: 20rpx;
}

.share-btn {
	display: flex;
	align-items: center;
	justify-content: center;
	width: 72rpx;
	height: 72rpx;
}

.share-icon {
	width: 56rpx;
	height: 56rpx;
}

.import-btn {
	display: flex;
	align-items: center;
	justify-content: center;
	width: 72rpx;
	height: 72rpx;
	background: #1D1D1F;
	border-radius: 16rpx;
}

.import-icon {
	width: 56rpx;
	height: 56rpx;
	filter: brightness(0) invert(1);
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