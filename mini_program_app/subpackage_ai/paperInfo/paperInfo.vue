<template>
  <view class="page">
    <nav-bar title="试卷基本信息" :showBack="true" placeholder />
    <view class="form">
      <view class="field"><text>试卷名称</text><input v-model="form.name" placeholder="例如：Python期末试卷" maxlength="60" /></view>
      <view class="field">
        <text>所属科目</text>
        <picker :range="subjectNames" :value="subjectIndex" @change="selectSubject">
          <view class="picker">{{ form.subject || '请选择科目' }} <text>›</text></view>
        </picker>
        <text class="create-link" @click="createDictionary('subject')">＋ 新建科目</text>
        <text class="manage-link" @click="openManager('subject')">管理</text>
      </view>
      <view class="field">
        <text>试卷分类</text>
        <picker :range="categoryNames" :value="categoryIndex" @change="selectCategory">
          <view class="picker">{{ form.category || '请选择分类' }} <text>›</text></view>
        </picker>
        <text class="create-link" @click="createDictionary('paper_category')">＋ 新建分类</text>
        <text class="manage-link" @click="openManager('paper_category')">管理</text>
      </view>
      <view class="field"><text>考试时长（分钟）</text><input v-model.number="form.duration" type="number" placeholder="可选" /></view>
      <view class="field"><text>试卷备注</text><textarea v-model="form.remark" placeholder="填写试卷说明（可选）" /></view>
      <button class="primary" :loading="saving" @click="next">下一步：选择试题</button>
    </view>
    <view v-if="manageType" class="manager-mask" @click="manageType = null">
      <view class="manager-panel" @click.stop>
        <view class="manager-title"><text>{{ manageType === 'subject' ? '管理科目' : '管理试卷分类' }}</text><text @click="manageType = null">×</text></view>
        <view v-for="item in managedItems" :key="item.id" class="manager-row">
          <text>{{ item.name }}</text><text v-if="item.creatorId == null" class="system-tag">系统</text><text v-else class="delete-link" @click="removeDictionary(item)">删除</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { createPaper, updatePaper, getPaper, listPaperDictionaries, createPaperDictionary, deletePaperDictionary } from '@/api/paper.js'

export default {
  components: { NavBar },
  data() {
    return {
      paperId: null, source: 'public', saving: false, manageType: null,
      subjects: [], categories: [],
      form: { name: '', subjectId: null, subject: '', category: '', duration: '', remark: '' }
    }
  },
  computed: {
    subjectNames() { return this.subjects.map(item => item.name) },
    categoryNames() { return this.categories.map(item => item.name) },
    subjectIndex() { return Math.max(0, this.subjects.findIndex(item => item.name === this.form.subject)) },
    categoryIndex() { return Math.max(0, this.categories.findIndex(item => item.name === this.form.category)) },
    managedItems() { return this.manageType === 'subject' ? this.subjects : this.categories }
  },
  onLoad(query) {
    this.paperId = query.paperId || null
    this.source = query.source || 'public'
    this.loadDictionaries()
    if (this.paperId) this.load()
  },
  methods: {
    async loadDictionaries() {
      const [subjects, categories] = await Promise.all([
        listPaperDictionaries('subject'),
        listPaperDictionaries('paper_category')
      ])
      this.subjects = subjects.data || []
      this.categories = categories.data || []
    },
    selectSubject(event) {
      const item = this.subjects[Number(event.detail.value)]
      if (item) {
        this.form.subjectId = item.id
        this.form.subject = item.name
      }
    },
    selectCategory(event) {
      const item = this.categories[Number(event.detail.value)]
      if (item) this.form.category = item.name
    },
    async load() {
      const result = await getPaper(this.paperId)
      this.form = {
        name: result.data.name,
        subjectId: result.data.subjectId,
        subject: result.data.subject,
        category: result.data.category,
        duration: result.data.duration || '',
        remark: result.data.remark || ''
      }
    },
    async createDictionary(type) {
      const label = type === 'subject' ? '科目' : '分类'
      uni.showModal({
        title: `新建${label}`,
        editable: true,
        placeholderText: `请输入${label}名称`,
        success: async modal => {
          if (!modal.confirm) return
          const name = (modal.content || '').trim()
          if (!name) return uni.showToast({ title: `${label}名称不能为空`, icon: 'none' })
          try {
            const result = await createPaperDictionary({ type, name })
            await this.loadDictionaries()
            if (type === 'subject') {
              this.form.subjectId = result.data.id
              this.form.subject = result.data.name
            } else {
              this.form.category = result.data.name
            }
            uni.showToast({ title: `${label}已新增`, icon: 'success' })
          } catch (error) {
            uni.showToast({ title: error?.msg || error?.message || `新增${label}失败`, icon: 'none' })
          }
        }
      })
    },
    openManager(type) { this.manageType = type },
    removeDictionary(item) {
      uni.showModal({
        title: '确认删除',
        content: `确定删除“${item.name}”吗？`,
        success: async result => {
          if (!result.confirm) return
          try {
            await deletePaperDictionary(item.id)
            if (this.manageType === 'subject' && this.form.subjectId === item.id) {
              this.form.subjectId = null
              this.form.subject = ''
            }
            if (this.manageType === 'paper_category' && this.form.category === item.name) this.form.category = ''
            await this.loadDictionaries()
            uni.showToast({ title: '删除成功', icon: 'success' })
          } catch (error) {
            uni.showToast({ title: error?.msg || error?.message || '删除失败', icon: 'none' })
          }
        }
      })
    },
    async next() {
      if (!this.form.name.trim() || !this.form.subject.trim() || !this.form.category) {
        uni.showToast({ title: '请填写试卷名称、科目和分类', icon: 'none' })
        return
      }
      this.saving = true
      try {
        const result = this.paperId ? await updatePaper(this.paperId, this.form) : await createPaper(this.form)
        this.paperId = result.data.id
        uni.navigateTo({ url: `/subpackage_ai/paperSelect/paperSelect?paperId=${this.paperId}&source=${this.source}` })
      } finally {
        this.saving = false
      }
    }
  }
}
</script>

<style scoped lang="scss">
.page{min-height:100vh;background:#f5f8fc}.form{padding:24rpx}.field{background:#fff;border-radius:18rpx;padding:22rpx 24rpx;margin-bottom:16rpx}.field>text{display:block;font-size:25rpx;color:#33415a;margin-bottom:14rpx}.field input,.field textarea{width:100%;font-size:28rpx;color:#27364d}.field textarea{height:130rpx}.picker{font-size:28rpx;color:#27364d;display:flex;justify-content:space-between}.create-link,.manage-link{display:inline-block!important;margin:18rpx 18rpx 0 0!important;color:#4d78e8!important;font-size:24rpx!important}.manage-link{color:#65738d!important}.primary{margin-top:40rpx;background:#4d78e8;color:#fff;border-radius:50rpx;font-size:29rpx}.manager-mask{position:fixed;inset:0;background:rgba(0,0,0,.35);z-index:20;display:flex;align-items:flex-end}.manager-panel{background:#fff;border-radius:24rpx 24rpx 0 0;width:100%;max-height:70vh;padding:24rpx}.manager-title,.manager-row{display:flex;justify-content:space-between;align-items:center;padding:18rpx 8rpx;border-bottom:1rpx solid #edf0f5}.manager-title{font-size:30rpx;font-weight:600}.system-tag{color:#9aa5b8}.delete-link{color:#e45d65}
</style>
