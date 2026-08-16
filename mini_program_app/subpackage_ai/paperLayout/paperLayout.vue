<template>
  <view class="page">
    <nav-bar title="纸质试卷设置" :showBack="true" placeholder />
    <scroll-view scroll-y class="content">
      <view v-if="loading" class="loading">正在读取版式配置...</view>
      <template v-else>
        <view class="card">
          <text class="section-title">纸张版式</text>
          <view class="setting-row">
            <text>纸张</text>
            <view class="choices">
              <text :class="['choice', { active: form.paperSize === 'A4' }]" @click="form.paperSize = 'A4'">A4</text>
              <text :class="['choice', { active: form.paperSize === 'A3' }]" @click="form.paperSize = 'A3'">A3</text>
            </view>
          </view>
          <view class="setting-row">
            <text>方向</text>
            <view class="choices">
              <text :class="['choice', { active: form.orientation === 'portrait' }]" @click="form.orientation = 'portrait'">纵向</text>
              <text :class="['choice', { active: form.orientation === 'landscape' }]" @click="form.orientation = 'landscape'">横向</text>
            </view>
          </view>
          <view class="setting-row">
            <text>栏数</text>
            <view class="choices">
              <text :class="['choice', { active: form.columnsCount === 1 }]" @click="form.columnsCount = 1">单栏</text>
              <text :class="['choice', { active: form.columnsCount === 2 }]" @click="form.columnsCount = 2">双栏</text>
            </view>
          </view>
          <view class="setting-row">
            <text>栏距</text>
            <view class="number-field"><input v-model="form.columnGap" type="digit" /><text>cm</text></view>
          </view>
        </view>

        <view class="card">
          <text class="section-title">装订线</text>
          <view class="setting-row"><text>显示装订线</text><switch color="#4d78e8" :checked="form.bindingLine" @change="form.bindingLine = $event.detail.value" /></view>
          <view v-if="form.bindingLine" class="setting-row">
            <text>装订线位置</text>
            <view class="choices">
              <text :class="['choice', { active: form.bindingPosition === 'left' }]" @click="form.bindingPosition = 'left'">左</text>
              <text :class="['choice', { active: form.bindingPosition === 'right' }]" @click="form.bindingPosition = 'right'">右</text>
            </view>
          </view>
        </view>

        <view class="card">
          <text class="section-title">学生信息栏</text>
          <view class="setting-row"><text>学校</text><switch color="#4d78e8" :checked="form.showSchool" @change="form.showSchool = $event.detail.value" /></view>
          <view class="setting-row"><text>年级</text><switch color="#4d78e8" :checked="form.showGrade" @change="form.showGrade = $event.detail.value" /></view>
          <view class="setting-row"><text>班级</text><switch color="#4d78e8" :checked="form.showClass" @change="form.showClass = $event.detail.value" /></view>
          <view class="setting-row"><text>姓名</text><switch color="#4d78e8" :checked="form.showName" @change="form.showName = $event.detail.value" /></view>
          <view class="setting-row"><text>学号</text><switch color="#4d78e8" :checked="form.showStudentNo" @change="form.showStudentNo = $event.detail.value" /></view>
        </view>

        <view class="card">
          <text class="section-title">字号</text>
          <view class="setting-row"><text>标题字号</text><view class="number-field"><input v-model="form.titleFontSize" type="number" /><text>pt</text></view></view>
          <view class="setting-row"><text>副标题字号</text><view class="number-field"><input v-model="form.subtitleFontSize" type="number" /><text>pt</text></view></view>
          <view class="setting-row"><text>正文字号</text><view class="number-field"><input v-model="form.bodyFontSize" type="number" /><text>pt</text></view></view>
        </view>
      </template>
    </scroll-view>
    <view class="bottom-actions">
      <button class="default-button" :disabled="loading || saving" @click="restoreDefaults">恢复默认</button>
      <button class="save-button" :loading="saving" :disabled="loading" @click="save">保存并预览</button>
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getPaperLayout, updatePaperLayout } from '@/api/paper.js'

export default {
  components: { NavBar },
  data() { return { paperId: null, loading: true, saving: false, form: {} } },
  onLoad(query) {
    this.paperId = query.paperId
    if (!this.paperId) {
      uni.showToast({ title: '缺少试卷ID', icon: 'none' })
      this.loading = false
      return
    }
    this.load()
  },
  methods: {
    async load(defaults = false) {
      this.loading = true
      try {
        const result = await getPaperLayout(this.paperId, defaults)
        this.form = this.normalize(result.data || {})
      } finally { this.loading = false }
    },
    normalize(value) {
      return {
        paperSize: value.paperSize,
        orientation: value.orientation,
        columnsCount: Number(value.columnsCount),
        columnGap: Number(value.columnGap),
        bindingLine: Boolean(value.bindingLine),
        bindingPosition: value.bindingPosition,
        marginTop: Number(value.marginTop),
        marginBottom: Number(value.marginBottom),
        marginLeft: Number(value.marginLeft),
        marginRight: Number(value.marginRight),
        showSchool: Boolean(value.showSchool),
        showGrade: Boolean(value.showGrade),
        showClass: Boolean(value.showClass),
        showName: Boolean(value.showName),
        showStudentNo: Boolean(value.showStudentNo),
        titleFontSize: Number(value.titleFontSize),
        subtitleFontSize: Number(value.subtitleFontSize),
        bodyFontSize: Number(value.bodyFontSize)
      }
    },
    restoreDefaults() { this.load(true) },
    async save() {
      this.saving = true
      try {
        await updatePaperLayout(this.paperId, this.normalize(this.form))
        await this.load()
        uni.showToast({ title: '版式已保存', icon: 'success' })
        setTimeout(() => uni.redirectTo({ url: `/subpackage_ai/paperPrintPreview/paperPrintPreview?paperId=${this.paperId}` }), 500)
      } finally { this.saving = false }
    }
  }
}
</script>

<style scoped lang="scss">
.page{min-height:100vh;background:#f3f7fc;padding-bottom:130rpx}.content{height:calc(100vh - 220rpx);box-sizing:border-box;padding:20rpx 24rpx}.loading{text-align:center;color:#7c899d;padding:100rpx 0}.card{background:#fff;border-radius:20rpx;padding:8rpx 24rpx;margin-bottom:18rpx;box-shadow:0 6rpx 20rpx rgba(64,101,160,.06)}.section-title{display:block;padding:22rpx 0 10rpx;font-size:28rpx;font-weight:600;color:#263852}.setting-row{min-height:88rpx;display:flex;align-items:center;justify-content:space-between;border-bottom:1rpx solid #edf1f6;font-size:26rpx;color:#43516a}.setting-row:last-child{border-bottom:0}.choices{display:flex;gap:12rpx}.choice{min-width:76rpx;padding:12rpx 18rpx;text-align:center;border:1rpx solid #c9d8ef;border-radius:24rpx;color:#5f6e84;background:#fff}.choice.active{color:#fff;border-color:#4d78e8;background:#4d78e8}.number-field{display:flex;align-items:center;gap:10rpx;color:#8a96a8}.number-field input{width:110rpx;padding:10rpx 14rpx;text-align:center;border:1rpx solid #c9d8ef;border-radius:12rpx;color:#34445d;background:#fafdff}.bottom-actions{position:fixed;left:0;right:0;bottom:0;display:flex;gap:16rpx;padding:16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));background:#fff;box-shadow:0 -5rpx 18rpx rgba(54,83,130,.08)}.bottom-actions button{flex:1;border-radius:42rpx;font-size:27rpx}.default-button{background:#edf3fc;color:#4d78e8}.save-button{background:#4d78e8;color:#fff}
</style>
