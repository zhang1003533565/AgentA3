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
          <text class="form-item__label">教务学年</text>
          <input
            v-model.trim="form.academicYear"
            class="form-item__input"
            type="text"
            maxlength="20"
            placeholder="例如 2025-2026"
            @blur="handleAcademicYearBlur"
          />
        </view>

        <view class="form-item">
          <text class="form-item__label">当前显示学期</text>
          <picker :range="semesterOptions" :value="currentSemesterIndex" @change="handleSemesterChange">
            <view class="form-item__picker">
              <text class="form-item__value">{{ termLabel(form.semesterTerm) }}</text>
            </view>
          </picker>
        </view>

        <view class="form-item">
          <text class="form-item__label">第 1 学期开学日期</text>
          <picker mode="date" :value="form.semesterStarts[1]" @change="handleSemesterDateChange($event, 1)">
            <view class="form-item__picker">
              <text :class="form.semesterStarts[1] ? 'form-item__value' : 'form-item__placeholder'">
                {{ form.semesterStarts[1] || '请选择第 1 学期开学日期' }}
              </text>
            </view>
          </picker>
        </view>

        <view class="form-item">
          <text class="form-item__label">第 2 学期开学日期</text>
          <picker mode="date" :value="form.semesterStarts[2]" @change="handleSemesterDateChange($event, 2)">
            <view class="form-item__picker">
              <text :class="form.semesterStarts[2] ? 'form-item__value' : 'form-item__placeholder'">
                {{ form.semesterStarts[2] || '请选择第 2 学期开学日期' }}
              </text>
            </view>
          </picker>
        </view>
      </view>

      <view class="tips-card">
        <text class="tips-card__title">说明</text>
        <text class="tips-card__line">导入时会按教务学年一次读取第 1、第 2 两个学期。</text>
        <text class="tips-card__line">每个学期独立使用自己的开学日期计算周次。</text>
      </view>

      <button class="save-btn" :loading="saving" @click="saveSettings">保存设置</button>
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getScheduleSettings, updateScheduleSettings } from '@/api/schedule.js'

const semesterCodeForTerm = (term) => (Number(term) === 2 ? '12' : '3')

const normalizeAcademicYear = (value) => {
  const text = String(value || '').trim()
  if (/^\d{4}-\d{4}$/.test(text)) return text
  if (/^\d{4}$/.test(text)) return `${text}-${Number(text) + 1}`

  const now = new Date()
  const year = now.getFullYear()
  const startYear = now.getMonth() + 1 >= 8 ? year : year - 1
  return `${startYear}-${startYear + 1}`
}

const defaultSemesterStarts = (academicYear) => {
  const startYear = Number(normalizeAcademicYear(academicYear).slice(0, 4))
  return {
    1: `${startYear}-09-01`,
    2: `${startYear + 1}-03-01`
  }
}

export default {
  components: { NavBar },
  data() {
    return {
      saving: false,
      showPassword: false,
      semesterOptions: ['第 1 学期', '第 2 学期'],
      savedSemesters: [],
      form: {
        jwxStudentId: '',
        jwxPassword: '',
        academicYear: normalizeAcademicYear(),
        semesterTerm: 1,
        semesterStarts: defaultSemesterStarts()
      }
    }
  },
  computed: {
    currentSemesterIndex() {
      return Math.max(0, Number(this.form.semesterTerm || 1) - 1)
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
        const academicYear = normalizeAcademicYear(data.academicYear)
        const semesterTerm = Number(data.semesterTerm || 1)
        this.savedSemesters = Array.isArray(data.semesters) ? data.semesters : []
        const semesterStarts = this.buildSemesterStarts(academicYear, semesterTerm, data.semesterStart)
        this.form = {
          jwxStudentId: data.jwxStudentId || '',
          jwxPassword: data.jwxPassword || '',
          academicYear,
          semesterTerm,
          semesterStarts
        }
      } catch (error) {
        uni.showToast({ title: error?.msg || '加载失败', icon: 'none' })
      } finally {
        uni.hideLoading()
      }
    },
    buildSemesterStarts(academicYear, selectedTerm, selectedStart) {
      const starts = defaultSemesterStarts(academicYear)
      this.savedSemesters
        .filter((item) => item && normalizeAcademicYear(item.academicYear) === academicYear)
        .forEach((item) => {
          const term = Number(item.semesterTerm)
          if ((term === 1 || term === 2) && item.semesterStart) {
            starts[term] = item.semesterStart
          }
        })
      if (selectedStart && (selectedTerm === 1 || selectedTerm === 2)) {
        starts[selectedTerm] = selectedStart
      }
      return starts
    },
    handleAcademicYearBlur() {
      const academicYear = normalizeAcademicYear(this.form.academicYear)
      this.form.academicYear = academicYear
      this.form.semesterStarts = this.buildSemesterStarts(academicYear, this.form.semesterTerm)
    },
    handleSemesterChange(e) {
      this.form.semesterTerm = Number(e.detail.value) + 1
    },
    handleSemesterDateChange(e, term) {
      this.form.semesterStarts = {
        ...this.form.semesterStarts,
        [term]: e.detail.value
      }
    },
    termLabel(term) {
      return Number(term) === 2 ? '第 2 学期' : '第 1 学期'
    },
    async saveSettings() {
      this.saving = true
      try {
        const academicYear = normalizeAcademicYear(this.form.academicYear)
        const semesterTerm = Number(this.form.semesterTerm || 1)
        const selectedStart = this.form.semesterStarts[semesterTerm]
        if (!selectedStart) {
          uni.showToast({ title: '请选择当前学期开学日期', icon: 'none' })
          return
        }

        await updateScheduleSettings({
          jwxStudentId: this.form.jwxStudentId,
          jwxPassword: this.form.jwxPassword,
          academicYear,
          semesterTerm,
          semesterStart: selectedStart,
          selected: true,
          semesters: [1, 2].map((term) => ({
            academicYear,
            semesterTerm: term,
            semesterCode: semesterCodeForTerm(term),
            semesterStart: this.form.semesterStarts[term] || defaultSemesterStarts(academicYear)[term]
          }))
        })
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
