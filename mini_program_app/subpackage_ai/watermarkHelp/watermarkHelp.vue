<template>
  <view class="page-wrap">
    <nav-bar title="使用帮助" :showBack="true"></nav-bar>
    <scroll-view class="scroll-wrap" scroll-y>
      <view class="content">
        <view class="help-block">
          <text class="title">加水印如何使用</text>
          <text class="desc">选择图片，输入水印文字，可调整字体、字号、字体颜色、透明度，支持修改水印位置、旋转角度，开启重复平铺可以铺满整张图片，设置完成后点击生成即可导出带水印图片。</text>
        </view>
        <view class="help-block">
          <text class="title">去水印如何使用</text>
          <text class="desc">上传图片，调整画笔大小，涂抹需要去除水印的区域，点击执行去除，等待AI处理完成，预览无误后保存图片到本地相册。</text>
        </view>
        <view class="help-block">
          <text class="title">批量处理</text>
          <text class="desc">支持多张图片一次性导入，统一设置水印参数，一键批量处理，全部完成后可以逐个保存图片。分为批量加水印、批量去水印两种模式。</text>
        </view>
        <view class="help-block">
          <text class="title">历史记录</text>
          <text class="desc">查看之前处理过的图片记录，点击管理可以多选删除记录，点击记录可以弹窗预览，支持跳回编辑页面二次修改图片。</text>
        </view>
        <view class="help-block">
          <text class="title">保存与导出</text>
          <text class="desc">处理完成后点击保存，图片会保存到手机相册。小程序会有权限弹窗，请允许相册存储权限，否则图片无法保存。</text>
        </view>
        <view class="help-block">
          <text class="title">常见问题</text>
          <text class="desc">1.图片导出失败：请检查图片大小，建议不要上传过大图片，确认已授予相册存储权限。\n2.去水印效果不好：涂抹区域尽量完整覆盖水印，可切换高清模式获得更好修复效果。\n3.页面加载卡顿：图片像素过高会导致处理变慢，建议压缩原图后再操作。</text>
        </view>
        <view class="help-block">
          <text class="title">温馨提示</text>
          <text class="desc">本工具仅用于个人学习使用，请不要用于侵权、伪造类用途，请尊重图片版权。</text>
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
  methods: {}
}
</script>

<style scoped>
.page-wrap {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background-color: #f5f7fa;
}
.scroll-wrap {
  flex: 1;
}
.content {
  padding: 30rpx;
}
.help-block {
  background: #ffffff;
  border-radius: 16rpx;
  padding:32rpx;
  margin-bottom:24rpx;
}
.title {
  font-size:32rpx;
  font-weight:bold;
  display:block;
  margin-bottom:16rpx;
}
.desc {
  font-size:28rpx;
  color:#666666;
  line-height:1.6;
}
</style>