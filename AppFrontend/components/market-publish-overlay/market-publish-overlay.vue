<template>
  <view v-if="visible" class="overlay">
    <view class="mask" @click="close" />
    <view class="panel">
      <view class="grabber" />
      <view class="header">
        <view>
          <view class="title">发布商品</view>
          <view class="subtitle">填写基础信息后进入校园市集</view>
        </view>
        <button class="close" @click="close">×</button>
      </view>

      <scroll-view scroll-y class="content">
        <view class="block">
          <view class="label">商品图片</view>
          <view class="upload-list">
            <view v-for="(img, index) in form.images" :key="img" class="upload-item">
              <image :src="img" mode="aspectFill" />
              <view class="remove" @click.stop="removeImage(index)">×</view>
            </view>
            <view v-if="form.images.length < 9" class="upload-add" @click="chooseImage">
              <text>＋</text>
              <text>添加</text>
            </view>
          </view>
        </view>

        <view class="block">
          <view class="label">商品名称</view>
          <input v-model.trim="form.title" class="input" maxlength="50" placeholder="4-50个字" />
        </view>

        <view class="block">
          <view class="label">商品描述</view>
          <textarea v-model.trim="form.description" class="textarea" maxlength="500" placeholder="至少10个字" />
        </view>

        <view class="block">
          <view class="label">价格</view>
          <view class="chips">
            <view
              v-for="mode in priceModes"
              :key="mode.value"
              class="chip"
              :class="{ active: form.priceMode === mode.value }"
              @click="form.priceMode = mode.value"
            >
              {{ mode.label }}
            </view>
          </view>
          <input v-if="form.priceMode !== 'face'" v-model="form.price" class="input" type="digit" placeholder="售价" />
        </view>

        <view class="block">
          <view class="label">分类</view>
          <view class="chips">
            <view
              v-for="cat in categories"
              :key="cat.id"
              class="chip"
              :class="{ active: String(form.categoryId) === String(cat.id) }"
              @click="selectCategory(cat)"
            >
              {{ cat.name }}
            </view>
          </view>
          <view v-if="subcategories.length" class="chips subchips">
            <view
              v-for="sub in subcategories"
              :key="sub.id"
              class="chip subchip"
              :class="{ active: String(form.subcategoryId) === String(sub.id) }"
              @click="form.subcategoryId = sub.id"
            >
              {{ sub.name }}
            </view>
          </view>
        </view>

        <view class="block">
          <view class="label">成色</view>
          <view class="chips">
            <view
              v-for="condition in conditionOptions"
              :key="condition.value"
              class="chip"
              :class="{ active: form.condition === condition.value }"
              @click="form.condition = condition.value"
            >
              {{ condition.label }}
            </view>
          </view>
        </view>

        <view class="block">
          <view class="label">取货信息</view>
          <view class="chips">
            <view
              v-for="campus in campusOptions"
              :key="campus.id"
              class="chip"
              :class="{ active: form.campusId === campus.id }"
              @click="selectCampus(campus)"
            >
              {{ campus.name }}
            </view>
          </view>
          <input v-model.trim="form.tradeLocation" class="input" maxlength="40" placeholder="交易区域" />
          <input v-model.trim="form.pickupPoint" class="input" maxlength="60" placeholder="具体取货点" />
        </view>
      </scroll-view>

      <view class="footer">
        <button class="submit" :loading="submitting" :disabled="submitting" @click="submit">发布</button>
      </view>
    </view>
  </view>
</template>

<script>
import { createSecondhandItem } from '@/api/secondhand'
import { getUploadErrorMessage, uploadImages } from '@/utils/upload'
import { MARKET_CATEGORIES } from '@/subpackage_lostfound/utils/marketCategories'

const CAMPUS_OPTIONS = [
  { id: 'main', name: '主校区' },
  { id: 'east', name: '东校区' },
  { id: 'west', name: '西校区' },
  { id: 'north', name: '北校区' }
]

function createForm() {
  const firstCategory = MARKET_CATEGORIES[0] || {}
  return {
    title: '',
    description: '',
    images: [],
    priceMode: 'fixed',
    price: '',
    categoryId: firstCategory.id || 1,
    subcategoryId: firstCategory.children?.[0]?.id || '',
    condition: 2,
    campusId: CAMPUS_OPTIONS[0].id,
    campusName: CAMPUS_OPTIONS[0].name,
    tradeLocation: '',
    pickupPoint: ''
  }
}

export default {
  name: 'MarketPublishOverlay',
  props: {
    visible: {
      type: Boolean,
      default: false
    }
  },
  emits: ['close', 'published'],
  data() {
    return {
      submitting: false,
      categories: MARKET_CATEGORIES,
      campusOptions: CAMPUS_OPTIONS,
      priceModes: [
        { value: 'fixed', label: '一口价' },
        { value: 'negotiable', label: '面议' },
        { value: 'face', label: '赠送' }
      ],
      conditionOptions: [
        { value: 1, label: '全新' },
        { value: 2, label: '几乎全新' },
        { value: 3, label: '轻微使用' },
        { value: 4, label: '明显使用' },
        { value: 5, label: '功能正常' }
      ],
      form: createForm()
    }
  },
  computed: {
    selectedCategory() {
      return this.categories.find(cat => String(cat.id) === String(this.form.categoryId)) || this.categories[0] || {}
    },
    subcategories() {
      return this.selectedCategory.children || []
    }
  },
  watch: {
    visible(value) {
      if (value) this.form = createForm()
    }
  },
  methods: {
    close() {
      if (!this.submitting) this.$emit('close')
    },
    selectCategory(category) {
      this.form.categoryId = category.id
      this.form.subcategoryId = category.children?.[0]?.id || ''
    },
    selectCampus(campus) {
      this.form.campusId = campus.id
      this.form.campusName = campus.name
    },
    chooseImage() {
      uni.chooseImage({
        count: 9 - this.form.images.length,
        sizeType: ['compressed'],
        sourceType: ['album', 'camera'],
        success: async (res) => {
          try {
            uni.showLoading({ title: '上传中...' })
            const urls = await uploadImages(res.tempFilePaths || [])
            this.form.images = [...this.form.images, ...urls].slice(0, 9)
          } catch (e) {
            uni.showToast({ title: getUploadErrorMessage(e), icon: 'none' })
          } finally {
            uni.hideLoading()
          }
        }
      })
    },
    removeImage(index) {
      this.form.images.splice(index, 1)
    },
    validate() {
      if (this.form.title.length < 4 || this.form.title.length > 50) return '商品名称需为4-50个字'
      if (this.form.description.length < 10 || this.form.description.length > 500) return '商品描述需为10-500个字'
      if (!this.form.images.length) return '至少上传一张图片'
      if (this.form.priceMode !== 'face' && (!this.form.price || Number(this.form.price) <= 0)) return '请输入正确售价'
      if (!this.form.tradeLocation && !this.form.pickupPoint) return '请填写交易区域或取货点'
      return ''
    },
    buildPayload() {
      const notes = []
      if (this.form.priceMode === 'negotiable') notes.push('价格可议')
      if (this.form.priceMode === 'face') notes.push('免费赠送')
      return {
        categoryId: Number(this.form.categoryId),
        subcategoryId: this.form.subcategoryId,
        title: this.form.title.trim(),
        description: [this.form.description.trim(), ...notes].join(notes.length ? '\n' : ''),
        images: this.form.images,
        price: this.form.priceMode === 'face' ? 0 : Number(this.form.price),
        condition: this.form.condition,
        location: this.form.pickupPoint || this.form.tradeLocation || this.form.campusName,
        campusId: this.form.campusId,
        campusName: this.form.campusName,
        tradeLocation: this.form.tradeLocation,
        pickupPoint: this.form.pickupPoint
      }
    },
    async submit() {
      const message = this.validate()
      if (message) {
        uni.showToast({ title: message, icon: 'none' })
        return
      }
      try {
        this.submitting = true
        uni.showLoading({ title: '发布中...' })
        const res = await createSecondhandItem(this.buildPayload())
        uni.showToast({ title: '发布成功', icon: 'success' })
        this.$emit('published', res?.data)
        this.close()
      } catch (e) {
        console.error('发布商品失败', e)
        uni.showToast({ title: '发布失败', icon: 'none' })
      } finally {
        this.submitting = false
        uni.hideLoading()
      }
    }
  }
}
</script>

<style scoped>
.overlay {
  position: fixed;
  inset: 0;
  z-index: 900;
}

.mask {
  position: absolute;
  inset: 0;
  background: rgba(15, 25, 36, 0.46);
}

.panel {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  max-height: 88vh;
  border-radius: 28rpx 28rpx 0 0;
  background: #e8f0f8;
  overflow: hidden;
}

.grabber {
  width: 72rpx;
  height: 8rpx;
  margin: 18rpx auto 0;
  border-radius: 999rpx;
  background: #b7c7d7;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 32rpx;
}

.title {
  color: #172331;
  font-size: 34rpx;
  font-weight: 900;
}

.subtitle {
  margin-top: 6rpx;
  color: #7d8c9c;
  font-size: 22rpx;
}

.close {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  background: #fff;
  color: #546374;
  font-size: 34rpx;
  line-height: 64rpx;
}

.close::after,
.submit::after {
  border: none;
}

.content {
  max-height: calc(88vh - 210rpx);
  padding: 0 24rpx;
  box-sizing: border-box;
}

.block {
  margin-bottom: 20rpx;
  padding: 24rpx;
  border-radius: 20rpx;
  background: #fff;
}

.label {
  margin-bottom: 16rpx;
  color: #172331;
  font-size: 26rpx;
  font-weight: 800;
}

.upload-list,
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 14rpx;
}

.upload-item,
.upload-add {
  width: 136rpx;
  height: 136rpx;
  border-radius: 16rpx;
  overflow: hidden;
  position: relative;
}

.upload-item image {
  width: 100%;
  height: 100%;
}

.upload-add {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #6f8294;
  font-size: 22rpx;
  background: #f4f8fb;
  border: 2rpx dashed #bfd0df;
}

.remove {
  position: absolute;
  top: 8rpx;
  right: 8rpx;
  width: 32rpx;
  height: 32rpx;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.58);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22rpx;
}

.input,
.textarea {
  width: 100%;
  box-sizing: border-box;
  padding: 18rpx 20rpx;
  border-radius: 16rpx;
  background: #f4f8fb;
  color: #172331;
  font-size: 25rpx;
}

.textarea {
  height: 170rpx;
  line-height: 1.6;
}

.chip {
  padding: 12rpx 18rpx;
  border-radius: 999rpx;
  background: #f1f6fa;
  color: #64778a;
  font-size: 23rpx;
  font-weight: 800;
}

.chip.active {
  background: #5c8ab8;
  color: #fff;
}

.subchips {
  margin-top: 14rpx;
}

.subchip {
  font-size: 21rpx;
}

.footer {
  padding: 20rpx 32rpx 44rpx;
  background: #e8f0f8;
}

.submit {
  width: 100%;
  height: 84rpx;
  border-radius: 24rpx;
  background: linear-gradient(135deg, #7ba8d4, #5c8ab8);
  color: #fff;
  font-size: 28rpx;
  font-weight: 900;
}
</style>
