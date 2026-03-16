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
