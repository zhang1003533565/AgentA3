<template>
  <view class="page-root">
    <view class="screen">
      <view class="container">
        <common-page-header title="发布闲置" :fixed="true" :placeholder="true" :showBack="true" />
        
        <scroll-view scroll-y class="page-body pub-body" :scroll-into-view="scrollTarget" scroll-with-animation>
          <view
            id="publish-field-images"
            class="section photo-section"
            :class="{ 'section--error': fieldErrors.images, 'shake-once': shakeField === 'images' }"
          >
            <view class="section-head">
              <text class="section-title" :class="{ 'is-error-text': fieldErrors.images }">图片</text>
              <text class="section-hint">{{ publishForm.images.length }}/9</text>
            </view>
            <view v-if="publishForm.images.length" class="photo-board">
              <view
                v-for="(img, index) in publishForm.images"
                :key="index"
                class="photo-item"
                :class="{ 'photo-item--cover': index === 0 }"
              >
                <image class="upload-preview" :src="img" mode="aspectFill" @click="previewImg(img)" />
                <view v-if="index === 0" class="cover-mark">封面</view>
                <view class="upload-delete" @click.stop="removeImg(index)">×</view>
              </view>
            </view>
            <view
              v-if="publishForm.images.length < 9"
              class="upload-add"
              :class="{ 'upload-add--error': fieldErrors.images }"
              @click="choosePublishImage"
            >
              <text class="upload-icon">+</text>
              <view class="upload-copy">
                <text class="upload-title">{{ publishForm.images.length ? '继续添加商品图片' : '添加商品图片' }}</text>
                <text class="upload-text">首张图片作为封面</text>
              </view>
            </view>
            <text v-if="fieldErrors.images" class="field-error-text">{{ fieldErrors.images }}</text>
          </view>

          <view class="section">
            <view
              id="publish-field-name"
              class="field-line"
              :class="{ 'field-line--error': fieldErrors.name, 'shake-once': shakeField === 'name' }"
            >
              <text class="field-label" :class="{ 'is-error-text': fieldErrors.name }">商品名称</text>
              <input class="field-input" v-model="publishForm.name" placeholder="请输入商品名称" @input="clearFieldError('name')" />
            </view>
            <text v-if="fieldErrors.name" class="field-error-text field-error-text--line">{{ fieldErrors.name }}</text>
            <view
              id="publish-field-price"
              class="field-line"
              :class="{ 'field-line--error': fieldErrors.price, 'shake-once': shakeField === 'price' }"
            >
              <text class="field-label" :class="{ 'is-error-text': fieldErrors.price }">价格</text>
              <input
                class="field-input price-input"
                v-model="publishForm.price"
                type="number"
                placeholder="输入价格，0表示免费"
                @input="clearFieldError('price')"
              />
            </view>
            <text v-if="fieldErrors.price" class="field-error-text field-error-text--line">{{ fieldErrors.price }}</text>
          </view>

          <view
            id="publish-field-desc"
            class="section"
            :class="{ 'section--error': fieldErrors.desc, 'shake-once': shakeField === 'desc' }"
          >
            <view class="section-head">
              <text class="section-title" :class="{ 'is-error-text': fieldErrors.desc }">描述</text>
              <text class="section-hint">至少10字</text>
            </view>
            <textarea
              class="desc-input"
              :class="{ 'desc-input--error': fieldErrors.desc }"
              v-model="publishForm.desc"
              placeholder="补充使用情况、瑕疵、配件等信息"
              @input="clearFieldError('desc')"
            />
            <text v-if="fieldErrors.desc" class="field-error-text">{{ fieldErrors.desc }}</text>
          </view>

          <view class="section">
            <view class="section-head">
              <text class="section-title">新旧程度</text>
            </view>
            <view class="condition-grid">
              <view
                v-for="c in conditionOptions"
                :key="c.value"
                class="condition-card"
                :class="{ active: publishForm.condition === c.value }"
                @click="publishForm.condition = c.value"
              >
                <text class="condition-name">{{ c.label }}</text>
                <text class="condition-desc">{{ c.desc }}</text>
              </view>
            </view>
          </view>

          <view
            id="publish-field-category"
            class="section"
            :class="{ 'section--error': fieldErrors.category, 'shake-once': shakeField === 'category' }"
          >
            <view class="section-head">
              <text class="section-title" :class="{ 'is-error-text': fieldErrors.category }">分类</text>
            </view>
            <text class="category-level-label">一级分类</text>
            <view class="category-grid category-grid--primary">
              <view
                v-for="cat in categories"
                :key="cat.id"
                class="category-card category-card--primary"
                :class="{ active: selectedCategoryLevel1Id === cat.id, 'category-card--error': fieldErrors.category }"
                @click="selectCategoryLevel1(cat.id)"
              >
                {{ cat.name }}
              </view>
              <view v-if="!categories.length" class="category-card disabled">暂无可用分类</view>
            </view>
            <text v-if="currentCategoryChildren.length" class="category-level-label category-level-label--sub">二级分类</text>
            <view v-if="currentCategoryChildren.length" class="category-grid category-grid--sub">
              <view
                v-for="cat in currentCategoryChildren"
                :key="cat.id"
                class="category-card category-card--sub"
                :class="{ active: publishForm.cat === cat.id, 'category-card--error': fieldErrors.category }"
                @click="selectCategoryLevel2(cat.id)"
              >
                {{ cat.name }}
              </view>
            </view>
            <text v-if="fieldErrors.category" class="field-error-text">{{ fieldErrors.category }}</text>
          </view>

          <view class="section">
            <view class="section-head">
              <text class="section-title">取货地点</text>
            </view>
            <text class="sub-label">常用地点</text>
            <view class="pickup-options">
              <view
                v-for="point in pickupOptions"
                :key="point.value"
                class="pickup-option"
                :class="{ active: publishForm.pickupPoint === point.value }"
                @click="selectPickupPoint(point.value)"
              >{{ point.label }}</view>
            </view>
            <view class="pickup-line">
              <input class="field-input" v-model="publishForm.pickupPoint" placeholder="例如：三食堂门口、XX宿舍楼下" maxlength="50" />
            </view>
          </view>

          <view class="section contact-section">
            <view class="section-head">
              <text class="section-title">联系方式</text>
            </view>
            <view class="chat-tip">
              <text class="chat-tip-title">平台聊天</text>
              <text class="chat-tip-desc">默认通过站内消息联系。</text>
              <text class="chat-tip-desc">交易确认后可交换联系方式。</text>
            </view>
          </view>

          <button class="pbtn" @click="publish">发布闲置</button>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script>
import CommonPageHeader from '@/components/common-page-header/common-page-header.vue'
import { createSecondhandItem } from '@/api/secondhand'
import { getUploadErrorMessage, uploadImages } from '@/utils/upload'
import { MARKET_CATEGORIES } from '@/subpackage_lostfound/utils/marketCategories.js'

const COMMON_PICKUP_POINTS = [
  { value: '宿舍楼下', label: '宿舍楼下' },
  { value: '食堂门口', label: '食堂门口' },
  { value: '图书馆', label: '图书馆' },
  { value: '教学楼', label: '教学楼' }
]

export default {
  components: {
    CommonPageHeader
  },
  data() {
    return {
      publishType: 'sell',
      categories: MARKET_CATEGORIES,
      selectedCategoryLevel1Id: MARKET_CATEGORIES[0]?.id || '',
      pickupOptions: COMMON_PICKUP_POINTS,
      conditionOptions: [
        { value: 1, label: '全新', desc: '未拆或基本没用' },
        { value: 2, label: '很新', desc: '看起来很干净' },
        { value: 3, label: '正常使用', desc: '有轻微痕迹' },
        { value: 4, label: '明显使用', desc: '瑕疵已说明' },
        { value: 5, label: '配件/零件', desc: '适合自取研究' }
      ],
      publishForm: {
        name: '',
        price: '',
        desc: '',
        cat: '',
        images: [],
        pickupPoint: '',
        condition: 2
      },
      fieldErrors: {
        images: '',
        name: '',
        price: '',
        desc: '',
        category: ''
      },
      scrollTarget: '',
      shakeField: ''
    }
  },
  computed: {
    currentCategoryChildren() {
      const current = this.categories.find((item) => item.id === this.selectedCategoryLevel1Id)
      return Array.isArray(current?.children) ? current.children : []
    }
  },
  async onLoad(options) {
    if (options.type) {
      this.publishType = options.type
    }
    this.ensureCategorySelection()
  },
  methods: {
    selectPickupPoint(value) {
      this.publishForm.pickupPoint = this.publishForm.pickupPoint === value ? '' : value
    },
    ensureCategorySelection() {
      if (!this.selectedCategoryLevel1Id && this.categories.length) {
        this.selectedCategoryLevel1Id = this.categories[0].id
      }
      if (!this.publishForm.cat && this.currentCategoryChildren.length) {
        this.publishForm.cat = this.currentCategoryChildren[0].id
      }
    },
    selectCategoryLevel1(id) {
      this.selectedCategoryLevel1Id = id
      const current = this.categories.find((item) => item.id === id)
      const children = Array.isArray(current?.children) ? current.children : []
      this.publishForm.cat = children[0]?.id || ''
      this.clearFieldError('category')
    },
    selectCategoryLevel2(id) {
      this.publishForm.cat = id
      this.clearFieldError('category')
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
            this.clearFieldError('images')
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
    clearFieldError(field) {
      if (this.fieldErrors[field]) {
        this.fieldErrors[field] = ''
      }
    },
    resetFieldErrors() {
      Object.keys(this.fieldErrors).forEach((key) => {
        this.fieldErrors[key] = ''
      })
    },
    focusInvalidField(field) {
      const targetMap = {
        images: 'publish-field-images',
        name: 'publish-field-name',
        price: 'publish-field-price',
        desc: 'publish-field-desc',
        category: 'publish-field-category'
      }
      this.scrollTarget = ''
      this.shakeField = ''
      this.$nextTick(() => {
        this.scrollTarget = targetMap[field] || ''
        this.shakeField = field
        setTimeout(() => {
          this.shakeField = ''
        }, 220)
      })
    },
    validatePublishForm() {
      this.resetFieldErrors()
      const title = this.publishForm.name.trim()
      const desc = this.publishForm.desc.trim()
      const priceText = String(this.publishForm.price).trim()
      const price = Number(priceText)
      const errors = []
      if (!this.publishForm.images.length) {
        errors.push({ field: 'images', message: '至少上传一张图片' })
      }
      if (title.length < 4) {
        errors.push({ field: 'name', message: '商品名称至少4个字' })
      } else if (title.length > 50) {
        errors.push({ field: 'name', message: '商品名称最多50字' })
      }
      if (priceText === '') {
        errors.push({ field: 'price', message: '请输入价格，0表示免费' })
      } else if (Number.isNaN(price) || price < 0) {
        errors.push({ field: 'price', message: '价格需为大于等于0的数字' })
      }
      if (desc.length < 10) {
        errors.push({ field: 'desc', message: '商品描述至少10个字' })
      }
      const categoryId = Number(this.publishForm.cat)
      if (!categoryId || Number.isNaN(categoryId)) {
        errors.push({ field: 'category', message: '请选择有效分类' })
      }
      errors.forEach((error) => {
        this.fieldErrors[error.field] = error.message
      })
      if (errors.length) {
        const first = errors[0]
        uni.showToast({ title: first.message, icon: 'none' })
        this.focusInvalidField(first.field)
        return null
      }
      return { title, desc, price, categoryId }
    },
    async publish() {
      const validated = this.validatePublishForm()
      if (!validated) return
      try {
        uni.showLoading({ title: '发布中...' })
        await createSecondhandItem({
          categoryId: validated.categoryId,
          title: validated.title,
          description: validated.desc,
          images: this.publishForm.images,
          price: validated.price,
          condition: this.publishForm.condition,
          location: this.publishForm.pickupPoint.trim() || '校内自提'
        })
        uni.showToast({ title: '发布成功！', icon: 'success' })
        setTimeout(() => {
          uni.navigateBack()
        }, 1000)
      } catch (error) {
        const msg = error?.data?.msg || error?.msg || error?.message || '发布失败'
        uni.showToast({ title: msg, icon: 'none' })
        console.error('发布失败', error)
      } finally {
        uni.hideLoading()
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.page-root {
  width: 100%;
  min-height: 100vh;
  background: #F7F7F9;
}

.screen {
  width: 100%;
  background: #F7F7F9;
  min-height: 100vh;
}

.container {
  width: 100%;
  margin: 0 auto;
  box-sizing: border-box;
  padding: 0 24rpx;
  background: #F7F7F9;
  min-height: 100vh;
  position: relative;
}

.page-body {
  flex: 1;
  overflow-y: auto;
}

.pub-body {
  padding: 24rpx 0 44rpx;
}

.section {
  margin-bottom: 18rpx;
  padding: 0 24rpx;
  border-radius: 24rpx;
  background: #FFFFFF;
  border: 1rpx solid transparent;
  box-sizing: border-box;
  box-shadow: 0 6rpx 18rpx rgba(92, 122, 153, 0.06);
}

.section--error {
  border-color: rgba(209, 67, 67, 0.42);
  background: linear-gradient(0deg, rgba(209, 67, 67, 0.035), rgba(209, 67, 67, 0.035)), #FFFFFF;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  min-height: 82rpx;
}

.section-title {
  font-size: 26rpx;
  font-weight: 700;
  color: #1D1D1F;
  line-height: 1.2;
}

.section-hint {
  font-size: 22rpx;
  color: #8E8E93;
  white-space: nowrap;
}

.photo-section {
  padding-bottom: 22rpx;
}

.photo-board {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10rpx;
  margin-bottom: 12rpx;
}

.photo-item {
  position: relative;
  width: 100%;
  aspect-ratio: 1;
  border-radius: 16rpx;
  overflow: hidden;
  background: #F1F3F5;
}

.photo-item--cover {
  grid-column: span 1;
  grid-row: span 1;
}

.upload-preview {
  width: 100%;
  height: 100%;
}

.cover-mark {
  position: absolute;
  left: 8rpx;
  bottom: 8rpx;
  padding: 3rpx 8rpx;
  border-radius: 6rpx;
  background: rgba(92, 122, 153, 0.86);
  color: #FFFFFF;
  font-size: 20rpx;
  font-weight: 500;
}

.upload-delete {
  position: absolute;
  top: 6rpx;
  right: 6rpx;
  width: 32rpx;
  height: 32rpx;
  background: rgba(29, 29, 31, 0.56);
  border-radius: 50%;
  color: #fff;
  font-size: 24rpx;
  line-height: 32rpx;
  text-align: center;
}

.upload-add {
  display: flex;
  align-items: center;
  gap: 18rpx;
  min-height: 112rpx;
  padding: 0 20rpx;
  border-radius: 18rpx;
  border: 1rpx solid #E8EEF4;
  background: #FFFFFF;
  box-sizing: border-box;
  box-shadow: 0 6rpx 18rpx rgba(92, 122, 153, 0.06);
}

.upload-add--error {
  border-color: rgba(209, 67, 67, 0.5);
  background: rgba(209, 67, 67, 0.05);
}

.upload-icon {
  width: 54rpx;
  height: 54rpx;
  border-radius: 16rpx;
  background: rgba(92, 122, 153, 0.08);
  border: 1rpx solid rgba(92, 122, 153, 0.14);
  color: #5C7A99;
  font-size: 36rpx;
  font-weight: 300;
  line-height: 50rpx;
  text-align: center;
  flex-shrink: 0;
}

.upload-copy {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
  min-width: 0;
}

.upload-title {
  font-size: 26rpx;
  font-weight: 750;
  color: #1D1D1F;
}

.upload-text {
  font-size: 22rpx;
  color: #8E8E93;
  line-height: 1.35;
}

.field-line {
  display: flex;
  align-items: center;
  min-height: 86rpx;
  border-bottom: 1rpx solid #EEEEEE;
}

.field-line--error {
  margin-top: 8rpx;
  padding: 0 16rpx;
  border: 1rpx solid rgba(209, 67, 67, 0.48);
  border-radius: 16rpx;
  background: rgba(209, 67, 67, 0.045);
}

.field-line:last-child {
  border-bottom: none;
}

.field-label {
  width: 152rpx;
  flex-shrink: 0;
  font-size: 25rpx;
  font-weight: 600;
  color: #1D1D1F;
}

.field-input {
  flex: 1;
  min-width: 0;
  height: 86rpx;
  font-size: 26rpx;
  color: #1D1D1F;
  background: transparent;
}

.price-input {
  font-weight: 700;
}

.desc-input {
  width: 100%;
  height: 208rpx;
  padding: 18rpx 0 24rpx;
  font-size: 26rpx;
  line-height: 1.6;
  color: #1D1D1F;
  background: #FFFFFF;
  box-sizing: border-box;
}

.desc-input--error {
  padding: 18rpx 16rpx 24rpx;
  border: 1rpx solid rgba(209, 67, 67, 0.48);
  border-radius: 16rpx;
  background: rgba(209, 67, 67, 0.045);
}

.condition-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10rpx;
  padding-bottom: 20rpx;
}

.condition-card {
  min-height: 86rpx;
  padding: 14rpx 16rpx;
  border-radius: 16rpx;
  background: #F8FAFC;
  border: 1rpx solid transparent;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.condition-card.active {
  background: rgba(92, 122, 153, 0.08);
  border-color: rgba(92, 122, 153, 0.28);
}

.condition-name {
  font-size: 24rpx;
  font-weight: 700;
  color: #1D1D1F;
}

.condition-desc {
  font-size: 21rpx;
  color: #8E8E93;
  line-height: 1.3;
}

.condition-card.active .condition-name {
  color: #4A6278;
}

.category-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10rpx;
  padding-bottom: 20rpx;
}

.category-grid--primary {
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 8rpx;
  padding-bottom: 18rpx;
}

.category-grid--sub {
  padding-top: 2rpx;
}

.category-level-label {
  display: block;
  margin: 0 0 12rpx;
  font-size: 22rpx;
  font-weight: 700;
  color: #8E8E93;
  line-height: 1.2;
}

.category-level-label--sub {
  margin-top: 4rpx;
  color: #5C7A99;
}

.category-card {
  min-height: 64rpx;
  padding: 0 16rpx;
  border-radius: 16rpx;
  background: #F8FAFC;
  color: #1D1D1F;
  font-size: 24rpx;
  font-weight: 600;
  display: flex;
  align-items: center;
  box-sizing: border-box;
}

.category-card--primary {
  min-height: 58rpx;
  padding: 0 8rpx;
  border-radius: 14rpx;
  justify-content: center;
  text-align: center;
  font-size: 22rpx;
  color: #5C6470;
  background: transparent;
}

.category-card--sub {
  background: #F8FAFC;
}

.category-card.active {
  background: #5C7A99;
  color: #FFFFFF;
}

.category-card--error {
  border: 1rpx solid rgba(209, 67, 67, 0.38);
}

.category-card.active.category-card--error {
  border-color: rgba(92, 122, 153, 0.28);
}

.category-card.disabled {
  color: #8E8E93;
  background: #F3F5F7;
}

.sub-label {
  display: block;
  margin: 0 0 12rpx;
  font-size: 23rpx;
  font-weight: 600;
  color: #5C7A99;
}

.pickup-options {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10rpx;
  margin-bottom: 18rpx;
}

.pickup-option {
  min-height: 64rpx;
  padding: 0 16rpx;
  border-radius: 16rpx;
  background: #F8FAFC;
  color: #1D1D1F;
  font-size: 24rpx;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
}

.pickup-option.active {
  background: #5C7A99;
  color: #FFFFFF;
}

.pickup-line {
  border-top: 1rpx solid #EEEEEE;
  margin-top: 2rpx;
  padding-top: 0;
}

.contact-section {
  padding-bottom: 0;
}

.chat-tip {
  min-height: 104rpx;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 6rpx;
}

.chat-tip-title {
  font-size: 25rpx;
  font-weight: 600;
  color: #1D1D1F;
}

.chat-tip-desc {
  font-size: 22rpx;
  color: #8E8E93;
  line-height: 1.4;
}

.pbtn {
  width: 100%;
  height: 88rpx;
  border-radius: 24rpx;
  background: #5C7A99;
  color: #FFFFFF;
  font-size: 28rpx;
  font-weight: 700;
  border: none;
  margin: 20rpx 0 20rpx;
  box-shadow: 0 10rpx 24rpx rgba(92, 122, 153, 0.22);
}

.pbtn::after {
  border: none;
}

.is-error-text {
  color: #D14343;
}

.field-error-text {
  display: block;
  margin-top: 12rpx;
  padding-bottom: 12rpx;
  color: #D14343;
  font-size: 22rpx;
  line-height: 1.35;
}

.field-error-text--line {
  padding-left: 16rpx;
}

.shake-once {
  animation: publish-field-shake 220ms ease-out 1;
}

@keyframes publish-field-shake {
  0%, 100% {
    transform: translateX(0);
  }
  25% {
    transform: translateX(-6rpx);
  }
  50% {
    transform: translateX(5rpx);
  }
  75% {
    transform: translateX(-4rpx);
  }
}

</style>
