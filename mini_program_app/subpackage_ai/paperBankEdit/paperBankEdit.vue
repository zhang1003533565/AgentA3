<template>
  <view class="page">
    <nav-bar :title="id ? '编辑题库组' : '新建题库组'" :showBack="true" placeholder />
    <view class="form">
      <view class="field" @tap="focusNameInput">
        <text>名称</text>
        <input v-model="form.name" type="text" :focus="nameInputFocused" placeholder="请输入题库组名称" @input="handleNameInput" @blur="nameInputFocused = false" />
      </view>
      <view class="field">
        <text>所属科目</text>
        <picker :range="subjectNames" @change="selectSubject">
          <view class="picker">{{ selectedSubjectName }}</view>
        </picker>
      </view>
      <view class="field">
        <text>类型</text>
        <picker :range="types" @change="form.bankType = types[$event.detail.value]">
          <view class="picker">{{ form.bankType || '请选择类型' }}</view>
        </picker>
      </view>
      <view class="field">
        <text>说明</text>
        <textarea v-model="form.description" placeholder="请输入题库组说明" />
      </view>
      <view class="field">
        <text>权限</text>
        <picker :range="permissionLabels" @change="form.visibility = permissions[$event.detail.value].value">
          <view class="picker">{{ permissionLabel }}</view>
        </picker>
      </view>
      <button class="save" :loading="saving" @click="save">保存</button>
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getPaperBank, createPaperBank, updatePaperBank, listPaperDictionaries } from '@/api/paper.js'

export default {
  components: { NavBar },
  data() {
    return {
      id: null,
      saving: false,
      nameInputFocused: false,
      subjects: [],
      types: [],
      permissions: [
        { label: '仅自己可见', value: 'private' },
        { label: '题库成员可见', value: 'shared' }
      ],
      form: {
        name: '',
        subjectId: null,
        bankType: '',
        description: '',
        visibility: 'private'
      }
    }
  },
  computed: {
    subjectNames() {
      return this.subjects.map(subject => subject.name)
    },
    selectedSubjectName() {
      const subject = this.subjects.find(item => Number(item.dictCode) === Number(this.form.subjectId))
      return subject ? subject.name : '请选择所属科目'
    },
    permissionLabels() {
      return this.permissions.map(item => item.label)
    },
    permissionLabel() {
      return (this.permissions.find(item => item.value === this.form.visibility) || this.permissions[0]).label
    }
  },
  onLoad(query) {
    this.loadDictionaries()
    if (query.id) {
      this.id = query.id
      this.load()
    }
  },
  methods: {
    focusNameInput() {
      this.nameInputFocused = true
    },
    handleNameInput(event) {
      this.form.name = event.detail.value
    },
    async loadDictionaries() {
      const [subjects, bankTypes] = await Promise.all([
        listPaperDictionaries('subject'),
        listPaperDictionaries('bank_type')
      ])
      this.subjects = subjects.data || []
      this.types = (bankTypes.data || []).map(item => item.name)
    },
    selectSubject(event) {
      this.form.subjectId = Number(this.subjects[Number(event.detail.value)].dictCode)
    },
    async load() {
      const result = await getPaperBank(this.id)
      this.form = { ...this.form, ...(result.data || {}) }
    },
    async save() {
      if (!this.form.name.trim()) {
        return uni.showToast({ title: '请输入名称', icon: 'none' })
      }
      this.saving = true
      try {
        if (this.id) await updatePaperBank(this.id, this.form)
        else await createPaperBank(this.form)
        uni.showToast({ title: '保存成功' })
        setTimeout(() => uni.navigateBack(), 500)
      } finally {
        this.saving = false
      }
    }
  }
}
</script>

<style scoped>
.page{min-height:100vh;background:#f5f8fc;padding:24rpx}.form{background:#fff;border-radius:20rpx;padding:26rpx}.field{margin-bottom:22rpx}.field>text{display:block;color:#344258;font-size:25rpx;margin-bottom:12rpx}.field input,.picker,.field textarea{width:100%;box-sizing:border-box;background:#f5f8fc;border-radius:14rpx;padding:20rpx;font-size:27rpx}.field textarea{height:150rpx}.save{background:#4d78e8;color:#fff;border-radius:36rpx;margin-top:28rpx}
</style>
