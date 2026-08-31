<template>
  <view class="page">
    <nav-bar title="页面格式" :showBack="true" placeholder />
    <view class="steps"><view class="step done">1 试卷信息与选题</view><view class="step active">2 页面格式</view><view class="step">3 预览与确认</view></view>
    <scroll-view scroll-y class="content">
      <view v-if="loading" class="loading">正在读取版式配置...</view>
      <template v-else>
        <view class="card template-card"><text class="section-title">版式模板</text><view class="template-list"><view v-for="name in templates" :key="name" :class="['template', { active: form.templateName === name }]" @click="chooseTemplate(name)"><text class="template-name">{{ name }}</text><text class="template-desc">{{ name === '标准模板' ? 'A3 · 横向 · 双栏' : 'A4 · 纵向 · 单栏' }}</text></view></view></view>
        <view class="card"><text class="section-title">纸张与排版</text>
          <view class="setting-row"><text>纸张</text><view class="choices"><text v-for="item in ['A3','A4']" :key="item" :class="['choice',{active:form.paperSize===item}]" @click="form.paperSize=item">{{ item }}</text></view></view>
          <view class="setting-row"><text>方向</text><view class="choices"><text :class="['choice',{active:form.orientation==='landscape'}]" @click="form.orientation='landscape'">横向</text><text :class="['choice',{active:form.orientation==='portrait'}]" @click="form.orientation='portrait'">纵向</text></view></view>
          <view class="setting-row"><text>栏数</text><view class="choices"><text :class="['choice',{active:form.columnsCount===1}]" @click="form.columnsCount=1">单栏</text><text :class="['choice',{active:form.columnsCount===2}]" @click="form.columnsCount=2">双栏</text></view></view>
          <view class="setting-row"><text>栏距</text><view class="number-field"><input v-model.number="form.columnGap" type="digit" /><text>cm</text></view></view>
          <view class="setting-row"><text>页边距</text><view class="choices"><text :class="['choice',{active:form.marginPreset==='标准装订线'}]" @click="applyMarginPreset('标准装订线')">标准装订线</text><text :class="['choice',{active:form.marginPreset==='自定义'}]" @click="form.marginPreset='自定义'">自定义</text></view></view>
          <view v-if="form.marginPreset==='自定义'" class="margin-grid"><view v-for="item in marginItems" :key="item.key" class="margin-item"><text>{{ item.label }}</text><input v-model.number="form[item.key]" type="digit" /><text>cm</text></view></view>
        </view>
        <view class="card"><text class="section-title">装订线</text><view class="setting-row"><text>显示装订线</text><switch color="#4d78e8" :checked="form.bindingLine" @change="form.bindingLine=$event.detail.value" /></view><view v-if="form.bindingLine" class="setting-row"><text>位置</text><view class="choices"><text :class="['choice',{active:form.bindingPosition==='left'}]" @click="form.bindingPosition='left'">左</text><text :class="['choice',{active:form.bindingPosition==='right'}]" @click="form.bindingPosition='right'">右</text></view></view></view>
        <view class="card"><text class="section-title">学生信息栏</text><view class="setting-row"><text>显示学生信息栏</text><switch color="#4d78e8" :checked="form.showStudentInfo" @change="form.showStudentInfo=$event.detail.value" /></view><view v-if="form.showStudentInfo" class="field-list"><label v-for="field in studentFieldOptions" :key="field.key" class="field-item"><checkbox :value="field.key" :checked="form.studentFields.includes(field.key)" @click="toggleStudentField(field.key)" /><text>{{ field.label }}</text></label></view></view>
        <view class="card"><text class="section-title">字号</text><view class="setting-row"><text>标题字号</text><view class="number-field"><input v-model.number="form.titleFontSize" type="number" /><text>pt</text></view></view><view class="setting-row"><text>副标题字号</text><view class="number-field"><input v-model.number="form.subtitleFontSize" type="number" /><text>pt</text></view></view><view class="setting-row"><text>正文字号</text><view class="number-field"><input v-model.number="form.bodyFontSize" type="number" /><text>pt</text></view></view></view>
      </template>
    </scroll-view>
    <view class="bottom-actions"><button class="back-button" :disabled="saving" @click="goBack">上一步</button><button class="default-button" :disabled="loading||saving" @click="restoreDefaults">恢复默认</button><button class="save-button" :loading="saving" :disabled="loading" @click="save">生成真实预览</button></view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getPaperLayout, updatePaperLayout } from '@/api/paper.js'

export default {
  components: { NavBar },
  data() { return { paperId: null, loading: true, saving: false, templates: ['标准模板', '简洁模板'], marginItems: [{ key: 'marginTop', label: '上' }, { key: 'marginBottom', label: '下' }, { key: 'marginLeft', label: '左' }, { key: 'marginRight', label: '右' }], studentFieldOptions: [{ key: 'school', label: '学校' }, { key: 'grade', label: '年级' }, { key: 'class', label: '班级' }, { key: 'name', label: '姓名' }, { key: 'studentNo', label: '学号' }], form: {} } },
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
    async load(defaults = false, templateName = '') {
      this.loading = true
      try {
        const result = await getPaperLayout(this.paperId, defaults, templateName)
        this.form = this.normalize(result.data || {})
      } finally { this.loading = false }
    },
    normalize(value) {
      const fields = value.studentFields ? String(value.studentFields).split(',').filter(Boolean) : ['school', 'grade', 'class', 'name', 'studentNo'].filter(key => value[`show${key === 'studentNo' ? 'StudentNo' : key.charAt(0).toUpperCase() + key.slice(1)}`] !== false)
      return {
        templateName: value.templateName || '标准模板',
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
        marginPreset: value.marginPreset || '标准装订线',
        showStudentInfo: value.showStudentInfo !== false,
        studentFields: fields,
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
    chooseTemplate(name) { if (name !== this.form.templateName) this.load(true, name) },
    applyMarginPreset(name) { this.form.marginPreset = name; if (name === '标准装订线') Object.assign(this.form, { marginTop: 2.54, marginBottom: 2.54, marginLeft: 2.8, marginRight: 2 }) },
    toggleStudentField(key) { const fields = this.form.studentFields.slice(); const index = fields.indexOf(key); index >= 0 ? fields.splice(index, 1) : fields.push(key); this.form.studentFields = fields },
    restoreDefaults() { this.load(true, this.form.templateName) },
    goBack() { uni.navigateBack() },
    async save() {
      this.saving = true
      try {
        const payload = this.normalize(this.form)
        payload.studentFields = this.form.studentFields.join(',')
        payload.showSchool = this.form.studentFields.includes('school')
        payload.showGrade = this.form.studentFields.includes('grade')
        payload.showClass = this.form.studentFields.includes('class')
        payload.showName = this.form.studentFields.includes('name')
        payload.showStudentNo = this.form.studentFields.includes('studentNo')
        await updatePaperLayout(this.paperId, payload)
        await this.load()
        uni.showToast({ title: '版式已保存', icon: 'success' })
        setTimeout(() => uni.redirectTo({ url: `/subpackage_ai/paperPrintPreview/paperPrintPreview?paperId=${this.paperId}` }), 500)
      } finally { this.saving = false }
    }
  }
}
</script>

<style scoped lang="scss">
.page{min-height:100vh;background:#f5f7fa;padding-bottom:150rpx}.steps{display:flex;align-items:center;padding:20rpx;background:#fff;border-bottom:1rpx solid #e2e7ed}.step{flex:1;display:flex;flex-direction:column;align-items:center;gap:6rpx;color:#98a2b3;font-size:19rpx}.step-no{width:38rpx;height:38rpx;line-height:38rpx;text-align:center;border-radius:50%;background:#eef1f4;color:#697586}.step.active{color:#1e6bb8;font-weight:600}.step.active .step-no{background:#1e6bb8;color:#fff}.step.done{color:#66849e}.content{height:calc(100vh - 300rpx);box-sizing:border-box;padding:20rpx 24rpx}.loading{text-align:center;color:#7c899d;padding:100rpx 0}.card{background:#fff;border:1rpx solid #e4e8ed;border-radius:10rpx;padding:8rpx 24rpx;margin-bottom:18rpx;box-shadow:0 4rpx 16rpx rgba(16,24,40,.035)}.section-title{display:block;padding:22rpx 0 10rpx;font-size:28rpx;font-weight:600;color:#263852}.template-list{display:flex;gap:16rpx;padding:8rpx 0 20rpx}.template{flex:1;border:2rpx solid #d8e3f2;border-radius:10rpx;padding:18rpx}.template.active{border-color:#1e6bb8;background:#f3f8fc}.template-name{display:block;color:#263852;font-size:27rpx;font-weight:600}.template-desc{display:block;color:#8493a8;font-size:22rpx;margin-top:8rpx}.setting-row{min-height:88rpx;display:flex;align-items:center;justify-content:space-between;border-bottom:1rpx solid #edf1f6;font-size:26rpx;color:#43516a}.setting-row:last-child{border-bottom:0}.choices{display:flex;gap:12rpx;flex-wrap:wrap;justify-content:flex-end}.choice{min-width:76rpx;padding:12rpx 18rpx;text-align:center;border:1rpx solid #c9d8ef;border-radius:8rpx;color:#5f6e84;background:#fff}.choice.active{color:#fff;border-color:#1e6bb8;background:#1e6bb8}.number-field{display:flex;align-items:center;gap:10rpx;color:#8a96a8}.number-field input,.margin-item input{width:110rpx;padding:10rpx 14rpx;text-align:center;border:1rpx solid #c9d8ef;border-radius:8rpx;color:#34445d;background:#fafdff}.margin-grid{display:grid;grid-template-columns:1fr 1fr;gap:14rpx;padding:18rpx 0}.margin-item{display:flex;align-items:center;gap:8rpx;color:#697991;font-size:23rpx}.field-list{display:flex;flex-wrap:wrap;padding:12rpx 0 20rpx;gap:18rpx}.field-item{display:flex;align-items:center;gap:6rpx;color:#51637c;font-size:25rpx}.bottom-actions{position:fixed;left:0;right:0;bottom:0;display:flex;gap:12rpx;padding:16rpx 20rpx calc(16rpx + env(safe-area-inset-bottom));background:#fff;box-shadow:0 -5rpx 18rpx rgba(54,83,130,.08)}.bottom-actions button{flex:1;border-radius:8rpx;font-size:24rpx}.back-button{background:#f1f4f8;color:#61728b}.default-button{background:#edf3fc;color:#1e6bb8}.save-button{background:#1e6bb8;color:#fff}
</style>
