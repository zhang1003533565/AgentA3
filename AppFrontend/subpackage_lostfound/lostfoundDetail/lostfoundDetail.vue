<template>
	<view class="lostfound-detail-container">
		<nav-bar title="信息详情" fixed placeholder />
		
		<view class="detail-content">
			<image :src="item.image" mode="aspectFill" class="detail-img" />
			
			<view class="article-header">
				<text class="article-title">{{ item.title }}</text>
				<view class="article-meta">
					<text class="article-type" :class="item.type">{{ item.type === 'lost' ? '寻物启事' : '招领信息' }}</text>
					<text class="article-time">{{ item.date }} 发布</text>
				</view>
			</view>
			
			<view class="article-body">
				<view class="info-group block-white">
					<view class="info-item">
						<text class="label">地点</text>
						<text class="value">{{ item.location }}</text>
					</view>
					<view class="info-item">
						<text class="label">联系人</text>
						<text class="value">{{ item.contactName }}</text>
					</view>
				</view>
				
				<view class="description-section">
					<text class="section-title">详细描述</text>
					<text class="article-text">{{ item.desc }}</text>
				</view>
			</view>
			
			<!-- 底部链接卡片：模仿学习通风格 -->
			<view class="link-card" @click="handleContact">
				<view class="link-icon" :class="item.type">
					<icon-line name="message-circle" size="service" color="#FFFFFF" />
				</view>
				<view class="link-info">
					<text class="link-title">联系发布者: {{ item.contactName }}</text>
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
				item: {
					id: null,
					title: '',
					type: '',
					date: '',
					location: '',
					contactName: '',
					desc: '',
					image: ''
				}
			}
		},
		onLoad(options) {
			if (options.id) {
				this.loadDetail(options.id)
			}
		},
		methods: {
			loadDetail(id) {
				const mockData = {
					'1': { id: 1, title: '蓝色小钱包', type: 'found', date: '刚刚', location: '一食堂二楼', contactName: '王同学', desc: '在二楼靠窗的桌子上捡到一个蓝色钱包，里面有少量现金和一张校园卡，请失主联系我。', image: 'https://picsum.photos/400/300?random=11' },
					'2': { id: 2, title: '校园卡-张三', type: 'found', date: '10分钟前', location: '图书馆5楼', contactName: '李同学', desc: '图书馆5楼阅览室捡到一张校园卡，姓名张三，学号2021xxxx。', image: 'https://picsum.photos/400/300?random=12' },
					'3': { id: 3, title: '丢失黑色雨伞', type: 'lost', date: '1小时前', location: '教2-101', contactName: '赵同学', desc: '今天下午第一节课在教2-101上课，走的时候忘拿雨伞了，黑色天堂伞，伞柄有划痕，看到的同学麻烦联系我，谢谢！', image: 'https://picsum.photos/400/300?random=13' }
				}
				if (mockData[id]) {
					this.item = mockData[id]
				}
			},
			handleContact() {
				uni.showModal({
					title: '联系方式',
					content: '联系电话：138****8888',
					showCancel: false
				})
			}
		}
	}
</script>

<style lang="scss">
	.lostfound-detail-container {
		min-height: 100vh;
		background-color: #FFFFFF;
	}

	.detail-content {
		padding: 40rpx 40rpx;
	}

	.detail-img {
		width: 100%;
		height: 400rpx;
		border-radius: 20rpx;
		margin-bottom: 40rpx;
		background-color: #F2F2F7;
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
		&.lost { color: #FF3B30; }
		&.found { color: #34C759; }
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
		background-color: #F8F9FB;
		
		.info-item {
			display: flex;
			justify-content: space-between;
			padding: 16rpx 0;
			border-bottom: 1rpx solid #E5E5EA;
			
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
			line-height: 1.8;
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
			border-radius: 12rpx;
			display: flex;
			align-items: center;
			justify-content: center;
			margin-right: 20rpx;
			flex-shrink: 0;
			
			&.lost { background-color: #FF3B30; }
			&.found { background-color: #34C759; }
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
