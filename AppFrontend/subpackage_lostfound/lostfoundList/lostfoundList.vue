<template>
	<view class="lostfound-container">
		<nav-bar title="失物招领" />
		
		<view class="search-section" :style="{ top: navBarHeight + 'px' }">
			<view class="search-bar">
				<icon-line name="search" size="small" color="#8E8E93" />
				<input type="text" v-model="searchQuery" placeholder="搜索失物或招领信息" class="search-input" />
			</view>
			<view class="tabs">
				<view class="tab-item" :class="{ active: currentTab === 'lost' }" @click="currentTab = 'lost'">寻物启事</view>
				<view class="tab-item" :class="{ active: currentTab === 'found' }" @click="currentTab = 'found'">招领信息</view>
			</view>
		</view>

		<scroll-view scroll-y class="list-content">
			<view class="grid-list">
				<view class="item-card block-white" v-for="(item, index) in filteredList" :key="index" @click="goToDetail(item)">
					<image :src="item.image || 'https://picsum.photos/200/200?random=' + index" mode="aspectFill" class="item-img" />
					<view class="item-info">
						<text class="item-title">{{ item.title }}</text>
						<view class="item-meta">
							<text class="item-date">{{ item.date }}</text>
							<view class="item-tag" :class="item.type">{{ item.type === 'lost' ? '寻物' : '招领' }}</view>
						</view>
						<view class="item-loc">
							<icon-line name="map" size="small" color="#8E8E93" />
							<text class="loc-text">{{ item.location }}</text>
						</view>
					</view>
				</view>
			</view>
			
			<view v-if="filteredList.length === 0" class="empty-state">
				<text class="empty-text">暂无相关信息</text>
			</view>
			<view class="safe-area-inset-bottom"></view>
		</scroll-view>

		<view class="fab-btn" @click="handlePublish">
			<icon-line name="edit-3" size="large" color="#FFFFFF" class="fab-icon" />
			<text class="fab-text">发布</text>
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
				currentTab: 'found',
				searchQuery: '',
				items: [
					{ id: 1, title: '蓝色小钱包', date: '刚刚', location: '一食堂二楼', type: 'found', image: 'https://picsum.photos/200/200?random=11' },
					{ id: 2, title: '校园卡-张三', date: '10分钟前', location: '图书馆5楼', type: 'found', image: 'https://picsum.photos/200/200?random=12' },
					{ id: 3, title: '丢失黑色雨伞', date: '1小时前', location: '教2-101', type: 'lost', image: 'https://picsum.photos/200/200?random=13' },
					{ id: 4, title: '寻找白色耳机仓', date: '2小时前', location: '操场主席台', type: 'lost', image: 'https://picsum.photos/200/200?random=14' },
					{ id: 5, title: '一串钥匙', date: '昨天', location: '校门口', type: 'found', image: 'https://picsum.photos/200/200?random=15' }
				]
			}
		},
		computed: {
			filteredList() {
				return this.items.filter(item => {
					const matchTab = item.type === this.currentTab
					const matchSearch = item.title.includes(this.searchQuery)
					return matchTab && matchSearch
				})
			}
		},
		onLoad() {
			const sys = uni.getSystemInfoSync()
			this.navBarHeight = sys.statusBarHeight + 44
		},
		methods: {
			goToDetail(item) {
				uni.navigateTo({
					url: `/subpackage_lostfound/lostfoundDetail/lostfoundDetail?id=${item.id}`
				})
			},
			handlePublish() {
				uni.navigateTo({
					url: `/subpackage_lostfound/lostfoundPublish/lostfoundPublish?type=${this.currentTab}`
				})
			}
		}
	}
</script>

<style lang="scss">
	.lostfound-container {
		min-height: 100vh;
		background-color: #F7F7F9;
		display: flex;
		flex-direction: column;
	}

	.search-section {
		background-color: #FFFFFF;
		padding: 20rpx 32rpx;
		box-shadow: 0 2rpx 10rpx rgba(0,0,0,0.05);
		flex-shrink: 0;
	}

	.search-bar {
		display: flex;
		align-items: center;
		background-color: #F2F2F7;
		height: 72rpx;
		border-radius: 36rpx;
		padding: 0 24rpx;
		margin-bottom: 20rpx;
		
		.search-input {
			flex: 1;
			margin-left: 12rpx;
			font-size: 28rpx;
		}
	}

	.tabs {
		display: flex;
		justify-content: space-around;
		
		.tab-item {
			font-size: 30rpx;
			color: #8E8E93;
			padding: 16rpx 0;
			position: relative;
			
			&.active {
				color: $color-primary;
				font-weight: 600;
				
				&::after {
					content: '';
					position: absolute;
					bottom: 0;
					left: 50%;
					transform: translateX(-50%);
					width: 40rpx;
					height: 4rpx;
					background-color: $color-primary;
					border-radius: 2rpx;
				}
			}
		}
	}

	.list-content {
		flex: 1;
		height: 0;
	}

	.grid-list {
		padding: 20rpx;
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: 20rpx;
	}

	.item-card {
		border-radius: 20rpx;
		overflow: hidden;
		display: flex;
		flex-direction: column;
		
		.item-img {
			width: 100%;
			height: 300rpx;
			background-color: #EEEEEE;
		}
		
		.item-info {
			padding: 16rpx;
			
			.item-title {
				font-size: 28rpx;
				font-weight: 600;
				color: #1D1D1F;
				margin-bottom: 8rpx;
				display: -webkit-box;
				-webkit-box-orient: vertical;
				-webkit-line-clamp: 1;
				line-clamp: 1;
				overflow: hidden;
			}
			
			.item-meta {
				display: flex;
				justify-content: space-between;
				align-items: center;
				margin-bottom: 8rpx;
				
				.item-date {
					font-size: 22rpx;
					color: #8E8E93;
				}
				
				.item-tag {
					font-size: 20rpx;
					padding: 2rpx 10rpx;
					border-radius: 4rpx;
					
					&.lost { background-color: rgba(255, 59, 48, 0.1); color: #FF3B30; }
					&.found { background-color: rgba(52, 199, 89, 0.1); color: #34C759; }
				}
			}
			
			.item-loc {
				display: flex;
				align-items: center;
				gap: 4rpx;
				
				.loc-text {
					font-size: 22rpx;
					color: #8E8E93;
					overflow: hidden;
					text-overflow: ellipsis;
					white-space: nowrap;
				}
			}
		}
	}

	.empty-state {
		padding-top: 200rpx;
		text-align: center;
		color: #8E8E93;
		font-size: 28rpx;
	}

	.fab-btn {
		position: fixed;
		right: 40rpx;
		bottom: calc(80rpx + var(--window-bottom));
		height: 96rpx;
		padding: 0 36rpx;
		background: linear-gradient(135deg, #89B0E0 0%, #7AA1D2 100%);
		border-radius: 48rpx;
		border: 2rpx solid rgba(255, 255, 255, 0.4);
		display: flex;
		align-items: center;
		justify-content: center;
		box-shadow: 0 12rpx 24rpx rgba(122, 161, 210, 0.3);
		z-index: 100;
		backdrop-filter: blur(10px);
		transition: all 0.2s ease;
		
		&:active {
			transform: scale(0.94);
			box-shadow: 0 4rpx 12rpx rgba(122, 161, 210, 0.2);
		}
	}

	.fab-icon {
		margin-right: 12rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		transform: translateY(-2rpx);
	}

	.fab-text {
		font-size: 30rpx;
		font-weight: 600;
		color: #FFFFFF;
		letter-spacing: 0.02em;
		line-height: 1;
		display: flex;
		align-items: center;
	}
</style>
