<template>
  <view class="page flex-col">
    <view class="group_1 flex-row justify-between">
      <view class="image-text_1 flex-col" @click="onTab('index')">
        <image
          class="label_1"
          referrerpolicy="no-referrer"
          :src="current === 'index' ? '/static/APPIcon/tabbar/tabbar_home_select.png' : '/static/APPIcon/tabbar/tabbar_home_notselected.png'"
        />
        <text class="text-group_1">首页</text>
      </view>
      <view class="group_2 flex-row">
        <view class="image-text_2 flex-col" @click="onTab('map')">
          <image
            class="image_1"
            referrerpolicy="no-referrer"
            src="/static/APPIcon/tabbar/tabbar_code.png"
          />
        </view>
      </view>
      <view class="image-text_3 flex-col" @click="onTab('mine')">
        <image
          class="label_2"
          referrerpolicy="no-referrer"
          :src="current === 'mine' ? '/static/APPIcon/tabbar/tabbar_me_select.png' : '/static/APPIcon/tabbar/tabbar_me_notselected.png'"
        />
        <text class="text-group_3">我的</text>
      </view>
    </view>
  </view>
</template>

<script>
export default {
  name: 'CustomTabBar',
  props: {
    current: {
      type: String,
      default: 'index'
    }
  },
  data() {
    return {};
  },
  methods: {
    onTab(type) {
      const routes = {
        'index': '/pages/index/index',
        'map': '/pages/map/map',
        'mine': '/pages/mine/mine'
      };
      const url = routes[type];
      if (!url) return;
      const pages = getCurrentPages();
      const cur = pages[pages.length - 1];
      const curRoute = cur ? ('/' + cur.route) : '';
      if (curRoute === url) return;
      uni.reLaunch({ url });
    }
  }
};
</script>

<style lang='css'>
@import './common.css';

.page {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  width: 750rpx;
  height: 180rpx;
  background: #fff;
  z-index: 999;
}

.group_1 {
  width: 750rpx;
  background: url('/static/APPIcon/tabbar/tabbar_bg.png') 0rpx 0rpx no-repeat;
  background-size: 750rpx 180rpx;
  padding: 10rpx 101rpx 24rpx 101rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.image-text_1 {
  margin-top: 20rpx;
}

.label_1 {
  width: 44rpx;
  height: 45rpx;
  align-self: center;
}

.text-group_1 {
  overflow-wrap: break-word;
  color: rgba(102, 102, 102, 1);
  font-size: 24rpx;
  text-align: center;
  white-space: nowrap;
  line-height: 32rpx;
  margin-top: 9rpx;
}

.group_2 {
  background-color: #5C7A99;
  border-radius: 50%;
  padding: 32rpx 44rpx 32rpx 44rpx;
}

.image-text_2 {
}

.image_1 {
  width: 60rpx;
  height: 60rpx;
}

.image-text_3 {
  margin-top: 20rpx;
}

.label_2 {
  width: 44rpx;
  height: 44rpx;
  margin-left: 4rpx;
}

.text-group_3 {
  overflow-wrap: break-word;
  color: rgba(102, 102, 102, 1);
  font-size: 24rpx;
  text-align: center;
  white-space: nowrap;
  line-height: 32rpx;
  margin-top: 10rpx;
}
</style>
