<template>
  <view class="course-page">
    <nav-bar :title="course?.name || '校园课程'" :showBack="true" fixed placeholder />

    <view v-if="loading" class="page-state">正在打开课程书...</view>
    <view v-else-if="errorMessage" class="page-state">
      <text>{{ errorMessage }}</text>
      <button size="mini" @tap="loadCourse">重新加载</button>
    </view>
    <view v-else-if="course" class="course-content">
      <view class="book-hero">
        <view class="book-cover">
          <image v-if="course.coverUrl" :src="course.coverUrl" mode="aspectFill" />
          <image v-else src="/static/icons/line/book-open.svg" mode="aspectFit" />
        </view>
        <view class="book-copy">
          <text class="course-name">{{ course.name }}</text>
          <text class="book-title">{{ course.bookTitle }}</text>
          <text class="owner">{{ course.ownerName || '课程管理员' }} · {{ course.semester || '当前学期' }}</text>
        </view>
      </view>

      <view class="progress-card">
        <view class="progress-head">
          <text>学习进度</text>
          <text>{{ course.progressPercent || 0 }}%</text>
        </view>
        <view class="progress-track">
          <view class="progress-fill" :style="{ width: `${course.progressPercent || 0}%` }"></view>
        </view>
        <text class="progress-note">{{ completedCount }}/{{ course.chapters.length }} 个章节已完成</text>
      </view>

      <view v-if="todayTasks.length" class="section">
        <view class="section-heading">
          <view>
            <text class="section-kicker">TODAY</text>
            <text class="section-title">今日学习任务</text>
          </view>
          <text class="section-count">{{ completedCount }}/{{ course.chapters.length }} 已完成</text>
        </view>
        <view
          v-for="(chapter, index) in todayTasks"
          :key="chapter.id"
          class="task-card"
          @tap="selectChapter(chapter)"
        >
          <view class="task-index">{{ chapter.completed ? '✓' : index + 1 }}</view>
          <view class="task-copy">
            <view class="task-title-row">
              <text class="task-title">{{ chapter.title }}</text>
              <text class="task-status">{{ chapter.completed ? '已完成' : index === 0 ? '进行中' : '待学习' }}</text>
            </view>
            <text class="task-summary">{{ chapter.summary || '按照课程顺序阅读本章内容并完成学习。' }}</text>
            <text class="task-meta">课程 · {{ chapter.estimatedMinutes || 30 }} 分钟</text>
          </view>
          <text v-if="!chapter.completed && index === 0" class="continue-button">继续学习</text>
        </view>
      </view>

      <view class="section">
        <view class="section-heading">
          <view>
            <text class="section-kicker">CONTENTS</text>
            <text class="section-title">课程目录</text>
          </view>
          <text class="section-count">共 {{ course.chapters.length }} 章</text>
        </view>
        <view v-if="course.chapters.length">
          <view
            v-for="(chapter, index) in course.chapters"
            :key="chapter.id"
            class="chapter-card"
            :class="{ 'chapter-card--active': activeChapterId === chapter.id }"
          >
            <view class="chapter-head" @tap="selectChapter(chapter)">
              <view class="chapter-number" :class="{ completed: chapter.completed }">{{ chapter.completed ? '✓' : index + 1 }}</view>
              <view class="chapter-heading-copy">
                <text class="chapter-title">{{ chapter.title }}</text>
                <text>{{ chapter.estimatedMinutes || 30 }} 分钟 · {{ chapter.required ? '必修' : '选修' }}</text>
              </view>
              <text class="chapter-toggle">{{ activeChapterId === chapter.id ? '收起' : '阅读' }}</text>
            </view>
            <view v-if="activeChapterId === chapter.id" class="chapter-body">
              <text v-if="chapter.summary" class="chapter-summary">{{ chapter.summary }}</text>
              <safe-markdown v-if="chapter.content" :content="chapter.content" />
              <view v-else class="chapter-empty">管理员暂未录入本章正文。</view>
              <view v-if="chapter.resourceUrl" class="resource-button" @tap.stop="openResource(chapter)">打开附加资料</view>
              <view
                class="complete-button"
                :class="{ 'complete-button--done': chapter.completed }"
                @tap.stop="toggleChapter(chapter)"
              >
                {{ chapter.completed ? '标记为未完成' : '完成本章学习' }}
              </view>
            </view>
          </view>
        </view>
        <view v-else class="empty-card">课程暂未配置章节。</view>
      </view>

      <view class="section">
        <view class="section-heading">
          <view>
            <text class="section-kicker">EXAMS</text>
            <text class="section-title">课程考试</text>
          </view>
          <text class="section-count">{{ course.exams.length }} 场</text>
        </view>
        <view
          v-for="exam in course.exams"
          :key="exam.id"
          class="exam-card"
          @tap="openExam(exam)"
        >
          <view class="exam-mark">考</view>
          <view class="exam-copy">
            <text class="exam-title">{{ exam.title }}</text>
            <text>{{ exam.chapterScope || '全部章节' }} · {{ exam.questionCount || 0 }} 题 · {{ exam.durationMinutes || 0 }} 分钟</text>
          </view>
          <text class="exam-action">开始考试</text>
        </view>
        <view v-if="!course.exams.length" class="empty-card">当前课程暂无已发布考试。</view>
      </view>
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import SafeMarkdown from '@/components/safe-markdown/safe-markdown.vue'
import { getCampusCourseDetail, updateCampusChapterProgress } from '@/api/campusCourse.js'

export default {
  components: { NavBar, SafeMarkdown },
  data() {
    return {
      courseId: '',
      course: null,
      loading: false,
      errorMessage: '',
      activeChapterId: null,
      savingChapterId: null
    }
  },
  computed: {
    completedCount() {
      return (this.course?.chapters || []).filter((chapter) => chapter.completed).length
    },
    todayTasks() {
      const chapters = this.course?.chapters || []
      const pending = chapters.filter((chapter) => !chapter.completed)
      return (pending.length ? pending : chapters.slice(-1)).slice(0, 5)
    }
  },
  onLoad(options) {
    this.courseId = options?.courseId || ''
    this.loadCourse()
  },
  onShow() {
    if (this.course) this.loadCourse(false)
  },
  methods: {
    async loadCourse(showLoading = true) {
      if (!this.courseId) {
        this.errorMessage = '缺少课程编号'
        return
      }
      if (showLoading) this.loading = true
      this.errorMessage = ''
      try {
        const response = await getCampusCourseDetail(this.courseId)
        this.course = response?.data || null
        if (!this.activeChapterId) {
          this.activeChapterId = this.course?.chapters?.find((chapter) => !chapter.completed)?.id || null
        }
      } catch (error) {
        if (showLoading) this.course = null
        this.errorMessage = error?.msg || error?.message || '课程加载失败'
      } finally {
        this.loading = false
      }
    },
    selectChapter(chapter) {
      this.activeChapterId = this.activeChapterId === chapter.id ? null : chapter.id
    },
    async toggleChapter(chapter) {
      if (this.savingChapterId) return
      this.savingChapterId = chapter.id
      try {
        const response = await updateCampusChapterProgress(this.courseId, chapter.id, !chapter.completed)
        this.course = response?.data || this.course
        uni.showToast({ title: chapter.completed ? '已取消完成' : '学习进度已更新', icon: 'none' })
      } catch (error) {
        uni.showToast({ title: error?.msg || error?.message || '进度保存失败', icon: 'none' })
      } finally {
        this.savingChapterId = null
      }
    },
    openExam(exam) {
      uni.navigateTo({ url: `/subpackage_exam/paperDetail/paperDetail?paperId=${encodeURIComponent(exam.paperId)}` })
    },
    openResource(chapter) {
      const url = chapter.resourceUrl
      if (!url) return
      // #ifdef H5
      window.open(url, '_blank')
      // #endif
      // #ifndef H5
      uni.downloadFile({
        url,
        success: (download) => {
          if (download.statusCode < 200 || download.statusCode >= 300) {
            uni.setClipboardData({ data: url })
            return
          }
          uni.openDocument({
            filePath: download.tempFilePath,
            showMenu: true,
            fail: () => uni.setClipboardData({ data: url })
          })
        },
        fail: () => uni.setClipboardData({ data: url })
      })
      // #endif
    }
  }
}
</script>

<style lang="scss" scoped>
.course-page{min-height:100vh;background:#f4f6f8;color:#172033}.course-content{padding:24rpx}.page-state{padding:180rpx 40rpx;display:flex;flex-direction:column;align-items:center;gap:24rpx;color:#64748b}.book-hero{display:flex;gap:26rpx;padding:30rpx;background:#fff;border:1px solid #e6eaf0;border-radius:22rpx}.book-cover{width:128rpx;height:164rpx;flex:none;display:flex;align-items:center;justify-content:center;overflow:hidden;border-radius:10rpx 18rpx 18rpx 10rpx;background:#eaf0f5;box-shadow:inset 8rpx 0 #cbd7e2}.book-cover image{width:100%;height:100%}.book-copy{flex:1;display:flex;flex-direction:column;justify-content:center}.course-name{font-size:36rpx;font-weight:750}.book-title{margin-top:10rpx;font-size:27rpx;color:#475569}.owner{margin-top:22rpx;font-size:23rpx;color:#94a3b8}.progress-card,.section{margin-top:22rpx;padding:28rpx;background:#fff;border:1px solid #e6eaf0;border-radius:22rpx}.progress-head{display:flex;justify-content:space-between;font-size:27rpx;font-weight:650}.progress-track{height:12rpx;margin-top:20rpx;overflow:hidden;border-radius:8rpx;background:#e8edf2}.progress-fill{height:100%;border-radius:8rpx;background:#627d95;transition:width .25s}.progress-note{display:block;margin-top:12rpx;font-size:22rpx;color:#94a3b8}.section-heading{display:flex;align-items:flex-end;justify-content:space-between;margin-bottom:22rpx}.section-kicker{display:block;color:#8192a3;font-size:19rpx;letter-spacing:2rpx}.section-title{display:block;margin-top:4rpx;font-size:31rpx;font-weight:750}.section-count{color:#94a3b8;font-size:22rpx}.task-card{display:flex;align-items:center;gap:20rpx;padding:24rpx 8rpx;border-top:1px solid #eef1f4}.task-card:first-of-type{border-top:0}.task-index,.chapter-number{width:54rpx;height:54rpx;flex:none;display:flex;align-items:center;justify-content:center;border:1px solid #cdd7e1;border-radius:50%;color:#52697d;font-weight:700}.task-copy{min-width:0;flex:1}.task-title-row{display:flex;align-items:center;gap:12rpx}.task-title{font-size:28rpx;font-weight:700}.task-status{padding:4rpx 10rpx;border-radius:8rpx;background:#eef2f5;color:#64748b;font-size:19rpx}.task-summary{display:block;margin-top:8rpx;overflow:hidden;color:#64748b;font-size:23rpx;text-overflow:ellipsis;white-space:nowrap}.task-meta{display:block;margin-top:10rpx;color:#94a3b8;font-size:21rpx}.continue-button{padding:12rpx 16rpx;border-radius:18rpx;background:#526f88;color:#fff;font-size:21rpx}.chapter-card{margin-top:14rpx;border:1px solid #e5e9ee;border-radius:16rpx;overflow:hidden}.chapter-card--active{border-color:#9aabba}.chapter-head{display:flex;align-items:center;gap:18rpx;padding:22rpx}.chapter-number.completed{border-color:#92a99c;background:#edf5f0;color:#4d7860}.chapter-heading-copy{min-width:0;flex:1;color:#8a94a3;font-size:21rpx}.chapter-title{display:block;margin-bottom:6rpx;color:#243142;font-size:27rpx;font-weight:650}.chapter-toggle{color:#526f88;font-size:22rpx}.chapter-body{padding:4rpx 24rpx 24rpx;border-top:1px solid #eef1f4;color:#435165}.chapter-summary{display:block;margin:20rpx 0;color:#64748b;line-height:1.6}.chapter-empty,.empty-card{padding:36rpx 20rpx;text-align:center;color:#94a3b8;font-size:23rpx}.resource-button,.complete-button{margin-top:24rpx;padding:20rpx;border:1px solid #607d96;border-radius:14rpx;text-align:center;color:#526f88;font-size:24rpx}.complete-button{background:#526f88;color:#fff}.complete-button--done{border-color:#d7dee5;background:#eef2f5;color:#64748b}.exam-card{display:flex;align-items:center;gap:18rpx;padding:22rpx 0;border-top:1px solid #eef1f4}.exam-card:first-of-type{border-top:0}.exam-mark{width:58rpx;height:58rpx;display:flex;align-items:center;justify-content:center;border-radius:14rpx;background:#eef1f7;color:#5b6686;font-weight:750}.exam-copy{min-width:0;flex:1;color:#8b95a3;font-size:21rpx}.exam-title{display:block;margin-bottom:7rpx;color:#253243;font-size:27rpx;font-weight:650}.exam-action{color:#526f88;font-size:22rpx}
</style>
