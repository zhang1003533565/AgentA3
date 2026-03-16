<template>
	<view class="page-wrap">
		<nav-bar title="我的活动" :showBack="false" />
		<!-- 分类 Tab：待参加、报名记录、已结束，选中蓝字+蓝下划线 -->
		<view class="tabs-bar">
			<view
				v-for="(tab, index) in tabs"
				:key="index"
				class="tabs-item"
				:class="{ active: currentTab === index }"
				@click="switchTab(index)"
			>
				<text class="tabs-text">{{ tab }}</text>
			</view>
		</view>
		<!-- 活动列表 -->
		<scroll-view class="activity-scroll" scroll-y>
			<view class="activity-list">
				<view
					v-for="(item, index) in currentList"
					:key="item.id || index"
					class="activity-card"
				>
					<view class="card-main">
						<view class="card-cover" :style="{ backgroundImage: 'url(' + (item.coverImage || item.image) + ')' }"></view>
						<view class="card-info">
							<text class="card-title">{{ item.title }}</text>
							<view class="card-meta">
								<text class="meta-text">🕐 {{ item.time }}</text>
							</view>
							<view class="card-meta">
								<text class="meta-text">📍 {{ item.location }}</text>
							</view>
						</view>
					</view>
					<view class="card-footer">
						<view class="status-tag" :class="item.statusClass">{{ item.statusText }}</view>
						<view class="action-btn" :class="{ disabled: item.actionDisabled }" @click.stop="onAction(item)">
							<text class="action-icon">{{ item.actionIcon }}</text>
							<text class="action-label">{{ item.actionLabel }}</text>
						</view>
					</view>
				</view>
				<view v-if="!(currentList && currentList.length)" class="empty-tip">
					<text>暂无{{ tabs[currentTab] }}活动</text>
				</view>
			</view>
		</scroll-view>
		<custom-tab-bar current="activity" />
	</view>
</template>

<script>
	import CustomTabBar from '@/components/custom-tab-bar/custom-tab-bar.vue'
	import NavBar from '@/components/nav-bar/nav-bar.vue'
	export default {
		components: { CustomTabBar, NavBar },
		data() {
			return {
				tabs: ['待参加', '报名记录', '已结束'],
				currentTab: 0,
				// 待参加
				listToJoin: [
					{
						id: 1,
						title: '校招宣讲会：科技行业未来展望',
						time: '2023-10-25 14:00',
						location: '校本部大礼堂',
						coverImage: 'https://picsum.photos/seed/lecture/320/180',
						statusText: '进行中',
						statusClass: 'status-ongoing',
						actionLabel: '签到',
						actionIcon: '📷',
						actionDisabled: false
					},
					{
						id: 2,
						title: '2023 校园秋季马拉松',
						time: '2023-11-01 08:00',
						location: '西区田径场',
						coverImage: 'https://picsum.photos/seed/marathon/320/180',
						statusText: '待参加',
						statusClass: 'status-pending',
						actionLabel: '未开始',
						actionIcon: '📷',
						actionDisabled: true
					},
					{
						id: 3,
						title: '创新创业设计工作坊',
						time: '2023-11-05 19:00',
						location: '科教楼302教室',
						coverImage: 'https://picsum.photos/seed/workshop/320/180',
						statusText: '待参加',
						statusClass: 'status-pending',
						actionLabel: '位置签到',
						actionIcon: '📍',
						actionDisabled: false
					}
				],
				listRegistered: [],
				listEnded: []
			}
		},
		computed: {
			currentList() {
				if (this.currentTab === 0) return this.listToJoin
				if (this.currentTab === 1) return this.listRegistered
				return this.listEnded
			}
		},
		methods: {
			switchTab(index) {
				this.currentTab = index
			},
			onAction(item) {
				if (item.actionDisabled) return
				uni.showToast({ title: item.actionLabel, icon: 'none' })
			}
		}
	}
</script>

<style lang="scss" scoped>
	.page-wrap {
		min-height: 100vh;
		background-color: #F7F7F9;
		padding-bottom: 120rpx;
		display: flex;
		flex-direction: column;
	}
	/* 分类 Tab：选中蓝字+蓝下划线，未选中灰色，文字上下居中 */
	.tabs-bar {
		display: flex;
		align-items: stretch;
		background-color: #FFFFFF;
		border-bottom: 1px solid #E2E8F0;
		padding: 0 32rpx;
	}
	.tabs-item {
		flex: 1;
		display: flex;
		align-items: center;
		justify-content: center;
		height: 88rpx;
		border-bottom: 3px solid transparent;
		box-sizing: border-box;
	}
	.tabs-item.active {
		border-bottom-color: $color-primary;
	}
	.tabs-text {
		font-size: 28rpx;
		font-weight: 700;
		color: #8E8E93;
		line-height: 88rpx;
	}
	.tabs-item.active .tabs-text {
		color: $color-primary;
	}
	.activity-scroll {
		flex: 1;
		height: 0;
	}
	.activity-list {
		padding: 24rpx;
		display: flex;
		flex-direction: column;
		gap: 24rpx;
	}
	.activity-card {
		background-color: #FFFFFF;
		border-radius: 24rpx;
		padding: 32rpx;
		border: none;
	}
	.card-main {
		display: flex;
		align-items: center;
		gap: 32rpx;
	}
	.card-cover {
		width: 128rpx;
		height: 128rpx;
		border-radius: 16rpx;
		background-size: cover;
		background-position: center;
		background-repeat: no-repeat;
		flex-shrink: 0;
	}
	.card-info {
		flex: 1;
		min-width: 0;
	}
	.card-title {
		font-size: 32rpx;
		font-weight: 700;
		color: #1D1D1F;
		line-height: 1.4;
		display: block;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.card-meta {
		margin-top: 12rpx;
	}
	.meta-text {
		font-size: 24rpx;
		color: #8E8E93;
	}
	.card-footer {
		display: flex;
		align-items: center;
		justify-content: space-between;
		margin-top: 24rpx;
		padding-top: 24rpx;
		border-top: 1px solid #EEEEEE;
	}
	.status-tag {
		padding: 8rpx 16rpx;
		border-radius: 8rpx;
		font-size: 24rpx;
		font-weight: 500;
	}
	.status-tag.status-ongoing {
		background-color: rgba(92, 122, 153, 0.15);
		color: $color-primary;
	}
	.status-tag.status-pending {
		background-color: rgba(142, 142, 147, 0.12);
		color: #1D1D1F;
	}
	.action-btn {
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 8rpx;
		min-width: 168rpx;
		height: 72rpx;
		padding: 0 32rpx;
		border-radius: 16rpx;
		background-color: $color-primary;
	}
	.action-btn .action-icon {
		font-size: 32rpx;
	}
	.action-btn .action-label {
		font-size: 28rpx;
		font-weight: 700;
		color: #FFFFFF;
	}
	.action-btn.disabled {
		background-color: #F2F2F2;
	}
	.action-btn.disabled .action-label {
		color: #8E8E93;
	}
	.empty-tip {
		text-align: center;
		padding: 80rpx 0;
		color: #8E8E93;
		font-size: 28rpx;
	}
</style>
