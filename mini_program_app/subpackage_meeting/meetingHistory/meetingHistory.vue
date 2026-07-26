<template>
	<view class="history-page">
		<view class="status-bar"></view>
		<view class="top-bar">
			<view class="back" @click="back">‹</view>
			<text class="nav-title">历史会议</text>
			<view></view>
		</view>
		<view class="page-body">
			<view class="search-wrap">
				<view class="search-input">
					<text class="search-icon">🔍</text>
					<input v-model="keyword" placeholder="会议名称、会议备注、会议号、主持人" @input="handleSearch" />
				</view>
			</view>

			<scroll-view scroll-y class="list-scroll">
				<view v-if="loading" class="empty-tip">加载中...</view>
				<view v-else-if="filterGroupData.length === 0" class="empty-tip">{{ keyword ? '未找到匹配的会议' : '暂无历史会议' }}</view>
				<view v-else class="group-wrap">
					<view v-for="group in filterGroupData" :key="group.dateLabel" class="date-group">
						<view class="date-title">{{ group.dateLabel }}</view>
						<view class="item-wrap">
							<view
								v-for="item in group.list"
								:key="item.sessionId"
								class="history-item"
								@click="goDetail(item)"
							>
								<view class="item-left">
									<text class="room-code">{{ formatRoomCode(item.roomCode) }}</text>
									<text class="meeting-title">{{ item.title }}</text>
									<!-- 完全复用详情页规则：优先取participants[0]，兜底兼容hostName，全部没有显示未知 -->
									<text class="sub-info">{{ item.showTime }} · 主持人：{{ getHostName(item) }}</text>
								</view>
								<text class="arrow">></text>
							</view>
						</view>
					</view>
				</view>
			</scroll-view>
		</view>
	</view>
</template>

<script>
import { getMeetings } from '@/api/ai.js'
export default {
	data() {
		return {
			keyword: '',
			loading: false,
			originHistoryList: [] // 原始全部已结束会议，用于搜索过滤
		}
	},
	onShow() {
		this.loadHistoryData()
	},
	computed: {
		// 原始分组（不参与渲染，只用来过滤）
		groupData() {
			const map = {}
			this.originHistoryList.forEach(row => {
				let dateStr = ''
				if (row.createTime) {
					const date = new Date(row.createTime)
					dateStr = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
				} else {
					dateStr = '未知日期'
				}
				if (!map[dateStr]) {
					map[dateStr] = { dateLabel: dateStr, list: [] }
				}
				map[dateStr].list.push(row)
			})
			// 日期倒序，最新日期置顶
			return Object.values(map).sort((a, b) => new Date(b.dateLabel) - new Date(a.dateLabel))
		},
		// 【重点】经过关键词筛选后的分组，页面实际渲染
		filterGroupData() {
			const key = this.keyword.trim().toLowerCase()
			if (!key) return this.groupData

			// 遍历日期分组，过滤每条会议
			const result = []
			this.groupData.forEach(g => {
				const filterList = g.list.filter(item => {
					const hostName = this.getHostName(item).toLowerCase()
					const title = (item.title || '').toLowerCase()
					const roomCode = (item.roomCode || '').toLowerCase()
					const remark = (item.remark || '').toLowerCase()
					return title.includes(key) || roomCode.includes(key) || hostName.includes(key) || remark.includes(key)
				})
				if (filterList.length > 0) {
					result.push({
						dateLabel: g.dateLabel,
						list: filterList
					})
				}
			})
			return result
		}
	},
	methods: {
		// 【核心】完全对齐meetingDetail主持人获取逻辑
		getHostName(item) {
			// 如果存在participants数组，取第一个，和详情页保持一模一样
			if (Array.isArray(item.participants) && item.participants.length > 0) {
				return item.participants[0]
			}
			// 兜底兼容旧字段
			if (item.hostName) return item.hostName
			return '未知'
		},
		async loadHistoryData() {
			this.loading = true
			this.keyword = '' // 刷新数据清空搜索
			try {
				const res = await getMeetings({ pageNum: 1, pageSize: 99 })
				const all = res?.data?.records || []
				// 只保存已结束会议作为原始数据源
				this.originHistoryList = all.filter(m => m.status === 'ended')
			} catch (e) {
				this.originHistoryList = []
			} finally {
				this.loading = false
			}
		},
		// 输入实时触发搜索
		handleSearch() {
			// filterGroupData 是计算属性，自动响应keyword变化，无需额外赋值
		},
		goDetail(item) {
			// 和详情页接收参数统一
			uni.navigateTo({
				url: `/subpackage_meeting/meetingDetail/meetingDetail?sessionId=${encodeURIComponent(item.sessionId)}&title=${encodeURIComponent(item.title)}&roomCode=${encodeURIComponent(item.roomCode)}`
			})
		},
		formatRoomCode(code) {
			if (!code) return ''
			return code.replace(/(.{3})/g, '$1 ').trim()
		},
		back() {
			uni.navigateBack()
		}
	}
}
</script>

<style lang="scss" scoped>
$main-color: #86C9A8;
$text-gray: #68747a;
.history-page {
	min-height: 100vh;
	background: #f5f7fa;
	padding: 0rpx;
}
.status-bar {
	height: var(--status-bar-height);
	min-height: 42rpx;
}
.top-bar {
	height: 88rpx;
	display: grid;
	grid-template-columns: 70rpx 1fr 70rpx;
	align-items: center;
	padding: 0 24rpx;
}
.back {
	font-size: 48rpx;
	line-height: 1;
}
.nav-title {
	text-align: center;
	font-size: 29rpx;
	font-weight: 850;
}
.page-body {
	padding: 0 28rpx;
}
.search-wrap {
	margin: 24rpx 0;
}
.search-input {
	height: 76rpx;
	background: #fff;
	border-radius: 16rpx;
	display: flex;
	align-items: center;
	padding: 0 24rpx;
	gap: 16rpx;
	font-size: 24rpx;
	color: #999;
}
.list-scroll {
	height: calc(100vh - 240rpx);
}
.empty-tip {
	text-align: center;
	margin-top: 120rpx;
	color: #999;
	font-size: 26rpx;
}
.date-group {
	margin-bottom: 32rpx;
}
.date-title {
	font-size: 26rpx;
	color: #333;
	margin-bottom: 16rpx;
}
.history-item {
	background: #fff;
	border-radius: 16rpx;
	padding: 24rpx;
	display: flex;
	justify-content: space-between;
	align-items: center;
	margin-bottom: 12rpx;
}
.room-code {
	font-size: 24rpx;
	color: $text-gray;
	display: block;
	margin-bottom: 8rpx;
}
.meeting-title {
	font-size: 28rpx;
	font-weight: bold;
	color: #111;
	display: block;
	margin-bottom: 8rpx;
}
.sub-info {
	font-size: 22rpx;
	color: $text-gray;
}
.arrow {
	font-size: 32rpx;
	color: #bbb;
}
</style>
