<template>
  <view class="page">
    <nav-bar title="去水印编辑" :showBack="true"></nav-bar>
    
    <scroll-view scroll-y class="scroll-wrap">
      <!-- 1. 上传区域（已去掉图标，只保留文字） -->
      <view class="preview-box" @tap="selectLocalImage">
        <image v-if="imgSrc" :src="imgSrc" class="preview-img" mode="aspectFit"></image>
        <view v-else class="empty-box">
          <text class="preview-tip">点击此处上传图片</text>
        </view>
      </view>

      <!-- 2. 核心去水印按钮 -->
      <button 
        class="start-process-btn" 
        :class="{ 'disabled-btn': isLoading }"
        @tap="goProcess"
      >
        <text v-if="!isLoading">开始去水印</text>
        <text v-else>处理中，请稍候...</text>
      </button>

      <!-- 3. 处理结果展示 -->
      <view v-if="resultImgSrc" class="result-block">
        <text class="result-title">✅ 处理完成，去水印结果</text>
        <image :src="resultImgSrc" class="result-img" mode="aspectFit"></image>
        
        <view class="action-row">
          <button class="save-btn" @tap="handleDownload">📥 下载保存</button>
          <button class="history-btn" @tap="saveResultToHistory">📂 保存历史</button>
        </view>
      </view>

      <!-- 4. 切换批量处理 -->
      <view class="batch-row">
        <view class="batch-btn" @tap="goBatchPage">切换批量处理</view>
      </view>

    </scroll-view>
  </view>
</template>

<script>
// 引入您保存历史的方法
import { addHistory } from '@/api/watermark.js';

export default {
  data() {
    return { 
      imgSrc: "",       
      resultImgSrc: "", 
      isLoading: false  
    }
  },
  methods: {
    // 1. 选择图片
    selectLocalImage() {
      if (this.isLoading) return; // 处理中不能选图
      uni.chooseImage({
        count: 1,
        sizeType: ['compressed'], // 压缩图片，防止太大导致请求失败
        success: (res) => { 
          this.imgSrc = res.tempFilePaths[0];
          this.resultImgSrc = ''; // 换图后清空上一张结果
        }
      })
    },
    
    // 2. 调用 Java 后端去水印接口
    goProcess() {
      if (!this.imgSrc) return uni.showToast({ title: "请先上传图片", icon: "none" });
      if (this.isLoading) return;
      
      this.isLoading = true;
      uni.showLoading({ title: "AI 处理中...", mask: true });

      // 【关键修改】：指向你 Spring Boot 的 8080 端口，而不是 Python 的 8081！
      const SPRING_BOOT_API_URL = 'http://localhost:8080/history/aiRemove'; 

      uni.uploadFile({
        url: SPRING_BOOT_API_URL,
        filePath: this.imgSrc,
        name: 'image',   // 注意要和 Java 里的 @RequestParam("image") 保持一致
        success: (uploadFileRes) => {
          uni.hideLoading();
          this.isLoading = false;
          
          try {
            const data = JSON.parse(uploadFileRes.data);
            if (data.code === 200 && data.data && data.data.url) {
              this.resultImgSrc = data.data.url; 
              uni.showToast({ title: "去水印成功！", icon: "success" });
            } else {
              uni.showToast({ title: data.msg || "处理失败", icon: "none" });
            }
          } catch (e) {
            uni.showToast({ title: "数据解析异常", icon: "error" });
          }
        },
        fail: (err) => {
          uni.hideLoading();
          this.isLoading = false;
          uni.showToast({ title: "网络请求失败", icon: "error" });
          console.error("Java后端请求失败:", err);
        }
      });
    },
    
    // 3. 下载结果图 (H5环境)
    handleDownload() {
      if(!this.resultImgSrc) return;
      const link = document.createElement('a');
      link.download = '去水印结果.png'; 
      link.href = this.resultImgSrc;
      document.body.appendChild(link); 
      link.click(); 
      document.body.removeChild(link);
    },
    
    // 4. 存入历史记录
    saveResultToHistory() {
      if (!this.resultImgSrc) return uni.showToast({ title: "没有结果可以保存", icon: "none" });
      
      const now = new Date();
      const dateStr = 
        `${now.getFullYear()}-${(now.getMonth()+1).toString().padStart(2,'0')}-${now.getDate().toString().padStart(2,'0')} ${now.getHours().toString().padStart(2,'0')}:${now.getMinutes().toString().padStart(2,'0')}`;

      addHistory({
        title: "AI智能去水印",
        time: dateStr,
        format: "PNG",
        imgUrl: this.resultImgSrc,
        editPage: '/subpackage_ai/watermarkRemoveEdit/watermarkRemoveEdit'
      }).then(() => {
        uni.showToast({ title: "已成功存入历史记录！", icon: "success" });
      });
    },

    // 5. 跳转批量处理
    goBatchPage() {
      uni.navigateTo({ url: "/subpackage_ai/watermarkBatch/watermarkBatch" })
    }
  }
}
</script>

<style scoped>
/* ========================== */
/* 保持和加水印一致的清爽风格 */
/* ========================== */
page { background-color: #f5f7fa; } 
.page { padding: 40rpx; height: 100vh; display: flex; flex-direction: column; }
.scroll-wrap { flex: 1; padding-bottom: 40rpx; }

/* --- 1. 上传区域（仅保留文字） --- */
.preview-box { 
  width:100%; height:416rpx; border-radius:24rpx; background:#e2e8f0; 
  display:flex; flex-direction:column; align-items:center; justify-content:center; 
  position:relative; color:#94a3b8; margin-bottom:40rpx; box-sizing: border-box; overflow: hidden; 
}
.preview-img { width: 100%; height: 100%; position: absolute; top:0; left:0; z-index:1; }
.empty-box { display: flex; flex-direction: column; align-items: center; justify-content: center; }
.preview-tip { font-size:24rpx; margin-top:24rpx; }

/* --- 2. 开始去水印（圆角已改成和下方按钮一致） --- */
.start-process-btn { 
  background: #1e293b; color: #ffffff; border-radius: 20rpx; font-size: 32rpx; 
  height: 100rpx; line-height: 100rpx; margin-bottom: 24rpx; border: none; 
}
.disabled-btn { background: #94a3b8; }

/* --- 3. 结果区域（保留原有） --- */
.result-block { background: #ffffff; border-radius: 40rpx; padding: 30rpx; margin-bottom: 24rpx; }
.result-title { font-size: 28rpx; font-weight: bold; color: #1e293b; margin-bottom: 20rpx; display: block; }
.result-img { width: 100%; height: 400rpx; border-radius: 24rpx; margin-bottom: 30rpx; background: #f8fafc; }
.action-row { display: flex; justify-content: space-between; gap: 20rpx; }
.save-btn, .history-btn { flex: 1; border-radius: 20rpx; font-size: 28rpx; height: 80rpx; line-height: 80rpx; }
.save-btn { background: #1e293b; color: #ffffff; border: none; }
.history-btn { background: #f1f5f9; color: #1e293b; border: 1rpx solid #e2e8f0; }

/* --- 4. 切换批量（白底灰边卡片，圆角一致） --- */
.batch-row { margin-top: 0rpx; }
.batch-btn { 
  width: 100%; text-align: center; border: 1rpx solid #cbd5e1; 
  border-radius: 20rpx; padding: 24rpx 0; font-size: 28rpx; 
  box-sizing: border-box; background: #ffffff; color: #1e293b;
}
</style>