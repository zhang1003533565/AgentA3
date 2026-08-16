<template>
  <view class="account-page">
    <nav-bar title="教务账号设置" :showBack="true" fixed placeholder />

    <view class="account-shell">
      <view class="account-card">
        <view class="account-card__head">
          <view class="account-card__heading">
            <text class="account-card__title">教务账号</text>
            <text class="account-card__desc">用于从教务系统导入课表，不影响学期列表</text>
          </view>
          <text class="account-status-badge" :class="{ 'account-status-badge--ok': accountConfigured }">
            {{ accountConfigured ? '已设置' : '未设置' }}
          </text>
        </view>

        <view class="account-state-line" :class="{ 'account-state-line--ok': accountConfigured }">
          <text>
            {{ accountConfigured ? '账号已保存，可以返回导入课表' : '请填写学号和密码后保存' }}
          </text>
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
      </view>

      <view class="tips-card">
        <text class="tips-card__title">说明</text>
        <text class="tips-card__line">账号只用于登录教务系统抓取课表。</text>
        <text class="tips-card__line">学号和密码会加密保存到数据库。</text>
        <text class="tips-card__line">学年、学期和开学日期请在学期管理中维护。</text>
      </view>

      <button class="save-btn" :loading="saving" @click="saveAccount">保存账号</button>
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
      form: {
        jwxStudentId: '',
        jwxPassword: ''
      },
      semesterList: []
    }
  },
  computed: {
    accountConfigured() {
      return Boolean(
        String(this.form.jwxStudentId || '').trim() &&
        String(this.form.jwxPassword || '').trim()
      )
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
          jwxPassword: data.jwxPassword || ''
        }
        this.semesterList = this.normalizeSemesterList(data)
      } catch (error) {
        uni.showToast({ title: error?.msg || error?.message || '加载失败', icon: 'none' })
      } finally {
        uni.hideLoading()
      }
    },
    normalizeSemesterList(data = {}) {
      const selectedAcademicYear = normalizeAcademicYear(data.academicYear)
      const selectedTerm = Number(data.semesterTerm || 1)
      const selectedStart = data.semesterStart || defaultSemesterStarts(selectedAcademicYear)[selectedTerm]
      const byKey = new Map()

      const addItem = (source = {}) => {
        const academicYear = normalizeAcademicYear(source.academicYear || selectedAcademicYear)
        const semesterTerm = Number(source.semesterTerm || selectedTerm || 1)
        if (semesterTerm !== 1 && semesterTerm !== 2) return
        const key = `${academicYear}-${semesterTerm}`
        byKey.set(key, {
          academicYear,
          semesterTerm,
          semesterCode: source.semesterCode || semesterCodeForTerm(semesterTerm),
          semesterStart: source.semesterStart || defaultSemesterStarts(academicYear)[semesterTerm],
          selected: Boolean(source.selected) || (
            academicYear === selectedAcademicYear && semesterTerm === selectedTerm
          )
        })
      }

      ;(Array.isArray(data.semesters) ? data.semesters : []).forEach(addItem)
      addItem({
        academicYear: selectedAcademicYear,
        semesterTerm: selectedTerm,
        semesterCode: semesterCodeForTerm(selectedTerm),
        semesterStart: selectedStart,
        selected: true
      })

      return Array.from(byKey.values())
    },
    buildPayload() {
      const selected = this.semesterList.find((item) => item.selected) || this.semesterList[0]
      return {
        jwxStudentId: this.form.jwxStudentId,
        jwxPassword: this.form.jwxPassword,
        academicYear: selected.academicYear,
        semesterTerm: selected.semesterTerm,
        semesterStart: selected.semesterStart,
        selected: true,
        semesters: this.semesterList.map((item) => ({
          academicYear: normalizeAcademicYear(item.academicYear),
          semesterTerm: Number(item.semesterTerm || 1),
          semesterCode: semesterCodeForTerm(item.semesterTerm),
          semesterStart: item.semesterStart
        }))
      }
    },
    async saveAccount() {
      this.saving = true
      try {
        await updateScheduleSettings(this.buildPayload())
        uni.showToast({ title: this.accountConfigured ? '账号已设置' : '保存成功', icon: 'success' })
      } catch (error) {
        uni.showToast({ title: error?.msg || error?.message || '保存失败', icon: 'none' })
      } finally {
        this.saving = false
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.account-page {
  min-height: 100vh;
  background: linear-gradient(180deg, #eef7ff 0%, #f8fbff 52%, #f7f9fb 100%);
}

.account-shell {
  padding: 24rpx;
}

.account-card,
.tips-card {
  background: rgba(255, 255, 255, 0.96);
  border-radius: 24rpx;
  padding: 28rpx;
  box-shadow: 0 14rpx 32rpx rgba(65, 102, 153, 0.08);
}

.account-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18rpx;
  margin-bottom: 14rpx;
}

.account-card__heading {
  flex: 1;
  min-width: 0;
}

.account-card__title,
.tips-card__title {
  display: block;
  font-size: 30rpx;
  font-weight: 700;
  color: #21334d;
}

.account-card__desc {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #7890ad;
}

.account-status-badge {
  flex-shrink: 0;
  padding: 9rpx 16rpx;
  border-radius: 999rpx;
  background: #fff2eb;
  color: #d95b37;
  font-size: 22rpx;
  font-weight: 700;
  white-space: nowrap;
}

.account-status-badge--ok {
  background: #eaf8f3;
  color: #168b6a;
}

.account-state-line {
  min-height: 68rpx;
  padding: 0 20rpx;
  margin: 18rpx 0 24rpx;
  border-radius: 16rpx;
  background: #fff7f2;
  color: #d95b37;
  display: flex;
  align-items: center;
  font-size: 24rpx;
  font-weight: 600;
  box-sizing: border-box;
}

.account-state-line--ok {
  background: #f0fbf7;
  color: #168b6a;
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

.form-item__input {
  width: 100%;
  min-height: 92rpx;
  padding: 0 24rpx;
  box-sizing: border-box;
  border-radius: 20rpx;
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
  color: #3f7df2;
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
  background: linear-gradient(135deg, #3f7df2, #23b08a);
  color: #fff;
  font-size: 30rpx;
  font-weight: 700;
  box-shadow: 0 18rpx 32rpx rgba(63, 125, 242, 0.18);
}

.save-btn::after {
  border: none;
}
</style>
