<template>
  <view class="page">
    <nav-bar title="试卷基本信息" :showBack="true" placeholder />
    <view class="steps"><view class="step active"><text class="step-no">1</text><text>试卷信息与选题</text></view><view class="step"><text class="step-no">2</text><text>页面格式</text></view><view class="step"><text class="step-no">3</text><text>预览与确认</text></view></view>
    <view class="form">
      <view class="field"><text>试卷名称</text><input v-model="form.name" placeholder="例如：Python期末试卷" maxlength="60" /></view>
      <view class="field">
        <text>所属科目</text>
        <view class="picker" @click="openSelector('subject')"><text :class="{placeholder: !form.subject}">{{ form.subject || '请选择科目' }}</text><text>›</text></view>
        <text class="create-link" @click="createDictionary('subject')">＋ 新建科目</text>
        <text class="manage-link" @click="openManager('subject')">管理</text>
      </view>
      <view class="field">
        <text>试卷分类</text>
        <view class="picker" @click="openSelector('paper_category')"><text :class="{placeholder: !form.category}">{{ form.category || '请选择分类' }}</text><text>›</text></view>
        <text class="create-link" @click="createDictionary('paper_category')">＋ 新建分类</text>
        <text class="manage-link" @click="openManager('paper_category')">管理</text>
      </view>
      <view class="field"><text>考试时长（分钟）</text><input v-model.number="form.duration" type="number" placeholder="可选" /></view>
      <view class="field"><text>试卷备注</text><textarea v-model="form.remark" placeholder="填写试卷说明（可选）" /></view>
      <button class="primary" :loading="saving" @click="next">下一步：选择试题</button>
    </view>
    <view v-if="selectorType" class="manager-mask selector-mask" @click="selectorType = null">
      <view class="manager-panel selector-panel" @click.stop>
        <view class="manager-title"><text>选择{{ selectorType === 'subject' ? '科目' : '试卷分类' }}</text><text class="close-button" @click="selectorType = null">×</text></view>
        <scroll-view scroll-y class="selector-list">
          <view v-if="selectorItems.length === 0" class="selector-empty">暂无可选项，请先新建</view>
          <view v-for="item in selectorItems" :key="item.id" :class="['selector-row', {selected: isSelected(item)}]" @click="selectDictionary(item)">
            <text>{{ item.name }}</text><text v-if="isSelected(item)" class="selected-mark">✓</text>
          </view>
        </scroll-view>
        <button class="selector-create" @click="createFromSelector">＋ 新建{{ selectorType === 'subject' ? '科目' : '分类' }}</button>
      </view>
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
      paperId: null, source: 'public', saving: false, manageType: null, selectorType: null,
      dictionariesLoading: false, dictionariesError: '',
      subjects: [], categories: [],
      form: { name: '', subjectId: null, subject: '', category: '', duration: '', remark: '' }
    }
  },
  computed: {
    subjectNames() { return this.subjects.map(item => item.name) },
    categoryNames() { return this.categories.map(item => item.name) },
    subjectIndex() { return Math.max(0, this.subjects.findIndex(item => item.name === this.form.subject)) },
    categoryIndex() { return Math.max(0, this.categories.findIndex(item => item.name === this.form.category)) },
    managedItems() { return this.manageType === 'subject' ? this.subjects : this.categories },
    selectorItems() { return this.selectorType === 'subject' ? this.subjects : this.categories }
  },
  onLoad(query) {
    this.paperId = query.paperId || null
    this.source = query.source || 'public'
    this.loadDictionaries()
    if (this.paperId) this.load()
  },
  methods: {
    async loadDictionaries() {
      this.dictionariesLoading = true
      this.dictionariesError = ''
      try {
        const [subjects, categories] = await Promise.all([
          listPaperDictionaries('subject'),
          listPaperDictionaries('paper_category')
        ])
        this.subjects = subjects.data || []
        this.categories = categories.data || []
      } catch (error) {
        this.dictionariesError = error?.data?.msg || error?.msg || '科目与分类加载失败，请检查后端服务'
      } finally {
        this.dictionariesLoading = false
      }
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
    openSelector(type) {
      if (this.dictionariesLoading) return uni.showToast({ title: '正在加载科目与分类', icon: 'none' })
      if (this.dictionariesError) {
        uni.showModal({
          title: '数据加载失败',
          content: this.dictionariesError,
          confirmText: '重新加载',
          success: result => { if (result.confirm) this.loadDictionaries() }
        })
        return
      }
      this.selectorType = type
    },
    isSelected(item) {
      return this.selectorType === 'subject' ? this.form.subjectId === item.id : this.form.category === item.name
    },
    selectDictionary(item) {
      if (this.selectorType === 'subject') {
        this.form.subjectId = item.id
        this.form.subject = item.name
      } else {
        this.form.category = item.name
      }
      this.selectorType = null
    },
    createFromSelector() {
      const type = this.selectorType
      this.selectorType = null
      this.createDictionary(type)
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
            const errorMessage = error?.message || error?.msg || error?.data?.message || error?.data?.msg || `新增${label}失败，请稍后重试`
            uni.showToast({ title: errorMessage, icon: 'none' })
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
.page{min-height:100vh;background:#f5f7fa}.steps{display:flex;align-items:center;padding:24rpx 20rpx;background:#fff;border-bottom:1rpx solid #e2e7ed}.step{flex:1;display:flex;flex-direction:column;align-items:center;gap:8rpx;color:#98a2b3;font-size:20rpx;text-align:center}.step-no{width:42rpx;height:42rpx;line-height:42rpx;text-align:center;border-radius:50%;background:#eef1f4;color:#697586;font-size:22rpx}.step.active{color:#1e5fae;font-weight:600}.step.active .step-no{background:#1e6bb8;color:#fff}.form{padding:24rpx}.field{background:#fff;border:1rpx solid #e4e8ed;border-radius:10rpx;padding:22rpx 24rpx;margin-bottom:16rpx;box-shadow:0 4rpx 16rpx rgba(16,24,40,.035)}.field>text{display:block;font-size:25rpx;color:#344054;margin-bottom:14rpx}.field input,.field textarea{width:100%;font-size:28rpx;color:#1d2939}.field textarea{height:130rpx}.picker{font-size:28rpx;color:#27364d;display:flex;justify-content:space-between;align-items:center;min-height:42rpx}.picker .placeholder{color:#98a2b3}.create-link,.manage-link{display:inline-block!important;margin:18rpx 18rpx 0 0!important;color:#1e6bb8!important;font-size:24rpx!important}.manage-link{color:#667085!important}.primary{margin-top:40rpx;background:#1e6bb8;color:#fff;border-radius:8rpx;font-size:29rpx}.manager-mask{position:fixed;inset:0;background:rgba(0,0,0,.35);z-index:20;display:flex;align-items:flex-end}.manager-panel{background:#fff;border-radius:18rpx 18rpx 0 0;width:100%;max-height:70vh;padding:24rpx}.manager-title,.manager-row{display:flex;justify-content:space-between;align-items:center;padding:18rpx 8rpx;border-bottom:1rpx solid #edf0f5}.manager-title{font-size:30rpx;font-weight:600}.system-tag{color:#9aa5b8}.delete-link{color:#e45d65}.selector-panel{max-height:78vh}.close-button{font-size:42rpx;font-weight:400;color:#98a2b3}.selector-list{max-height:52vh}.selector-row{display:flex;justify-content:space-between;align-items:center;padding:24rpx 10rpx;border-bottom:1rpx solid #edf0f5;font-size:28rpx;color:#344054}.selector-row.selected{color:#1e6bb8;font-weight:600}.selected-mark{font-size:34rpx;color:#1e6bb8}.selector-empty{padding:50rpx 10rpx;text-align:center;color:#98a2b3;font-size:26rpx}.selector-create{margin-top:18rpx;background:#edf5ff;color:#1e6bb8;border-radius:8rpx;font-size:26rpx}
</style>
