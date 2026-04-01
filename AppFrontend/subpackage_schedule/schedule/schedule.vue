<template>
	<view class="schedule-container">
		<nav-bar title="我的课表" fixed placeholder />
		
		<view class="schedule-content">
			<!-- 这里简单模拟一个课表视图 -->
			<view class="week-selector block-white">
				<text class="week-text">第 5 周 (本周)</text>
			</view>
			
			<view class="course-grid">
				<view class="course-item block-white" v-for="(course, index) in courses" :key="index" @click="goToDetail(course)">
					<view class="course-time">
						<text class="time-range">{{ course.time }}</text>
						<text class="section">第 {{ course.section }} 节</text>
					</view>
					<view class="course-info">
						<text class="course-name">{{ course.name }}</text>
						<view class="course-loc">
							<icon-line name="map" size="small" color="#8E8E93" />
							<text class="loc-text">{{ course.location }}</text>
						</view>
					</view>
					<text class="arrow">›</text>
				</view>
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
				courses: [
					{ id: 1, name: '软件工程', time: '08:00 - 09:35', section: '1-2', location: '教3-201', teacher: '张教授', type: '必修' },
					{ id: 2, name: '计算机网络', time: '10:00 - 11:35', section: '3-4', location: '实验楼B402', teacher: '李博士', type: '必修' },
					{ id: 3, name: '数据库系统', time: '14:00 - 15:35', section: '5-6', location: '教1-104', teacher: '王老师', type: '必修' }
				]
			}
		},
		methods: {
			goToDetail(course) {
				uni.navigateTo({
					url: `/subpackage_schedule/scheduleDetail/scheduleDetail?id=${course.id}`
				})
			}
		}
	}
</script>

<style lang="scss">
	.schedule-container {
		min-height: 100vh;
		background-color: #F7F7F9;
	}
	
	.schedule-content {
		padding: 24rpx 32rpx;
	}
	
	.week-selector {
		padding: 24rpx;
		border-radius: 16rpx;
		margin-bottom: 24rpx;
		display: flex;
		justify-content: center;
		align-items: center;
		
		.week-text {
			font-size: 32rpx;
			font-weight: 600;
			color: #1D1D1F;
		}
	}
	
	.course-item {
		padding: 32rpx;
		border-radius: 20rpx;
		margin-bottom: 20rpx;
		display: flex;
		align-items: center;
		position: relative;
		
		&:active {
			background-color: #F5F5F7;
		}
		
		.course-time {
			display: flex;
			flex-direction: column;
			width: 140rpx;
			margin-right: 24rpx;
			
			.time-range {
				font-size: 24rpx;
				color: #1D1D1F;
				font-weight: 500;
			}
			.section {
				font-size: 22rpx;
				color: #8E8E93;
				margin-top: 4rpx;
			}
		}
		
		.course-info {
			flex: 1;
			
			.course-name {
				font-size: 32rpx;
				font-weight: 600;
				color: #1D1D1F;
				display: block;
				margin-bottom: 8rpx;
			}
			
			.course-loc {
				display: flex;
				align-items: center;
				gap: 4rpx;
				
				.loc-text {
					font-size: 24rpx;
					color: #8E8E93;
				}
			}
		}
		
		.arrow {
			font-size: 40rpx;
			color: #C7C7CC;
		}
	}
</style>
