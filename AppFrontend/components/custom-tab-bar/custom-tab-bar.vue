<template>
  <view class="page flex-col">
    <view class="group_1 flex-row justify-between">
      <view class="image-text_1 flex-col">
        <image
          class="label_1"
          referrerpolicy="no-referrer"
          :src="IMAGE_PATH + 'tabbar/tabbar_home_select.png'"
        />
        <text class="text-group_1">首页</text>
      </view>
      <view class="group_2 flex-row">
        <view class="image-text_2 flex-col">
          <image
            class="image_1"
            referrerpolicy="no-referrer"
            :src="IMAGE_PATH + 'tabbar/tabbar_code_icon.png'"
          />
          <text class="text-group_2">地图</text>
        </view>
      </view>
      <view class="image-text_3 flex-col">
        <image
          class="label_2"
          referrerpolicy="no-referrer"
          :src="IMAGE_PATH + 'tabbar/tabbar_me_notselected.png'"
        />
        <text class="text-group_3">我的</text>
      </view>
    </view>
  </view>
</template>
<script>
export default {
  data() {
    return {
      constants: {},
      IMAGE_PATH: process.env.IMAGE_PATH
    };
  },
  methods: {}
};
</script>
<style lang='css'>
@import './common.css';
@import './index.rpx.css';

.page {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  height: 250rpx;
  z-index: 999;
}
</style>
