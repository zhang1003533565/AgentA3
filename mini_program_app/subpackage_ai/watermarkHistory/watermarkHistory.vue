<template>
	<view class="page">
		<!-- 导航栏，使用插槽把管理按钮放到导航栏右上角 -->
		<nav-bar title="历史记录" :showBack="true">
			<template #right>
				<text class="nav-manage-btn" @click="toggleManageMode">{{isManageMode ? '完成' : '管理'}}</text>
			</template>
		</nav-bar>

		<scroll-view scroll-y class="scroll-wrap">
			<view class="page-body">
				<text class="tip-text">最近处理的图片</text>

				<view class="record-list">
					<view class="record-item" v-for="(item,idx) in recordList" :key="item.id || idx" @click="onRecordClick(item,idx)">
						<!-- 【终极修复】：去掉 change 事件，改用 click.stop 手动强制同步状态 -->
						<checkbox class="record-checkbox" :class="{hide:!isManageMode}" :checked="item.selected" @click.stop="toggleSelect(idx)"/>
						
						<view class="record-preview">
							<image 
								lazy-load
								v-if="item.imgUrl" 
								:src="item.imgUrl" 
								mode="aspectFill" 
								class="record-thumb"
								@error="onImageError(idx)"
							></image>
							<text v-else>🖼</text>
						</view>
						<view class="record-info">
							<text class="record-title">{{item.title}}</text>
							<text class="record-desc">{{formatTime(item.time)}} · {{item.format}}</text>
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
				<view class="popup-preview">
					<image 
						v-if="currentRecord && currentRecord.imgUrl" 
						:src="currentRecord.imgUrl" 
						mode="aspectFit" 
						class="popup-img"
						@error="onPopupImageError"
					></image>
					<text v-else class="no-img-text">暂无预览</text>
				</view>
				<view class="popup-btn-row">
					<button class="popup-btn border-btn" @click="reEdit">重新编辑</button>
					<button class="popup-btn red-btn" @click="deleteSingle">删除</button>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
import { getHistoryList, deleteHistory } from '@/api/watermark.js'; 

export default {
	data() {
		return {
			isManageMode:false,
			showPopup:false,
			currentRecord:null,
			recordList:[]
		}
	},
	onShow() {
		this.loadHistory();
	},
	methods:{
		async loadHistory() {
			try {
				const res = await getHistoryList();
				if (res && res.code === 200) {
					this.recordList = res.data.map(item => ({ ...item, selected: false })); 
				}
			} catch (e) {
				console.log("获取历史数据失败", e);
			}
		},

		// 列表内图片加载失败的容错
		onImageError(idx) {
			this.$set(this.recordList[idx], 'imgUrl', null);
		},
		// 弹窗内图片加载失败的容错
		onPopupImageError() {
			if (this.currentRecord) {
				this.currentRecord.imgUrl = null;
			}
		},

		formatTime(timeStr) {
			if (!timeStr) return '';
			const now = new Date();
			const today = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
			const target = new Date(timeStr.replace(/-/g, '/')).getTime();
			const diffDays = Math.floor((today - target) / (1000 * 60 * 60 * 24));
			if (diffDays === 0) return '今天 ' + timeStr.split(' ')[1];
			if (diffDays === 1) return '昨天 ' + timeStr.split(' ')[1];
			return timeStr; 
		},

		goBack(){ uni.navigateBack() },
		toggleManageMode(){ this.isManageMode = !this.isManageMode },
		
		// 【终极修复】：点击勾选框，直接手动取反并强制更新数据
		toggleSelect(idx) {
			this.$set(this.recordList[idx], 'selected', !this.recordList[idx].selected);
		},
		
		onRecordClick(item, idx){
			if(this.isManageMode) return
			this.currentRecord = item
			this.showPopup = true
		},
		closePopup(){
			this.showPopup = false
			this.currentRecord = null
		},
		
		// 【核心修改】：根据标题智能判断跳转目标，并缓存图片便于自动加载
		reEdit(){
			if(!this.currentRecord) return;
			
			const title = this.currentRecord.title || '';
			let targetPage = '';

			// ===== 【新增】：跳转前，将要编辑的图片存入本地缓存 =====
			if (this.currentRecord.imgUrl) {
				uni.setStorageSync('reEditImgData', this.currentRecord.imgUrl);
			} else {
				// 如果没有图，就把缓存清掉，防止错乱
				uni.removeStorageSync('reEditImgData');
			}
			// =======================================================

			// 根据历史记录的名称，自动判断跳转的目标页面
			if (title.includes('加水印')) {
				targetPage = '/subpackage_ai/watermarkAddEdit/watermarkAddEdit';
			} else if (title.includes('去水印')) {
				targetPage = '/subpackage_ai/watermarkRemoveEdit/watermarkRemoveEdit';
			} else {
				// 兜底处理：如果既不是加水印也不是去水印，并且原数据没存过路径
				if (!this.currentRecord.editPage) {
					return uni.showToast({ title: "无法找到匹配的编辑页面", icon: "none" });
				}
				targetPage = this.currentRecord.editPage;
			}

			// 执行跳转
			uni.navigateTo({ url: targetPage });
			this.closePopup();
		},

		async deleteSingle(){
			if(!this.currentRecord) return
			uni.showLoading({ title: '正在删除...', mask: true });
			try {
				await deleteHistory(this.currentRecord.id);
				uni.hideLoading();
				const index = this.recordList.findIndex(r => r.id === this.currentRecord.id)
				if(index > -1) this.recordList.splice(index, 1)
				this.closePopup()
				uni.showToast({ title: "已删除", icon: "success" })
			} catch (e) {
				uni.hideLoading();
				uni.showToast({ title: "删除失败", icon: "none" })
			}
		},

		async deleteSelected(){
			// 此时再过滤，必定能拿到最新同步的数据
			const selectedItems = this.recordList.filter(item => item.selected);
			if(selectedItems.length === 0) {
				return uni.showToast({ title: "请先勾选要删除的项", icon: "none" });
			}
			uni.showLoading({ title: '正在删除...', mask: true });
			try {
				for(let item of selectedItems) {
					await deleteHistory(item.id);
				}
				uni.hideLoading();
				this.recordList = this.recordList.filter(item => !item.selected);
				uni.showToast({ title: "已删除选中", icon: "success" });
			} catch (e) {
				uni.hideLoading();
				uni.showToast({ title: "批量删除出错", icon: "none" });
			}
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
.nav-manage-btn{ font-size:28rpx; color:#334155; }
.tip-text{ font-size:24rpx; color:#94a3b8; margin-bottom:24rpx; }
.record-item{
	display:flex;
	align-items:center;
	gap:24rpx;
	border:1rpx solid #f1f5f9;
	border-radius:32rpx;
	padding:24rpx;
	margin-bottom:24rpx;
	background-color: #fff;
}
.record-checkbox.hide{ display:none; }
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
	overflow: hidden;
}
.record-thumb {
	width: 100%;
	height: 100%;
}
.record-info{ flex:1; }
.record-title{ font-size:28rpx; font-weight:500; display:block; }
.record-desc{ font-size:24rpx; color:#94a3b8; margin-top:8rpx; display:block; }
.arrow-icon{ font-size:36rpx; color:#94a3b8; }
.arrow-icon.hide{ display:none; }
.bottom-delete-bar{ padding:32rpx 40rpx; border-top:1rpx solid #f1f5f9; }
.delete-btn{ width:100%; background:#ef4444; color:#fff; border-radius:24rpx; padding:24rpx 0; font-size:28rpx; }
.mask{
	position:fixed; left:0; top:0; right:0; bottom:0; background:rgba(30,41,59,0.4);
	z-index:99; display:flex; align-items:flex-end;
}
.popup{ width:100%; background:#ffffff; border-radius:48rpx 48rpx 0 0; padding:40rpx; }
.popup-preview{ 
	height:384rpx; 
	background:#f1f5f9; 
	border-radius:32rpx; 
	display:flex; 
	align-items:center; 
	justify-content:center; 
	overflow: hidden;
}
.popup-img { width: 100%; height: 100%; }
.no-img-text { font-size: 28rpx; color: #94a3b8; }
.popup-btn-row{ display:flex; gap:24rpx; margin-top:40rpx; }
.popup-btn{ flex:1; padding:24rpx 0; border-radius:24rpx; font-size:28rpx; }
.border-btn{ border:1rpx solid #e2e8f0; }
.red-btn{ background:#ef4444; color:#ffffff; }
</style>