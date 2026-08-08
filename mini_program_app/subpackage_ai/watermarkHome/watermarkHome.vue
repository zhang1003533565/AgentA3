<template>
	<view class="page-wrap">
		<!-- 替换为全局导航组件，和使用帮助头部样式完全一致 -->
		<nav-bar title="水印工具" :showBack="true"></nav-bar>

		<scroll-view class="scroll-wrap" scroll-y>
			<view class="page-body">
				<!-- 两大功能卡片 -->
				<view class="card-row">
					<view class="func-card" @click="goWatermarkEdit">
						<view class="card-icon-wrap">
							<text class="card-icon">🖼</text>
						</view>
						<view class="card-title">图片加水印</view>
						<view class="card-desc">上传图片，添加多类型水印并导出</view>
						<view class="card-btn">立即制作</view>
					</view>
					<view class="func-card" @click="goRemoveEdit">
						<view class="card-icon-wrap">
							<text class="card-icon">🧹</text>
						</view>
						<view class="card-title">图片去水印</view>
						<view class="card-desc">标记水印区域，智能修复并导出</view>
						<view class="card-btn">立即制作</view>
					</view>
				</view>

				<!-- 常用操作区域 -->
				<view class="operate-block">
					<view class="block-title">常用操作</view>
					<view class="operate-row">
						<view class="operate-item" @click="goHistory">
							<text class="operate-icon">🕒</text>
							<text class="operate-text">历史记录</text>
						</view>
						<view class="operate-item" @click="goBatch">
							<text class="operate-icon">📚</text>
							<text class="operate-text">批量处理</text>
						</view>
						<view class="operate-item" @click="goHelp">
							<text class="operate-icon">❓</text>
							<text class="operate-text">使用帮助</text>
						</view>
					</view>
				</view>
			</view>
		</scroll-view>
	</view>
</template>

<script>
export default {
	data() {
		return {}
	},
	methods: {
		goBack() {
			uni.navigateBack()
		},
		// 跳转到：图片加水印编辑页
		goWatermarkEdit() {
			uni.navigateTo({
				url:"/subpackage_ai/watermarkAddEdit/watermarkAddEdit"
			})
		},
		// 跳转到：图片去水印编辑页
		goRemoveEdit() {
			uni.navigateTo({
				url:"/subpackage_ai/watermarkRemoveEdit/watermarkRemoveEdit"
			})
		},
		// 历史记录
		goHistory() {
			uni.navigateTo({
				url:"/subpackage_ai/watermarkHistory/watermarkHistory"
			})
		},
		// 批量处理
		goBatch() {
			uni.navigateTo({
				url:"/subpackage_ai/watermarkBatch/watermarkBatch"
			})
		},
		// 使用帮助
		goHelp() {
			uni.navigateTo({
				url:"/subpackage_ai/watermarkHelp/watermarkHelp"
			})
		}
	}
}
</script>

<style scoped>
.page-wrap {
	height: 100vh;
	display: flex;
	flex-direction: column;
	/* 和使用帮助页面背景色完全统一 */
	background-color: #f5f7fa;
}
.scroll-wrap {
	flex: 1;
}
.page-body{
	padding:30rpx;
}
.card-row{
	display:flex;
	gap:24rpx;
}
.func-card{
	flex:1;
	border:1rpx solid #edf2f7;
	/* 和帮助页卡片圆角统一16rpx */
	border-radius:16rpx;
	padding:32rpx;
	min-height:420rpx;
	display:flex;
	flex-direction:column;
	background:#ffffff;
}
.card-icon-wrap{
	width:80rpx;
	height:80rpx;
	background:#f7f8fa;
	border-radius:20rpx;
	display:flex;
	align-items:center;
	justify-content:center;
}
.card-icon{
	font-size:40rpx;
}
.card-title{
	font-size:28rpx;
	font-weight:600;
	margin-top:32rpx;
	color:#1a202c;
}
.card-desc{
	font-size:22rpx;
	color:#718096;
	line-height:1.5;
	margin-top:12rpx;
}
.card-btn{
	margin-top:auto;
	text-align:center;
	background:#1a202c;
	color:#ffffff;
	font-size:24rpx;
	border-radius:20rpx;
	padding:18rpx 0;
}
.operate-block{
	margin-top:64rpx;
}
.block-title{
	font-size:28rpx;
	font-weight:600;
	color:#1a202c;
}
.operate-row{
	display:flex;
	gap:20rpx;
	margin-top:28rpx;
}
.operate-item{
	flex:1;
	aspect-ratio:1/1;
	background:#ffffff;
	border-radius:16rpx;
	display:flex;
	flex-direction:column;
	align-items:center;
	justify-content:center;
}
.operate-icon{
	font-size:40rpx;
}
.operate-text{
	font-size:22rpx;
	color:#2d3748;
	margin-top:12rpx;
}
</style>