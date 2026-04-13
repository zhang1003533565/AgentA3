<template>
  <view class="page-root">
    <view class="screen">
      <view class="container">
        <nav-bar title="发布闲置" :fixed="true" :placeholder="true" />
        
        <scroll-view scroll-y class="page-body pub-body">
          <view class="fg">
            <view class="fl">类型</view>
            <view class="opts">
              <view class="opt on">🏷️ 出售闲置</view>
            </view>
          </view>

          <view class="fg">
            <view class="fl">商品图片</view>
            <view class="upload-list">
              <view
                v-for="(img, index) in publishForm.images"
                :key="index"
                class="upload-item preview-item"
              >
                <image class="upload-preview" :src="img" mode="aspectFill" @click="previewImg(img)" />
                <view class="upload-delete" @click.stop="removeImg(index)">×</view>
              </view>
              <view
                v-if="publishForm.images.length < 9"
                class="upload-item upload-add"
                @click="choosePublishImage"
              >
                <text class="upload-icon">🖼️</text>
                <text class="upload-text">添加图片</text>
              </view>
            </view>
          </view>

          <view class="fg">
            <view class="fl">商品名称</view>
            <view class="input-wrap">
              <input class="fi" v-model="publishForm.name" placeholder="起个名字" />
            </view>
          </view>

          <view class="fg">
            <view class="fl">售价（元）</view>
            <view class="input-wrap">
              <input class="fi" v-model="publishForm.price" type="number" placeholder="输入售价" />
            </view>
          </view>

          <view class="fg">
            <view class="fl">商品描述</view>
            <view class="input-wrap">
              <textarea class="ft" v-model="publishForm.desc" placeholder="描述一下情况..." />
            </view>
          </view>

          <view class="fg">
            <view class="fl">分类</view>
            <view class="opts">
              <view
                v-for="cat in categories.filter(c => c.key !== 'all')"
                :key="cat.key"
                class="opt"
                :class="{ on: publishForm.cat === cat.key }"
                @click="publishForm.cat = cat.key"
              >
                {{ cat.label }}
              </view>
            </view>
          </view>

          <view class="fg">
            <view class="fl">微信号</view>
            <view class="input-wrap">
              <input class="fi" v-model="publishForm.phone" placeholder="你的微信号" maxlength="30" />
            </view>
          </view>

          <button class="pbtn" @click="publish">发布闲置</button>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import {
  createSecondhandItem,
  getSecondhandCategories
} from '@/api/secondhand'
import { getUploadErrorMessage, uploadImages } from '@/utils/upload'

const CATEGORIES = [
  { key: '1', label: '数码产品' }
]

export default {
  components: {
    NavBar
  },
  data() {
    return {
      publishType: 'sell',
      categories: CATEGORIES,
      publishForm: {
        name: '',
        price: '',
        desc: '',
        cat: '',
        phone: '',
        images: []
      }
    }
  },
  async onLoad(options) {
    if (options.type) {
      this.publishType = options.type
    }
    await this.loadCategories()
  },
  methods: {
    async loadCategories() {
      try {
        const res = await getSecondhandCategories()
        const records = Array.isArray(res?.data) ? res.data : []
        this.categories = records.map((item) => ({
          key: String(item.id),
          label: item.categoryName
        }))
        if (!this.publishForm.cat && this.categories.length) {
          this.publishForm.cat = this.categories[0].key
        }
      } catch (e) {
        console.error('加载分类失败', e)
      }
    },
    choosePublishImage() {
      uni.chooseImage({
        count: 9 - this.publishForm.images.length,
        sizeType: ['compressed'],
        sourceType: ['album', 'camera'],
        success: async (res) => {
          const files = res.tempFilePaths
          try {
            const urls = await uploadImages(files)
            this.publishForm.images = [...this.publishForm.images, ...urls]
          } catch (e) {
            uni.showToast({ title: getUploadErrorMessage(e), icon: 'none' })
          }
        }
      })
    },
    removeImg(index) {
      this.publishForm.images.splice(index, 1)
    },
    previewImg(src) {
      uni.previewImage({
        urls: this.publishForm.images,
        current: src
      })
    },
    async publish() {
      if (!this.publishForm.name.trim()) {
        uni.showToast({ title: '请输入名称', icon: 'none' })
        return
      }
      if (!this.publishForm.desc.trim() || this.publishForm.desc.trim().length < 10) {
        uni.showToast({ title: '描述至少10个字', icon: 'none' })
        return
      }
      if (!this.publishForm.price || Number(this.publishForm.price) <= 0) {
        uni.showToast({ title: '请输入正确售价', icon: 'none' })
        return
      }
      if (!this.publishForm.images.length) {
        uni.showToast({ title: '至少上传一张图片', icon: 'none' })
        return
      }
      try {
        uni.showLoading({ title: '发布中...' })
        await createSecondhandItem({
          categoryId: Number(this.publishForm.cat),
          title: this.publishForm.name.trim(),
          description: this.publishForm.desc.trim(),
          images: this.publishForm.images,
          price: Number(this.publishForm.price),
          condition: 2,
          location: '校内自提'
        })
        uni.showToast({ title: '发布成功！', icon: 'success' })
        setTimeout(() => {
          uni.navigateBack()
        }, 1000)
      } catch (error) {
        console.error('发布失败', error)
      } finally {
        uni.hideLoading()
      }
    }
  }
}
</script>

<style lang="scss">
.page-root {
  width: 100%;
  min-height: 100vh;
  background: #F0F5FA;
}

.screen {
  width: 100%;
  background: #F0F5FA;
  min-height: 100vh;
}

.container {
  width: 100%;
  margin: 0 auto;
  box-sizing: border-box;
  padding: 0 48rpx;
  background: #E8F0F8;
  min-height: 100vh;
  position: relative;
}

.page-body {
  flex: 1;
  overflow-y: auto;
}

.pub-body {
  padding: 32rpx 24rpx;
}

.fg {
  margin-bottom: 32rpx;
}

.fl {
  font-size: 26rpx;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.7);
  margin-bottom: 12rpx;
}

.opts {
  display: flex;
  gap: 12rpx;
  flex-wrap: wrap;
  justify-content: flex-start;
}

.opt {
  padding: 14rpx 20rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.6);
  font-size: 24rpx;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.5);
  text-align: center;
  white-space: nowrap;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
}

.opt.on {
  background: #7ba8d4;
  color: #fff;
}

.input-wrap {
  background: #fff;
  border-radius: 20rpx;
  overflow: hidden;
}

.fi {
  width: 100%;
  padding: 20rpx 24rpx;
  font-size: 26rpx;
  background: transparent;
}

.ft {
  width: 100%;
  height: 200rpx;
  padding: 20rpx 24rpx;
  font-size: 26rpx;
  background: transparent;
}

.upload-list {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.upload-item {
  width: 160rpx;
  height: 160rpx;
  border-radius: 16rpx;
  overflow: hidden;
}

.preview-item {
  position: relative;
}

.upload-preview {
  width: 100%;
  height: 100%;
}

.upload-delete {
  position: absolute;
  top: 8rpx;
  right: 8rpx;
  width: 36rpx;
  height: 36rpx;
  background: rgba(0, 0, 0, 0.5);
  border-radius: 50%;
  color: #fff;
  font-size: 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.upload-add {
  background: rgba(255, 255, 255, 0.6);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  border: 2rpx dashed rgba(0, 0, 0, 0.15);
}

.upload-icon {
  font-size: 40rpx;
}

.upload-text {
  font-size: 20rpx;
  color: rgba(0, 0, 0, 0.4);
}

.pbtn {
  width: 100%;
  height: 88rpx;
  border-radius: 24rpx;
  background: linear-gradient(135deg, #7ba8d4, #5c8ab8);
  color: #fff;
  font-size: 30rpx;
  font-weight: 800;
  border: none;
  margin-top: 20rpx;
}

.pbtn::after {
  border: none;
}
</style>
