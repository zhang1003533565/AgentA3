<template>
	<view class="page">
		<!--统一导航栏，和主页风格保持一致-->
		<nav-bar title="去水印编辑" :showBack="true"></nav-bar>

		<scroll-view scroll-y class="scroll-wrap">
			<!--预览涂抹区域-->
			<view class="preview-box">
				<text class="preview-icon">🖼</text>
				<text class="preview-tip">涂抹需要修复的区域</text>
			</view>

			<!--修复工具面板-->
			<view class="tool-panel">
				<view class="tool-panel-head">
					<text class="tool-title">修复工具</text>
					<text class="tool-mode">画笔模式</text>
				</view>
				<view class="tool-btn-row">
					<view class="tool-btn" :class="{active:curTool==='brush'}" @click="curTool='brush'">
						<text class="tool-icon">🖌</text>
						<text class="tool-text">涂抹画笔</text>
					</view>
					<view class="tool-btn" @click="handleUndo">
						<text class="tool-icon">↩</text>
						<text class="tool-text">撤销</text>
					</view>
					<view class="tool-btn" @click="handleReset">
						<text class="tool-icon">🔄</text>
						<text class="tool-text">重置图片</text>
					</view>
				</view>
				<view class="slider-item">
					<view class="label-row">
						<text class="label">画笔大小</text>
						<text class="value-text">{{brushSize}} px</text>
					</view>
					<slider :value="brushSize" min="5" max="80" @change="onBrushSizeChange"/>
				</view>
			</view>

			<!--切换批量处理按钮-->
			<view class="switch-batch-btn" @click="goBatchPage">切换批量处理</view>

			<!--导出设置-->
			<view class="export-block">
				<text class="export-title">导出设置</text>
				<view class="export-row">
					<picker mode="selector" :value="compressIndex" :range="compressList" @change="onCompressChange">
						<view class="export-picker">{{compressList[compressIndex]}}</view>
					</picker>
					<picker mode="selector" :value="formatIndex" :range="formatList" @change="onFormatChange">
						<view class="export-picker">{{formatList[formatIndex]}}</view>
					</picker>
					<button class="save-btn">下载保存</button>
				</view>
			</view>
		</scroll-view>
	</view>
</template>

<script>
export default {
	data() {
		return {
			curTool:"brush",
			brushSize:20,
			compressList:["低压缩","中压缩","高压缩"],
			compressIndex:1,
			formatList:["JPG","PNG","WebP"],
			formatIndex:0
		}
	},
	methods:{
		goBack(){
			uni.navigateBack()
		},
		onBrushSizeChange(e){
			this.brushSize = e.detail.value
		},
		onCompressChange(e){
			this.compressIndex = e.detail.value
		},
		onFormatChange(e){
			this.formatIndex = e.detail.value
		},
		handleUndo(){
			uni.showToast({title:"撤销",icon:"none"})
		},
		handleReset(){
			uni.showToast({title:"重置图片",icon:"none"})
		},
		goBatchPage(){
			uni.navigateTo({
				url:"/subpackage_ai/watermarkBatch/watermarkBatch"
			})
		}
	}
}
</script>

<style scoped>
page {
	margin: 0;
	padding: 0;
	width: 100%;
	height: 100%;
	overflow: hidden;
	background-color:#f1f5f9;
}
.page {
	width: 100%;
	height: 100vh;
	display: flex;
	flex-direction: column;
}
.scroll-wrap {
	flex: 1;
	padding:40rpx;
	overflow-y: auto;
	box-sizing: border-box;
}

.preview-box{
	height:240rpx;
	background:#f1f5f9;
	border-radius:40rpx;
	display:flex;
	flex-direction:column;
	align-items:center;
	justify-content:center;
	color:#94a3b8;
}
.preview-icon{
	font-size:80rpx;
}
.preview-tip{
	font-size:24rpx;
	margin-top:12rpx;
}
.tool-panel{
	margin-top:40rpx;
	border:1rpx solid #e2e8f0;
	border-radius:40rpx;
	padding:32rpx;
}
.tool-panel-head{
	display:flex;
	justify-content:space-between;
	align-items:center;
}
.tool-title{
	font-size:28rpx;
	font-weight:500;
}
.tool-mode{
	font-size:24rpx;
	color:#94a3b8;
}
.tool-btn-row{
	display:grid;
	grid-template-columns:1fr 1fr 1fr;
	gap:16rpx;
	margin-top:32rpx;
}
.tool-btn{
	background:#f8fafc;
	border-radius:24rpx;
	padding:24rpx 8rpx;
	display:flex;
	flex-direction:column;
	align-items:center;
	gap:8rpx;
}
.tool-btn.active{
	background:#1e293b;
	color:#ffffff;
}
.tool-icon{
	font-size:36rpx;
}
.tool-text{
	font-size:22rpx;
}
.slider-item{
	margin-top:40rpx;
}
.label-row{
	display:flex;
	justify-content:space-between;
}
.label{
	font-size:24rpx;
	color:#64748b;
}
.value-text{
	font-size:24rpx;
	color:#333333;
}
.switch-batch-btn{
	text-align:center;
	border:1rpx solid #cbd5e1;
	border-radius:24rpx;
	padding:20rpx 0;
	font-size:28rpx;
	margin-top:48rpx;
}
.export-block{
	margin-top:48rpx;
	padding-top:40rpx;
	border-top:1rpx solid #f1f5f9;
}
.export-title{
	font-size:28rpx;
	font-weight:500;
}
.export-row{
	display:flex;
	gap:16rpx;
	margin-top:24rpx;
}
.export-picker{
	flex:1;
	border:1rpx solid #e2e8f0;
	border-radius:16rpx;
	padding:16rpx 8rpx;
	font-size:24rpx;
	text-align:center;
}
.save-btn{
	flex:1;
	background:#1e293b;
	color:#fff;
	border-radius:16rpx;
	font-size:24rpx;
}
</style>