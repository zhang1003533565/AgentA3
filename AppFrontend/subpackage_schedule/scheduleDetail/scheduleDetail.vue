<template>
	<view class="schedule-detail-container">
		<nav-bar title="课程详情" />
		
		<view class="detail-content" :style="{ paddingTop: navBarHeight + 'px' }">
			<view class="article-header">
				<text class="article-title">{{ course.name }}</text>
				<view class="article-meta">
					<text class="article-type">{{ course.type || '课程' }}</text>
					<text class="article-time">第 {{ course.section }} 节</text>
				</view>
			</view>
			
			<view class="article-body">
				<view class="info-group block-white">
					<view class="info-item">
						<text class="label">授课教师</text>
						<text class="value">{{ course.teacher }}</text>
					</view>
					<view class="info-item">
						<text class="label">上课地点</text>
						<text class="value">{{ course.location }}</text>
					</view>
					<view class="info-item">
						<text class="label">上课时间</text>
						<text class="value">{{ course.time }}</text>
					</view>
					<view class="info-item">
						<text class="label">上课周次</text>
						<text class="value">1-16周 (全)</text>
					</view>
				</view>
				
				<view class="description-section">
					<text class="section-title">课程简介</text>
					<text class="article-text">这是一门关于{{ course.name }}的基础核心课程，旨在培养学生掌握相关理论知识与实践技能。请同学们准时参加，保持课堂纪律。</text>
				</view>
			</view>
			
			<!-- 底部链接卡片：模仿学习通风格 -->
			<view class="link-card" @click="goToMap">
				<view class="link-icon">
					<icon-line name="map" size="service" color="#FFFFFF" />
				</view>
				<view class="link-info">
					<text class="link-title">在地图中查看地点: {{ course.location }}</text>
				</view>
				<text class="link-arrow">›</text>
			</view>
		</view>
	</view>
</template>

<script>
	import NavBar from '@/components/nav-bar/nav-bar.vue'
	import IconLine from '@/components/icon-line/icon-line.vue'
	
	export default {
		components: { NavBar, IconLine },
		data() {
			return {
				navBarHeight: 88,
				course: {
					id: null,
					name: '',
					teacher: '',
					location: '',
					time: '',
					section: '',
					type: ''
				}
			}
		},
		onLoad(options) {
			const sys = uni.getSystemInfoSync()
			this.navBarHeight = sys.statusBarHeight + 44
			
			if (options.id) {
				this.loadCourseDetail(options.id)
			}
		},
		methods: {
			loadCourseDetail(id) {
				const mockData = {
					'1': { id: 1, name: '软件工程', time: '08:00 - 09:35', section: '1-2', location: '教3-201', teacher: '张教授', type: '必修' },
					'2': { id: 2, name: '计算机网络', time: '10:00 - 11:35', section: '3-4', location: '实验楼B402', teacher: '李博士', type: '必修' },
					'3': { id: 3, name: '数据库系统', time: '14:00 - 15:35', section: '5-6', location: '教1-104', teacher: '王老师', type: '必修' }
				}
				if (mockData[id]) {
					this.course = mockData[id]
				}
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
		background-color: #FFFFFF;
	}

	.detail-content {
		padding: 40rpx 40rpx;
	}

	.article-header {
		margin-bottom: 40rpx;
	}

	.article-title {
		font-size: 44rpx;
		font-weight: 700;
		color: #1D1D1F;
		line-height: 1.4;
		margin-bottom: 24rpx;
		display: block;
	}

	.article-meta {
		display: flex;
		align-items: center;
		gap: 20rpx;
	}

	.article-type {
		font-size: 26rpx;
		color: #7AA1D2;
	}

	.article-time {
		font-size: 26rpx;
		color: #B2B2B2;
	}

	.article-body {
		margin-bottom: 60rpx;
	}

	.info-group {
		padding: 24rpx 32rpx;
		border-radius: 16rpx;
		margin-bottom: 40rpx;
		
		.info-item {
			display: flex;
			justify-content: space-between;
			padding: 16rpx 0;
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
			}
		}
	}

	.description-section {
		.section-title {
			font-size: 32rpx;
			font-weight: 600;
			color: #1D1D1F;
			margin-bottom: 16rpx;
			display: block;
		}
		.article-text {
			font-size: 30rpx;
			color: #3A3A3C;
			line-height: 1.6;
		}
	}

	.link-card {
		display: flex;
		align-items: center;
		background-color: #F8F9FB;
		padding: 24rpx;
		border-radius: 12rpx;
		transition: background-color 0.2s;

		&:active {
			background-color: #F0F2F5;
		}
		
		.link-icon {
			width: 80rpx;
			height: 80rpx;
			background-color: #7AA1D2;
			border-radius: 12rpx;
			display: flex;
			align-items: center;
			justify-content: center;
			margin-right: 20rpx;
			flex-shrink: 0;
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
