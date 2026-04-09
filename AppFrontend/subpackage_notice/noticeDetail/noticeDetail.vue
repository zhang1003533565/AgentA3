<template>
	<view class="notice-detail-container">
		<nav-bar title="公告详情" :showBack="true" fixed placeholder />
		
		<view class="detail-content" v-if="notice.id">
			<view class="article-header">
				<text class="article-title">{{ notice.title }}</text>
				<view class="article-meta">
					<text class="article-tag">公告</text>
					<text class="article-time">{{ notice.createTime }}</text>
				</view>
			</view>
			
			<view class="article-body">
				<text class="article-text">{{ notice.content }}</text>
			</view>
		</view>
		
		<view class="loading-state" v-else>
			<text class="loading-text">加载中...</text>
		</view>
	</view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getAnnouncementById } from '@/api/notice.js'

export default {
	components: { NavBar },
	data() {
		return {
			notice: {
				id: null,
				title: '',
				content: '',
				createTime: ''
			}
		}
	},
	onLoad(options) {
		if (options.id) {
			this.fetchNoticeDetail(options.id)
		} else {
			uni.showToast({ title: '公告ID不存在', icon: 'none' })
		}
	},
	methods: {
		async fetchNoticeDetail(id) {
			try {
				const res = await getAnnouncementById(id)
				if (res.code === 200 && res.data) {
					this.notice = res.data
				} else {
					uni.showToast({ title: '公告不存在', icon: 'none' })
				}
			} catch (err) {
				console.error('获取公告详情失败:', err)
				uni.showToast({ title: '获取公告详情失败', icon: 'none' })
			}
		}
	}
}
</script>

<style lang="scss">
.notice-detail-container {
	min-height: 100vh;
	background-color: #FFFFFF;
}

.detail-content {
	padding: 40rpx;
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

.article-tag {
	font-size: 26rpx;
	color: #62b6b3;
	font-weight: 500;
}

.article-time {
	font-size: 26rpx;
	color: #B2B2B2;
}

.article-body {
	margin-top: 40rpx;
}

.article-text {
	font-size: 32rpx;
	color: #3A3A3C;
	line-height: 1.8;
	white-space: pre-wrap;
}

.loading-state {
	display: flex;
	justify-content: center;
	align-items: center;
	min-height: 400rpx;
}

.loading-text {
	font-size: 28rpx;
	color: #999;
}
</style>
