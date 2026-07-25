<template>
  <view class="page flex-col">
    <view class="group_1 flex-row justify-between">
      <view class="image-text_1 flex-col" @click="onTab('index')">
        <image
          class="label_1"
          referrerpolicy="no-referrer"
          :src="current === 'index' ? '/static/APPIcon/tabbar/tabbar_home_select.png' : '/static/APPIcon/tabbar/tabbar_home_notselected.png'"
        />
        <text class="text-group_1" :class="{ active: current === 'index' }">首页</text>
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
        <text class="text-group_3" :class="{ active: current === 'mine' }">我的</text>
      </view>
    </view>
  </view>
</template>
<script>
export default {
  name: 'AppMainTabBar',
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
.page view {
  box-sizing: border-box;
  flex-shrink: 0;
}

.page {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  width: 750rpx;
  height: calc(262rpx + env(safe-area-inset-bottom));
  background: transparent;
  z-index: 999;
  padding-bottom: env(safe-area-inset-bottom);
}

.page::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  top: 122rpx;
  bottom: 0;
  background: #ffffff;
  z-index: 0;
}

.group_1 {
  position: relative;
  z-index: 1;
  width: 750rpx;
  background: url('/static/APPIcon/tabbar/tabbar_bg.png') 0rpx 0rpx no-repeat;
  background-size: 750rpx 183rpx;
  padding: 10rpx 101rpx 24rpx 101rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.image-text_1 {
  margin-top: 62rpx;
}

.label_1 {
  width: 80rpx;
  height: 80rpx;
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

.text-group_1.active {
  color: rgba(0, 118, 255, 1);
  font-weight: 500;
}

.group_2 {
  border-radius: 50%;
  padding: 0;
}

.image-text_2 {
}

.image_1 {
  width: 160rpx;
  height: 160rpx;
  margin-bottom: 20rpx;
  margin-left: 10rpx;
}

.text-group_2 {
  overflow-wrap: break-word;
  color: rgba(255, 255, 255, 1);
  font-size: 24rpx;
  font-family: PingFangSC-Medium;
  font-weight: 500;
  text-align: center;
  white-space: nowrap;
  line-height: 40rpx;
  align-self: center;
  margin-top: 12rpx;
}

.image-text_3 {
  margin-top: 62rpx;
}

.label_2 {
  width: 80rpx;
  height: 80rpx;
  margin-left: 4rpx;
}

.text-group_3 {
  overflow-wrap: break-word;
  color: rgba(102, 102, 102, 1);
  font-size: 24rpx;
  font-family: PingFangSC-Medium;
  font-weight: 500;
  text-align: center;
  white-space: nowrap;
  line-height: 32rpx;
  margin-top: 10rpx;
}

.text-group_3.active {
  color: rgba(0, 118, 255, 1);
}

.image_2 {
  width: 750rpx;
  height: 68rpx;
}

.flex-col {
  display: flex;
  flex-direction: column;
}

.flex-row {
  display: flex;
  flex-direction: row;
}

.justify-between {
  display: flex;
  justify-content: space-between;
}
</style>
