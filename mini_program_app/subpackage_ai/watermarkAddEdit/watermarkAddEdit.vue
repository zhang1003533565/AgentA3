<template>
  <view class="page">
    <nav-bar title="加水印编辑" :showBack="true"></nav-bar>

    <scroll-view scroll-y class="scroll-wrap">
      <view class="preview-box" @tap="selectLocalImage">
        <image v-if="imgSrc" :src="imgSrc" class="preview-img" mode="aspectFit"></image>
        <view v-else class="empty-box">
          <icon type="image" class="preview-icon"></icon>
          <text class="preview-tip">点击此处上传图片</text>
        </view>

        <view v-if="imgSrc" class="fabric-wrapper" id="fabricContainer"></view>
      </view>

      <view class="tab-wrap">
        <view class="tab-item" :class="{'tab-active':tabType==='text'}" @tap="switchTab('text')">文字水印</view>
        <view class="tab-item" :class="{'tab-active':tabType==='shape'}" @tap="switchTab('shape')">形状水印</view>
      </view>

      <view v-show="tabType==='text'" class="param-block">
        <view class="form-item">
          <text class="form-label">水印文字</text>
          <input class="input" v-model="form.text" placeholder="请输入水印文字" @input="onTextChange"/>
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
          <slider :value="form.fontSize" min="12" max="120" @change="onFontSizeChange"/>
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
            <text class="form-label">大小</text>
            <text class="val-right">{{form.scale}}%</text>
          </view>
          <slider :value="form.scale" min="10" max="300" @change="onScaleChange"/>
        </view>
        <view class="form-item">
          <text class="form-label">透明度</text>
          <slider :value="form.opacity" min="0" max="100" @change="onOpacityChange"/>
        </view>
      </view>

      <!-- 旋转与重复平铺并排 -->
      <view class="param-block">
        <view class="two-col">
          <view class="col-item">
            <text class="form-label">精确角度</text>
            <input class="input" :value="form.rotate + '°'" placeholder="输入角度" @input="onRotateInput"/>
          </view>
          <view class="col-item">
            <text class="form-label">重复平铺</text>
            <view class="toggle-btn" :class="{'active': form.repeat}" @tap="toggleRepeat">
              {{ form.repeat ? '关闭平铺' : '开启平铺' }}
            </view>
          </view>
        </view>
      </view>

      <!-- 平铺间距调节（只有开启重复平铺时显示） -->
      <view v-if="form.repeat" class="param-block" style="margin-top: -10rpx;">
        <view class="form-item">
          <view class="label-row">
            <text class="form-label">平铺间距</text>
            <text class="val-right">{{ form.spacing }} px</text>
          </view>
          <slider :value="form.spacing" min="20" max="200" @change="onSpacingChange" activeColor="#409eff"/>
        </view>
      </view>

      <!-- 横向无缝延展长条 -->
      <view class="param-block">
        <view class="two-col">
          <view class="col-item">
            <text class="form-label">横向延展长条</text>
            <view class="toggle-btn" :class="{'active': form.horizontalFill}" @tap="toggleHorizontalFill">
              {{ form.horizontalFill ? '关闭延展' : '开启延展' }}
            </view>
          </view>
          <!-- 长条数量调节（只有开启延展时显示） -->
          <view v-if="form.horizontalFill" class="col-item" style="display:flex; flex-direction:column; padding-bottom: 20rpx; justify-content: flex-end;">
            <text class="form-label" style="margin-bottom: 8rpx;">长条数量</text>
            <slider :value="form.horizontalLines" min="1" max="10" @change="onHorizontalLinesChange" activeColor="#409eff" style="width: 100%;"/>
            <text class="val-right" style="text-align: right; margin-top: 8rpx;">{{ form.horizontalLines }} 排</text>
          </view>
        </view>
      </view>

      <!-- 批量处理独立一行 -->
      <view class="batch-row">
        <view class="batch-btn" @tap="goBatch">切换批量处理</view>
      </view>

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
import { addHistory } from '@/api/watermark.js';

export default {
  data(){
    return{
      imgSrc: "",   
      imgWidth: 0,  
      imgHeight: 0,     
      canvas: null, 
      watermarkObject: null,      
      watermarkObjects: [],       
      imgScale: 1, // 记录预览时图片的缩放比例

      tabType:'text',
      targetColorType:'text',
      colorPopupShow:false,
      textColor:'#1e293b',
      shapeColor:'#1e293b',
      tempColor:'#1e293b',
      fontList:["黑体","宋体","楷体","圆体"],
      fontIndex:0,
      shapeList:["矩形","圆形"],
      shapeIndex:0,
      compressList:["低压缩","中压缩","高压缩"],
      compressIndex:1,
      formatList:["JPG","PNG","WebP"],
      formatIndex:0,
      presetColors:['#1e293b','#ef4444','#fbbf24','#10b981','#3b82f6'],
      form:{
        text:"仅供预览",
        fontSize:36,
        opacity:80,
        scale:100,
        rotate:0,
        repeat:false,
        spacing: 80,
        horizontalFill: false,
        horizontalLines: 1
      }
    }
  },
  // ========== 页面显示时自动加载历史图片 ==========
  onShow() {
    const reEditImg = uni.getStorageSync('reEditImgData');
    if (reEditImg) {
      uni.removeStorageSync('reEditImgData');
      
      if (this.imgSrc !== reEditImg) {
        this.imgSrc = reEditImg;
        
        uni.getImageInfo({
          src: this.imgSrc,
          success: (info) => {
            this.imgWidth = info.width;
            this.imgHeight = info.height;
            this.$nextTick(() => { 
              this.initFabric(); 
            });
          }
        });
      }
    }
  },
  // ========================================================
  methods:{
    goBack(){ uni.navigateBack() },
    
    selectLocalImage() {
      uni.chooseImage({
        count: 1,
        success: (res) => {
          this.imgSrc = res.tempFilePaths[0]; 
          uni.getImageInfo({
            src: this.imgSrc,
            success: (info) => {
              this.imgWidth = info.width;
              this.imgHeight = info.height;
              this.$nextTick(() => { this.initFabric(); });
            }
          });
        }
      })
    },

    initFabric() {
      const container = document.getElementById('fabricContainer');
      if (!container) return;
      container.innerHTML = ''; 

      if (typeof window.fabric === 'undefined') {
        setTimeout(() => this.initFabric(), 300);
        return;
      }

      const box = container.parentElement;
      const w = box.clientWidth || 350;
      const h = box.clientHeight || 400;

      this.canvas = new window.fabric.Canvas('fabricCanvas', {
        width: w,
        height: h,
        selection: true
      });
      container.appendChild(this.canvas.wrapperEl);

      window.fabric.Image.fromURL(this.imgSrc, (img) => {
        if (!img) return;
        const canvasW = this.canvas.width;
        const canvasH = this.canvas.height;
        const imgAspect = img.width / img.height;
        const canvasAspect = canvasW / canvasH;
        let scale;
        if (imgAspect > canvasAspect) {
          scale = canvasW / img.width;
        } else {
          scale = canvasH / img.height;
        }
        
        this.imgScale = scale; 

        img.scaleX = scale;
        img.scaleY = scale;
        img.left = (canvasW - img.width * scale) / 2;
        img.top = (canvasH - img.height * scale) / 2;
        this.canvas.setBackgroundImage(img, this.canvas.renderAll.bind(this.canvas));
        this.addWatermarkObject();
      }, { crossOrigin: 'anonymous' });
    },

    // 彻底隐藏蓝色边框、缩放点和旋转手柄
    createWatermarkObject(left, top, fontSize_override = null, shapeSize_override = null) {
      let obj = null;
      if (this.tabType === 'text') {
        const fontSize = fontSize_override || this.form.fontSize;
        obj = new window.fabric.IText(this.form.text, {
          left, top,
          fontSize: fontSize,
          fontFamily: this.fontList[this.fontIndex],
          fill: this.textColor,
          opacity: this.form.opacity / 100,
          originX: 'center',
          originY: 'center',
          angle: this.form.rotate,
          // 【核心属性】彻底去掉控制手柄、蓝色选中框，并锁定缩放和旋转
          hasControls: false,
          hasBorders: false,
          lockScalingX: true,
          lockScalingY: true,
          lockRotation: true
        });
      } else {
        let size = shapeSize_override;
        if (size === null) {
          size = 100 * (this.form.scale / 100);
        }
        
        if (this.shapeIndex === 0) {
          obj = new window.fabric.Rect({
            left, top,
            width: size, height: size,
            fill: this.shapeColor,
            opacity: this.form.opacity / 100,
            originX: 'center', originY: 'center',
            angle: this.form.rotate,
            hasControls: false,
            hasBorders: false,
            lockScalingX: true,
            lockScalingY: true,
            lockRotation: true
          });
        } else {
          obj = new window.fabric.Circle({
            left, top,
            radius: size / 2,
            fill: this.shapeColor,
            opacity: this.form.opacity / 100,
            originX: 'center', originY: 'center',
            angle: this.form.rotate,
            hasControls: false,
            hasBorders: false,
            lockScalingX: true,
            lockScalingY: true,
            lockRotation: true
          });
        }
      }
      return obj;
    },

    addWatermarkObject() {
      if (!this.canvas) return;

      if (this.watermarkObjects.length) {
        this.watermarkObjects.forEach(obj => this.canvas.remove(obj));
        this.watermarkObjects = [];
      }
      if (this.watermarkObject) {
        this.canvas.remove(this.watermarkObject);
        this.watermarkObject = null;
      }

      const canvasW = this.canvas.width;
      const canvasH = this.canvas.height;

      if (this.form.horizontalFill) {
        const tempObj = this.createWatermarkObject(0, 0);
        const itemWidth = tempObj.width * (tempObj.scaleX || 1);
        const itemHeight = tempObj.height * (tempObj.scaleY || 1);
        
        const safeWidth = itemWidth > 0 ? itemWidth : 100;
        const safeHeight = itemHeight > 0 ? itemHeight : 100;

        const cols = Math.ceil(canvasW / safeWidth);
        const lines = this.form.horizontalLines || 1;

        const totalLinesHeight = safeHeight * lines;
        const startY = (canvasH - totalLinesHeight) / 2 + safeHeight / 2;

        for (let l = 0; l < lines; l++) {
          const targetY = startY + l * safeHeight;
          for (let c = 0; c < cols; c++) {
            const targetX = c * safeWidth + safeWidth / 2;
            const obj = this.createWatermarkObject(targetX, targetY);
            this.canvas.add(obj);
            this.watermarkObjects.push(obj);
          }
        }
        this.canvas.discardActiveObject();
        this.canvas.renderAll();
        return;
      }

      if (this.form.repeat) {
        let itemWidth = 100, itemHeight = 100;
        if (this.tabType === 'text') {
          const text = this.form.text || '水印';
          const fontSize = this.form.fontSize || 36;
          const charWidth = fontSize * 0.7;
          itemWidth = text.length * charWidth + 20;
          itemHeight = fontSize * 1.6 + 20;
        } else {
          const size = 100 * (this.form.scale / 100);
          itemWidth = size + 20;
          itemHeight = size + 20;
        }
        
        const spacingX = Math.max(itemWidth, this.form.spacing);
        const spacingY = Math.max(itemHeight, this.form.spacing);
        
        const cols = Math.max(1, Math.floor(canvasW / spacingX));
        const rows = Math.max(1, Math.floor(canvasH / spacingY));

        for (let r = 0; r < rows; r++) {
          for (let c = 0; c < cols; c++) {
            const obj = this.createWatermarkObject(
              spacingX * c + spacingX / 2,
              spacingY * r + spacingY / 2
            );
            this.canvas.add(obj);
            this.watermarkObjects.push(obj);
          }
        }
        this.canvas.discardActiveObject();
      } else {
        const obj = this.createWatermarkObject(canvasW / 2, canvasH / 2);
        this.canvas.add(obj);
        this.watermarkObject = obj;
      }

      this.canvas.renderAll();
    },

    toggleRepeat() {
      this.$set(this.form, 'repeat', !this.form.repeat);
      this.addWatermarkObject();
    },

    toggleHorizontalFill() {
      this.$set(this.form, 'horizontalFill', !this.form.horizontalFill);
      this.addWatermarkObject();
    },
    onHorizontalLinesChange(e) {
      this.form.horizontalLines = parseInt(e.detail.value);
      this.addWatermarkObject();
    },

    switchTab(type){ 
      this.tabType = type; 
      this.addWatermarkObject(); 
    },

    onTextChange(e){ 
      this.form.text = e.detail.value; 
      this.addWatermarkObject(); 
    },
    onFontChange(e){ 
      this.fontIndex = e.detail.value; 
      this.addWatermarkObject(); 
    },
    onFontSizeChange(e){ 
      this.form.fontSize = e.detail.value; 
      this.addWatermarkObject(); 
    },
    onOpacityChange(e){ 
      this.form.opacity = e.detail.value; 
      this.addWatermarkObject(); 
    },
    onScaleChange(e){ 
      this.form.scale = e.detail.value; 
      this.addWatermarkObject(); 
    },
    onShapeChange(e){ 
      this.shapeIndex = e.detail.value; 
      this.addWatermarkObject(); 
    },
    onRotateInput(e){ 
      let num = Number(e.detail.value.replace(/[^0-9\-]/g,'')); 
      if(!isNaN(num)){ 
        this.form.rotate = num;
        this.addWatermarkObject(); 
      } 
    },
    onSpacingChange(e){ 
      this.form.spacing = e.detail.value; 
      this.addWatermarkObject(); 
    },

    onCompressChange(e){ this.compressIndex = e.detail.value; },
    onFormatChange(e){ this.formatIndex = e.detail.value; },

    // ========================================================
    // 【终极修复】：基于背景图的原点相对坐标 + 单一的比例尺放大
    // 彻底解决长图模式下大小和位置不对齐的问题！
    // ========================================================
    handleDownload(){
      if(!this.imgSrc) return uni.showToast({title:"请先上传图片",icon:"none"});
      uni.showLoading({ title: "高清渲染中...", mask: true });

      // 1. 获取预览画布的尺寸和当前背景图的缩放比例
      const scale = this.imgScale; 
      const canvasW = this.canvas.width;
      const canvasH = this.canvas.height;

      // 2. 计算高清原图的“反向放大”倍数
      const invScale = 1 / scale;

      // 3. 计算预览画布中，背景图的左上角起点偏移（因为使用了 aspectFit 居中留白）
      const bgLeft = (canvasW - this.imgWidth * scale) / 2;
      const bgTop = (canvasH - this.imgHeight * scale) / 2;

      const tempId = 'temp_export_' + Date.now();
      const tempEl = document.createElement('canvas');
      tempEl.id = tempId;
      tempEl.width = this.imgWidth;
      tempEl.height = this.imgHeight;
      tempEl.style.display = 'none'; 
      document.body.appendChild(tempEl);

      const tempCanvas = new window.fabric.Canvas(tempId, {
        width: this.imgWidth,
        height: this.imgHeight
      });

      window.fabric.Image.fromURL(this.imgSrc, (img) => {
        img.scaleX = 1;
        img.scaleY = 1;
        img.left = 0;
        img.top = 0;
        tempCanvas.setBackgroundImage(img, () => {
          
          // 核心：获取当前所有水印对象
          const objectsToRender = this.watermarkObjects.length > 0 ? this.watermarkObjects : (this.watermarkObject ? [this.watermarkObject] : []);

          for (let i = 0; i < objectsToRender.length; i++) {
            const objData = objectsToRender[i];
            
            // 4. 换算水印在预览图内部的相对坐标
            const relativeX = objData.left - bgLeft;
            const relativeY = objData.top - bgTop;
            
            // 5. 放大到原图真实尺寸的真实坐标
            const origX = relativeX * invScale;
            const origY = relativeY * invScale;
            
            // 6. 放大文字和形状的真实大小
            const origFontSize = this.form.fontSize * invScale;
            const origShapeSize = this.tabType === 'shape' ? 100 * (this.form.scale / 100) * invScale : null;
            
            const obj = this.createWatermarkObject(origX, origY, origFontSize, origShapeSize);
            tempCanvas.add(obj);
          }

          tempCanvas.renderAll();
          const base64 = tempCanvas.toDataURL('png');
          uni.hideLoading();

          const link = document.createElement('a');
          link.download = '加水印图片.png';
          link.href = base64;
          document.body.appendChild(link);
          link.click();
          document.body.removeChild(link);

          tempCanvas.dispose();
          document.body.removeChild(tempEl);

          const now = new Date();
          const y = now.getFullYear();
          const m = (now.getMonth() + 1).toString().padStart(2, '0');
          const d = now.getDate().toString().padStart(2, '0');
          const h = now.getHours().toString().padStart(2, '0');
          const min = now.getMinutes().toString().padStart(2, '0');
          const dateStr = `${y}-${m}-${d} ${h}:${min}`;

          addHistory({
              title: "图片加水印",
              time: dateStr,
              format: "PNG",
              imgUrl: base64,
              editPage: '/subpackage_ai/watermarkAddEdit/watermarkAddEdit'
          });
        }, { crossOrigin: 'anonymous' });
      }, { crossOrigin: 'anonymous' });
    },
    // ========================================================

    goBatch(){ uni.navigateTo({url:"/subpackage_ai/watermarkBatch/watermarkBatch"}) },

    openColorPopup(type){ this.targetColorType = type; this.tempColor = type==='text'?this.textColor:this.shapeColor; this.colorPopupShow = true; },
    selectPreset(c){ this.tempColor = c; },
    confirmColor(){ 
      if(this.targetColorType==='text') this.textColor = this.tempColor; 
      else this.shapeColor = this.tempColor; 
      this.colorPopupShow = false; 
      this.addWatermarkObject(); 
    },
    closeColorPopup(){ this.colorPopupShow = false; }
  }
}
</script>

<style scoped>
page { margin: 0; padding: 0; width: 100%; height: 100%; overflow: hidden; }
.page { width: 100%; height: 100vh; background-color: #f5f7fa; display: flex; flex-direction: column; }
.scroll-wrap { flex: 1; padding: 40rpx 40rpx 120rpx 40rpx; overflow-y: auto; box-sizing: border-box; }

.preview-box{ 
  width:100%; height:416rpx; border-radius:24rpx; background:#e2e8f0; 
  display:flex; flex-direction:column; align-items:center; justify-content:center; 
  position:relative; color:#94a3b8; margin-bottom:40rpx; box-sizing: border-box; overflow: hidden; 
}
.preview-img { width: 100%; height: 100%; position: absolute; top:0; left:0; z-index:1; }
.fabric-wrapper { position: absolute; top:0; left:0; width:100%; height:100%; z-index:10; }
.empty-box { display: flex; flex-direction: column; align-items: center; justify-content: center; }
.preview-icon{ font-size:80rpx; }
.preview-tip{ font-size:24rpx; margin-top:24rpx; }

.tab-wrap{ display:grid; grid-template-columns:1fr 1fr; background:#e2e8f0; border-radius:20rpx; padding:8rpx; }
.tab-item{ text-align:center; padding:16rpx 0; font-size:28rpx; color:#64748b; border-radius:16rpx; }
.tab-active{ background:#ffffff; color:#111111; font-weight:500; }

.param-block{ margin-top:40rpx; }
.form-item{ margin-bottom:32rpx; display:flex; flex-direction:column; }
.form-label{ display:block; font-size:24rpx; color:#475569; margin-bottom:16rpx; line-height:1; flex-shrink:0; }
.label-row{ display:flex; justify-content:space-between; align-items:center; min-height:40rpx; line-height:1; }
.val-right{ font-size:26rpx; color:#1e293b; line-height:1; flex-shrink:0; }
.input{ width:100%; height:80rpx; box-sizing:border-box; border:1rpx solid #cbd5e1; border-radius:20rpx; padding:0 24rpx; font-size:28rpx; background:#fff; line-height:80rpx; flex-shrink:0; }
.picker-box{ width:100%; height:80rpx; box-sizing:border-box; border:1rpx solid #cbd5e1; border-radius:20rpx; padding:0 24rpx; font-size:28rpx; background:#fff; line-height:80rpx; flex-shrink:0; }
.color-dot{ width:72rpx; height:72rpx; border-radius:50%; border:4rpx #fff solid; box-shadow:0 0 0 1rpx #cbd5e1; }

.two-col{ display:grid; grid-template-columns:1fr 1fr; gap:24rpx; align-items:stretch; }
.col-item{ display:flex; flex-direction:column; justify-content:flex-start; min-height:130rpx; }

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
}
.toggle-btn.active {
  background: #ecf5ff;    
  border-color: #409eff;  
  color: #409eff;         
}

.batch-row { margin-top: 40rpx; }
.batch-btn { 
  width: 100%;
  text-align: center; 
  border: 1rpx solid #cbd5e1; 
  border-radius: 20rpx; 
  padding: 24rpx 0; 
  font-size: 28rpx; 
  box-sizing: border-box; 
  background: #ffffff;
  color: #1e293b;
}

.export-block{ margin-top:48rpx; padding-top:40rpx; border-top:1rpx solid #e2e8f0; }
.export-title{ font-size:30rpx; font-weight:500; color:#111; }
.export-row{ display:flex; gap:16rpx; margin-top:24rpx; }
.export-picker{ flex:1; border:1rpx solid #cbd5e1; border-radius:12rpx; padding:16rpx 8rpx; text-align:center; font-size:24rpx; background:#fff; }
.export-btn{ flex:1; background:#1e293b; color:#fff; font-size:24rpx; border-radius:12rpx; }

.color-popup-mask{ position:fixed; left:0; top:0; right:0; bottom:0; background:rgba(30,41,59,0.4); z-index:999; display:flex; align-items:flex-end; }
.color-popup-box{ width:100%; background:#ffffff; border-radius:28rpx 28rpx 0 0; padding:48rpx 40rpx; box-sizing: border-box; }
.popup-head{ display:block; text-align:center; font-size:30rpx; font-weight:500; margin-bottom:40rpx; }
.preset-color-wrap{ display:flex; gap:24rpx; justify-content:center; margin-bottom:48rpx; }
.preset-color-item{ width:56rpx; height:56rpx; border-radius:50%; }
.popup-btn-row{ display:flex; gap:32rpx; }
.popup-cancel{ flex:1; background:#ffffff; color:#64748b; font-size:28rpx; }
.popup-ok{ flex:1; background:#1e293b; color:#ffffff; font-size:28rpx; }
</style>