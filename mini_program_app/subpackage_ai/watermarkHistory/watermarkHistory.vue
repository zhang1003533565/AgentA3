<template>
	<view class="page">
		<!-- 导航栏，使用插槽把管理按钮放到导航栏右上角 -->
		<nav-bar title="历史记录" :showBack="true">
			<template #right>
				<text class="nav‑manage-btn" @click="toggleManageMode">{{isManageMode ? '完成' : '管理'}}</text>
			</template>
		</nav-bar>

		<scroll-view scroll-y class="scroll-wrap">
			<view class="page-body">
				<text class="tip-text">最近处理的图片</text>

				<view class="record-list">
					<view class="record-item" v-for="(item,idx) in recordList" :key="idx" @click="onRecordClick(item,idx)">
						<checkbox class="record-checkbox" :class="{hide:!isManageMode}" :checked="item.selected" @change="onCheckChange(idx, $event)"/>
						<view class="record-preview">🖼</view>
						<view class="record-info">
							<text class="record-title">{{item.title}}</text>
							<text class="record-desc">{{item.time}} · {{item.format}}</text>
						</view>
						<text class="arrow-icon" :class="{hide:isManageMode}">›</text>
					</view>
				</view>
			</view>
		</scroll-view>

		<!--底部删除操作栏，管理模式才显示-->
		<view v-if="isManageMode" class="bottom-delete-bar">
			<button class="delete-btn" @click="deleteSelected">删除选中记录</button>
		</view>

		<!--记录弹窗-->
		<view v-if="showPopup" class="mask" @click="closePopup">
			<view class="popup" @click.stop>
				<view class="popup-preview">🖼</view>
				<view class="popup-btn-row">
					<button class="popup-btn border-btn" @click="reEdit">重新编辑</button>
					<button class="popup-btn red-btn" @click="deleteSingle">删除</button>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
export default {
	data() {
		return {
			isManageMode:false,
			showPopup:false,
			currentRecord:null,
			recordList:[
				{title:"图片加水印",time:"今天 10:24",format:"PNG",selected:false,editPage:"watermarkAddEdit/watermarkAddEdit"},
				{title:"图片去水印",time:"昨天 18:06",format:"JPG",selected:false,editPage:"watermarkRemoveEdit/watermarkRemoveEdit"},
				{title:"图片加水印",time:"7月30日 14:12",format:"WebP",selected:false,editPage:"watermarkAddEdit/watermarkAddEdit"}
			]
		}
	},
	methods:{
		goBack(){
			uni.navigateBack()
		},
		toggleManageMode(){
			this.isManageMode = !this.isManageMode
		},
		onCheckChange(idx,e){
			this.recordList[idx].selected = e.detail.value
		},
		onRecordClick(item,idx){
			if(this.isManageMode){
				return
			}
			this.currentRecord = item
			this.showPopup = true
		},
		closePopup(){
			this.showPopup = false
			this.currentRecord = null
		},
		reEdit(){
			if(!this.currentRecord) return
			uni.navigateTo({
				url:`/subpackage_ai/${this.currentRecord.editPage}`
			})
			this.closePopup()
		},
		deleteSingle(){
			if(!this.currentRecord) return
			const index = this.recordList.findIndex(r=>r===this.currentRecord)
			if(index>-1){
				this.recordList.splice(index,1)
			}
			this.closePopup()
			uni.showToast({title:"已删除",icon:"none"})
		},
		deleteSelected(){
			this.recordList = this.recordList.filter(item=>!item.selected)
			uni.showToast({title:"已删除选中",icon:"none"})
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

.nav‑manage-btn{
	font-size:28rpx;
	color:#334155;
}

.tip-text{
	font-size:24rpx;
	color:#94a3b8;
	margin-bottom:24rpx;
}
.record-list{

}
.record-item{
	display:flex;
	align-items:center;
	gap:24rpx;
	border:1rpx solid #f1f5f9;
	border-radius:32rpx;
	padding:24rpx;
	margin-bottom:24rpx;
}
.record-checkbox.hide{
	display:none;
}
.record-preview{
	width:112rpx;
	height:112rpx;
	background:#f1f5f9;
	border-radius:24rpx;
	display:flex;
	align-items:center;
	justify-content:center;
	font-size:36rpx;
	color:#94a3b8;
}
.record-info{
	flex:1;
}
.record-title{
	font-size:28rpx;
	font-weight:500;
	display:block;
}
.record-desc{
	font-size:24rpx;
	color:#94a3b8;
	margin-top:8rpx;
	display:block;
}
.arrow-icon{
	font-size:36rpx;
	color:#94a3b8;
}
.arrow-icon.hide{
	display:none;
}
.bottom-delete-bar{
	padding:32rpx 40rpx;
	border-top:1rpx solid #f1f5f9;
}
.delete-btn{
	width:100%;
	background:#ef4444;
	color:#fff;
	border-radius:24rpx;
	padding:24rpx 0;
	font-size:28rpx;
}
/*弹窗*/
.mask{
	position:fixed;
	left:0;
	top:0;
	right:0;
	bottom:0;
	background:rgba(30,41,59,0.4);
	z-index:99;
	display:flex;
	align-items:flex-end;
}
.popup{
	width:100%;
	background:#ffffff;
	border-radius:48rpx 48rpx 0 0;
	padding:40rpx;
}
.popup-preview{
	height:384rpx;
	background:#f1f5f9;
	border-radius:32rpx;
	display:flex;
	align-items:center;
	justify-content:center;
	font-size:96rpx;
	color:#94a3b8;
}
.popup-btn-row{
	display:flex;
	gap:24rpx;
	margin-top:40rpx;
}
.popup-btn{
	flex:1;
	padding:24rpx 0;
	border-radius:24rpx;
	font-size:28rpx;
}
.border-btn{
	border:1rpx solid #e2e8f0;
}
.red-btn{
	background:#ef4444;
	color:#ffffff;
}
</style>