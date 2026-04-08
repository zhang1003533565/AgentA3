<template>
	<view class="schedule-detail-container">
		<nav-bar title="课程详情" fixed placeholder />

		<view class="detail-content">
			<view class="article-header">
				<text class="article-title">{{ course.courseName }}</text>
				<view class="article-meta">
					<text class="article-semester">{{ semester }}</text>
					<text class="article-type">{{ course.assessmentType || '必修' }}</text>
					<text class="article-time">{{ course.credit }} 学分</text>
				</view>
			</view>

			<view class="article-body">
				<view class="info-group block-white">
					<view class="info-item">
						<text class="label">授课教师</text>
						<text class="value">{{ course.teacherName || '-' }}</text>
					</view>
					<view class="info-item">
						<text class="label">上课地点</text>
						<text class="value">{{ course.location || '-' }}</text>
					</view>
					<view class="info-item">
						<text class="label">上课时间</text>
						<text class="value">{{ getWeekdayName(course.weekday) }} {{ course.classSessions }}</text>
					</view>
					<view class="info-item">
						<text class="label">上课周次</text>
						<text class="value">{{ course.weekRange || '-' }}</text>
					</view>
					<view class="info-item">
						<text class="label">教学班号</text>
						<text class="value">{{ course.classCode || '-' }}</text>
					</view>
					<view class="info-item">
						<text class="label">考核方式</text>
						<text class="value">{{ course.assessmentType || '-' }}</text>
					</view>
				</view>

				<!-- 学时信息 -->
				<view class="info-group block-white">
					<view class="info-item">
						<text class="label">总学时</text>
						<text class="value">{{ course.totalHours || 0 }} 学时</text>
					</view>
					<view class="info-item">
						<text class="label">周学时</text>
						<text class="value">{{ course.weeklyHours || 0 }} 学时/周</text>
					</view>
					<view class="info-item" v-if="course.theoryHours > 0">
						<text class="label">理论学时</text>
						<text class="value">{{ course.theoryHours }} 学时</text>
					</view>
					<view class="info-item" v-if="course.labHours > 0">
						<text class="label">实验学时</text>
						<text class="value">{{ course.labHours }} 学时</text>
					</view>
				</view>

				<!-- 其他信息 -->
				<view class="info-group block-white" v-if="course.classComposition">
					<view class="info-item">
						<text class="label">教学班组成</text>
						<text class="value">{{ course.classComposition }}</text>
					</view>
				</view>
			</view>

			<!-- 底部链接卡片：在地图中查看地点 -->
			<view class="link-card" @click="goToMap">
				<view class="link-icon">
					<image class="link-icon-img" src="/static/icons/line/map.svg" mode="aspectFit" />
				</view>
				<view class="link-info">
					<text class="link-title">在地图中查看地点：{{ course.location }}</text>
				</view>
				<text class="link-arrow">›</text>
			</view>
		</view>
	</view>
</template>

<script>
	import NavBar from '@/components/nav-bar/nav-bar.vue'
	import { BASE_URL } from '@/utils/config.js'

	export default {
		components: { NavBar },
		data() {
			return {
				course: {
					id: null,
					courseName: '',
					teacherName: '',
					location: '',
					classSessions: '',
					weekday: 1,
					weekRange: '',
					campus: '',
					classCode: '',
					classComposition: '',
					assessmentType: '',
					theoryHours: 0,
					labHours: 0,
					weeklyHours: 0,
					totalHours: 0,
					credit: 0
				},
				semester: ''
			}
		},
		onLoad(options) {
			if (options.id) {
				this.loadCourseDetail(options.id)
			}
		},
		methods: {
			async loadCourseDetail(courseId) {
				try {
					const token = uni.getStorageSync('token') || ''

					uni.request({
						url: `${BASE_URL}/api/browser/jwx/schedule/${courseId}`,
						method: 'GET',
						header: {
							'Authorization': 'Bearer ' + token
						},
						success: (res) => {
							if (res.statusCode === 200 && res.data.code === 200) {
								this.course = res.data.data.course
								this.semester = res.data.data.semester || ''
							} else {
								uni.showToast({
									title: res.data.message || '获取课程详情失败',
									icon: 'none'
								})
							}
						},
						fail: (err) => {
							console.error('获取课程详情失败:', err)
							uni.showToast({
								title: '网络错误',
								icon: 'none'
							})
						}
					})
				} catch (error) {
					console.error('获取课程详情失败:', error)
					uni.showToast({
						title: '加载失败',
						icon: 'none'
					})
				}
			},
			getWeekdayName(day) {
				const names = ['日', '一', '二', '三', '四', '五', '六']
				return '星期' + names[day]
			},
			goToMap() {
				uni.reLaunch({
					url: '/pages/map/map'
				})
			}
		}
	}
</script>

<style lang="scss">
	.schedule-detail-container {
		min-height: 100vh;
		background-color: #f5f6f7;
	}

	.detail-content {
		padding: 40rpx 32rpx;
	}

	.article-header {
		margin-bottom: 32rpx;
	}

	.article-title {
		font-size: 44rpx;
		font-weight: 700;
		color: #1D1D1F;
		line-height: 1.4;
		margin-bottom: 16rpx;
		display: block;
	}

	.article-meta {
		display: flex;
		align-items: center;
		gap: 20rpx;
	}

	.article-semester {
		font-size: 26rpx;
		color: #666;
		background-color: #f5f5f5;
		padding: 6rpx 16rpx;
		border-radius: 12rpx;
	}

	.article-type {
		font-size: 26rpx;
		color: #007AFF;
		background-color: rgba(0, 122, 255, 0.1);
		padding: 6rpx 16rpx;
		border-radius: 12rpx;
	}

	.article-time {
		font-size: 26rpx;
		color: #8E8E93;
	}

	.article-body {
		margin-bottom: 60rpx;
	}

	.info-group {
		background-color: #FFFFFF;
		border-radius: 16rpx;
		padding: 24rpx 28rpx;
		margin-bottom: 24rpx;
		box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);

		.info-item {
			display: flex;
			justify-content: space-between;
			align-items: center;
			padding: 20rpx 0;
			border-bottom: 1rpx solid #F0F0F2;

			&:last-child {
				border-bottom: none;
			}

			.label {
				font-size: 28rpx;
				color: #8E8E93;
			}
			.value {
				font-size: 28rpx;
				color: #1D1D1F;
				font-weight: 500;
				text-align: right;
				max-width: 60%;
			}
		}
	}

	.link-card {
		display: flex;
		align-items: center;
		background-color: #FFFFFF;
		padding: 24rpx;
		border-radius: 16rpx;
		box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
		transition: background-color 0.2s;

		&:active {
			background-color: #F8F9FB;
		}

		.link-icon {
			width: 80rpx;
			height: 80rpx;
			background: linear-gradient(135deg, #7AA1D2, #5B8EF4);
			border-radius: 16rpx;
			display: flex;
			align-items: center;
			justify-content: center;
			margin-right: 20rpx;
			flex-shrink: 0;
		}

		.link-icon-img {
			width: 44rpx;
			height: 44rpx;
			filter: brightness(0) invert(1);
		}

		.link-info {
			flex: 1;
			min-width: 0;

			.link-title {
				font-size: 28rpx;
				color: #1D1D1F;
				font-weight: 500;
				overflow: hidden;
				text-overflow: ellipsis;
				white-space: nowrap;
			}
		}

		.link-arrow {
			font-size: 36rpx;
			color: #C7C7CC;
			margin-left: 12rpx;
		}
	}
</style>
