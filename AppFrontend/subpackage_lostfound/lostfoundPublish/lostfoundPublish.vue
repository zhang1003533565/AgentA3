<template>
  <view class="page-root">
    <view class="screen">
      <view class="container">
        <nav-bar title="发布商品" :fixed="true" :placeholder="true" />

        <scroll-view scroll-y class="page-body">
          <view class="section">
            <view class="section-title">商品图片</view>
            <view class="upload-list">
              <view v-for="(img, index) in form.images" :key="img" class="upload-item">
                <image class="upload-img" :src="img" mode="aspectFill" @click="previewImage(img)" />
                <view class="remove" @click.stop="removeImage(index)">×</view>
              </view>
              <view v-if="form.images.length < 9" class="upload-add" @click="chooseImage">
                <text class="plus">＋</text>
                <text>添加图片</text>
              </view>
            </view>
            <view class="hint">{{ form.images.length }}/9，至少上传 1 张</view>
          </view>

          <view class="section">
            <view class="section-title">基本信息</view>
            <view class="field">
              <input v-model.trim="form.title" class="input" maxlength="50" placeholder="商品名称，4-50个字" />
            </view>
            <view class="field">
              <textarea
                v-model.trim="form.description"
                class="textarea"
                maxlength="500"
                placeholder="描述商品成色、配件、购买时间等，至少10个字"
              />
            </view>
          </view>

          <view class="section">
            <view class="section-title">价格</view>
            <view class="segmented">
              <view
                v-for="mode in priceModes"
                :key="mode.value"
                class="segment"
                :class="{ active: form.priceMode === mode.value }"
                @click="form.priceMode = mode.value"
              >
                {{ mode.label }}
              </view>
            </view>
            <view v-if="form.priceMode !== 'face'" class="field">
              <input v-model="form.price" class="input" type="digit" placeholder="请输入售价" />
            </view>
            <view class="switch-row">
              <text>可议价</text>
              <switch :checked="form.negotiable" color="#5c8ab8" @change="onNegotiableChange" />
            </view>
          </view>

          <view class="section">
            <view class="section-title">分类</view>
            <view class="option-grid">
              <view
                v-for="cat in categories"
                :key="cat.id"
                class="option"
                :class="{ active: String(form.categoryId) === String(cat.id) }"
                @click="selectCategory(cat)"
              >
                {{ cat.name }}
              </view>
            </view>
            <view v-if="subcategories.length" class="sub-list">
              <view
                v-for="sub in subcategories"
                :key="sub.id"
                class="sub"
                :class="{ active: String(form.subcategoryId) === String(sub.id) }"
                @click="form.subcategoryId = sub.id"
              >
                {{ sub.name }}
              </view>
            </view>
          </view>

          <view class="section">
            <view class="section-title">成色</view>
            <view class="condition-list">
              <view
                v-for="condition in conditionOptions"
                :key="condition.value"
                class="condition"
                :class="{ active: form.condition === condition.value }"
                @click="form.condition = condition.value"
              >
                <text>{{ condition.label }}</text>
                <text>{{ condition.desc }}</text>
              </view>
            </view>
          </view>

          <view class="section">
            <view class="section-title">取货信息</view>
            <view class="option-grid">
              <view
                v-for="campus in campusOptions"
                :key="campus.id"
                class="option"
                :class="{ active: form.campusId === campus.id }"
                @click="selectCampus(campus)"
              >
                {{ campus.name }}
              </view>
            </view>
            <view class="field">
              <input v-model.trim="form.tradeLocation" class="input" maxlength="40" placeholder="交易区域，例如：图书馆东门" />
            </view>
            <view class="field">
              <input v-model.trim="form.pickupPoint" class="input" maxlength="60" placeholder="具体取货点，例如：一食堂门口" />
            </view>
          </view>

          <button class="submit" :loading="submitting" :disabled="submitting" @click="submit">
            发布商品
          </button>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { createSecondhandItem } from '@/api/secondhand'
import { getUploadErrorMessage, uploadImages } from '@/utils/upload'
import { MARKET_CATEGORIES } from '../utils/marketCategories'

const CAMPUS_OPTIONS = [
  { id: 'main', name: '主校区' },
  { id: 'east', name: '东校区' },
  { id: 'west', name: '西校区' },
  { id: 'north', name: '北校区' }
]

export default {
  components: {
    NavBar
  },
  data() {
    const firstCategory = MARKET_CATEGORIES[0]
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
        { value: 1, label: '全新', desc: '未拆封或未使用' },
        { value: 2, label: '几乎全新', desc: '使用痕迹很少' },
        { value: 3, label: '轻微使用', desc: '功能完好' },
        { value: 4, label: '明显使用', desc: '有可见磨损' },
        { value: 5, label: '功能正常', desc: '适合低价转手' }
      ],
      form: {
        title: '',
        description: '',
        images: [],
        priceMode: 'fixed',
        price: '',
        negotiable: false,
        categoryId: firstCategory?.id || 1,
        subcategoryId: firstCategory?.children?.[0]?.id || '',
        condition: 2,
        campusId: CAMPUS_OPTIONS[0].id,
        campusName: CAMPUS_OPTIONS[0].name,
        tradeLocation: '',
        pickupPoint: ''
      }
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
  methods: {
    selectCategory(category) {
      this.form.categoryId = category.id
      this.form.subcategoryId = category.children?.[0]?.id || ''
    },
    selectCampus(campus) {
      this.form.campusId = campus.id
      this.form.campusName = campus.name
    },
    onNegotiableChange(event) {
      this.form.negotiable = event.detail.value
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
    previewImage(src) {
      uni.previewImage({
        urls: this.form.images,
        current: src
      })
    },
    validate() {
      if (this.form.title.length < 4 || this.form.title.length > 50) {
        return '商品名称需为4-50个字'
      }
      if (this.form.description.length < 10 || this.form.description.length > 500) {
        return '商品描述需为10-500个字'
      }
      if (!this.form.images.length) {
        return '至少上传一张商品图片'
      }
      if (this.form.priceMode !== 'face' && (!this.form.price || Number(this.form.price) <= 0)) {
        return '请输入正确售价'
      }
      if (!this.form.categoryId) {
        return '请选择商品分类'
      }
      if (!this.form.tradeLocation && !this.form.pickupPoint) {
        return '请填写交易区域或取货点'
      }
      return ''
    },
    buildPayload() {
      const price = this.form.priceMode === 'face' ? 0 : Number(this.form.price)
      const notes = []
      if (this.form.priceMode === 'negotiable' || this.form.negotiable) notes.push('价格可议')
      if (this.form.priceMode === 'face') notes.push('免费赠送')
      return {
        categoryId: Number(this.form.categoryId),
        subcategoryId: this.form.subcategoryId,
        title: this.form.title.trim(),
        description: [this.form.description.trim(), ...notes].join(notes.length ? '\n' : ''),
        images: this.form.images,
        price,
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
        await createSecondhandItem(this.buildPayload())
        uni.showToast({ title: '发布成功', icon: 'success' })
        setTimeout(() => uni.navigateBack(), 900)
      } catch (e) {
        console.error('发布商品失败', e)
        uni.showToast({ title: '发布失败，请稍后重试', icon: 'none' })
      } finally {
        this.submitting = false
        uni.hideLoading()
      }
    }
  }
}
</script>

<style scoped>
.page-root,
.screen {
  width: 100%;
  min-height: 100vh;
  background: #f0f5fa;
}

.container {
  width: 100%;
  max-width: 430px;
  min-height: 100vh;
  margin: 0 auto;
  padding: 0 16rpx;
  box-sizing: border-box;
  background: #e8f0f8;
}

.page-body {
  height: calc(100vh - 88rpx);
  padding: 28rpx 0 56rpx;
  box-sizing: border-box;
}

.section {
  margin-bottom: 24rpx;
  padding: 28rpx;
  border-radius: 20rpx;
  background: #fff;
  box-shadow: 0 6rpx 18rpx rgba(43, 68, 94, 0.08);
}

.section-title {
  margin-bottom: 20rpx;
  color: #172331;
  font-size: 28rpx;
  font-weight: 800;
}

.upload-list {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.upload-item,
.upload-add {
  width: 148rpx;
  height: 148rpx;
  border-radius: 16rpx;
  overflow: hidden;
  position: relative;
}

.upload-img {
  width: 100%;
  height: 100%;
}

.upload-add {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  color: #6f8294;
  font-size: 22rpx;
  border: 2rpx dashed #bfd0df;
  background: #f4f8fb;
}

.plus {
  font-size: 40rpx;
  line-height: 1;
}

.remove {
  position: absolute;
  top: 8rpx;
  right: 8rpx;
  width: 34rpx;
  height: 34rpx;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.58);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24rpx;
}

.hint {
  margin-top: 14rpx;
  color: #8a99a8;
  font-size: 22rpx;
}

.field {
  margin-top: 16rpx;
  border-radius: 16rpx;
  background: #f4f8fb;
  overflow: hidden;
}

.input,
.textarea {
  width: 100%;
  box-sizing: border-box;
  padding: 20rpx 22rpx;
  color: #172331;
  font-size: 26rpx;
}

.textarea {
  height: 200rpx;
  line-height: 1.6;
}

.segmented,
.option-grid,
.sub-list {
  display: flex;
  flex-wrap: wrap;
  gap: 14rpx;
}

.segment,
.option,
.sub {
  padding: 14rpx 20rpx;
  border-radius: 999rpx;
  background: #f1f6fa;
  color: #64778a;
  font-size: 24rpx;
  font-weight: 700;
}

.segment.active,
.option.active,
.sub.active {
  background: #5c8ab8;
  color: #fff;
}

.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 18rpx;
  color: #172331;
  font-size: 26rpx;
  font-weight: 700;
}

.sub-list {
  margin-top: 18rpx;
}

.condition-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14rpx;
}

.condition {
  padding: 18rpx;
  border-radius: 16rpx;
  background: #f4f8fb;
  border: 2rpx solid transparent;
}

.condition.active {
  border-color: #5c8ab8;
  background: #edf5fc;
}

.condition text:first-child {
  display: block;
  color: #172331;
  font-size: 25rpx;
  font-weight: 800;
}

.condition text:last-child {
  display: block;
  margin-top: 6rpx;
  color: #7d8c9c;
  font-size: 21rpx;
}

.submit {
  width: 100%;
  height: 88rpx;
  margin: 20rpx 0 32rpx;
  border-radius: 24rpx;
  background: linear-gradient(135deg, #7ba8d4, #5c8ab8);
  color: #fff;
  font-size: 30rpx;
  font-weight: 900;
}

.submit::after {
  border: none;
}
</style>
