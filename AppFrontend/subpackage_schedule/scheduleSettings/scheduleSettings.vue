<template>
  <view class="settings-page">
    <nav-bar title="课表设置" :showBack="true" fixed placeholder />

    <view class="settings-shell">
      <view class="settings-card">
        <view class="settings-card__head">
          <text class="settings-card__title">教务与学期信息</text>
          <text class="settings-card__desc">用于导入课表和计算当前周次</text>
        </view>

        <view class="form-item">
          <text class="form-item__label">教务系统学号</text>
          <input
            v-model.trim="form.jwxStudentId"
            class="form-item__input"
            type="text"
            maxlength="50"
            placeholder="请输入教务系统学号"
          />
        </view>

        <view class="form-item">
          <text class="form-item__label">教务系统密码</text>
          <input
            v-model.trim="form.jwxPassword"
            class="form-item__input"
            :password="!showPassword"
            maxlength="100"
            placeholder="请输入教务系统密码"
          />
          <text class="form-item__toggle" @click="showPassword = !showPassword">
            {{ showPassword ? '隐藏' : '显示' }}
          </text>
        </view>

        <view class="form-item">
          <text class="form-item__label">开学日期</text>
          <picker mode="date" :value="form.semesterStart" @change="handleDateChange">
            <view class="form-item__picker">
              <text :class="form.semesterStart ? 'form-item__value' : 'form-item__placeholder'">
                {{ form.semesterStart || '请选择学期开始日期' }}
              </text>
            </view>
          </picker>
        </view>
      </view>

      <view class="tips-card">
        <text class="tips-card__title">说明</text>
        <text class="tips-card__line">教务学号和密码用于一键导入教务课表。</text>
        <text class="tips-card__line">开学日期用于自动计算当前周次。</text>
      </view>

      <button class="save-btn" :loading="saving" @click="saveSettings">保存设置</button>
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getScheduleSettings, updateScheduleSettings } from '@/api/schedule.js'

export default {
  components: { NavBar },
  data() {
    return {
      saving: false,
      showPassword: false,
      form: {
        jwxStudentId: '',
        jwxPassword: '',
        semesterStart: ''
      }
    }
  },
  onLoad() {
    this.loadSettings()
  },
  methods: {
    async loadSettings() {
      try {
        uni.showLoading({ title: '加载中...' })
        const res = await getScheduleSettings()
        const data = res.data || {}
        this.form = {
          jwxStudentId: data.jwxStudentId || '',
          jwxPassword: data.jwxPassword || '',
          semesterStart: data.semesterStart || ''
        }
      } catch (error) {
        uni.showToast({ title: error?.msg || '加载失败', icon: 'none' })
      } finally {
        uni.hideLoading()
      }
    },
    handleDateChange(e) {
      this.form.semesterStart = e.detail.value
    },
    async saveSettings() {
      this.saving = true
      try {
        await updateScheduleSettings(this.form)
        uni.showToast({ title: '保存成功', icon: 'success' })
      } catch (error) {
        uni.showToast({ title: error?.msg || '保存失败', icon: 'none' })
      } finally {
        this.saving = false
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.settings-page {
  min-height: 100vh;
  background: linear-gradient(180deg, #edf6ff 0%, #f7fbff 100%);
}

.settings-shell {
  padding: 24rpx;
}

.settings-card,
.tips-card {
  background: rgba(255, 255, 255, 0.94);
  border-radius: 28rpx;
  padding: 28rpx;
  box-shadow: 0 14rpx 32rpx rgba(62, 108, 184, 0.08);
}

.settings-card__head {
  margin-bottom: 12rpx;
}

.settings-card__title,
.tips-card__title {
  display: block;
  font-size: 30rpx;
  font-weight: 700;
  color: #21334d;
}

.settings-card__desc {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #7890ad;
}

.form-item + .form-item {
  margin-top: 22rpx;
}

.form-item__label {
  display: block;
  margin-bottom: 14rpx;
  font-size: 24rpx;
  color: #48627f;
}

.form-item__input,
.form-item__picker {
  width: 100%;
  min-height: 92rpx;
  padding: 0 24rpx;
  box-sizing: border-box;
  border-radius: 22rpx;
  background: #f7fbff;
  border: 2rpx solid #dce9fb;
  display: flex;
  align-items: center;
  font-size: 28rpx;
  color: #21334d;
}

.form-item__toggle {
  display: inline-block;
  margin-top: 12rpx;
  font-size: 22rpx;
  color: #4b80ef;
}

.form-item__value {
  color: #21334d;
}

.form-item__placeholder {
  color: #96a7bb;
}

.tips-card {
  margin-top: 20rpx;
}

.tips-card__line {
  display: block;
  margin-top: 12rpx;
  font-size: 24rpx;
  line-height: 1.7;
  color: #5d738c;
}

.save-btn {
  margin-top: 28rpx;
  height: 92rpx;
  border: none;
  border-radius: 999rpx;
  background: linear-gradient(135deg, #4b82f6, #6aaaff);
  color: #fff;
  font-size: 30rpx;
  font-weight: 700;
  box-shadow: 0 18rpx 32rpx rgba(75, 130, 246, 0.22);
}
</style>
