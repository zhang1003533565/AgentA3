<template>
  <view class="page">
    <!-- 只统一头部：和主页一样使用 nav-bar -->
    <nav-bar title="加水印编辑" :showBack="true"></nav-bar>

    <scroll-view scroll-y class="scroll-wrap">
      <!-- 预览区域（替换为Canvas画布，支持点击上传图片） -->
      <view class="preview-box" @tap="selectLocalImage">
        <canvas 
          canvas-id="waterCanvas" 
          class="canvas-view"
          :style="{ width: previewWidth + 'rpx', height: previewHeight + 'rpx' }"
        ></canvas>
        <!-- 无图时显示提示 -->
        <view v-if="!imgSrc" class="empty-tip">
          <icon type="image" class="preview-icon"></icon>
          <text class="preview-tip">点击此处上传图片</text>
        </view>
      </view>

      <!-- tab切换：文字水印 / 形状水印 -->
      <view class="tab-wrap">
        <view class="tab-item" :class="{'tab-active':tabType==='text'}" @tap="switchTab('text')">文字水印</view>
        <view class="tab-item" :class="{'tab-active':tabType==='shape'}" @tap="switchTab('shape')">形状水印</view>
      </view>

      <!-- 文字水印参数面板 -->
      <view v-show="tabType==='text'" class="param-block">
        <view class="form-item">
          <text class="form-label">水印文字</text>
          <input class="input" v-model="form.text" placeholder="请输入水印文字" @input="renderWaterMark"/>
        </view>
        <view class="form-item">
          <text class="form-label">字体</text>
          <picker mode="selector" :value="fontIndex" :range="fontList" @change="onFontChange">
            <view class="picker-box">{{fontList[fontIndex]}}</view>
          </picker>
        </view>
        <view class="form-item">
          <view class="label-row">
            <text class="form-label">字号</text>
            <text class="val-right">{{form.fontSize}} px</text>
          </view>
          <slider :value="form.fontSize" min="12" max="48" @change="onFontSizeChange"/>
        </view>
        <view class="form-item" @tap="openColorPopup('text')">
          <view class="label-row">
            <text class="form-label">字体颜色</text>
            <view class="color-dot" :style="{backgroundColor:textColor}"></view>
          </view>
        </view>
        <view class="form-item">
          <text class="form-label">透明度</text>
          <slider :value="form.opacity" min="0" max="100" @change="onOpacityChange"/>
        </view>
      </view>

      <!-- 形状水印参数面板 -->
      <view v-show="tabType==='shape'" class="param-block">
        <view class="form-item">
          <text class="form-label">形状选择</text>
          <picker mode="selector" :value="shapeIndex" :range="shapeList" @change="onShapeChange">
            <view class="picker-box">{{shapeList[shapeIndex]}}</view>
          </picker>
        </view>
        <view class="form-item" @tap="openColorPopup('shape')">
          <view class="label-row">
            <text class="form-label">填充颜色</text>
            <view class="color-dot" :style="{backgroundColor:shapeColor}"></view>
          </view>
        </view>
        <view class="form-item">
          <view class="label-row">
            <text class="form-label">描边粗细</text>
            <text class="val-right">{{form.strokeWidth}} px</text>
          </view>
          <slider :value="form.strokeWidth" min="0" max="10" @change="onStrokeChange"/>
        </view>
        <view class="form-item">
          <view class="label-row">
            <text class="form-label">大小</text>
            <text class="val-right">{{form.scale}}%</text>
          </view>
          <slider :value="form.scale" min="20" max="200" @change="onScaleChange"/>
        </view>
      </view>

      <!-- 通用参数：位置、旋转、重复平铺 -->
      <view class="param-block">
        <view class="two-col">
          <view class="col-item">
            <text class="form-label">位置调节</text>
            <picker mode="selector" :value="posIndex" :range="posList" @change="onPosChange">
              <view class="picker-box">{{posList[posIndex]}}</view>
            </picker>
          </view>
          <view class="col-item">
            <text class="form-label">旋转角度</text>
            <input class="input" :value="form.rotate + '°'" @input="onRotateInput"/>
          </view>
        </view>
        <view class="checkbox-row">
          <text>重复平铺</text>
          <checkbox :checked="form.repeat" @change="onRepeatChange"></checkbox>
        </view>
      </view>

      <!-- 切换批量处理 -->
      <view class="batch-btn" @tap="goBatch">切换批量处理</view>

      <!-- 导出设置区域 -->
      <view class="export-block">
        <text class="export-title">导出设置</text>
        <view class="export-row">
          <picker mode="selector" :value="compressIndex" :range="compressList" @change="onCompressChange">
            <view class="export-picker">{{compressList[compressIndex]}}</view>
          </picker>
          <picker mode="selector" :value="formatIndex" :range="formatList" @change="onFormatChange">
            <view class="export-picker">{{formatList[formatIndex]}}</view>
          </picker>
          <button class="export-btn" @tap="handleDownload">下载保存</button>
        </view>
      </view>
    </scroll-view>

    <!-- 颜色弹窗 -->
    <view v-show="colorPopupShow" class="color-popup-mask" @tap="closeColorPopup">
      <view class="color-popup-box" @tap.stop>
        <text class="popup-head">选择颜色</text>
        <view class="preset-color-wrap">
          <view v-for="(c,idx) in presetColors" :key="idx" class="preset-color-item" :style="{backgroundColor:c}" @tap="selectPreset(c)"></view>
        </view>
        <view class="popup-btn-row">
          <button class="popup-cancel" @tap="closeColorPopup">取消</button>
          <button class="popup-ok" @tap="confirmColor">完成</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
export default {
  data(){
    return{
      // 画布核心变量
      imgSrc: "", // 本地上传图片地址
      canvasCtx: null, // canvas绘图上下文
      // 图片原始尺寸
      imgWidth: 0,
      imgHeight: 0,
      // 自适应预览画布尺寸
      previewWidth: 0,
      previewHeight: 0,
      // 预览框最大限制（rpx）
      maxPreviewW: 750,
      maxPreviewH: 500,

      tabType:'text',
      targetColorType:'text',
      colorPopupShow:false,
      textColor:'#1e293b',
      shapeColor:'#1e293b',
      tempColor:'#1e293b',
      fontList:["黑体","宋体","楷体","圆体"],
      fontIndex:0,
      shapeList:["矩形","圆形","斜线条纹"],
      shapeIndex:0,
      posList:["居中","左上","右下"],
      posIndex:0,
      compressList:["低压缩","中压缩","高压缩"],
      compressIndex:1,
      formatList:["JPG","PNG","WebP"],
      formatIndex:0,
      presetColors:['#1e293b','#ef4444','#fbbf24','#10b981','#3b82f6'],
      form:{
        text:"仅供预览",
        fontSize:18,
        opacity:60,
        strokeWidth:2,
        scale:100,
        rotate:-25,
        repeat:false
      }
    }
  },
  onReady() {
    // 页面加载完成初始化画布
    this.canvasCtx = uni.createCanvasContext("waterCanvas", this);
  },
  methods:{
    // 1. 点击预览框上传本地图片（修复：读取原图尺寸自适应缩放）
    selectLocalImage() {
      uni.chooseImage({
        count: 1,
        sourceType: ["album", "camera"],
        success: (res) => {
          this.imgSrc = res.tempFilePaths[0];
          // 获取图片真实宽高
          uni.getImageInfo({
            src: this.imgSrc,
            success: (info) => {
              this.imgWidth = info.width;
              this.imgHeight = info.height;
              // 计算等比例缩放尺寸
              const imgRatio = this.imgWidth / this.imgHeight;
              let w = this.maxPreviewW;
              let h = w / imgRatio;
              // 高度超出限制则重新计算
              if(h > this.maxPreviewH){
                h = this.maxPreviewH;
                w = h * imgRatio;
              }
              this.previewWidth = w;
              this.previewHeight = h;
              // 等待dom更新后渲染画布
              this.$nextTick(()=>{
                this.renderWaterMark();
              })
            }
          })
        }
      })
    },

    // 2. 核心：Canvas绘制图片+水印（修复：自适应画布尺寸，不裁切图片）
    renderWaterMark() {
      if (!this.imgSrc || this.previewWidth === 0) return;
      const ctx = this.canvasCtx;
      // 清空画布
      ctx.clearRect(0, 0, this.previewWidth, this.previewHeight);
      // 等比例绘制原图，完整显示不裁切
      ctx.drawImage(this.imgSrc, 0, 0, this.previewWidth, this.previewHeight);

      // 全局透明度转换 0-100 → 0~1
      const alpha = this.form.opacity / 100;
      ctx.setGlobalAlpha(alpha);
      // 画布中心点
      const centerX = this.previewWidth / 2;
      const centerY = this.previewHeight / 2;
      // 旋转角度
      ctx.save();
      ctx.translate(centerX, centerY);
      ctx.rotate(this.form.rotate * Math.PI / 180);

      // 根据位置选择偏移坐标（按画布比例适配）
      let offsetX = 0, offsetY = 0;
      if(this.posIndex === 0) { offsetX = 0; offsetY = 0; } // 居中
      if(this.posIndex === 1) { offsetX = -this.previewWidth * 0.35; offsetY = -this.previewHeight * 0.35; } // 左上
      if(this.posIndex === 2) { offsetX = this.previewWidth * 0.35; offsetY = this.previewHeight * 0.35; } // 右下

      // 文字水印绘制
      if(this.tabType === "text") {
        ctx.setFillStyle(this.textColor);
        ctx.setFontSize(this.form.fontSize);
        const text = this.form.text;
        // 重复平铺逻辑
        if(this.form.repeat) {
          const stepX = Math.max(120, this.form.fontSize * 6);
          const stepY = Math.max(80, this.form.fontSize * 4);
          for(let x = -this.previewWidth; x < this.previewWidth; x += stepX) {
            for(let y = -this.previewHeight; y < this.previewHeight; y += stepY) {
              ctx.fillText(text, x + offsetX, y + offsetY);
            }
          }
        } else {
          ctx.fillText(text, offsetX, offsetY);
        }
      }
      // 形状水印绘制
      if(this.tabType === "shape") {
        const scale = this.form.scale / 100;
        const w = 60 * scale;
        const h = 60 * scale;
        ctx.setFillStyle(this.shapeColor);
        ctx.setStrokeStyle(this.shapeColor);
        ctx.setLineWidth(this.form.strokeWidth);
        if(this.shapeIndex === 0) {
          // 矩形
          ctx.fillRect(offsetX - w/2, offsetY - h/2, w, h);
        } else if(this.shapeIndex === 1) {
          // 圆形
          ctx.beginPath();
          ctx.arc(offsetX, offsetY, w/2, 0, 2 * Math.PI);
          ctx.fill();
        }
        // 斜线条纹可后续扩展
      }
      ctx.restore();
      // 执行绘制
      ctx.draw();
    },

    goBack(){
      uni.navigateBack()
    },
    switchTab(type){
      this.tabType = type
      this.renderWaterMark();
    },
    onTextChange(e){
      this.form.text = e.detail.value
      this.renderWaterMark();
    },
    onFontChange(e){
      this.fontIndex = e.detail.value
      this.renderWaterMark();
    },
    onFontSizeChange(e){
      this.form.fontSize = e.detail.value
      this.renderWaterMark();
    },
    onOpacityChange(e){
      this.form.opacity = e.detail.value
      this.renderWaterMark();
    },
    onShapeChange(e){
      this.shapeIndex = e.detail.value
      this.renderWaterMark();
    },
    onStrokeChange(e){
      this.form.strokeWidth = e.detail.value
      this.renderWaterMark();
    },
    onScaleChange(e){
      this.form.scale = e.detail.value
      this.renderWaterMark();
    },
    onPosChange(e){
      this.posIndex = e.detail.value
      this.renderWaterMark();
    },
    onRotateInput(e){
      let numStr = e.detail.value.replace(/[^0-9\-]/g,'')
      let num = Number(numStr)
      if(!isNaN(num)){
        this.form.rotate = num
        this.renderWaterMark();
      }
    },
    onRepeatChange(e){
      this.form.repeat = e.detail.value
      this.renderWaterMark();
    },
    onCompressChange(e){
      this.compressIndex = e.detail.value
    },
    onFormatChange(e){
      this.formatIndex = e.detail.value
    },
    goBatch(){
      uni.navigateTo({url:"/subpackage_ai/watermarkBatch/watermarkBatch"})
    },
    // 3. 下载保存图片（纯前端导出，无需后端）
    handleDownload(){
      if(!this.imgSrc) {
        uni.showToast({title:"请先上传图片",icon:"none"})
        return;
      }
      uni.canvasToTempFilePath({
        canvasId: "waterCanvas",
        success: (res) => {
          uni.saveImageToPhotosAlbum({
            filePath: res.tempFilePath,
            success: () => {
              uni.showToast({title:"图片保存成功！"})
            },
            fail: () => {
              uni.showToast({title:"请开启相册存储权限",icon:"none"})
            }
          })
        }
      }, this)
    },
    openColorPopup(type){
      this.targetColorType = type
      if(type==='text') this.tempColor = this.textColor
      else this.tempColor = this.shapeColor
      this.colorPopupShow = true
    },
    selectPreset(c){
      this.tempColor = c
    },
    confirmColor(){
      if(this.targetColorType==='text'){
        this.textColor = this.tempColor
      }else{
        this.shapeColor = this.tempColor
      }
      this.colorPopupShow = false
      this.renderWaterMark();
    },
    closeColorPopup(){
      this.colorPopupShow = false
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
}

.page {
  width: 100%;
  height: 100vh;
  background-color: #f5f7fa;
  display: flex;
  flex-direction: column;
}

.scroll-wrap {
  flex: 1;
  padding: 40rpx 40rpx 120rpx 40rpx;
  overflow-y: auto;
  box-sizing: border-box;
}

/* 修复预览框：取消固定高度，自适应画布高度 */
.preview-box{
  width:100%;
  min-height: 240rpx;
  border-radius:24rpx;
  background:#e2e8f0;
  position:relative;
  color:#94a3b8;
  margin-bottom:40rpx;
  box-sizing: border-box;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}
.canvas-view {
  display: block;
}
.empty-tip {
  position: absolute;
  left:0;top:0;right:0;bottom:0;
  display:flex;
  flex-direction:column;
  align-items:center;
  justify-content:center;
}
.preview-icon{
  font-size:80rpx;
}
.preview-tip{
  font-size:24rpx;
  margin-top:24rpx;
}
.tab-wrap{
  display:grid;
  grid-template-columns:1fr 1fr;
  background:#e2e8f0;
  border-radius:20rpx;
  padding:8rpx;
}
.tab-item{
  text-align:center;
  padding:16rpx 0;
  font-size:28rpx;
  color:#64748b;
  border-radius:16rpx;
}
.tab-active{
  background:#ffffff;
  color:#111111;
  font-weight:500;
}
.param-block{
  margin-top:40rpx;
}
.form-item{
  margin-bottom:32rpx;
  display:flex;
  flex-direction:column;
}
.form-label{
  display:block;
  font-size:24rpx;
  color:#475569;
  margin-bottom:16rpx;
  line-height:1;
  flex-shrink:0;
}
.label-row{
  display:flex;
  justify-content:space-between;
  align-items:center;
  min-height:40rpx;
  line-height:1;
}
.val-right{
  font-size:26rpx;
  color:#1e293b;
  line-height:1;
  flex-shrink:0;
}
.input{
  width:100%;
  height:80rpx;
  box-sizing:border-box;
  border:1rpx solid #cbd5e1;
  border-radius:20rpx;
  padding:0 24rpx;
  font-size:28rpx;
  background:#fff;
  line-height:80rpx;
  flex-shrink:0;
}
.picker-box{
  width:100%;
  height:80rpx;
  box-sizing:border-box;
  border:1rpx solid #cbd5e1;
  border-radius:20rpx;
  padding:0 24rpx;
  font-size:28rpx;
  background:#fff;
  line-height:80rpx;
  flex-shrink:0;
}
.color-dot{
  width:72rpx;
  height:72rpx;
  border-radius:50%;
  border:4rpx #fff solid;
  box-shadow:0 0 0 1rpx #cbd5e1;
}
.two-col{
  display:grid;
  grid-template-columns:1fr 1fr;
  gap:24rpx;
  align-items:stretch;
}
.col-item{
  display:flex;
  flex-direction:column;
  justify-content:flex-start;
  min-height:130rpx;
}
.checkbox-row{
  display:flex;
  justify-content:space-between;
  align-items:center;
  font-size:28rpx;
  margin-top:20rpx;
}
.batch-btn{
  width:100%;
  text-align:center;
  border:1rpx solid #cbd5e1;
  border-radius:20rpx;
  padding:24rpx 0;
  font-size:28rpx;
  margin-top:48rpx;
  box-sizing: border-box;
}
.export-block{
  margin-top:48rpx;
  padding-top:40rpx;
  border-top:1rpx solid #e2e8f0;
}
.export-title{
  font-size:30rpx;
  font-weight:500;
  color:#111;
}
.export-row{
  display:flex;
  gap:16rpx;
  margin-top:24rpx;
}
.export-picker{
  flex:1;
  border:1rpx solid #cbd5e1;
  border-radius:12rpx;
  padding:16rpx 8rpx;
  text-align:center;
  font-size:24rpx;
  background:#fff;
}
.export-btn{
  flex:1;
  background:#1e293b;
  color:#fff;
  font-size:24rpx;
  border-radius:12rpx;
}
.color-popup-mask{
  position:fixed;
  left:0;
  top:0;
  right:0;
  bottom:0;
  background:rgba(30,41,59,0.4);
  z-index:999;
  display:flex;
  align-items:flex-end;
}
.color-popup-box{
  width:100%;
  background:#ffffff;
  border-radius:28rpx 28rpx 0 0;
  padding:48rpx 40rpx;
  box-sizing: border-box;
}
.popup-head{
  display:block;
  text-align:center;
  font-size:30rpx;
  font-weight:500;
  margin-bottom:40rpx;
}
.preset-color-wrap{
  display:flex;
  gap:24rpx;
  justify-content:center;
  margin-bottom:48rpx;
}
.preset-color-item{
  width:56rpx;
  height:56rpx;
  border-radius:50%;
}
.popup-btn-row{
  display:flex;
  gap:32rpx;
}
.popup-cancel{
  flex:1;
  background:#ffffff;
  color:#64748b;
  font-size:28rpx;
}
.popup-ok{
  flex:1;
  background:#1e293b;
  color:#ffffff;
  font-size:28rpx;
}
</style>