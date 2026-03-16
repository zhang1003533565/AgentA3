<template>
	<view class="home-container">
		<!-- Header：统一 nav-bar + 搜索 + Banner -->
		<view class="header-wrap">
			<nav-bar title="智慧校园" :showBack="false" />
			<view class="header-top">
				<view class="header-search-row">
					<view class="search-box">
						<view class="search-icon-wrap"><icon-line name="search" size="service" /></view>
						<input class="search-input" type="text" placeholder="搜索课程、通知、活动..." placeholder-class="search-placeholder" />
					</view>
				</view>
			</view>
			<view class="banner-section">
				<swiper class="banner-swiper" :indicator-dots="true" :autoplay="true" :interval="3000" :duration="500">
					<swiper-item v-for="(item, index) in (banners || [])" :key="index">
						<image class="banner-image" :src="item.image" mode="aspectFill" @click="onBannerClick(item)" />
					</swiper-item>
				</swiper>
			</view>
		</view>

		<view class="section-divider"></view>
		<view class="menu-section block-white">
			<view class="menu-grid">
				<view class="menu-item" v-for="(item, index) in (menus || [])" :key="index" @click="onMenuClick(item)">
					<view class="menu-icon-wrap">
						<icon-line :name="item.icon" size="diamond" />
					</view>
					<text class="menu-name">{{item.name}}</text>
				</view>
			</view>
		</view>

		<view class="section-divider"></view>
		<view class="notice-section notice-course-fullwidth">
			<view class="section-header-inner">
				<view class="section-title-with-bar">
					<view class="section-title-bar"></view>
					<text class="section-title-inner">通知公告</text>
				</view>
				<text class="more" @click="goToNotice">更多 ></text>
			</view>
			<view class="notice-list">
				<view class="notice-item" v-for="(item, index) in (notices || [])" :key="index" @click="onNoticeClick(item)">
					<text class="notice-tag" :class="item.type">{{item.tag}}</text>
					<text class="notice-title">{{item.title}}</text>
					<text class="notice-time">{{item.time}}</text>
				</view>
			</view>
		</view>

		<view class="section-divider"></view>
		<view class="course-section notice-course-fullwidth">
			<view class="section-header-inner">
				<view class="section-title-with-bar">
					<view class="section-title-bar"></view>
					<text class="section-title-inner">今日课程</text>
				</view>
				<text class="more" @click="goToSchedule">课表 ></text>
			</view>
			<view class="course-list">
				<view class="course-item" v-for="(item, index) in (todayCourses || [])" :key="index" :class="item.status">
					<view class="course-time" :class="{ 'course-time--ongoing': item.status === 'ongoing' }">
						<text class="time-start">{{item.startTime}}</text>
						<text class="time-end">{{item.endTime}}</text>
					</view>
					<view class="course-info">
						<view class="course-row">
							<text class="course-name">{{item.name}}</text>
							<view class="course-status-text" :class="item.status"><view class="course-status-dot"></view><text>{{item.statusText}}</text></view>
						</view>
						<text class="course-location">{{item.location}}</text>
					</view>
				</view>
				<view v-if="!(todayCourses && todayCourses.length)" class="empty-tip">
					<text>今天没有课程，好好休息吧~</text>
				</view>
			</view>
		</view>

		<view class="section-divider"></view>
		<view class="service-section block-white">
			<view class="section-header">
				<view class="section-title-with-bar">
					<view class="section-title-bar"></view>
					<text class="section-title">校园服务</text>
				</view>
			</view>
			<view class="service-grid">
				<view class="service-item" v-for="(item, index) in (services || [])" :key="index" @click="onServiceClick(item)">
					<view class="service-icon-wrap">
						<icon-line :name="item.icon" size="service" />
					</view>
					<text class="service-name">{{item.name}}</text>
				</view>
			</view>
		</view>
		<custom-tab-bar current="index" />
	</view>
</template>

<script>
	import CustomTabBar from '@/components/custom-tab-bar/custom-tab-bar.vue'
	import NavBar from '@/components/nav-bar/nav-bar.vue'
	import IconLine from '@/components/icon-line/icon-line.vue'
	export default {
		components: { CustomTabBar, NavBar, IconLine },
		data() {
			return {
				statusBarHeight: 20,
				userInfo: null,
				userAvatar: '',
				unreadCount: 3,
				banners: [
					{ image: 'https://picsum.photos/400/150?random=1', title: '新学期开学典礼', url: '' },
					{ image: 'https://picsum.photos/400/150?random=2', title: '图书馆开放时间调整', url: '' },
					{ image: 'https://picsum.photos/400/150?random=3', title: '校园美食节活动', url: '' }
				],
				menus: [
					{ name: '课程表', icon: 'calendar', path: '/pages/schedule/schedule' },
					{ name: '成绩查询', icon: 'award', path: '/pages/grade/grade' },
					{ name: '考试安排', icon: 'edit-3', path: '/pages/exam/exam' },
					{ name: '图书馆', icon: 'book-open', path: '/pages/library/library' },
					{ name: '校园卡', icon: 'credit-card', path: '/pages/card/card' },
					{ name: '请假申请', icon: 'clipboard', path: '/pages/leave/leave' },
					{ name: '校园活动', icon: 'compass', path: '/subpackage_activity/activityList/activityList' },
					{ name: '失物招领', icon: 'search', path: '/pages/lostfound/lostfound' }
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
					{ name: '校车时刻', icon: 'bus', path: '/pages/bus/bus' },
					{ name: '校历', icon: 'calendar-alt', path: '/pages/calendar/calendar' },
					{ name: '场馆预约', icon: 'venue', path: '/pages/venue/venue' },
					{ name: '维修报修', icon: 'tool', path: '/pages/repair/repair' },
					{ name: '心理咨询', icon: 'message-circle', path: '/pages/counseling/counseling' },
					{ name: '就业信息', icon: 'briefcase', path: '/pages/jobs/jobs' },
					{ name: '校园地图', icon: 'map', path: '/pages/map/map' },
					{ name: '更多服务', icon: 'more', path: '/pages/services/services' }
				]
			}
		},
		onLoad() {
			const sys = uni.getSystemInfoSync()
			this.statusBarHeight = sys.statusBarHeight || 20
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
				// 活动列表在子包中，使用 reLaunch 避免 H5 下动态加载子包报 500 / switchTab 限制
				const activityListPath = '/subpackage_activity/activityList/activityList'
				if (item.path === activityListPath) {
					uni.reLaunch({ url: activityListPath })
				} else {
					uni.navigateTo({ url: item.path })
				}
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

<style lang="scss">
	/* 画布：极浅灰，无影化 */
	.home-container {
		min-height: 100vh;
		background-color: #F7F7F9;
		padding-bottom: 120rpx;
	}

	/* 区域分隔：灰条，与画布同色 */
	.section-divider {
		height: 20rpx;
		background-color: #F7F7F9;
		width: 100%;
	}

	/* 通栏白块：无阴影 */
	.block-white {
		background-color: #FFFFFF;
		border-radius: 12rpx;
	}

	/* ========== Header：纯白覆盖至顶部，消除状态栏区域灰边 ========== */
	.header-wrap {
		background-color: #FFFFFF;
		padding-left: 32rpx;
		padding-right: 32rpx;
		padding-bottom: 0;
	}
	.header-top {
		background-color: #FFFFFF;
		margin-left: -32rpx;
		margin-right: -32rpx;
		padding-left: 32rpx;
		padding-right: 32rpx;
	}
	.header-search-row {
		padding-top: 16rpx;
		padding-bottom: 20rpx;
		margin-bottom: 0;
	}
	.search-box {
		display: flex;
		align-items: center;
		height: 76rpx;          /* 38px */
		background-color: #F2F2F2;
		border: none;
		border-radius: 16rpx;   /* 8px */
		padding: 0 32rpx;
		width: 100%;
		box-sizing: border-box;
	}
	.search-icon-wrap {
		display: flex;
		align-items: center;
		justify-content: center;
		margin-right: 16rpx;
		color: $color-icon-line;
	}
	.search-input {
		flex: 1;
		font-size: 28rpx;   /* 14px 三级 */
		color: #4A4A4A;
		font-weight: 400;
	}
	.search-placeholder {
		color: #8E8E93;
	}
	.banner-section {
		width: 100%;
		margin-top: 48rpx;     /* 24px 上下呼吸感 */
		margin-bottom: 48rpx;
		border-radius: 12rpx;
		overflow: hidden;
	}
	.banner-swiper {
		height: 300rpx;
		border-radius: 12rpx;  /* 6px */
		overflow: hidden;
	}
	.banner-image {
		width: 100%;
		height: 100%;
		display: block;
	}

	/* ========== 金刚区：通栏白块 ========== */
	.menu-section {
		padding: 32rpx 32rpx;
	}
	.menu-grid {
		display: grid;
		grid-template-columns: repeat(4, 1fr);
		gap: 16rpx 48rpx;
	}
	.menu-item {
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
	}
	.menu-icon-wrap {
		width: 96rpx;
		height: 96rpx;
		border-radius: 50%;
		background-color: $icon-primary-bg-5;
		display: flex;
		align-items: center;
		justify-content: center;
		margin-bottom: 8rpx;
	}
	.menu-name {
		font-size: $font-size-body-rpx;
		font-weight: 400;
		color: #4A4A4A;
	}

	/* ========== 通知公告 / 今日课程：白块 + 1px 分割线 ========== */
	.notice-course-fullwidth {
		background-color: #FFFFFF;
		width: 100%;
		border-radius: 12rpx;
		border: none;
	}
	.notice-section .section-header-inner,
	.course-section .section-header-inner {
		display: flex;
		justify-content: space-between;
		align-items: center;
		padding: 28rpx 32rpx 20rpx;
	}
	.section-title-with-bar {
		display: flex;
		align-items: center;
		gap: 12rpx;
	}
	.section-title-bar {
		width: 8rpx;
		height: 28rpx;
		background-color: $color-primary;
		border-radius: 4rpx;
		flex-shrink: 0;
	}
	.section-title-inner {
		font-size: 32rpx;
		font-weight: 600;
		color: $color-text-title;
		text-align: left;
	}
	.notice-section .more,
	.course-section .more {
		font-size: $font-size-body-rpx;
		font-weight: 400;
		color: #4A4A4A;
	}
	.notice-list {
		display: flex;
		flex-direction: column;
		padding: 0 32rpx 24rpx;
	}
	.notice-item {
		position: relative;
		display: flex;
		align-items: center;
		height: 120rpx;
		padding: 0;
		box-sizing: border-box;
	}
	/* 1px 极细分割线，从标题文字处开始 */
	.notice-item + .notice-item::before {
		content: "";
		position: absolute;
		left: 140rpx;
		right: 32rpx;
		top: 0;
		height: 1px;
		background-color: #EEEEEE;
	}
	.notice-tag {
		font-size: 24rpx;
		padding: 4rpx 12rpx;
		border-radius: 4rpx;  /* 2px */
		margin-right: 20rpx;
		white-space: nowrap;
		flex-shrink: 0;
	}
	.notice-tag.jw {
		background-color: rgba(0, 122, 255, 0.05);
		color: #007AFF;
	}
	.notice-tag.tsg {
		background-color: rgba(52, 199, 89, 0.05);
		color: #34C759;
	}
	.notice-tag.xg {
		background-color: rgba(255, 149, 0, 0.05);
		color: #FF9500;
	}
	.notice-tag.xxh {
		background-color: rgba(88, 86, 214, 0.05);
		color: #5856D6;
	}
	.notice-title {
		flex: 1;
		font-size: 28rpx;   /* 14px 三级 Regular */
		font-weight: 400;
		color: #4A4A4A;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
		min-width: 0;
	}
	.notice-time {
		font-size: 24rpx;
		font-weight: 400;
		color: #8E8E93;
		margin-left: 16rpx;
		flex-shrink: 0;
	}

	/* ========== 今日课程：左时间右标题，状态 6px 圆点，压缩行高 ========== */
	.course-section .course-list {
		padding: 0 32rpx 24rpx;
	}
	.course-list {
		display: flex;
		flex-direction: column;
	}
	.course-item {
		position: relative;
		display: flex;
		align-items: center;
		height: 96rpx;
		padding: 0;
		box-sizing: border-box;
	}
	.course-item.finished .course-name,
	.course-item.finished .course-location,
	.course-item.finished .time-start,
	.course-item.finished .time-end,
	.course-item.finished .course-status-text {
		color: #C7C7CC;
	}
	.course-item + .course-item::before {
		content: "";
		position: absolute;
		left: 0;
		right: 0;
		top: 0;
		height: 1px;
		background-color: #EEEEEE;
	}
	.course-time {
		display: flex;
		flex-direction: column;
		align-items: flex-start;
		justify-content: center;
		margin-right: 32rpx;  /* 16px 固定间距 */
		padding-right: 0;
		flex-shrink: 0;
		min-width: 90rpx;
	}
	.course-time--ongoing .time-start,
	.course-time--ongoing .time-end {
		font-weight: 700;
		color: #5C7A99;
	}
	.time-start {
		font-size: 26rpx;
		font-weight: 600;
		color: #1D1D1F;
	}
	.time-end {
		font-size: 24rpx;
		font-weight: 400;
		color: #4A4A4A;
		margin-top: 2rpx;
	}
	.course-info {
		flex: 1;
		display: flex;
		flex-direction: column;
		justify-content: center;
		min-width: 0;
	}
	.course-row {
		display: flex;
		align-items: center;
		gap: 12rpx;
		margin-bottom: 4rpx;
	}
	.course-name {
		font-size: $font-size-body-rpx;
		font-weight: 400;
		color: #4A4A4A;
		flex: 1;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.course-status-text {
		font-size: 24rpx;
		font-weight: 400;
		flex-shrink: 0;
		display: inline-flex;
		align-items: center;
		gap: 8rpx;
	}
	.course-status-dot {
		display: inline-block;
		width: 12rpx;   /* 6px */
		height: 12rpx;
		border-radius: 50%;
		flex-shrink: 0;
	}
	.course-status-text.finished .course-status-dot {
		background-color: #C7C7CC;
	}
	.course-status-text.ongoing .course-status-dot {
		background-color: #5C7A99;
	}
	.course-status-text.upcoming .course-status-dot {
		background-color: #34C759;
	}
	.course-status-text.finished {
		color: #C7C7CC;
	}
	.course-status-text.ongoing {
		color: #5C7A99;
	}
	.course-status-text.upcoming {
		color: #34C759;
	}
	.course-location {
		font-size: 22rpx;
		font-weight: 400;
		color: #4A4A4A;
	}
	.empty-tip {
		text-align: center;
		padding: 48rpx;
		color: #4A4A4A;
		font-size: $font-size-body-rpx;
		font-weight: 400;
	}

	.section-header {
		display: flex;
		justify-content: space-between;
		align-items: center;
		padding: 32rpx 32rpx 24rpx 0;
		margin-bottom: 0;
	}
	.section-header .section-title-with-bar {
		display: flex;
		align-items: center;
		gap: 12rpx;
	}
	.section-header .section-title-bar {
		width: 8rpx;
		height: 28rpx;
		background-color: $color-primary;
		border-radius: 4rpx;
		flex-shrink: 0;
	}
	.section-title {
		font-size: 32rpx;
		font-weight: 600;
		color: #1D1D1F;
	}

	/* ========== 校园服务：通栏白块 ========== */
	.service-section {
		padding: 32rpx 32rpx;
	}
	.service-grid {
		display: grid;
		grid-template-columns: repeat(4, 1fr);
		gap: 48rpx;
	}
	.service-item {
		display: flex;
		flex-direction: column;
		align-items: center;
		padding: 24rpx 0;
	}
	.service-icon-wrap {
		display: flex;
		align-items: center;
		justify-content: center;
		margin-bottom: $spacing-sm-rpx;
	}
	.service-name {
		font-size: $font-size-body-rpx;
		font-weight: 400;
		color: #3A3A3C;
	}
</style>
