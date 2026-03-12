<template>
	<view class="home-container">
		<!-- 顶部搜索栏 -->
		<view class="header">
			<view class="search-box">
				<text class="search-icon">🔍</text>
				<input class="search-input" type="text" placeholder="搜索课程、通知、活动..." />
			</view>
			<view class="message-icon" @click="goToMessage">
				<text>🔔</text>
				<text v-if="unreadCount > 0" class="badge">{{unreadCount}}</text>
			</view>
			<view class="user-avatar" @click="goToProfile">
				<image :src="userAvatar" mode="aspectFill" class="avatar-img"></image>
			</view>
		</view>

		<!-- 轮播图 -->
		<view class="banner-section">
			<swiper class="banner-swiper" :indicator-dots="true" :autoplay="true" :interval="3000" :duration="500">
				<swiper-item v-for="(item, index) in banners" :key="index">
					<image class="banner-image" :src="item.image" mode="aspectFill" @click="onBannerClick(item)" />
				</swiper-item>
			</swiper>
		</view>

		<!-- 功能菜单 -->
		<view class="menu-section">
			<view class="menu-grid">
				<view class="menu-item" v-for="(item, index) in menus" :key="index" @click="onMenuClick(item)">
					<view class="menu-icon" :style="{backgroundColor: item.bgColor}">
						<text>{{item.icon}}</text>
					</view>
					<text class="menu-name">{{item.name}}</text>
				</view>
			</view>
		</view>

		<!-- 通知公告 -->
		<view class="notice-section">
			<view class="section-header">
				<text class="section-title">📢 通知公告</text>
				<text class="more" @click="goToNotice">更多 ></text>
			</view>
			<view class="notice-list">
				<view class="notice-item" v-for="(item, index) in notices" :key="index" @click="onNoticeClick(item)">
					<text class="notice-tag" :class="item.type">{{item.tag}}</text>
					<text class="notice-title">{{item.title}}</text>
					<text class="notice-time">{{item.time}}</text>
				</view>
			</view>
		</view>

		<!-- 今日课程 -->
		<view class="course-section">
			<view class="section-header">
				<text class="section-title">📚 今日课程</text>
				<text class="more" @click="goToSchedule">课表 ></text>
			</view>
			<view class="course-list">
				<view class="course-item" v-for="(item, index) in todayCourses" :key="index">
					<view class="course-time">
						<text class="time-start">{{item.startTime}}</text>
						<text class="time-end">{{item.endTime}}</text>
					</view>
					<view class="course-info">
						<text class="course-name">{{item.name}}</text>
						<text class="course-location">📍 {{item.location}}</text>
					</view>
					<view class="course-status" :class="item.status">{{item.statusText}}</view>
				</view>
				<view v-if="todayCourses.length === 0" class="empty-tip">
					<text>今天没有课程，好好休息吧~</text>
				</view>
			</view>
		</view>

		<!-- 校园服务 -->
		<view class="service-section">
			<view class="section-header">
				<text class="section-title">🎯 校园服务</text>
			</view>
			<view class="service-grid">
				<view class="service-item" v-for="(item, index) in services" :key="index" @click="onServiceClick(item)">
					<text class="service-icon">{{item.icon}}</text>
					<text class="service-name">{{item.name}}</text>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
	export default {
		data() {
			return {
				userInfo: null,
				userAvatar: '',
				unreadCount: 3,
				banners: [
					{ image: 'https://picsum.photos/400/150?random=1', title: '新学期开学典礼', url: '' },
					{ image: 'https://picsum.photos/400/150?random=2', title: '图书馆开放时间调整', url: '' },
					{ image: 'https://picsum.photos/400/150?random=3', title: '校园美食节活动', url: '' }
				],
				menus: [
					{ name: '课程表', icon: '📅', bgColor: '#4A90D9', path: '/pages/schedule/schedule' },
					{ name: '成绩查询', icon: '📊', bgColor: '#67C23A', path: '/pages/grade/grade' },
					{ name: '考试安排', icon: '📝', bgColor: '#E6A23C', path: '/pages/exam/exam' },
					{ name: '图书馆', icon: '📖', bgColor: '#909399', path: '/pages/library/library' },
					{ name: '校园卡', icon: '💳', bgColor: '#F56C6C', path: '/pages/card/card' },
					{ name: '请假申请', icon: '📋', bgColor: '#8E44AD', path: '/pages/leave/leave' },
					{ name: '校园活动', icon: '🎉', bgColor: '#FF6B6B', path: '/subpackage_activity/activityList/activityList' },
					{ name: '失物招领', icon: '🔍', bgColor: '#4ECDC4', path: '/pages/lostfound/lostfound' }
				],
				notices: [
					{ title: '关于2026年清明节放假安排的通知', time: '10:30', tag: '教务', type: 'jw' },
					{ title: '图书馆电子资源使用培训讲座', time: '昨天', tag: '图书馆', type: 'tsg' },
					{ title: '2026年春季学期奖学金评选开始', time: '昨天', tag: '学工', type: 'xg' },
					{ title: '校园网络维护通知（3月12日）', time: '03-08', tag: '信息化', type: 'xxh' }
				],
				todayCourses: [
					{ name: '高等数学', location: '教学楼A301', startTime: '08:00', endTime: '09:40', status: 'finished', statusText: '已结束' },
					{ name: '大学英语', location: '教学楼B205', startTime: '10:00', endTime: '11:40', status: 'ongoing', statusText: '进行中' },
					{ name: '计算机基础', location: '实验楼C102', startTime: '14:00', endTime: '15:40', status: 'upcoming', statusText: '待上课' }
				],
				services: [
					{ name: '校车时刻', icon: '🚌', path: '/pages/bus/bus' },
					{ name: '校历', icon: '📆', path: '/pages/calendar/calendar' },
					{ name: '场馆预约', icon: '🏟️', path: '/pages/venue/venue' },
					{ name: '维修报修', icon: '🔧', path: '/pages/repair/repair' },
					{ name: '心理咨询', icon: '💬', path: '/pages/counseling/counseling' },
					{ name: '就业信息', icon: '💼', path: '/pages/jobs/jobs' },
					{ name: '校园地图', icon: '🗺️', path: '/pages/map/map' },
					{ name: '更多服务', icon: '➕', path: '/pages/services/services' }
				]
			}
		},
		onLoad() {
			this.checkLogin()
			this.loadData()
		},
			onShow() {
			this.checkLogin()
		},
		onPullDownRefresh() {
			this.loadData()
			setTimeout(() => {
				uni.stopPullDownRefresh()
			}, 1000)
		},
		methods: {
			checkLogin() {
				const token = uni.getStorageSync('token')
				const userInfoStr = uni.getStorageSync('userInfo')
				if (!token || !userInfoStr) {
					uni.reLaunch({
						url: '/pages/login/login'
					})
					return
				}
				this.userInfo = JSON.parse(userInfoStr)
				this.userAvatar = `https://api.dicebear.com/7.x/avataaars/svg?seed=${this.userInfo.username}`
			},
			loadData() {
				// 这里可以调用接口加载真实数据
				console.log('加载首页数据')
			},
			goToMessage() {
				uni.navigateTo({
					url: '/pages/message/message'
				})
			},
			onBannerClick(item) {
				console.log('点击轮播图:', item)
			},
			onMenuClick(item) {
				uni.navigateTo({
					url: item.path
				})
			},
			goToNotice() {
				uni.navigateTo({
					url: '/pages/notice/notice'
				})
			},
			onNoticeClick(item) {
				console.log('点击通知:', item)
			},
			goToSchedule() {
				uni.navigateTo({
					url: '/pages/schedule/schedule'
				})
			},
			onServiceClick(item) {
				uni.navigateTo({
					url: item.path
				})
			},
			goToProfile() {
				uni.showActionSheet({
					title: `您好，${this.userInfo?.username || '用户'}`,
					itemList: ['个人中心', '退出登录'],
					success: (res) => {
						if (res.tapIndex === 0) {
							uni.showToast({ title: '个人中心开发中', icon: 'none' })
						} else if (res.tapIndex === 1) {
							this.handleLogout()
						}
					}
				})
			},
			handleLogout() {
				uni.showModal({
					title: '提示',
					content: '确定要退出登录吗？',
					success: (res) => {
						if (res.confirm) {
							uni.removeStorageSync('token')
							uni.removeStorageSync('userInfo')
							uni.reLaunch({
								url: '/pages/login/login'
							})
						}
					}
				})
			}
		}
	}
</script>

<style>
	.home-container {
		min-height: 100vh;
		background-color: #f5f5f5;
		padding-bottom: 30rpx;
	}

	/* 顶部搜索栏 */
	.header {
		display: flex;
		align-items: center;
		padding: 20rpx 30rpx;
		background: linear-gradient(135deg, #4A90D9 0%, #357ABD 100%);
	}

	.search-box {
		flex: 1;
		display: flex;
		align-items: center;
		background-color: rgba(255, 255, 255, 0.9);
		border-radius: 40rpx;
		padding: 15rpx 25rpx;
		margin-right: 20rpx;
	}

	.search-icon {
		font-size: 28rpx;
		margin-right: 10rpx;
	}

	.search-input {
		flex: 1;
		font-size: 28rpx;
		color: #333;
	}

	.message-icon {
		position: relative;
		font-size: 40rpx;
		padding: 10rpx;
	}

	.badge {
		position: absolute;
		top: 0;
		right: 0;
		background-color: #ff4d4f;
		color: #fff;
		font-size: 20rpx;
		width: 32rpx;
		height: 32rpx;
		border-radius: 50%;
		display: flex;
		align-items: center;
		justify-content: center;
	}

	.user-avatar {
		margin-left: 20rpx;
	}

	.avatar-img {
		width: 60rpx;
		height: 60rpx;
		border-radius: 50%;
		border: 2rpx solid white;
	}

	/* 轮播图 */
	.banner-section {
		padding: 20rpx 30rpx;
		background-color: #fff;
	}

	.banner-swiper {
		height: 300rpx;
		border-radius: 16rpx;
		overflow: hidden;
	}

	.banner-image {
		width: 100%;
		height: 100%;
	}

	/* 功能菜单 */
	.menu-section {
		background-color: #fff;
		padding: 30rpx;
		margin-top: 20rpx;
	}

	.menu-grid {
		display: grid;
		grid-template-columns: repeat(4, 1fr);
		gap: 30rpx;
	}

	.menu-item {
		display: flex;
		flex-direction: column;
		align-items: center;
	}

	.menu-icon {
		width: 90rpx;
		height: 90rpx;
		border-radius: 20rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		font-size: 40rpx;
		margin-bottom: 15rpx;
	}

	.menu-name {
		font-size: 26rpx;
		color: #333;
	}

	/* 通知公告 */
	.notice-section {
		background-color: #fff;
		padding: 30rpx;
		margin-top: 20rpx;
	}

	.section-header {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-bottom: 20rpx;
	}

	.section-title {
		font-size: 32rpx;
		font-weight: bold;
		color: #333;
	}

	.more {
		font-size: 26rpx;
		color: #999;
	}

	.notice-list {
		display: flex;
		flex-direction: column;
		gap: 20rpx;
	}

	.notice-item {
		display: flex;
		align-items: center;
		padding: 20rpx 0;
		border-bottom: 1rpx solid #f0f0f0;
	}

	.notice-item:last-child {
		border-bottom: none;
	}

	.notice-tag {
		font-size: 22rpx;
		padding: 4rpx 12rpx;
		border-radius: 8rpx;
		margin-right: 15rpx;
		white-space: nowrap;
	}

	.notice-tag.jw {
		background-color: #e6f7ff;
		color: #1890ff;
	}

	.notice-tag.tsg {
		background-color: #f6ffed;
		color: #52c41a;
	}

	.notice-tag.xg {
		background-color: #fff7e6;
		color: #fa8c16;
	}

	.notice-tag.xxh {
		background-color: #f9f0ff;
		color: #722ed1;
	}

	.notice-title {
		flex: 1;
		font-size: 28rpx;
		color: #333;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.notice-time {
		font-size: 24rpx;
		color: #999;
		margin-left: 15rpx;
	}

	/* 今日课程 */
	.course-section {
		background-color: #fff;
		padding: 30rpx;
		margin-top: 20rpx;
	}

	.course-list {
		display: flex;
		flex-direction: column;
		gap: 20rpx;
	}

	.course-item {
		display: flex;
		align-items: center;
		padding: 25rpx;
		background-color: #f8f9fa;
		border-radius: 16rpx;
	}

	.course-time {
		display: flex;
		flex-direction: column;
		align-items: center;
		margin-right: 25rpx;
		padding-right: 25rpx;
		border-right: 2rpx solid #e0e0e0;
	}

	.time-start {
		font-size: 28rpx;
		font-weight: bold;
		color: #333;
	}

	.time-end {
		font-size: 22rpx;
		color: #999;
		margin-top: 5rpx;
	}

	.course-info {
		flex: 1;
		display: flex;
		flex-direction: column;
	}

	.course-name {
		font-size: 30rpx;
		font-weight: bold;
		color: #333;
		margin-bottom: 8rpx;
	}

	.course-location {
		font-size: 24rpx;
		color: #666;
	}

	.course-status {
		font-size: 22rpx;
		padding: 6rpx 16rpx;
		border-radius: 20rpx;
	}

	.course-status.finished {
		background-color: #f0f0f0;
		color: #999;
	}

	.course-status.ongoing {
		background-color: #e6f7ff;
		color: #1890ff;
	}

	.course-status.upcoming {
		background-color: #f6ffed;
		color: #52c41a;
	}

	.empty-tip {
		text-align: center;
		padding: 40rpx;
		color: #999;
		font-size: 28rpx;
	}

	/* 校园服务 */
	.service-section {
		background-color: #fff;
		padding: 30rpx;
		margin-top: 20rpx;
	}

	.service-grid {
		display: grid;
		grid-template-columns: repeat(4, 1fr);
		gap: 30rpx;
	}

	.service-item {
		display: flex;
		flex-direction: column;
		align-items: center;
		padding: 20rpx 0;
	}

	.service-icon {
		font-size: 48rpx;
		margin-bottom: 10rpx;
	}

	.service-name {
		font-size: 24rpx;
		color: #666;
	}
</style>
