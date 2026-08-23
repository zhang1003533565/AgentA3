<template>
	<view class="page">
		<nav-bar title="批量处理" :showBack="true"></nav-bar>

		<scroll-view scroll-y class="scroll-wrap">
			<!--tab切换：批量加水印 / 批量去水印-->
			<view class="tab-wrap">
				<view class="tab-item" :class="{active:batchMode==='add'}" @click="batchMode='add'">批量加水印</view>
				<view class="tab-item" :class="{active:batchMode==='remove'}" @click="batchMode='remove'">批量去水印</view>
			</view>

			<!-- 上传区域 -->
			<view class="upload-box" @tap="selectMultipleImages" v-if="imageList.length === 0">
				<text class="upload-icon">⬆</text>
				<text class="upload-text">导入多张图片</text>
				<text class="upload-desc">支持 JPG、PNG、WebP</text>
			</view>

			<!--图片预览网格-->
			<view class="img-grid" v-if="imageList.length > 0">
				<view class="img-item" v-for="(item, idx) in imageList" :key="idx">
					<image class="grid-thumb" :src="item" mode="aspectFill"></image>
					<text class="remove-icon" @tap.stop="removeImage(idx)">×</text>
				</view>
				<view class="img-item add-more" @tap="selectMultipleImages">➕</view>
			</view>

			<!-- 开始批量处理按钮 -->
			<button class="start-btn" @tap="startBatchProcess">开始批量处理</button>

			<!-- ================= 统一参数设置面板 ================= -->
			<!-- 【修改点】：在批量去水印时，整个设置面板都不显示 -->
			<view class="setting-panel" v-if="batchMode === 'add'">
				
				<!-- 文字与形状水印的切换开关 (仅加水印模式) -->
				<view class="watermark-type-switch">
					<view class="type-item" :class="{active:watermarkType==='text'}" @tap="watermarkType='text'">文字水印</view>
					<view class="type-item" :class="{active:watermarkType==='shape'}" @tap="watermarkType='shape'">形状水印</view>
				</view>

				<text class="panel-title">统一参数设置</text>

				<!-- ============== 批量加水印：文字参数 ============== -->
				<view v-if="watermarkType==='text'">
					<!-- 水印文字 -->
					<view class="form-item">
						<text class="form-label">水印文字</text>
						<input class="box-input" type="text" v-model="batchWatermarkText" placeholder="请输入水印文字" />
					</view>

					<!-- 字体选择 -->
					<view class="form-item">
						<text class="form-label">字体</text>
						<picker mode="selector" :value="fontIndex" :range="fontList" @change="onFontChange">
							<view class="box-input picker-box">{{fontList[fontIndex]}}</view>
						</picker>
					</view>

					<!-- 字号 + 字体颜色 -->
					<view class="form-row">
						<view class="form-col">
							<text class="form-label">字号</text>
							<view class="size-row">
								<slider class="size-slider" :value="batchFontSize" min="12" max="120" activeColor="#1e293b" @change="onFontSizeChange"/>
								<text class="size-val">{{batchFontSize}} px</text>
							</view>
						</view>
						<view class="form-col">
							<text class="form-label">字体颜色</text>
							<view class="color-picker-box" @tap="openColorPopup">
								<view class="color-dot" :style="{backgroundColor: batchTextColor}"></view>
							</view>
						</view>
					</view>

					<!-- 透明度 -->
					<view class="form-item">
						<view class="label-row">
							<text class="form-label">透明度</text>
							<text class="value-text">{{batchOpacity}}%</text>
						</view>
						<slider :value="batchOpacity" min="0" max="100" activeColor="#1e293b" @change="onBatchOpacityChange"/>
					</view>
					
					<!-- 精确角度 + 重复平铺 并排 -->
					<view class="form-row">
						<view class="form-col">
							<text class="form-label">精确角度</text>
							<input class="box-input" type="text" :value="batchRotate + '°'" placeholder="输入角度" @input="onRotateInput" style="height: 80rpx;"/>
						</view>
						<view class="form-col">
							<text class="form-label">重复平铺</text>
							<view class="toggle-btn" :class="{'active': batchTile}" @tap="toggleBatchRepeat">
								{{ batchTile ? '关闭平铺' : '开启平铺' }}
							</view>
						</view>
					</view>

					<!-- 平铺间距调节 -->
					<view v-if="batchTile" class="form-item" style="margin-top: -10rpx;">
						<view class="label-row">
							<text class="form-label">平铺间距</text>
							<text class="value-text">{{ batchSpacing }} px</text>
						</view>
						<slider :value="batchSpacing" min="20" max="200" activeColor="#409eff" @change="onBatchSpacingChange"/>
					</view>

					<!-- 横向无缝延展长条 -->
					<view class="form-row">
						<view class="form-col">
							<text class="form-label">横向延展长条</text>
							<view class="toggle-btn" :class="{'active': batchHorizontalFill}" @tap="toggleBatchHorizontalFill">
								{{ batchHorizontalFill ? '关闭延展' : '开启延展' }}
							</view>
						</view>
						<view v-if="batchHorizontalFill" class="form-col">
							<view class="label-row">
								<text class="form-label">长条数量</text>
							</view>
							<view class="size-row">
								<slider class="size-slider" :value="batchHorizontalLines" min="1" max="10" activeColor="#1e293b" @change="onBatchHLinesChange"/>
								<text class="size-val" style="width: 60rpx; text-align: right; min-width: 60rpx;">{{ batchHorizontalLines }}</text>
							</view>
						</view>
					</view>
				</view>

				<!-- ============== 批量加水印：形状参数 ============== -->
				<view v-if="watermarkType==='shape'">
					<view class="form-item">
						<text class="form-label">形状选择</text>
						<picker mode="selector" :value="shapeIndex" :range="shapeList" @change="onShapeChange">
							<view class="box-input picker-box">{{shapeList[shapeIndex]}}</view>
						</picker>
					</view>
					
					<view class="form-row">
						<view class="form-col">
							<text class="form-label">填充颜色</text>
							<view class="color-picker-box" @tap="openShapeColorPopup">
								<view class="color-dot" :style="{backgroundColor: batchShapeColor}"></view>
							</view>
						</view>
						<view class="form-col">
							<text class="form-label">大小</text>
							<view class="size-row">
								<slider class="size-slider" :value="batchShapeScale" min="10" max="300" activeColor="#1e293b" @change="onShapeScaleChange"/>
								<text class="size-val">{{batchShapeScale}}%</text>
							</view>
						</view>
					</view>

					<!-- 透明度 -->
					<view class="form-item">
						<view class="label-row">
							<text class="form-label">透明度</text>
							<text class="value-text">{{batchOpacity}}%</text>
						</view>
						<slider :value="batchOpacity" min="0" max="100" activeColor="#1e293b" @change="onBatchOpacityChange"/>
					</view>
					
					<!-- 精确角度 + 重复平铺 并排 (形状也支持平铺和延展) -->
					<view class="form-row">
						<view class="form-col">
							<text class="form-label">精确角度</text>
							<input class="box-input" type="text" :value="batchRotate + '°'" placeholder="输入角度" @input="onRotateInput" style="height: 80rpx;"/>
						</view>
						<view class="form-col">
							<text class="form-label">重复平铺</text>
							<view class="toggle-btn" :class="{'active': batchTile}" @tap="toggleBatchRepeat">
								{{ batchTile ? '关闭平铺' : '开启平铺' }}
							</view>
						</view>
					</view>

					<!-- 平铺间距调节 (形状) -->
					<view v-if="batchTile" class="form-item" style="margin-top: -10rpx;">
						<view class="label-row">
							<text class="form-label">平铺间距</text>
							<text class="value-text">{{ batchSpacing }} px</text>
						</view>
						<slider :value="batchSpacing" min="20" max="200" activeColor="#409eff" @change="onBatchSpacingChange"/>
					</view>

					<!-- 横向无缝延展长条 (形状) -->
					<view class="form-row">
						<view class="form-col">
							<text class="form-label">横向延展长条</text>
							<view class="toggle-btn" :class="{'active': batchHorizontalFill}" @tap="toggleBatchHorizontalFill">
								{{ batchHorizontalFill ? '关闭延展' : '开启延展' }}
							</view>
						</view>
						<view v-if="batchHorizontalFill" class="form-col">
							<view class="label-row">
								<text class="form-label">长条数量</text>
							</view>
							<view class="size-row">
								<slider class="size-slider" :value="batchHorizontalLines" min="1" max="10" activeColor="#1e293b" @change="onBatchHLinesChange"/>
								<text class="size-val" style="width: 60rpx; text-align: right; min-width: 60rpx;">{{ batchHorizontalLines }}</text>
							</view>
						</view>
					</view>
				</view>
			</view>

			<!-- 处理结果 -->
			<view class="result-area" v-if="resultImages.length > 0">
				<view class="result-header">
					<text class="result-title">处理结果 ({{resultImages.length}}张)</text>
					<text class="result-download-all" @tap="downloadAllResults">一键下载全部</text>
				</view>
				<scroll-view scroll-x class="result-scroll">
					<view class="result-item" v-for="(img, idx) in resultImages" :key="idx" @tap="previewResultImage(idx)">
						<image :src="img" mode="aspectFill" class="result-thumb"></image>
					</view>
				</scroll-view>
			</view>
		</scroll-view>
		
		<!-- 颜色选择弹窗 -->
		<view class="color-mask" v-if="showColorPicker" @tap="showColorPicker=false">
			<view class="color-box" @tap.stop>
				<text class="color-title">选择颜色</text>
				<view class="color-list">
					<view class="c-item" v-for="c in presetColors" :key="c" :style="{backgroundColor:c}" @tap="selectColor(c)"></view>
				</view>
				<button class="color-close" @tap="showColorPicker=false">完成</button>
			</view>
		</view>
		<!-- 形状颜色弹窗 -->
		<view class="color-mask" v-if="showShapeColorPicker" @tap="showShapeColorPicker=false">
			<view class="color-box" @tap.stop>
				<text class="color-title">选择形状颜色</text>
				<view class="color-list">
					<view class="c-item" v-for="c in presetColors" :key="c" :style="{backgroundColor:c}" @tap="selectShapeColor(c)"></view>
				</view>
				<button class="color-close" @tap="showShapeColorPicker=false">完成</button>
			</view>
		</view>
	</view>
</template>

<script>
import { addHistory } from '@/api/watermark.js';

export default {
	data() {
		return {
			batchMode:"add",
			watermarkType: 'text',
			batchWatermarkText:"仅供预览",
			batchOpacity:80,
			batchFontSize: 28,
			batchTextColor: '#1e293b',
			batchTile:false,
			batchSpacing: 80,
			batchHorizontalFill: false,
			batchHorizontalLines: 1,
			batchRotate: 0,
			
			// 形状相关
			shapeList: ["矩形","圆形","斜线条纹"],
			shapeIndex: 0,
			batchShapeColor: '#1e293b',
			batchShapeScale: 100,
			batchStrokeWidth: 2,

			// 字体相关
			fontList: ["黑体","宋体","楷体","圆体"],
			fontIndex: 0,
			
			imageList: [],
			resultImages: [],
			
			// 颜色相关
			showColorPicker: false,
			showShapeColorPicker: false,
			presetColors: ['#1e293b', '#ef4444', '#fbbf24', '#10b981', '#3b82f6']
		}
	},
	methods:{
		goBack(){ uni.navigateBack() },
		onBatchOpacityChange(e){ this.batchOpacity = e.detail.value },
		onFontSizeChange(e){ this.batchFontSize = e.detail.value },
		onFontChange(e){ this.fontIndex = e.detail.value },
		onShapeChange(e){ this.shapeIndex = e.detail.value },
		onShapeScaleChange(e){ this.batchShapeScale = e.detail.value },
		
		onRotateInput(e){
			let numStr = e.detail.value.replace(/[^0-9\-]/g,'')
			let num = Number(numStr)
			if(!isNaN(num)){
				this.batchRotate = num
			}
		},
		
		toggleBatchRepeat() {
			this.batchTile = !this.batchTile;
		},
		onBatchSpacingChange(e) {
			this.batchSpacing = e.detail.value;
		},
		toggleBatchHorizontalFill() {
			this.batchHorizontalFill = !this.batchHorizontalFill;
		},
		onBatchHLinesChange(e) {
			this.batchHorizontalLines = parseInt(e.detail.value);
		},

		openColorPopup(){ this.showColorPicker = true },
		selectColor(c){ this.batchTextColor = c; this.showColorPicker = false },
		openShapeColorPopup(){ this.showShapeColorPicker = true },
		selectShapeColor(c){ this.batchShapeColor = c; this.showShapeColorPicker = false },

		selectMultipleImages() {
			uni.chooseImage({
				count: 9,
				success: (res) => {
					this.imageList = this.imageList.concat(res.tempFilePaths);
					this.resultImages = [];
				}
			})
		},
		removeImage(idx) {
			this.imageList.splice(idx, 1);
			if(this.imageList.length === 0) this.resultImages = [];
		},

		async startBatchProcess() {
			if (this.imageList.length === 0) return uni.showToast({title:"请先导入图片", icon:"none"});
			if (this.batchMode === 'add') {
				await this.startBatchAddWatermark();
			} else {
				await this.startBatchRemoveWatermark();
			}
		},

		async startBatchAddWatermark() {
			uni.showLoading({ title: "正在合成水印...", mask: true });
			const processed = [];
			
			for (let i = 0; i < this.imageList.length; i++) {
				const src = this.imageList[i];
				const canvas = document.createElement('canvas');
				const ctx = canvas.getContext('2d');
				const img = new Image();
				img.crossOrigin = 'anonymous';
				img.src = src;
				
				await new Promise(resolve => { img.onload = resolve; });
				canvas.width = img.width;
				canvas.height = img.height;

				ctx.drawImage(img, 0, 0);

				let itemW = 0, itemH = 0;
				ctx.globalAlpha = this.batchOpacity / 100;

				if (this.watermarkType === 'text') {
					ctx.font = `${this.batchFontSize}px ${this.fontList[this.fontIndex]}`;
					itemW = ctx.measureText(this.batchWatermarkText).width;
					itemH = this.batchFontSize * 1.2;
				} else {
					itemW = itemH = 60 * (this.batchShapeScale / 100);
				}

				const drawFunc = (x, y) => {
					ctx.save();
					ctx.translate(x, y);
					ctx.rotate(this.batchRotate * Math.PI / 180);
					
					if (this.watermarkType === 'text') {
						ctx.fillStyle = this.batchTextColor;
						ctx.textAlign = 'center';
						ctx.textBaseline = 'middle';
						ctx.fillText(this.batchWatermarkText, 0, 0);
					} else {
						ctx.fillStyle = this.batchShapeColor;
						ctx.strokeStyle = this.batchShapeColor;
						ctx.lineWidth = this.batchStrokeWidth;
						
						const w = 60 * (this.batchShapeScale / 100);
						if (this.shapeIndex === 0) {
							ctx.fillRect(-w/2, -w/2, w, w);
						} else if (this.shapeIndex === 1) {
							ctx.beginPath();
							ctx.arc(0, 0, w/2, 0, 2 * Math.PI);
							ctx.fill();
						} else if (this.shapeIndex === 2) {
							ctx.beginPath();
							ctx.moveTo(-w/2, w/2);
							ctx.lineTo(w/2, -w/2);
							ctx.stroke();
						}
					}
					ctx.restore();
				}

				if (this.batchHorizontalFill) {
					const totalLinesHeight = itemH * this.batchHorizontalLines;
					const startY = (canvas.height - totalLinesHeight) / 2 + itemH / 2;
					const cols = Math.ceil(canvas.width / itemW);
					for(let l=0; l<this.batchHorizontalLines; l++) {
						const drawY = startY + l * itemH;
						for(let c=0; c<cols; c++) {
							drawFunc(c * itemW + itemW / 2, drawY);
						}
					}
				} 
				else if (this.batchTile) {
					const spacingX = Math.max(itemW, this.batchSpacing);
					const spacingY = Math.max(itemH, this.batchSpacing);
					const cols = Math.max(1, Math.floor(canvas.width / spacingX));
					const rows = Math.max(1, Math.floor(canvas.height / spacingY));
					const startX = (canvas.width - cols * spacingX) / 2 + spacingX / 2;
					const startY = (canvas.height - rows * spacingY) / 2 + spacingY / 2;
					
					for(let r=0; r<rows; r++) {
						for(let c=0; c<cols; c++) {
							drawFunc(startX + c*spacingX, startY + r*spacingY);
						}
					}
				} 
				else {
					drawFunc(canvas.width / 2, canvas.height / 2);
				}

				processed.push(canvas.toDataURL('image/png'));
			}
			
			uni.hideLoading();
			this.resultImages = processed;
			this.saveBatchHistory("批量加水印");
			uni.showToast({ title: `成功生成 ${processed.length} 张`, icon: "success" });
		},

		async startBatchRemoveWatermark() {
			uni.showLoading({ title: "正在联系服务器...", mask: true });
			const processed = [];
			
			for (let i = 0; i < this.imageList.length; i++) {
				const res = await this.uploadSingleImage(this.imageList[i]);
				if (res && res.code === 200) {
					processed.push(res.data);
				}
			}
			uni.hideLoading();
			this.resultImages = processed;
			this.saveBatchHistory("批量去水印");
			uni.showToast({ title: `成功处理 ${processed.length} 张`, icon: "success" });
		},

		uploadSingleImage(filePath) {
			return new Promise((resolve) => {
				uni.uploadFile({
					// 使用和单图去水印完全相同的接口地址
					url: 'http://localhost:8080/history/aiRemove',
					filePath: filePath,
					name: 'image',
					success: (uploadRes) => {
						try {
							const data = JSON.parse(uploadRes.data);
							resolve(data);
						} catch(e) {
							resolve({ code: 500, msg: "解析失败" });
						}
					},
					fail: () => { resolve({ code: 500, msg: "失败" }); }
				});
			});
		},

		saveBatchHistory(title) {
			const now = new Date();
			const y = now.getFullYear();
			const m = (now.getMonth() + 1).toString().padStart(2, '0');
			const d = now.getDate().toString().padStart(2, '0');
			const h = now.getHours().toString().padStart(2, '0');
			const min = now.getMinutes().toString().padStart(2, '0');
			const dateStr = `${y}-${m}-${d} ${h}:${min}`;
			
			this.resultImages.forEach(img => {
				addHistory({ title: title, time: dateStr, format: "PNG", imgUrl: img });
			});
		},

		previewResultImage(index) {
			uni.previewImage({
				urls: this.resultImages,
				current: index
			})
		},

		downloadAllResults() {
			if (this.resultImages.length === 0) return uni.showToast({title:"请先处理图片", icon:"none"});
			this.resultImages.forEach((base64, idx) => {
				const link = document.createElement('a');
				link.download = `批量_${idx+1}.png`;
				link.href = base64;
				document.body.appendChild(link);
				link.click();
				document.body.removeChild(link);
			});
			uni.showToast({ title: "下载已开始", icon: "success" });
		}
	}
}
</script>

<style scoped>
page{ margin: 0; padding: 0; width: 100%; height: 100%; overflow: hidden; background-color:#f1f5f9; }
.page{ width:100%; height:100vh; display:flex; flex-direction:column; }
.scroll-wrap{ flex:1; padding:40rpx 40rpx 120rpx 40rpx; overflow-y:auto; box-sizing: border-box; }

.tab-wrap{ display:flex; background:#f1f5f9; border-radius:24rpx; padding:8rpx; }
.tab-item{ flex:1; text-align:center; font-size:28rpx; padding:16rpx 0; border-radius:16rpx; }
.tab-item.active{ background:#ffffff; font-weight:500; }

.upload-box{ margin-top:40rpx; border:2rpx dashed #cbd5e1; border-radius:40rpx; height:256rpx; display:flex; flex-direction:column; align-items:center; justify-content:center; color:#64748b; }
.upload-icon{ font-size:56rpx; }
.upload-text{ font-size:28rpx; margin-top:16rpx; }
.upload-desc{ font-size:24rpx; color:#94a3b8; margin-top:8rpx; }

.img-grid{ display:grid; grid-template-columns:repeat(4, 1fr); gap:16rpx; margin-top:32rpx; }
.img-item{ height:128rpx; background:#f1f5f9; border-radius:16rpx; display:flex; align-items:center; justify-content:center; color:#94a3b8; font-size:32rpx; position:relative; overflow:hidden; }
.grid-thumb{ width:100%; height:100%; }
.remove-icon{ position:absolute; top:0; right:0; background:rgba(0,0,0,0.6); color:#fff; width:40rpx; height:40rpx; text-align:center; line-height:40rpx; font-size:28rpx; border-radius:0 0 0 16rpx; z-index:10; }
.img-item.add-more{ border:2rpx dashed #cbd5e1; background:#ffffff; cursor:pointer; }

.start-btn{ width:100%; background:#1e293b; color:#ffffff; border-radius:24rpx; padding:24rpx 0; font-size:28rpx; margin-top:32rpx; margin-bottom: 32rpx; }

.setting-panel{ background:#f8fafc; border-radius:40rpx; padding:32rpx; }
.panel-title{ font-size:28rpx; font-weight:500; margin-bottom: 32rpx; display: block; }

/* 文字/形状切换标签 */
.watermark-type-switch {
	display: flex;
	background: #e2e8f0;
	border-radius: 20rpx;
	padding: 6rpx;
	margin-bottom: 32rpx;
}
.type-item {
	flex: 1;
	text-align: center;
	padding: 16rpx 0;
	font-size: 28rpx;
	color: #64748b;
	border-radius: 16rpx;
}
.type-item.active {
	background: #ffffff;
	color: #111111;
	font-weight: 500;
}

/* 仿单图页面的开关切换按钮 */
.toggle-btn {
	width: 100%;
	height: 80rpx;
	line-height: 80rpx;
	text-align: center;
	border: 1rpx solid #cbd5e1;
	border-radius: 20rpx;
	background: #ffffff;
	color: #64748b;
	font-size: 28rpx;
	box-sizing: border-box;
	transition: all 0.3s ease;
	cursor: pointer;
}
.toggle-btn.active {
	background: #ecf5ff;
	border-color: #409eff;
	color: #409eff;
}

.form-item{ margin-bottom:32rpx; }
.form-row{ display:flex; justify-content:space-between; margin-bottom:32rpx; gap:24rpx; }
.form-col{ flex:1; display:flex; flex-direction:column; gap:16rpx; }
.form-label{ font-size:24rpx; color:#64748b; margin-bottom: 8rpx; }
.label-row{ display:flex; justify-content:space-between; margin-bottom: 8rpx; }
.value-text{ font-size:24rpx; color:#333333; }

/* 统一输入框尺寸 */
.box-input {
	width: 100%;
	height: 80rpx;
	background: #fff;
	border: 1rpx solid #e2e8f0;
	border-radius: 20rpx;
	padding: 0 24rpx;
	font-size: 28rpx;
	box-sizing: border-box;
	line-height: 80rpx;
	color: #333;
	display: flex;
	align-items: center;
}
.picker-box { justify-content: flex-start; }

.color-picker-box{ display:flex; align-items:center; }
.color-dot{ width:48rpx; height:48rpx; border-radius:50%; border:2rpx solid #cbd5e1; }
.size-row{ display:flex; align-items:center; gap:16rpx; }
.size-slider{ flex:1; }
.size-val{ font-size:24rpx; color:#333; width:60rpx; text-align:right; }

.result-area{ margin-top:48rpx; padding-top:40rpx; border-top:1rpx solid #f1f5f9; }
.result-header{ display:flex; justify-content:space-between; align-items:center; margin-bottom:24rpx; }
.result-title{ font-size:28rpx; font-weight:500; }
.result-download-all{ font-size:24rpx; color:#1e293b; font-weight:bold; border-bottom:2rpx solid #1e293b; padding-bottom:4rpx; }
.result-scroll{ white-space:nowrap; width:100%; }
.result-item{ display:inline-block; width:160rpx; height:160rpx; margin-right:16rpx; border-radius:16rpx; overflow:hidden; background:#e2e8f0; }
.result-thumb{ width:100%; height:100%; }

/* 颜色弹窗 */
.color-mask{ position:fixed; left:0; top:0; right:0; bottom:0; background:rgba(0,0,0,0.4); z-index:999; display:flex; align-items:flex-end; justify-content:center; }
.color-box{ width:100%; background:#fff; border-radius:40rpx 40rpx 0 0; padding:40rpx; }
.color-title{ display:block; text-align:center; font-size:32rpx; font-weight:500; margin-bottom:40rpx; }
.color-list{ display:flex; justify-content:center; gap:24rpx; margin-bottom:40rpx; }
.c-item{ width:64rpx; height:64rpx; border-radius:50%; border:4rpx solid #f1f5f9; }
.color-close{ width:100%; background:#1e293b; color:#fff; border-radius:24rpx; font-size:28rpx; }
</style>