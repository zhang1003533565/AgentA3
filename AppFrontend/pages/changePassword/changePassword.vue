<template>
  <view class="page">
    <nav-bar title="修改密码" :showBack="true" />
    <view class="form-card">
      <view class="field">
        <text class="label">当前密码</text>
        <input v-model="form.oldPassword" class="input" password placeholder="请输入当前密码" />
      </view>
      <view class="field">
        <text class="label">新密码</text>
        <input v-model="form.newPassword" class="input" password placeholder="请输入新密码，至少 6 位" />
      </view>
      <view class="field">
        <text class="label">确认新密码</text>
        <input v-model="form.confirmPassword" class="input" password placeholder="请再次输入新密码" />
      </view>
      <button class="submit-btn" :loading="loading" :disabled="loading" @click="submit">
        保存修改
      </button>
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { updatePassword } from '@/api/user.js'

export default {
  components: { NavBar },
  data() {
    return {
      loading: false,
      form: {
        oldPassword: '',
        newPassword: '',
        confirmPassword: ''
      }
    }
  },
  methods: {
    async submit() {
      if (!this.form.oldPassword || !this.form.newPassword || !this.form.confirmPassword) {
        uni.showToast({ title: '请填写完整信息', icon: 'none' })
        return
      }
      if (this.form.newPassword.length < 6) {
        uni.showToast({ title: '新密码至少 6 位', icon: 'none' })
        return
      }
      if (this.form.newPassword !== this.form.confirmPassword) {
        uni.showToast({ title: '两次输入的新密码不一致', icon: 'none' })
        return
      }
      this.loading = true
      try {
        await updatePassword({
          oldPassword: this.form.oldPassword,
          newPassword: this.form.newPassword
        })
        uni.showToast({ title: '修改成功', icon: 'success' })
        setTimeout(() => {
          uni.navigateBack()
        }, 700)
      } catch (error) {
      } finally {
        this.loading = false
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #f7f7f9;
}

.form-card {
  margin: 24rpx;
  padding: 32rpx;
  border-radius: 24rpx;
  background: #fff;
}

.field + .field {
  margin-top: 24rpx;
}

.label {
  display: block;
  margin-bottom: 12rpx;
  font-size: 26rpx;
  color: #4b5563;
}

.input {
  height: 88rpx;
  padding: 0 24rpx;
  border-radius: 18rpx;
  background: #f3f4f6;
  font-size: 28rpx;
}

.submit-btn {
  margin-top: 40rpx;
  border-radius: 999rpx;
  background: #111827;
  color: #fff;
}
</style>
