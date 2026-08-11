<template>
  <view class="page">
    <nav-bar title="导入按钮测试" :showBack="true" :border="false" />

    <view class="tip">点击右侧灰色感叹号，气泡应出现在「导入文件」按钮正上方</view>

    <!-- 场景1：默认态（输入框底部 footer，模拟真实生成页布局） -->
    <view class="case">
      <view class="case-title">场景1 · 输入框底部（默认态）</view>
      <view class="input-card">
        <view class="input-footer">
          <ImportFileButton :loading="false" @click="onImportClick('场景1')" />
          <text class="char-count">0/500</text>
        </view>
      </view>
    </view>

    <!-- 场景2：loading 态（解析中，按钮应禁用，叹号仍可点） -->
    <view class="case">
      <view class="case-title">场景2 · 解析中（loading 态）</view>
      <view class="input-card">
        <view class="input-footer">
          <ImportFileButton :loading="true" @click="onImportClick('场景2')" />
          <text class="char-count">0/500</text>
        </view>
      </view>
    </view>

    <!-- 场景3：右对齐（验证不同容器位置下气泡定位） -->
    <view class="case">
      <view class="case-title">场景3 · 右对齐</view>
      <view class="input-card">
        <view class="input-footer input-footer--right">
          <ImportFileButton :loading="false" @click="onImportClick('场景3')" />
        </view>
      </view>
    </view>

    <!-- 场景4：居中 -->
    <view class="case">
      <view class="case-title">场景4 · 居中</view>
      <view class="input-card">
        <view class="input-footer input-footer--center">
          <ImportFileButton :loading="false" @click="onImportClick('场景4')" />
        </view>
      </view>
    </view>

    <view class="tip tip--bottom">验证点：气泡紧贴按钮上方，箭头指向按钮中心；点蒙层关闭；loading 态按钮不触发 click 但叹号可点。</view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import NavBar from '@/components/nav-bar/nav-bar.vue'
import ImportFileButton from '../components/ImportFileButton.vue'

function onImportClick(scene) {
  uni.showToast({ title: `${scene} 点击导入`, icon: 'none' })
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #FCFAFC;
  padding: 0 0 60rpx;
}

.tip {
  font-size: 24rpx;
  color: #778397;
  padding: 24rpx 32rpx 8rpx;
  line-height: 1.5;
}
.tip--bottom {
  margin-top: 40rpx;
  padding-top: 24rpx;
  border-top: 2rpx solid #F0F0F4;
}

.case {
  margin: 24rpx 32rpx;
}
.case-title {
  font-size: 26rpx;
  font-weight: 700;
  color: #1E293B;
  margin-bottom: 16rpx;
}

.input-card {
  background: #fff;
  border-radius: 28rpx;
  padding: 24rpx;
  box-shadow: 0 4rpx 20rpx rgba(30, 52, 79, 0.05);
}

.input-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.input-footer--right {
  justify-content: flex-end;
}
.input-footer--center {
  justify-content: center;
}

.char-count {
  color: #8290a1;
  font-size: 22rpx;
}
</style>
