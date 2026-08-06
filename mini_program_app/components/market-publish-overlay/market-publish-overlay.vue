<template>
  <view class="publish-overlay">
    <view class="publish-panel" :class="{ 'publish-panel--visible': visible }">
      <view class="publish-shell">
        <common-page-header title="发布闲置" :fixed="true" :placeholder="true" :showBack="true" :autoBack="false" @back="requestClose" />

        <scroll-view scroll-y class="publish-body" :show-scrollbar="false" :scroll-into-view="scrollTarget" scroll-with-animation>
          <view class="publish-guide">
            <view class="guide-icon guide-megaphone"></view>
            <text class="guide-text">真实描述，友好交易，让闲置找到新伙伴～</text>
            <view class="guide-sparkles">
              <view class="sparkle sparkle-large"></view>
              <view class="sparkle sparkle-small"></view>
            </view>
          </view>

          <view
            id="publish-field-tradetype"
            class="section tradetype-section"
            :class="{ 'section--error': fieldErrors.tradeType, 'shake-once': shakeField === 'tradeType' }"
          >
            <view class="section-head">
              <view class="section-title-wrap">
                <text class="section-title" :class="{ 'is-error-text': fieldErrors.tradeType }">出物 / 收物</text>
              </view>
            </view>
            <view class="tradetype-row">
              <view
                class="tradetype-card"
                :class="{ active: publishForm.tradeType === 'sell' }"
                @click="publishForm.tradeType = 'sell'"
              >
                <view class="tradetype-icon tradetype-icon--sell"></view>
                <text class="tradetype-label">出物</text>
                <text class="tradetype-desc">我要出售闲置</text>
              </view>
              <view
                class="tradetype-card"
                :class="{ active: publishForm.tradeType === 'buy' }"
                @click="publishForm.tradeType = 'buy'"
              >
                <view class="tradetype-icon tradetype-icon--buy"></view>
                <text class="tradetype-label">收物</text>
                <text class="tradetype-desc">我要求购物品</text>
              </view>
            </view>
            <text v-if="fieldErrors.tradeType" class="field-error-text">请选择出物或收物</text>
          </view>

          <view
            id="publish-field-images"
            class="section photo-section"
            :class="{ 'section--error': fieldErrors.images, 'shake-once': shakeField === 'images' }"
          >
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
              <view
                v-if="publishForm.images.length < 9"
                class="photo-item photo-add-tile"
                :class="{ 'upload-add--error': fieldErrors.images }"
                @click="choosePublishImage"
              >
                <view class="photo-add-icon"></view>
              </view>
            </view>
            <view
              v-if="!publishForm.images.length"
              class="upload-add"
              :class="{ 'upload-add--error': fieldErrors.images }"
              @click="choosePublishImage"
            >
              <view class="upload-illustration">
                <view class="camera-body"></view>
                <view class="camera-lens"></view>
                <view class="camera-plus"></view>
                <view class="camera-spark camera-spark-a"></view>
                <view class="camera-spark camera-spark-b"></view>
              </view>
              <view class="upload-copy">
                <text class="upload-title">上传商品图片</text>
                <text class="upload-text">最多9张，首张作为封面</text>
                <text class="upload-subtle">拍清楚，成交更快哦！</text>
              </view>
            </view>
            <view class="photo-tips">
              <view class="photo-tip"><view class="tip-icon tip-sun"></view><text>光线充足</text></view>
              <view class="photo-tip"><view class="tip-icon tip-frame"></view><text>正面清晰</text></view>
              <view class="photo-tip"><view class="tip-icon tip-search"></view><text>细节完整</text></view>
              <view class="photo-tip"><view class="tip-icon tip-shield"></view><text>真实描述</text></view>
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
              <text class="field-label" :class="{ 'is-error-text': fieldErrors.price }">参考价格</text>
              <input
                class="field-input"
                v-model="publishForm.price"
                type="number"
                placeholder="请输入价格"
                @input="clearFieldError('price')"
              />
            </view>
            <text v-if="fieldErrors.price" class="field-error-text field-error-text--line">{{ fieldErrors.price }}</text>
          </view>

          <view
            id="publish-field-category"
            class="section"
            :class="{ 'section--error': fieldErrors.category, 'shake-once': shakeField === 'category' }"
          >
            <view class="section-head">
              <view class="section-title-wrap">
                <view class="title-icon title-icon-folder"></view>
                <text class="section-title" :class="{ 'is-error-text': fieldErrors.category }">商品分类</text>
              </view>
              <view class="line-arrow"></view>
            </view>
            <view class="category-select" :class="{ 'category-select--error': fieldErrors.category }" @click="openCategorySheet">
              <text class="category-select-text" :class="{ placeholder: !selectedCategoryName }">
                {{ selectedCategoryName || '选择商品所属分类' }}
              </text>
              <view class="category-bag">
                <view class="bag-body"></view>
                <view class="bag-handle"></view>
              </view>
            </view>
            <text v-if="fieldErrors.category" class="field-error-text">{{ fieldErrors.category }}</text>
          </view>

          <view
            id="publish-field-desc"
            class="section desc-section"
            :class="{ 'section--error': fieldErrors.desc, 'shake-once': shakeField === 'desc' }"
          >
            <view class="section-head">
              <view class="section-title-wrap">
                <view class="title-icon title-icon-edit"></view>
                <text class="section-title" :class="{ 'is-error-text': fieldErrors.desc }">详细描述</text>
              </view>
            </view>
            <textarea
              class="desc-input"
              :class="{ 'desc-input--error': fieldErrors.desc }"
              v-model="publishForm.desc"
              placeholder="补充使用时长、瑕疵情况、配件信息、购买时间、交易备注等，让买家更全面了解商品～"
              @input="clearFieldError('desc')"
            />
            <text v-if="fieldErrors.desc" class="field-error-text">{{ fieldErrors.desc }}</text>
          </view>

          <view
            id="publish-field-location"
            class="section"
            :class="{ 'section--error': fieldErrors.pickupPoint, 'shake-once': shakeField === 'pickupPoint' }"
          >
            <view class="section-head">
              <view class="section-title-wrap">
                <view class="title-icon title-icon-location"></view>
                <text class="section-title" :class="{ 'is-error-text': fieldErrors.pickupPoint }">取货地点</text>
              </view>
            </view>
            <view class="location-line">
              <input class="location-input" v-model="publishForm.pickupPoint" placeholder="请填写校内取货地点" maxlength="50" @input="clearFieldError('pickupPoint')" />
              <view class="line-arrow"></view>
            </view>
            <text v-if="fieldErrors.pickupPoint" class="field-error-text">{{ fieldErrors.pickupPoint }}</text>
          </view>

          <view class="section contact-section">
            <view class="section-head">
              <view class="section-title-wrap">
                <view class="title-icon title-icon-contact"></view>
                <text class="section-title">交易与联系</text>
              </view>
              <view class="contact-art">
                <view class="chat-block chat-block-a"></view>
                <view class="chat-block chat-block-b"></view>
                <view class="chat-face"></view>
              </view>
            </view>
            <view class="contact-list">
              <view class="contact-rule"><view class="contact-rule-icon rule-chat"></view><text>默认通过站内聊天联系</text></view>
              <view class="contact-rule"><view class="contact-rule-icon rule-exchange"></view><text>双方确认交易后，可自愿交换联系方式</text></view>
              <view class="contact-rule"><view class="contact-rule-icon rule-platform"></view><text>平台仅提供信息沟通服务，不参与线下交易</text></view>
            </view>
          </view>

          <view class="bottom-spacer"></view>
        </scroll-view>

        <view class="publish-submit-area">
          <button class="pbtn" :disabled="submitting" @click="publish">
            <text>{{ itemId ? '保存修改' : '发布闲置' }}</text>
          </button>
        </view>

        <view v-if="categorySheetVisible" class="category-mask" @click="categorySheetVisible = false">
          <view class="category-sheet" @click.stop>
            <view class="sheet-head">
              <text class="sheet-title">选择商品分类</text>
              <view class="sheet-close" @click="categorySheetVisible = false">×</view>
            </view>
            <scroll-view scroll-y class="sheet-body">
              <view
                v-for="cat in categories"
                :key="cat.id"
                class="sheet-category"
                :class="{ active: selectedCategoryLevel1Id === cat.id }"
                @click="selectCategoryLevel1(cat.id)"
              >
                <view class="sheet-category-main">
                  <text class="sheet-category-title">{{ cat.name }}</text>
                  <text v-if="cat.children && cat.children.length" class="sheet-category-desc">继续选择细分类</text>
                </view>
                <view class="sheet-category-check"></view>
              </view>
              <view v-if="currentCategoryChildren.length" class="sheet-sub-list">
                <view
                  v-for="cat in currentCategoryChildren"
                  :key="cat.id"
                  class="sheet-sub-item"
                  :class="{ active: publishForm.cat === cat.id }"
                  @click="selectCategoryLevel2(cat.id)"
                >
                  {{ cat.name }}
                </view>
              </view>
            </scroll-view>
          </view>
        </view>
      </view>
    </view>
    <view v-if="publishToastText" class="publish-validation-toast">
      {{ publishToastText }}
    </view>
  </view>
</template>

<script>
import CommonPageHeader from '@/components/common-page-header/common-page-header.vue'
import { createSecondhandItem, updateSecondhandItem, getSecondhandItemDetail } from '@/api/secondhand'
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
    tradeType: '',
    name: '',
    price: '',
    desc: '',
    cat: '',
    images: [],
    pickupPoint: ''
  }
}

export default {
  name: 'MarketPublishOverlay',
  components: { CommonPageHeader },
  props: {
    visible: { type: Boolean, default: false },
    itemId: { type: [String, Number], default: null }
  },
  data() {
    return {
      categories: MARKET_CATEGORIES,
      selectedCategoryLevel1Id: '',
      categorySheetVisible: false,
      submitting: false,
      publishToastText: '',
      publishForm: createDefaultForm(),
      fieldErrors: {
        tradeType: '',
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
    },
    selectedCategoryName() {
      const current = this.categories.find((item) => item.id === this.selectedCategoryLevel1Id)
      if (!current) return ''
      const child = this.currentCategoryChildren.find((item) => item.id === this.publishForm.cat)
      return child ? `${current.name} / ${child.name}` : current.name
    }
  },
  mounted() {
    this.ensureCategorySelection()
    if (this.itemId) {
      this.loadItemForEdit()
    }
  },
  methods: {
    requestClose() {
      this.$emit('close')
    },
    resetForm() {
      this.publishForm = createDefaultForm()
      this.selectedCategoryLevel1Id = ''
      this.resetFieldErrors()
      this.scrollTarget = ''
      this.shakeField = ''
      this.categorySheetVisible = false
      this.ensureCategorySelection()
    },
    ensureCategorySelection() {
      if (!this.selectedCategoryLevel1Id) return
      const current = this.categories.find((item) => item.id === this.selectedCategoryLevel1Id)
      if (!current) {
        this.selectedCategoryLevel1Id = ''
        this.publishForm.cat = ''
        return
      }
      if (this.currentCategoryChildren.length && !this.publishForm.cat) {
        this.publishForm.cat = this.currentCategoryChildren[0].id
      }
    },
    openCategorySheet() {
      this.categorySheetVisible = true
    },
    selectCategoryLevel1(id) {
      this.selectedCategoryLevel1Id = id
      const current = this.categories.find((item) => item.id === id)
      const children = Array.isArray(current?.children) ? current.children : []
      this.publishForm.cat = children[0]?.id || ''
      this.clearFieldError('category')
      if (!children.length) {
        this.categorySheetVisible = false
      }
    },
    selectCategoryLevel2(id) {
      this.publishForm.cat = id
      this.clearFieldError('category')
      this.categorySheetVisible = false
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
    async loadItemForEdit() {
      try {
        uni.showLoading({ title: '加载中...' })
        const data = await getSecondhandItemDetail(this.itemId)
        const item = data?.data || data
        if (!item) return
        this.publishForm.tradeType = item.tradeType || item.trade_type || 'sell'
        this.publishForm.name = item.title || ''
        this.publishForm.desc = item.description || ''
        this.publishForm.price = item.price != null ? String(item.price) : ''
        this.publishForm.images = Array.isArray(item.images) ? item.images : []
        this.publishForm.pickupPoint = item.location || item.pickupPoint || ''
        const catId = item.categoryId || item.category_id
        if (catId) {
          const level1 = this.findCategoryLevel1(catId)
          if (level1) {
            this.selectedCategoryLevel1Id = level1.id
            const level2 = level1.children?.find((c) => Number(c.id) === Number(catId))
            this.publishForm.cat = level2 ? level2.id : catId
          }
        }
      } catch (e) {
        console.error('加载商品数据失败', e)
        uni.showToast({ title: '加载失败', icon: 'none' })
      } finally {
        uni.hideLoading()
      }
    },
    findCategoryLevel1(categoryId) {
      const id = Number(categoryId)
      for (const cat of this.categories) {
        if (Number(cat.id) === id) return cat
        if (cat.children?.some((c) => Number(c.id) === id)) return cat
      }
      return null
    },
    notifyPublishSuccess() {
      uni.$emit('secondhand:item:published')
      if (this.itemId) {
        uni.$emit('secondhand:item:updated')
      }
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
        tradeType: 'publish-field-tradetype',
        images: 'publish-field-images',
        name: 'publish-field-name',
        price: 'publish-field-price',
        desc: 'publish-field-desc',
        category: 'publish-field-category',
        pickupPoint: 'publish-field-location'
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
      const title = String(this.publishForm.name || '').trim()
      const rawDescription = String(this.publishForm.desc || '').trim()
      const priceText = String(this.publishForm.price ?? '').trim()
      const price = Number(priceText)
      const errors = []

      if (!this.publishForm.tradeType) {
        errors.push({ field: 'tradeType', message: '请选择出物或收物' })
      }

      const images = Array.isArray(this.publishForm.images) ? this.publishForm.images.filter(Boolean) : []
      if (!images.length) {
        errors.push({ field: 'images', message: '请上传至少一张图片' })
      }
      if (!title) {
        errors.push({ field: 'name', message: '请输入商品名称' })
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
      if (!rawDescription) {
        errors.push({ field: 'desc', message: '请输入商品描述' })
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

      const pickup = String(this.publishForm.pickupPoint || '').trim()
      if (!pickup) {
        errors.push({ field: 'pickupPoint', message: '请填写校内取货地点' })
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
        tradeType: this.publishForm.tradeType,
        categoryId,
        title,
        description: rawDescription,
        images,
        price,
        location: pickup
      }
    },
    async publish() {
      if (this.submitting) return
      const payload = this.validatePublishForm()
      if (!payload) return

      this.submitting = true
      const isEdit = !!this.itemId
      try {
        uni.showLoading({ title: isEdit ? '保存中...' : '发布中...' })
        if (isEdit) {
          await updateSecondhandItem(this.itemId, payload)
        } else {
          await createSecondhandItem(payload)
        }
        this.notifyPublishSuccess()
        uni.showToast({ title: isEdit ? '保存成功' : '发布成功', icon: 'success' })
        this.resetForm()
        setTimeout(() => {
          this.$emit('close')
        }, 300)
      } catch (error) {
        console.error('发布失败', error)
        let msg = ''
        if (error && error.data && typeof error.data === 'object') {
          msg = error.data.msg || error.data.message || ''
        }
        if (!msg) msg = error?.errMsg || error?.message || ''
        if (msg === 'request:ok' || !msg) msg = `服务器错误 (${error?.statusCode || '未知'})，请检查网络后重试`
        uni.showToast({ title: msg, icon: 'none' })
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
  font-weight: 700;
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
  background: #F3F5F8;
  box-sizing: border-box;
  opacity: 0;
  overflow: hidden;
  transform: translateY(100%);
  transition: transform 300ms cubic-bezier(0.22, 1, 0.36, 1);
}

.publish-panel--visible {
  opacity: 1;
  transform: none;
}

.publish-shell {
  position: relative;
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100vh;
  background: #F3F5F8;
  box-sizing: border-box;
  overflow: hidden;
}

.publish-body {
  position: relative;
  flex: 1;
  min-height: 0;
  width: 100%;
  height: 0;
  padding: 18rpx 18rpx 0;
  background: #F3F5F8;
  box-sizing: border-box;
  overflow-y: auto;
  overflow-x: hidden;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.publish-body::-webkit-scrollbar,
.publish-body ::-webkit-scrollbar {
  width: 0;
  height: 0;
  display: none;
  background: transparent;
}

.publish-guide,
.section,
.publish-submit-area {
  width: 100%;
  margin-bottom: 18rpx;
  border-radius: 24rpx;
  background: #FFFFFF;
  border: 1rpx solid #EEF2F6;
  box-sizing: border-box;
  box-shadow: 0 4rpx 14rpx rgba(15, 23, 42, 0.045);
}

.publish-guide {
  display: flex;
  align-items: center;
  min-height: 62rpx;
  padding: 0 24rpx;
  gap: 16rpx;
}

.guide-text {
  flex: 1;
  min-width: 0;
  color: #202938;
  font-size: 23rpx;
  font-weight: 700;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.guide-icon {
  position: relative;
  width: 34rpx;
  height: 34rpx;
  flex-shrink: 0;
  color: #4D78E6;
}

.guide-megaphone::before {
  content: '';
  position: absolute;
  left: 3rpx;
  top: 10rpx;
  width: 18rpx;
  height: 14rpx;
  background: currentColor;
  border-radius: 3rpx;
  transform: skew(-14deg);
}

.guide-megaphone::after {
  content: '';
  position: absolute;
  left: 19rpx;
  top: 6rpx;
  width: 7rpx;
  height: 22rpx;
  border: 3rpx solid currentColor;
  border-radius: 999rpx;
  box-sizing: border-box;
}

.guide-sparkles {
  position: relative;
  width: 48rpx;
  height: 42rpx;
  color: #AFC3FF;
  flex-shrink: 0;
}

.sparkle {
  position: absolute;
}

.sparkle::before,
.sparkle::after {
  content: '';
  position: absolute;
  background: currentColor;
  border-radius: 999rpx;
}

.sparkle::before {
  left: 50%;
  top: 0;
  width: 4rpx;
  height: 100%;
  transform: translateX(-50%);
}

.sparkle::after {
  left: 0;
  top: 50%;
  width: 100%;
  height: 4rpx;
  transform: translateY(-50%);
}

.sparkle-large {
  right: 8rpx;
  top: 2rpx;
  width: 28rpx;
  height: 28rpx;
  transform: rotate(45deg);
}

.sparkle-small {
  left: 5rpx;
  bottom: 4rpx;
  width: 18rpx;
  height: 18rpx;
  transform: rotate(45deg);
}

.section {
  padding: 0 24rpx;
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
  min-height: 84rpx;
}

.section-title-wrap {
  display: flex;
  align-items: center;
  gap: 14rpx;
  min-width: 0;
}

.section-title {
  color: #0F172A;
  font-size: 28rpx;
  font-weight: 900;
  line-height: 1.2;
}

.section-hint {
  color: #667085;
  font-size: 23rpx;
  font-weight: 700;
  white-space: nowrap;
}

.help-icon {
  width: 28rpx;
  height: 28rpx;
  border: 3rpx solid #8D98A7;
  border-radius: 50%;
  color: #667085;
  font-size: 19rpx;
  font-weight: 900;
  line-height: 22rpx;
  text-align: center;
  box-sizing: border-box;
}

.title-icon {
  position: relative;
  width: 34rpx;
  height: 34rpx;
  color: #527BEA;
  flex-shrink: 0;
}

.title-icon-folder::before {
  content: '';
  position: absolute;
  left: 3rpx;
  top: 10rpx;
  width: 28rpx;
  height: 19rpx;
  border: 3rpx solid currentColor;
  border-radius: 4rpx;
  box-sizing: border-box;
}

.title-icon-folder::after {
  content: '';
  position: absolute;
  left: 5rpx;
  top: 5rpx;
  width: 14rpx;
  height: 8rpx;
  border: 3rpx solid currentColor;
  border-bottom: 0;
  border-radius: 4rpx 4rpx 0 0;
  box-sizing: border-box;
}

.title-icon-edit::before {
  content: '';
  position: absolute;
  left: 6rpx;
  top: 8rpx;
  width: 18rpx;
  height: 20rpx;
  border: 3rpx solid currentColor;
  border-radius: 5rpx;
  box-sizing: border-box;
}

.title-icon-edit::after {
  content: '';
  position: absolute;
  right: 5rpx;
  top: 7rpx;
  width: 18rpx;
  height: 5rpx;
  border-radius: 999rpx;
  background: currentColor;
  transform: rotate(-45deg);
  transform-origin: center;
}

.title-icon-location::before {
  content: '';
  position: absolute;
  left: 7rpx;
  top: 3rpx;
  width: 20rpx;
  height: 20rpx;
  border: 4rpx solid currentColor;
  border-radius: 50% 50% 50% 0;
  transform: rotate(-45deg);
  box-sizing: border-box;
}

.title-icon-location::after {
  content: '';
  position: absolute;
  left: 14rpx;
  top: 10rpx;
  width: 6rpx;
  height: 6rpx;
  border-radius: 50%;
  background: currentColor;
}

.title-icon-contact::before,
.title-icon-contact::after {
  content: '';
  position: absolute;
  border: 4rpx solid currentColor;
  border-radius: 12rpx;
  box-sizing: border-box;
}

.title-icon-contact::before {
  left: 3rpx;
  top: 9rpx;
  width: 17rpx;
  height: 16rpx;
}

.title-icon-contact::after {
  right: 3rpx;
  top: 9rpx;
  width: 17rpx;
  height: 16rpx;
}

.line-arrow {
  position: relative;
  width: 22rpx;
  height: 22rpx;
  flex-shrink: 0;
}

.line-arrow::before {
  content: '';
  position: absolute;
  left: 4rpx;
  top: 3rpx;
  width: 14rpx;
  height: 14rpx;
  border-top: 4rpx solid #8D98A7;
  border-right: 4rpx solid #8D98A7;
  transform: rotate(45deg);
  box-sizing: border-box;
}

.photo-section {
  padding: 22rpx;
}

.photo-board {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12rpx;
  margin-bottom: 18rpx;
  box-sizing: border-box;
}

.photo-item {
  position: relative;
  width: 100%;
  aspect-ratio: 1;
  border-radius: 16rpx;
  overflow: hidden;
  background: #F1F4F8;
}

.photo-add-tile {
  border: 2rpx dashed #C8D2E0;
  background: #F8FAFC;
  display: flex;
  align-items: center;
  justify-content: center;
}

.photo-add-icon {
  position: relative;
  width: 40rpx;
  height: 40rpx;
  color: #667085;
}

.photo-add-icon::before,
.photo-add-icon::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 50%;
  width: 34rpx;
  height: 4rpx;
  border-radius: 999rpx;
  background: currentColor;
  transform: translate(-50%, -50%);
}

.photo-add-icon::after {
  transform: translate(-50%, -50%) rotate(90deg);
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
  border-radius: 8rpx;
  background: rgba(69, 97, 132, 0.88);
  color: #FFFFFF;
  font-size: 20rpx;
  font-weight: 700;
}

.upload-delete {
  position: absolute;
  top: 6rpx;
  right: 6rpx;
  width: 32rpx;
  height: 32rpx;
  background: rgba(18, 25, 38, 0.55);
  border-radius: 50%;
  color: #FFFFFF;
  font-size: 24rpx;
  line-height: 32rpx;
  text-align: center;
}

.upload-add {
  position: relative;
  min-height: 300rpx;
  padding: 38rpx 24rpx;
  border-radius: 20rpx;
  border: 3rpx dashed #AFC7FF;
  background: #FFFFFF;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 18rpx;
  box-sizing: border-box;
  overflow: hidden;
}

.upload-add--error {
  border-color: rgba(209, 67, 67, 0.58);
  background: rgba(209, 67, 67, 0.045);
}

.upload-illustration {
  position: relative;
  width: 126rpx;
  height: 92rpx;
  color: #5C82E9;
}

.camera-body {
  position: absolute;
  left: 24rpx;
  top: 22rpx;
  width: 72rpx;
  height: 52rpx;
  border-radius: 14rpx;
  background: #4F7DE8;
  box-shadow: 0 6rpx 14rpx rgba(66, 108, 225, 0.16);
}

.camera-body::before {
  content: '';
  position: absolute;
  left: 16rpx;
  top: -10rpx;
  width: 24rpx;
  height: 12rpx;
  border-radius: 10rpx 10rpx 0 0;
  background: #6D91F1;
}

.camera-lens {
  position: absolute;
  left: 50rpx;
  top: 37rpx;
  width: 22rpx;
  height: 22rpx;
  border-radius: 50%;
  border: 6rpx solid rgba(255, 255, 255, 0.58);
  background: #FFFFFF;
  box-sizing: border-box;
}

.camera-plus {
  position: absolute;
  right: 8rpx;
  bottom: 6rpx;
  width: 42rpx;
  height: 42rpx;
  border-radius: 50%;
  background: #6E98F3;
  box-shadow: 0 4rpx 10rpx rgba(72, 112, 220, 0.14);
}

.camera-plus::before,
.camera-plus::after {
  content: '';
  position: absolute;
  left: 12rpx;
  top: 19rpx;
  width: 18rpx;
  height: 4rpx;
  border-radius: 999rpx;
  background: #FFFFFF;
}

.camera-plus::after {
  transform: rotate(90deg);
}

.camera-spark {
  position: absolute;
  color: #AFC7FF;
}

.camera-spark::before,
.camera-spark::after {
  content: '';
  position: absolute;
  background: currentColor;
  border-radius: 999rpx;
}

.camera-spark::before {
  left: 50%;
  top: 0;
  width: 4rpx;
  height: 100%;
  transform: translateX(-50%);
}

.camera-spark::after {
  left: 0;
  top: 50%;
  width: 100%;
  height: 4rpx;
  transform: translateY(-50%);
}

.camera-spark-a {
  right: 0;
  top: 0;
  width: 24rpx;
  height: 24rpx;
  transform: rotate(45deg);
}

.camera-spark-b {
  left: 8rpx;
  top: 40rpx;
  width: 18rpx;
  height: 18rpx;
  transform: rotate(45deg);
}

.upload-copy {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10rpx;
  min-width: 0;
  text-align: center;
}

.upload-title {
  color: #0F172A;
  font-size: 30rpx;
  font-weight: 900;
}

.upload-text {
  color: #5F6B7A;
  font-size: 24rpx;
  font-weight: 700;
  line-height: 1.35;
}

.upload-subtle {
  margin-top: 16rpx;
  color: #4B5565;
  font-size: 23rpx;
  font-weight: 700;
}

.photo-tips {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10rpx;
  margin-top: 20rpx;
}

.photo-tip {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  min-width: 0;
  color: #4B5565;
  font-size: 22rpx;
  font-weight: 800;
  white-space: nowrap;
}

.tip-icon {
  position: relative;
  width: 28rpx;
  height: 28rpx;
  color: #667085;
  flex-shrink: 0;
}

.tip-sun {
  border: 3rpx solid currentColor;
  border-radius: 50%;
  box-sizing: border-box;
}

.tip-sun::before {
  content: '';
  position: absolute;
  left: 10rpx;
  top: -8rpx;
  width: 3rpx;
  height: 5rpx;
  border-radius: 999rpx;
  background: currentColor;
  box-shadow: 0 32rpx 0 currentColor, 16rpx 16rpx 0 currentColor, -16rpx 16rpx 0 currentColor;
}

.tip-frame::before {
  content: '';
  position: absolute;
  top: 4rpx;
  right: 4rpx;
  bottom: 4rpx;
  left: 4rpx;
  border: 3rpx dashed currentColor;
  border-radius: 5rpx;
  box-sizing: border-box;
}

.tip-search::before {
  content: '';
  position: absolute;
  left: 3rpx;
  top: 3rpx;
  width: 15rpx;
  height: 15rpx;
  border: 3rpx solid currentColor;
  border-radius: 50%;
  box-sizing: border-box;
}

.tip-search::after {
  content: '';
  position: absolute;
  right: 3rpx;
  bottom: 4rpx;
  width: 11rpx;
  height: 4rpx;
  border-radius: 999rpx;
  background: currentColor;
  transform: rotate(45deg);
}

.tip-shield::before {
  content: '';
  position: absolute;
  left: 6rpx;
  top: 2rpx;
  width: 16rpx;
  height: 22rpx;
  border: 3rpx solid currentColor;
  border-radius: 10rpx 10rpx 12rpx 12rpx;
  box-sizing: border-box;
}

.field-line {
  width: 100%;
  min-height: 92rpx;
  border-bottom: 1rpx solid #EEF1F5;
  display: flex;
  align-items: center;
  box-sizing: border-box;
}

.field-line:last-child {
  border-bottom: none;
}

.field-line--error {
  margin: 8rpx 0;
  padding: 0 14rpx;
  border: 1rpx solid rgba(209, 67, 67, 0.48);
  border-radius: 16rpx;
  background: rgba(209, 67, 67, 0.045);
}

.field-label {
  width: 170rpx;
  flex-shrink: 0;
  color: #0F172A;
  font-size: 28rpx;
  font-weight: 900;
}

.field-input {
  flex: 1;
  min-width: 0;
  height: 92rpx;
  color: #101828;
  font-size: 26rpx;
  font-weight: 700;
  background: transparent;
}

.field-count {
  margin-left: 12rpx;
  color: #667085;
  font-size: 23rpx;
  font-weight: 700;
  white-space: nowrap;
}

.condition-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12rpx;
  padding-bottom: 24rpx;
}

.option-card {
  box-sizing: border-box;
}

.condition-card {
  min-height: 92rpx;
  padding: 14rpx;
  border-radius: 16rpx;
  border: 1rpx solid transparent;
  background: #F6F8FB;
  display: flex;
  align-items: center;
  gap: 10rpx;
}

.condition-card.active {
  border-color: #5A83F0;
  background: #F5F8FF;
  box-shadow: inset 0 0 0 1rpx rgba(90, 131, 240, 0.18);
}

.condition-icon {
  position: relative;
  width: 34rpx;
  height: 34rpx;
  border-radius: 10rpx;
  background: #E8EDF4;
  color: #667085;
  flex-shrink: 0;
}

.condition-card.active .condition-icon {
  background: #E4EDFF;
  color: #4F7DE8;
}

.condition-icon::before,
.condition-icon::after {
  content: '';
  position: absolute;
  box-sizing: border-box;
}

.condition-icon--new::before {
  left: 9rpx;
  top: 9rpx;
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  background: currentColor;
}

.condition-icon--new::after {
  left: 13rpx;
  top: 5rpx;
  width: 8rpx;
  height: 24rpx;
  border-radius: 999rpx;
  background: currentColor;
}

.condition-icon--fresh::before {
  left: 8rpx;
  top: 8rpx;
  width: 18rpx;
  height: 18rpx;
  border: 4rpx solid currentColor;
  border-radius: 4rpx;
}

.condition-icon--normal::before,
.condition-icon--used::before {
  left: 8rpx;
  top: 8rpx;
  width: 18rpx;
  height: 18rpx;
  border: 4rpx solid currentColor;
  border-radius: 8rpx;
}

.condition-icon--parts::before {
  left: 7rpx;
  top: 15rpx;
  width: 20rpx;
  height: 4rpx;
  border-radius: 999rpx;
  background: currentColor;
  transform: rotate(-35deg);
}

.condition-icon--parts::after {
  right: 5rpx;
  top: 5rpx;
  width: 9rpx;
  height: 9rpx;
  border: 3rpx solid currentColor;
  border-radius: 50%;
}

.condition-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.option-card-title {
  color: #0F172A;
  font-size: 24rpx;
  font-weight: 900;
  line-height: 1.15;
}

.option-card-desc {
  color: #667085;
  font-size: 21rpx;
  font-weight: 700;
  line-height: 1.2;
}

.condition-card.active .option-card-title {
  color: #4F7DE8;
}

.category-select {
  display: flex;
  align-items: center;
  min-height: 72rpx;
  margin-bottom: 22rpx;
  padding: 0 18rpx 0 28rpx;
  border-radius: 18rpx;
  background: linear-gradient(90deg, #F3F7FF, #FFFFFF);
  box-sizing: border-box;
}

.category-select--error {
  border: 1rpx solid rgba(209, 67, 67, 0.48);
  background: rgba(209, 67, 67, 0.045);
}

.category-select-text {
  flex: 1;
  min-width: 0;
  color: #344054;
  font-size: 25rpx;
  font-weight: 800;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.category-select-text.placeholder {
  color: #667085;
}

.category-bag {
  position: relative;
  width: 56rpx;
  height: 56rpx;
  margin-left: 14rpx;
  border-radius: 14rpx;
  background: #E5EEFF;
  color: #5F88EF;
  flex-shrink: 0;
}

.bag-body {
  position: absolute;
  left: 15rpx;
  top: 21rpx;
  width: 26rpx;
  height: 22rpx;
  border: 4rpx solid currentColor;
  border-radius: 4rpx;
  box-sizing: border-box;
}

.bag-handle {
  position: absolute;
  left: 20rpx;
  top: 13rpx;
  width: 16rpx;
  height: 12rpx;
  border: 4rpx solid currentColor;
  border-bottom: 0;
  border-radius: 9rpx 9rpx 0 0;
  box-sizing: border-box;
}

.desc-section {
  padding-bottom: 22rpx;
}

.desc-input {
  width: 100%;
  height: 238rpx;
  padding: 22rpx;
  border-radius: 18rpx;
  background: #F6F8FB;
  color: #101828;
  font-size: 25rpx;
  line-height: 1.65;
  box-sizing: border-box;
  overflow-x: hidden;
}

.desc-input--error {
  border: 1rpx solid rgba(209, 67, 67, 0.48);
  background: rgba(209, 67, 67, 0.045);
}

.desc-tags {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-top: 20rpx;
  overflow-x: auto;
  white-space: nowrap;
}

.desc-tag,
.location-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 46rpx;
  padding: 0 20rpx;
  border-radius: 999rpx;
  background: #F1F4F8;
  color: #4B5565;
  font-size: 22rpx;
  font-weight: 800;
  white-space: nowrap;
}

.location-line {
  display: flex;
  align-items: center;
  min-height: 76rpx;
  padding: 0 18rpx;
  border-radius: 18rpx;
  background: #F6F8FB;
  box-sizing: border-box;
}

.location-input {
  flex: 1;
  min-width: 0;
  height: 76rpx;
  color: #101828;
  font-size: 25rpx;
  font-weight: 700;
  background: transparent;
}

.location-chips {
  display: flex;
  align-items: center;
  gap: 14rpx;
  padding: 18rpx 0 24rpx;
  overflow-x: auto;
  white-space: nowrap;
}

.contact-section {
  position: relative;
  padding-bottom: 28rpx;
  overflow: hidden;
}

.contact-art {
  position: relative;
  width: 118rpx;
  height: 72rpx;
  flex-shrink: 0;
}

.chat-block {
  position: absolute;
  border-radius: 14rpx;
  background: #DDE8FF;
}

.chat-block-a {
  right: 34rpx;
  top: 16rpx;
  width: 48rpx;
  height: 42rpx;
}

.chat-block-b {
  right: 8rpx;
  top: 2rpx;
  width: 52rpx;
  height: 48rpx;
  background: #779AF0;
}

.chat-face {
  position: absolute;
  right: 20rpx;
  top: 21rpx;
  width: 22rpx;
  height: 12rpx;
  border-bottom: 4rpx solid #FFFFFF;
  border-radius: 0 0 999rpx 999rpx;
}

.chat-face::before,
.chat-face::after {
  content: '';
  position: absolute;
  top: -7rpx;
  width: 4rpx;
  height: 4rpx;
  border-radius: 50%;
  background: #FFFFFF;
}

.chat-face::before {
  left: 1rpx;
}

.chat-face::after {
  right: 1rpx;
}

.contact-list {
  display: flex;
  flex-direction: column;
  gap: 22rpx;
  padding-top: 10rpx;
}

.contact-rule {
  display: flex;
  align-items: center;
  gap: 18rpx;
  color: #202938;
  font-size: 24rpx;
  font-weight: 800;
  line-height: 1.35;
}

.contact-rule-icon {
  position: relative;
  width: 38rpx;
  height: 38rpx;
  border-radius: 50%;
  background: #E8EEF6;
  color: #667085;
  flex-shrink: 0;
}

.contact-rule-icon::before,
.contact-rule-icon::after {
  content: '';
  position: absolute;
  box-sizing: border-box;
}

.rule-chat::before {
  left: 9rpx;
  top: 10rpx;
  width: 20rpx;
  height: 15rpx;
  border: 3rpx solid currentColor;
  border-radius: 5rpx;
}

.rule-exchange::before {
  left: 8rpx;
  top: 8rpx;
  width: 22rpx;
  height: 22rpx;
  border: 3rpx solid currentColor;
  border-radius: 50%;
}

.rule-platform::before {
  left: 10rpx;
  top: 8rpx;
  width: 18rpx;
  height: 22rpx;
  border: 3rpx solid currentColor;
  border-radius: 5rpx;
}

.publish-submit-area {
  flex-shrink: 0;
  margin-bottom: 0;
  padding: 16rpx 22rpx calc(22rpx + env(safe-area-inset-bottom));
  border-radius: 24rpx 24rpx 0 0;
  box-shadow: none;
}

.pbtn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 18rpx;
  width: 100%;
  height: 84rpx;
  margin: 0;
  border: none;
  border-radius: 42rpx;
  background: linear-gradient(135deg, #5E8DFF, #386DE6);
  color: #FFFFFF;
  font-size: 30rpx;
  font-weight: 900;
  line-height: 84rpx;
  box-shadow: 0 8rpx 18rpx rgba(56, 109, 230, 0.18);
}

.pbtn[disabled] {
  opacity: 0.72;
}

.pbtn::after {
  border: none;
}

.category-mask {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 20;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  background: rgba(21, 30, 44, 0.22);
}

.category-sheet {
  width: 100%;
  max-height: 64vh;
  padding: 0 24rpx 26rpx;
  border-radius: 30rpx 30rpx 0 0;
  background: #FFFFFF;
  box-sizing: border-box;
  box-shadow: 0 -16rpx 36rpx rgba(33, 55, 82, 0.12);
}

.sheet-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 90rpx;
}

.sheet-title {
  color: #121926;
  font-size: 30rpx;
  font-weight: 900;
}

.sheet-close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  background: #F1F4F8;
  color: #667085;
  font-size: 34rpx;
  line-height: 56rpx;
}

.sheet-body {
  max-height: calc(64vh - 118rpx);
}

.sheet-category {
  display: flex;
  align-items: center;
  min-height: 78rpx;
  padding: 0 18rpx;
  margin-bottom: 12rpx;
  border-radius: 18rpx;
  background: #F6F8FB;
  box-sizing: border-box;
}

.sheet-category.active {
  background: #EEF4FF;
}

.sheet-category-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.sheet-category-title {
  color: #121926;
  font-size: 26rpx;
  font-weight: 900;
}

.sheet-category-desc {
  color: #667085;
  font-size: 22rpx;
  font-weight: 700;
}

.sheet-category-check {
  position: relative;
  width: 28rpx;
  height: 28rpx;
  border: 3rpx solid #B5BEC9;
  border-radius: 50%;
  box-sizing: border-box;
  flex-shrink: 0;
}

.sheet-category.active .sheet-category-check {
  border-color: #4F7DE8;
  background: #4F7DE8;
}

.sheet-category.active .sheet-category-check::after {
  content: '';
  position: absolute;
  left: 6rpx;
  top: 5rpx;
  width: 10rpx;
  height: 6rpx;
  border-left: 3rpx solid #FFFFFF;
  border-bottom: 3rpx solid #FFFFFF;
  transform: rotate(-45deg);
}

.sheet-sub-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin: 10rpx 0 22rpx;
  padding: 16rpx;
  border-radius: 18rpx;
  background: #F6F8FB;
}

.sheet-sub-item {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 50rpx;
  padding: 0 20rpx;
  border-radius: 999rpx;
  background: #FFFFFF;
  color: #4B5565;
  font-size: 23rpx;
  font-weight: 800;
}

.sheet-sub-item.active {
  background: #4F7DE8;
  color: #FFFFFF;
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

.tradetype-section {
  padding-bottom: 24rpx;
}

.tradetype-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16rpx;
}

.tradetype-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10rpx;
  padding: 28rpx 16rpx;
  border-radius: 18rpx;
  border: 2rpx solid #E5EAF0;
  background: #F6F8FB;
  box-sizing: border-box;
  transition: all 160ms ease;
}

.tradetype-card.active {
  border-color: #5A83F0;
  background: #F5F8FF;
  box-shadow: 0 4rpx 14rpx rgba(90, 131, 240, 0.16);
}

.tradetype-icon {
  position: relative;
  width: 44rpx;
  height: 44rpx;
  border-radius: 12rpx;
  background: #E8EDF4;
  color: #667085;
}

.tradetype-card.active .tradetype-icon {
  background: #E4EDFF;
  color: #4F7DE8;
}

.tradetype-icon--sell::before {
  content: '';
  position: absolute;
  left: 10rpx;
  top: 10rpx;
  width: 24rpx;
  height: 24rpx;
  border: 4rpx solid currentColor;
  border-radius: 50%;
  box-sizing: border-box;
}

.tradetype-icon--sell::after {
  content: '';
  position: absolute;
  left: 17rpx;
  top: 17rpx;
  width: 10rpx;
  height: 4rpx;
  background: currentColor;
  border-radius: 999rpx;
}

.tradetype-icon--buy::before {
  content: '';
  position: absolute;
  left: 11rpx;
  top: 11rpx;
  width: 22rpx;
  height: 22rpx;
  border: 4rpx solid currentColor;
  border-radius: 6rpx;
  box-sizing: border-box;
}

.tradetype-icon--buy::after {
  content: '';
  position: absolute;
  left: 19rpx;
  top: 19rpx;
  width: 6rpx;
  height: 6rpx;
  background: currentColor;
  border-radius: 50%;
}

.tradetype-label {
  font-size: 30rpx;
  font-weight: 900;
  color: #0F172A;
}

.tradetype-card.active .tradetype-label {
  color: #4F7DE8;
}

.tradetype-desc {
  font-size: 22rpx;
  font-weight: 700;
  color: #667085;
}

.bottom-spacer {
  height: 0;
}
</style>
