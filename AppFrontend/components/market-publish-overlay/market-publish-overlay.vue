<template>
  <view class="publish-overlay">
    <view class="publish-panel" :class="{ 'publish-panel--visible': visible }">
      <view class="publish-shell">
        <view class="publish-header">
          <view class="publish-close" @click="requestClose">‹</view>
          <text class="publish-title">发布闲置</text>
          <view class="publish-placeholder"></view>
        </view>

        <scroll-view scroll-y class="publish-body" :show-scrollbar="false" :scroll-into-view="scrollTarget" scroll-with-animation>
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
                placeholder="输入参考价格"
                @input="clearFieldError('price')"
              />
            </view>
            <text v-if="fieldErrors.price" class="field-error-text field-error-text--line">{{ fieldErrors.price }}</text>
            <view class="trade-info-box">
              <view class="trade-info-row">
                <view class="trade-info-icon">i</view>
                <view class="trade-info-content">
                  <text class="trade-info-title">交易说明</text>
                  <text class="trade-info-desc">商品价格仅作为参考信息。</text>
                  <text class="trade-info-desc">双方确认交易后在线下完成交易。</text>
                  <text class="trade-info-desc">平台提供信息沟通服务，不参与线下交易。</text>
                  <text class="trade-info-desc">如免费赠送可填写0。</text>
                </view>
              </view>
            </view>
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
                class="option-card condition-card"
                :class="{ active: publishForm.condition === c.value }"
                @click="publishForm.condition = c.value"
              >
                <text class="option-card-title condition-name">{{ c.label }}</text>
                <text class="option-card-desc condition-desc">{{ c.desc }}</text>
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
                class="option-card category-card category-card--primary"
                :class="{ active: selectedCategoryLevel1Id === cat.id, 'option-card--error': fieldErrors.category }"
                @click="selectCategoryLevel1(cat.id)"
              >
                <text class="option-card-title">{{ cat.name }}</text>
              </view>
              <view v-if="!categories.length" class="option-card category-card disabled">
                <text class="option-card-title">暂无可用分类</text>
              </view>
            </view>
            <text v-if="currentCategoryChildren.length" class="category-level-label category-level-label--sub">二级分类</text>
            <view v-if="currentCategoryChildren.length" class="category-grid category-grid--sub">
              <view
                v-for="cat in currentCategoryChildren"
                :key="cat.id"
                class="option-card category-card category-card--sub"
                :class="{ active: publishForm.cat === cat.id, 'option-card--error': fieldErrors.category }"
                @click="selectCategoryLevel2(cat.id)"
              >
                <text class="option-card-title">{{ cat.name }}</text>
              </view>
            </view>
            <text v-if="fieldErrors.category" class="field-error-text">{{ fieldErrors.category }}</text>
          </view>

          <view class="section">
            <view class="section-head">
              <text class="section-title">取货地点</text>
            </view>
            <view class="field-line pickup-line">
              <text class="field-label">地址</text>
              <input class="field-input" v-model="publishForm.pickupPoint" placeholder="例如：三食堂门口、XX宿舍楼下" maxlength="50" />
            </view>
          </view>

          <view class="section contact-section">
            <view class="section-head">
              <text class="section-title">联系方式</text>
            </view>
            <view class="chat-tip">
              <view class="chat-tip-main">
                <text class="chat-tip-title">平台聊天</text>
              </view>
              <text class="chat-tip-desc">默认通过站内消息联系。</text>
              <text class="chat-tip-note">交易确认后，双方可自愿交换联系方式。</text>
            </view>
          </view>

          <button class="pbtn" :disabled="submitting" @click="publish">发布闲置</button>
          <view class="bottom-spacer"></view>
        </scroll-view>
      </view>
    </view>
    <view v-if="publishToastText" class="publish-validation-toast">
      {{ publishToastText }}
    </view>
  </view>
</template>

<script>
import { createSecondhandItem } from '@/api/secondhand'
import { getUploadErrorMessage, uploadImages } from '@/utils/upload'
import { MARKET_CATEGORIES } from '@/subpackage_lostfound/utils/marketCategories.js'

const BACKEND_CATEGORY_ID_MAP = {
  digital: 1,
  textbook: 2,
  clothing: 3,
  dorm: 4,
  other: 5
}

function createDefaultForm() {
  return {
    name: '',
    price: '',
    desc: '',
    cat: '',
    images: [],
    pickupPoint: '',
    condition: 2
  }
}

export default {
  name: 'MarketPublishOverlay',
  props: {
    visible: { type: Boolean, default: false }
  },
  data() {
    return {
      categories: MARKET_CATEGORIES,
      selectedCategoryLevel1Id: MARKET_CATEGORIES[0]?.id || '',
      conditionOptions: [
        { value: 1, label: '全新', desc: '未拆或基本没用' },
        { value: 2, label: '很新', desc: '看起来很干净' },
        { value: 3, label: '正常使用', desc: '有轻微痕迹' },
        { value: 4, label: '明显使用', desc: '瑕疵已说明' },
        { value: 5, label: '配件/零件', desc: '适合自取研究' }
      ],
      submitting: false,
      publishToastText: '',
      publishForm: createDefaultForm(),
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
  mounted() {
    this.ensureCategorySelection()
  },
  methods: {
    requestClose() {
      this.$emit('close')
    },
    resetForm() {
      this.publishForm = createDefaultForm()
      this.selectedCategoryLevel1Id = this.categories[0]?.id || ''
      this.resetFieldErrors()
      this.scrollTarget = ''
      this.shakeField = ''
      this.ensureCategorySelection()
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
    notifyPublishSuccess() {
      uni.$emit('secondhand:item:published')
      const pages = getCurrentPages()
      const currentPage = pages[pages.length - 1]
      if (
        currentPage?.route === 'subpackage_lostfound/lostfoundList/lostfoundList' &&
        typeof currentPage?.$vm?.loadItems === 'function'
      ) {
        currentPage.$vm.loadItems()
      }
    },
    getSubmitCategoryId(level1Id) {
      return parseInt(level1Id, 10) || 0
    },
    showValidationToast(title) {
      console.log('validation toast', title)
      clearTimeout(this.publishToastTimer)
      this.publishToastText = title
      this.publishToastTimer = setTimeout(() => {
        this.publishToastText = ''
      }, 2000)
      return null
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
      console.log('validate start', this.publishForm.name)
      this.resetFieldErrors()
      const title = String(this.publishForm.name || '').trim()
      const rawDescription = String(this.publishForm.desc || '').trim()
      const priceText = String(this.publishForm.price ?? '').trim()
      const price = Number(priceText)
      const errors = []
      const images = Array.isArray(this.publishForm.images) ? this.publishForm.images.filter(Boolean) : []
      if (!images.length) {
        errors.push({ field: 'images', message: '请上传至少一张图片' })
      }
      if (title.length < 4) {
        errors.push({ field: 'name', message: '商品名称至少4个字' })
      } else if (title.length > 50) {
        errors.push({ field: 'name', message: '商品名称最多50字' })
      }
      if (priceText === '') {
        errors.push({ field: 'price', message: '请输入价格' })
      } else if (Number.isNaN(price)) {
        errors.push({ field: 'price', message: '请输入有效价格' })
      } else if (price < 0) {
        errors.push({ field: 'price', message: '价格不能小于0' })
      } else if (price > 99999) {
        errors.push({ field: 'price', message: '价格不能超过99999元' })
      }
      if (rawDescription.length < 10) {
        errors.push({ field: 'desc', message: '商品描述至少10个字' })
      }
      const selectedLevel1 = this.categories.find((item) => item.id === this.selectedCategoryLevel1Id)
      if (!this.selectedCategoryLevel1Id || !selectedLevel1) {
        errors.push({ field: 'category', message: '请选择商品分类' })
      }

      const categoryChildren = Array.isArray(selectedLevel1?.children) ? selectedLevel1.children : []
      if (categoryChildren.length && !this.publishForm.cat) {
        errors.push({ field: 'category', message: '请选择商品分类' })
      }

      const selectedLevel2Id = Number(this.publishForm.cat)
      if (categoryChildren.length && (!selectedLevel2Id || Number.isNaN(selectedLevel2Id))) {
        errors.push({ field: 'category', message: '请选择商品分类' })
      }

      const categoryId = this.getSubmitCategoryId(this.selectedCategoryLevel1Id)
      if (!categoryId) {
        errors.push({ field: 'category', message: '请选择商品分类' })
      }

      if (!this.publishForm.condition) {
        return this.showValidationToast('请选择商品成色')
      }

      errors.forEach((error) => {
        this.fieldErrors[error.field] = error.message
      })
      if (errors.length) {
        const first = errors[0]
        this.showValidationToast(first.message)
        this.focusInvalidField(first.field)
        return null
      }

      return {
        categoryId,
        title,
        description: rawDescription,
        images,
        price,
        condition: this.publishForm.condition,
        location: String(this.publishForm.pickupPoint || '').trim() || '校内自提'
      }
    },
    async publish() {
      console.log('publish called')
      if (this.submitting) return
      const payload = this.validatePublishForm()
      if (!payload) return

      this.submitting = true
      try {
        uni.showLoading({ title: '发布中...' })
        console.log('submit payload', payload)
        await createSecondhandItem(payload)
        this.notifyPublishSuccess()
        uni.showToast({ title: '发布成功', icon: 'success' })
        this.resetForm()
        setTimeout(() => {
          this.$emit('close')
        }, 300)
      } catch (error) {
        console.error('发布失败', error)
      } finally {
        this.submitting = false
        uni.hideLoading()
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.publish-overlay {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  width: 100%;
  z-index: 2147483647;
  overflow: hidden;
  overflow-x: hidden;
  background: transparent;
}

.publish-validation-toast {
  position: fixed;
  top: calc(var(--status-bar-height, 0px) + 132rpx);
  left: 50%;
  z-index: 2147483647;
  max-width: calc(100% - 96rpx);
  padding: 18rpx 28rpx;
  border-radius: 999rpx;
  background: rgba(29, 29, 31, 0.88);
  color: #FFFFFF;
  font-size: 24rpx;
  font-weight: 600;
  line-height: 1.35;
  text-align: center;
  box-sizing: border-box;
  transform: translateX(-50%);
  pointer-events: none;
}

.publish-panel {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  width: 100%;
  background: #F7F7F9;
  box-sizing: border-box;
  overflow-x: hidden;
  opacity: 0;
  transform: translateY(100%);
  transition: transform 300ms cubic-bezier(0.22, 1, 0.36, 1);
  will-change: transform, opacity;
}

.publish-panel--visible {
  opacity: 1;
  transform: translateY(0);
}

.publish-shell {
  width: 100%;
  height: 100vh;
  background: #F7F7F9;
  box-sizing: border-box;
  position: relative;
  overflow: hidden;
  overflow-x: hidden;
}

.publish-header {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 2;
  height: calc(88rpx + var(--status-bar-height, 0px));
  padding: var(--status-bar-height, 0px) 24rpx 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #F7F7F9;
  box-sizing: border-box;
}

.publish-close,
.publish-placeholder {
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.publish-close {
  margin-left: 0;
  color: #111111;
  font-size: 48rpx;
  font-weight: 300;
  line-height: 1;
}

.publish-title {
  font-size: 32rpx;
  font-weight: 800;
  color: #1D1D1F;
}

.publish-body {
  position: absolute;
  top: calc(88rpx + var(--status-bar-height, 0px));
  left: 0;
  right: 0;
  bottom: 0;
  width: 100%;
  height: auto;
  padding: 0 24rpx 24rpx;
  box-sizing: border-box;
  overflow-y: auto;
  overflow-x: hidden;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.publish-body::-webkit-scrollbar {
  width: 0;
  height: 0;
  display: none;
  background: transparent;
}

.publish-body ::-webkit-scrollbar {
  width: 0;
  height: 0;
  display: none;
  background: transparent;
}

.section {
  width: 100%;
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
  width: 100%;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10rpx;
  margin-bottom: 12rpx;
  box-sizing: border-box;
}

.photo-item {
  position: relative;
  width: 100%;
  aspect-ratio: 1;
  border-radius: 16rpx;
  overflow: hidden;
  background: #F1F3F5;
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
  color: #FFFFFF;
  font-size: 24rpx;
  line-height: 32rpx;
  text-align: center;
}

.upload-add {
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 18rpx;
  min-height: 252rpx;
  padding: 34rpx 24rpx;
  border-radius: 18rpx;
  border: none;
  background: #F8FAFC;
  box-sizing: border-box;
  box-shadow: none;
}

.upload-add--error {
  border: 1rpx solid rgba(209, 67, 67, 0.5);
  background: rgba(209, 67, 67, 0.05);
}

.upload-icon {
  width: auto;
  height: auto;
  background: transparent;
  border: none;
  color: #5C7A99;
  font-size: 78rpx;
  font-weight: 300;
  line-height: 1;
  text-align: center;
  flex-shrink: 0;
}

.upload-copy {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
  min-width: 0;
  text-align: center;
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
  width: 100%;
  display: flex;
  align-items: center;
  min-height: 86rpx;
  border-bottom: 1rpx solid #EEEEEE;
  box-sizing: border-box;
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
  padding: 18rpx 8rpx 24rpx 0;
  font-size: 26rpx;
  line-height: 1.6;
  color: #1D1D1F;
  background: #FFFFFF;
  box-sizing: border-box;
  overflow-x: hidden;
}

.desc-input--error {
  padding: 18rpx 16rpx 24rpx;
  border: 1rpx solid rgba(209, 67, 67, 0.48);
  border-radius: 16rpx;
  background: rgba(209, 67, 67, 0.045);
}

.condition-grid,
.category-grid {
  width: 100%;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10rpx;
  box-sizing: border-box;
}

.condition-grid,
.category-grid {
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

.option-card {
  width: 100%;
  min-width: 0;
  min-height: 64rpx;
  padding: 0 16rpx;
  border-radius: 16rpx;
  background: #F8FAFC;
  border: none;
  display: flex;
  align-items: center;
  box-sizing: border-box;
  transition:
    background-color 180ms ease,
    color 180ms ease;
}

.option-card--error {
  border: 1rpx solid rgba(209, 67, 67, 0.38);
}

.option-card.active.option-card--error {
  border-color: rgba(92, 122, 153, 0.28);
}

.category-card--primary {
  min-height: 58rpx;
  padding: 0 8rpx;
  border-radius: 14rpx;
  justify-content: center;
  text-align: center;
  background: transparent;
}

.category-card--primary .option-card-title {
  font-size: 22rpx;
  color: #5C6470;
}

.category-card--sub {
  background: #F8FAFC;
}

.option-card.active {
  background: #5C7A99;
}

.option-card-title {
  font-size: 24rpx;
  font-weight: 700;
  color: #1D1D1F;
  transition: color 180ms ease;
}

.option-card-desc {
  font-size: 21rpx;
  color: #8E8E93;
  line-height: 1.3;
  transition: color 180ms ease;
}

.option-card.active .option-card-title {
  color: #FFFFFF;
}

.option-card.active .option-card-desc {
  color: rgba(255, 255, 255, 0.72);
}

.condition-card {
  min-height: 86rpx;
  padding: 14rpx 16rpx;
  flex-direction: column;
  align-items: flex-start;
  gap: 6rpx;
}

.category-card.disabled {
  background: #F3F5F7;
}

.category-card.disabled .option-card-title {
  color: #8E8E93;
}

.pickup-line {
  border-bottom: none;
}

.trade-info-box {
  margin: 12rpx 0 22rpx;
  padding: 20rpx;
  border-radius: 18rpx;
  background: rgba(92, 122, 153, 0.06);
  box-sizing: border-box;
}

.trade-info-row {
  display: flex;
  gap: 18rpx;
  align-items: flex-start;
}

.trade-info-icon {
  width: 44rpx;
  height: 44rpx;
  border-radius: 50%;
  border: 2rpx solid rgba(92, 122, 153, 0.36);
  color: #5C7A99;
  background: rgba(92, 122, 153, 0.08);
  font-size: 26rpx;
  font-weight: 700;
  line-height: 40rpx;
  text-align: center;
  box-sizing: border-box;
  flex-shrink: 0;
}

.trade-info-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.trade-info-title {
  font-size: 25rpx;
  font-weight: 700;
  color: #1D1D1F;
  line-height: 1.35;
}

.trade-info-desc {
  font-size: 22rpx;
  color: #5C6470;
  line-height: 1.45;
}

.contact-section {
  padding-bottom: 0;
}

.chat-tip {
  min-height: 118rpx;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 7rpx;
  padding-bottom: 18rpx;
}

.chat-tip-main {
  display: flex;
  align-items: center;
  gap: 10rpx;
  margin-top: -2rpx;
}

.chat-tip-title {
  font-size: 24rpx;
  font-weight: 600;
  color: #30363D;
  line-height: 1.25;
}

.chat-tip-desc {
  font-size: 22rpx;
  color: #6F7680;
  line-height: 1.4;
}

.chat-tip-note {
  font-size: 20rpx;
  color: #A8ADB4;
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
  margin: 20rpx 0;
  box-sizing: border-box;
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

.bottom-spacer {
  height: calc(32rpx + env(safe-area-inset-bottom));
}
</style>
