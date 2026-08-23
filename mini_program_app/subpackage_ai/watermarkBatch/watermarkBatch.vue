<template>
	<view class="page">
		<!--统一导航栏，和项目全部水印页面保持一致-->
		<nav-bar title="批量处理" :showBack="true"></nav-bar>

		<scroll-view scroll-y class="scroll-wrap">
			<!--tab切换：批量加水印 / 批量去水印-->
			<view class="tab-wrap">
				<view class="tab-item" :class="{active:batchMode==='add'}" @click="batchMode='add'">批量加水印</view>
				<view class="tab-item" :class="{active:batchMode==='remove'}" @click="batchMode='remove'">批量去水印</view>
			</view>

			<!--上传区域-->
			<view class="upload-box">
				<text class="upload-icon">⬆</text>
				<text class="upload-text">导入多张图片</text>
				<text class="upload-desc">支持 JPG、PNG、WebP</text>
			</view>

			<!--图片缩略图网格-->
			<view class="img-grid">
				<view class="img-item">🖼</view>
				<view class="img-item">🖼</view>
				<view class="img-item">🖼</view>
				<view class="img-item add-more">➕</view>
			</view>

			<!--统一参数面板-->
			<view class="setting-panel">
				<text class="panel-title">统一参数</text>

				<!--批量加水印参数-->
				<view v-if="batchMode==='add'" class="add-param">
					<view class="form-item">
						<text class="label">水印文字</text>
						<input class="input" v-model="batchWatermarkText" placeholder="请输入水印文字"/>
					</view>
					<view class="form-item">
						<view class="label-row">
							<text class="label">透明度</text>
							<text class="value-text">{{batchOpacity}}%</text>
						</view>
						<slider :value="batchOpacity" min="0" max="100" @change="onBatchOpacityChange"/>
					</view>
					<view class="checkbox-row">
						<text>重复平铺</text>
						<checkbox :checked="batchTile" @change="onBatchTileChange"/>
					</view>
				</view>

				<!--批量去水印参数-->
				<view v-if="batchMode==='remove'" class="remove-param">
					<text class="label">修复精度</text>
					<view class="btn-row">
						<view class="pre-btn" :class="{active:precision==='fast'}" @click="precision='fast'">快速</view>
						<view class="pre-btn" :class="{active:precision==='hd'}" @click="precision='hd'">高清</view>
					</view>
				</view>
			</view>

			<!--开始批量处理按钮-->
			<button class="start-btn">开始批量处理</button>

			<!--导出设置-->
			<view class="export-block">
				<text class="export-title">处理完成后导出</text>
				<view class="export-row">
					<picker mode="selector" :value="compressIndex" :range="compressList" @change="onCompressChange">
						<view class="export-picker">{{compressList[compressIndex]}}</view>
					</picker>
					<picker mode="selector" :value="formatIndex" :range="formatList" @change="onFormatChange">
						<view class="export-picker">{{formatList[formatIndex]}}</view>
					</picker>
				</view>
			</view>
		</scroll-view>
	</view>
</template>

<script>
export default {
	data() {
		return {
			batchMode:"add",
			batchWatermarkText:"团队素材",
			batchOpacity:80,
			batchTile:false,
			precision:"hd",
			compressList:["中压缩","低压缩","高压缩"],
			compressIndex:0,
			formatList:["JPG","PNG","WebP"],
			formatIndex:0
		}
	},
	methods:{
		goBack(){
			uni.navigateBack()
		},
		onBatchOpacityChange(e){
			this.batchOpacity = e.detail.value
		},
		onBatchTileChange(e){
			this.batchTile = e.detail.value
		},
		onCompressChange(e){
			this.compressIndex = e.detail.value
		},
		onFormatChange(e){
			this.formatIndex = e.detail.value
		}
	}
}
</script>

<style scoped>
page{
	margin: 0;
	padding: 0;
	width: 100%;
	height: 100%;
	overflow: hidden;
	background-color:#f1f5f9;
}
.page{
	width:100%;
	height:100vh;
	display:flex;
	flex-direction:column;
}
.scroll-wrap{
	flex:1;
	padding:40rpx;
	overflow-y:auto;
	box-sizing: border-box;
}

.tab-wrap{
	display:flex;
	background:#f1f5f9;
	border-radius:24rpx;
	padding:8rpx;
}
.tab-item{
	flex:1;
	text-align:center;
	font-size:28rpx;
	padding:16rpx 0;
	border-radius:16rpx;
}
.tab-item.active{
	background:#ffffff;
	font-weight:500;
}
.upload-box{
	margin-top:40rpx;
	border:2rpx dashed #cbd5e1;
	border-radius:40rpx;
	height:256rpx;
	display:flex;
	flex-direction:column;
	align-items:center;
	justify-content:center;
	color:#64748b;
}
.upload-icon{
	font-size:56rpx;
}
.upload-text{
	font-size:28rpx;
	margin-top:16rpx;
}
.upload-desc{
	font-size:24rpx;
	color:#94a3b8;
	margin-top:8rpx;
}
.img-grid{
	display:grid;
	grid-template-columns:1fr 1fr 1fr 1fr;
	gap:16rpx;
	margin-top:32rpx;
}
.img-item{
	height:128rpx;
	background:#f1f5f9;
	border-radius:16rpx;
	display:flex;
	align-items:center;
	justify-content:center;
	color:#94a3b8;
	font-size:32rpx;
}
.img-item.add-more{
	border:2rpx dashed #cbd5e1;
	background:#ffffff;
}
.setting-panel{
	margin-top:48rpx;
	background:#f8fafc;
	border-radius:40rpx;
	padding:32rpx;
}
.panel-title{
	font-size:28rpx;
	font-weight:500;
}
.form-item{
	margin-top:32rpx;
}
.label{
	font-size:24rpx;
	color:#64748b;
}
.label-row{
	display:flex;
	justify-content:space-between;
}
.value-text{
	font-size:24rpx;
	color:#333333;
}
.input{
	margin-top:20rpx;
	width:100%;
	background:#fff;
	border:1rpx solid #e2e8f0;
	border-radius:24rpx;
	padding:24rpx;
	font-size:28rpx;
}
.checkbox-row{
	display:flex;
	justify-content:space-between;
	align-items:center;
	margin-top:32rpx;
	font-size:28rpx;
}
.btn-row{
	display:flex;
	gap:16rpx;
	margin-top:20rpx;
}
.pre-btn{
	flex:1;
	text-align:center;
	background:#ffffff;
	border:1rpx solid #e2e8f0;
	border-radius:24rpx;
	padding:24rpx 0;
	font-size:28rpx;
}
.pre-btn.active{
	background:#1e293b;
	color:#ffffff;
	border:none;
}
.start-btn{
	width:100%;
	background:#1e293b;
	color:#ffffff;
	border-radius:24rpx;
	padding:24rpx 0;
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
</style>