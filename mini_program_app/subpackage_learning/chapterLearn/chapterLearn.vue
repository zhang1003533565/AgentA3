<template>
  <view class="page">
    <nav-bar
      :title="chapterData?.chapter?.title || '章节学习'"
      :showBack="true" fixed placeholder
    />

    <!-- 骨架屏 -->
    <view v-if="loading" class="skeleton-wrap">
      <view class="skeleton-block skeleton-block--video"></view>
      <view class="skeleton-block skeleton-block--text"></view>
      <view class="skeleton-block skeleton-block--text skeleton-block--short"></view>
      <view class="skeleton-block skeleton-block--text skeleton-block--mid"></view>
    </view>

    <!-- 错误 -->
    <view v-else-if="errorMessage" class="state-box">
      <text>{{ errorMessage }}</text>
      <view class="retry-btn" @tap="loadChapter">重新加载</view>
    </view>

    <!-- 内容（带过场动画） -->
    <view v-else-if="chapterData" class="content-shell">
      <!-- ========== 章节信息 ========== -->
      <view class="chapter-content">
        <text class="chapter-title">{{ chapterData.chapter.title }}</text>
        <view class="chapter-meta-row">
          <view class="chapter-meta">
            <text>时长：{{ chapterData.chapter.estimatedMinutes || 30 }}分钟</text>
            <text class="sep">·</text>
            <text>{{ chapterData.chapter.completed ? '已完成' : '未完成' }}</text>
          </view>
          <view class="chapter-nav">
            <view class="nav-item" :class="{ disabled: !hasPrev }" @tap="prevChapter">
              <text class="nav-icon">‹</text>
              <text class="nav-label">上一节</text>
            </view>
            <view class="nav-item next" :class="{ disabled: !hasNext }" @tap="nextChapter">
              <text class="nav-label">下一节</text>
              <text class="nav-icon">›</text>
            </view>
          </view>
        </view>
      </view>

      <!-- ========== 浮动按钮 ========== -->
      <view class="floating-actions">
        <view
          class="float-btn complete-btn"
          :class="{ active: chapterData.chapter.completed }"
          @tap="toggleComplete"
        >
          <text class="float-icon">{{ chapterData.chapter.completed ? '✓' : '○' }}</text>
          <text class="float-label">标记完成</text>
        </view>
        <view
          class="float-btn note-btn"
          @tap="activeSection = 'note'"
        >
          <text class="float-icon">📝</text>
          <text class="float-label">记笔记</text>
        </view>
        <view
          class="float-btn fav-btn"
          :class="{ active: isFavorited }"
          @tap="isFavorited = !isFavorited"
        >
          <text class="float-icon">{{ isFavorited ? '❤️' : '🤍' }}</text>
          <text class="float-label">收藏</text>
        </view>
      </view>

      <!-- ========== Tab 栏 ========== -->
      <view class="section-tabs">
        <view
          class="section-tab"
          :class="{ active: activeSection === 'content' }"
          @tap="activeSection = 'content'"
        >课程内容</view>
        <view
          class="section-tab"
          :class="{ active: activeSection === 'note' }"
          @tap="activeSection = 'note'"
        >我的笔记</view>
        <view
          class="section-tab"
          :class="{ active: activeSection === 'qa' }"
          @tap="activeSection = 'qa'"
        >问答</view>
      </view>

      <!-- ========== 课程内容 Tab ========== -->
      <view v-if="activeSection === 'content'" class="tab-content">
        <!-- 本节概要 -->
        <view class="content-card" v-if="chapterData.chapter.content">
          <text class="card-heading">本节概要</text>
          <safe-markdown :content="chapterData.chapter.content" />
        </view>

        <!-- 视频占位符 -->
        <view v-if="hasVideo && videoMaterial" class="content-card">
          <text class="card-heading">视频资料</text>
          <view class="player-section-inline">
            <view class="video-player" v-if="activeVideoId === videoMaterial.id">
              <video
                :id="`video-${videoMaterial.id}`"
                :src="videoMaterial.fileUrl"
                :controls="true"
                :autoplay="false"
                :show-center-play-btn="true"
                :show-fullscreen-btn="true"
                :enable-progress-gesture="true"
                object-fit="contain"
                class="video-element"
                @play="onVideoPlay"
                @pause="onVideoPause"
                @ended="onVideoEnded"
              />
            </view>
            <view v-else class="video-placeholder" @tap="playVideo(videoMaterial)">
              <view class="player-overlay">
                <view class="play-btn">
                  <text class="play-icon">▶</text>
                </view>
              </view>
            </view>
          </view>
        </view>

        <!-- Word 文本占位符 -->
        <view v-if="hasWordDocuments && wordMaterials.length" class="content-card">
          <text class="card-heading">文本资料</text>
          <view v-if="wordMaterials.length > 1" class="word-tabs">
            <view
              v-for="wm in wordMaterials"
              :key="wm.id"
              class="word-tab"
              :class="{ active: activeWordTab === wm.id }"
              @tap="switchWordTab(wm)"
            >{{ wm.fileName }}</view>
          </view>
          <view class="word-content-wrap">
            <view v-if="wordLoadingMap[activeWordTab]" class="word-loading">
              <text>加载中...</text>
            </view>
            <view v-else-if="wordContentMap[activeWordTab]" class="word-text">
              <text class="word-page-content">{{ wordContentMap[activeWordTab].content }}</text>
            </view>
            <view v-else class="word-empty">
              <text>暂无文本内容</text>
            </view>
          </view>
          <view v-if="wordPageInfo" class="word-pager">
            <view
              class="pager-btn"
              :class="{ disabled: wordPageInfo.currentPage <= 1 }"
              @tap="prevWordPage"
            >
              <text class="pager-icon">‹</text>
              <text>上一页</text>
            </view>
            <text class="pager-info">{{ wordPageInfo.currentPage }} / {{ wordPageInfo.totalPages }}</text>
            <view
              class="pager-btn"
              :class="{ disabled: wordPageInfo.currentPage >= wordPageInfo.totalPages }"
              @tap="nextWordPage"
            >
              <text>下一页</text>
              <text class="pager-icon">›</text>
            </view>
          </view>
        </view>

        <!-- 附件资源 -->
        <view class="content-card">
          <text class="card-heading">附件资源</text>
          <view v-if="attachmentMaterials.length" class="attachment-list">
            <view
              v-for="file in attachmentMaterials"
              :key="file.id"
              class="attachment-item"
              @tap="openAttachment(file)"
            >
              <text class="attachment-icon">{{ fileIcon(file) }}</text>
              <view class="attachment-info">
                <text class="attachment-name">{{ file.fileName }}</text>
                <text class="attachment-size">{{ formatSize(file.fileSize) }}</text>
              </view>
              <text class="download-btn">打开</text>
            </view>
          </view>
          <text v-else class="empty-hint">暂无附件资源</text>
        </view>

        <!-- 知识点 -->
        <view class="content-card" v-if="knowledgePoints.length">
          <text class="card-heading">知识点</text>
          <view class="point-list">
            <view class="point-item" v-for="(point, index) in knowledgePoints" :key="index">
              <view class="point-number">{{ index + 1 }}</view>
              <text class="point-text">{{ point }}</text>
            </view>
          </view>
        </view>
      </view>

      <!-- ========== 我的笔记 Tab ========== -->
      <view v-else-if="activeSection === 'note'" class="tab-content">
        <view class="content-card">
          <text class="card-heading">添加笔记</text>
          <view class="note-editor">
            <textarea
              v-model="noteContent"
              placeholder="记录你的学习笔记..."
              class="note-textarea"
            ></textarea>
          </view>
        </view>
        <view class="content-card" v-if="savedNotes.length">
          <text class="card-heading">历史笔记</text>
          <view class="note-item" v-for="note in savedNotes" :key="note.id">
            <text class="note-time">{{ note.time }}</text>
            <text class="note-text">{{ note.content }}</text>
          </view>
        </view>
      </view>

      <!-- ========== 问答 Tab ========== -->
      <view v-else class="tab-content">
        <view class="content-card">
          <text class="card-heading">常见问题</text>
          <view v-if="questions.length" class="qa-list">
            <view class="qa-item" v-for="qa in questions" :key="qa.id">
              <text class="qa-question">Q: {{ qa.question }}</text>
              <text class="qa-answer">A: {{ qa.answer }}</text>
            </view>
          </view>
          <text v-else class="empty-hint">暂无问答记录</text>
        </view>
      </view>

      <!-- 底部安全距离 -->
      <view class="safe-bottom"></view>
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import SafeMarkdown from '@/components/safe-markdown/safe-markdown.vue'
import {
  getChapterDetail, getCampusCourseDetail, updateCampusChapterProgress,
  getChapterResources, getWordContent
} from '@/api/campusCourse.js'

const VIDEO_EXTS = new Set(['mp4', 'mov', 'avi', 'mkv', 'webm', 'flv', 'm3u8'])
const IMAGE_EXTS = new Set(['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'svg'])
const FILE_ICONS = {
  pdf: '📄', doc: '📝', docx: '📝', xls: '📊', xlsx: '📊',
  ppt: '📑', pptx: '📑', txt: '📃', zip: '🗜', rar: '🗜'
}
const PAGE_SIZE = 400

export default {
  components: { NavBar, SafeMarkdown },
  data() {
    return {
      courseId: '',
      chapterId: '',
      chapterData: null,
      chapters: [],
      loading: false,
      errorMessage: '',
      activeVideoId: null,
      videoCtx: null,
      activeSection: 'content',
      isFavorited: false,
      savingProgress: false,
      noteContent: '',
      savedNotes: [],
      /* 资源状态（来自 /resources 检查接口） */
      hasVideo: false,
      hasWordDocuments: false,
      hasAttachments: false,
      /* Word 文本阅读状态 */
      wordContentMap: {},
      activeWordTab: null,
      wordPageMap: {},
      wordLoadingMap: {}
    }
  },
  computed: {
    currentChapterIndex() {
      return this.chapters.findIndex(c => String(c.id) === String(this.chapterId))
    },
    hasPrev() {
      return this.currentChapterIndex > 0
    },
    hasNext() {
      return this.currentChapterIndex >= 0 && this.currentChapterIndex < this.chapters.length - 1
    },
    videoMaterial() {
      const list = this.chapterData?.materials || []
      return list.find(m => VIDEO_EXTS.has((m.fileType || '').toLowerCase())) || null
    },
    attachmentMaterials() {
      return this.chapterData?.additionalMaterials || []
    },
    wordMaterials() {
      return this.chapterData?.wordMaterials || []
    },
    wordPageInfo() {
      if (!this.activeWordTab) return null
      return this.wordContentMap[this.activeWordTab] || null
    },
    knowledgePoints() {
      const content = this.chapterData?.chapter?.content || ''
      if (!content) return []
      const lines = content.split('\n').filter(l => l.trim())
      const points = []
      for (const line of lines) {
        const stripped = line.replace(/^[#*\-\s]+/, '').trim()
        if (stripped && stripped.length > 6) {
          points.push(stripped)
          if (points.length >= 5) break
        }
      }
      return points
    },
    questions() {
      return [
        { id: 1, question: '如何高效完成本章学习？', answer: '建议先浏览知识点，再仔细阅读正文内容，结合实际案例加深理解。' }
      ]
    }
  },
  onLoad(options) {
    this.courseId = options?.courseId || ''
    this.chapterId = options?.chapterId || ''
    this.loadChapter()
  },
  onUnload() {
    if (this.videoCtx) {
      try { this.videoCtx.pause() } catch (e) { /* noop */ }
      this.videoCtx = null
    }
  },
  methods: {
    async loadChapter() {
      if (!this.courseId || !this.chapterId) {
        this.errorMessage = '缺少课程或章节编号'
        return
      }
      this.loading = true
      this.errorMessage = ''
      try {
        // 1. 先调用资源检查接口，获取 hasVideo/hasWordDocuments/hasAttachments
        let resources = { hasVideo: false, hasWordDocuments: false, hasAttachments: false }
        try {
          const res = await getChapterResources(this.courseId, this.chapterId)
          if (res?.data) {
            resources = res.data
          }
        } catch (e) {
          // 资源检查失败不影响主流程，默认全部显示
        }
        this.hasVideo = !!resources.hasVideo
        this.hasWordDocuments = !!resources.hasWordDocuments
        this.hasAttachments = !!resources.hasAttachments

        // 2. 并行加载章节详情和课程章节列表
        const [chapterRes, courseRes] = await Promise.all([
          getChapterDetail(this.courseId, this.chapterId),
          getCampusCourseDetail(this.courseId)
        ])
        this.chapterData = chapterRes?.data || null
        this.chapters = courseRes?.data?.chapters || []

        // 3. 如果有 Word 文件，初始化默认 Tab 并加载第一页
        const wordList = this.chapterData?.wordMaterials || []
        if (wordList.length > 0) {
          const first = wordList[0]
          this.activeWordTab = first.id
          await this.loadWordPage(first.id, 1)
        }
      } catch (error) {
        this.chapterData = null
        this.chapters = []
        this.errorMessage = error?.msg || error?.message || '章节加载失败'
      } finally {
        this.loading = false
      }
    },

    /* ======== Word 文本阅读 ======== */
    async loadWordPage(materialId, page) {
      if (!materialId || !this.courseId || !this.chapterId) return
      this.$set(this.wordLoadingMap, materialId, true)
      try {
        const res = await getWordContent(this.courseId, this.chapterId, materialId, page, PAGE_SIZE)
        if (res?.data) {
          this.$set(this.wordContentMap, materialId, res.data)
          this.$set(this.wordPageMap, materialId, page)
        }
      } catch (e) {
        // 加载失败静默处理
      } finally {
        this.$set(this.wordLoadingMap, materialId, false)
      }
    },
    switchWordTab(wm) {
      if (!wm || !wm.id) return
      this.activeWordTab = wm.id
      if (!this.wordContentMap[wm.id]) {
        this.loadWordPage(wm.id, 1)
      }
    },
    prevWordPage() {
      const info = this.wordPageInfo
      if (!info || info.currentPage <= 1) return
      this.loadWordPage(this.activeWordTab, info.currentPage - 1)
    },
    nextWordPage() {
      const info = this.wordPageInfo
      if (!info || info.currentPage >= info.totalPages) return
      this.loadWordPage(this.activeWordTab, info.currentPage + 1)
    },

    playVideo(material) {
      this.activeVideoId = material.id
      this.$nextTick(() => {
        try {
          this.videoCtx = uni.createVideoContext(`video-${material.id}`, this)
          this.videoCtx.play()
        } catch (e) { /* noop */ }
      })
    },
    closeVideo() {
      if (this.videoCtx) { try { this.videoCtx.pause() } catch (e) { /* noop */ } }
      this.activeVideoId = null
      this.videoCtx = null
    },
    onVideoPlay() {},
    onVideoPause() {},
    onVideoEnded() {},

    openAttachment(material) {
      const url = material.fileUrl
      if (!url) return
      if (IMAGE_EXTS.has((material.fileType || '').toLowerCase())) {
        const urls = this.attachmentMaterials.filter(m => IMAGE_EXTS.has((m.fileType || '').toLowerCase())).map(m => m.fileUrl)
        uni.previewImage({ urls, current: urls.indexOf(url) || 0 })
        return
      }
      uni.showLoading({ title: '打开中...' })
      uni.downloadFile({
        url,
        success: (res) => {
          uni.hideLoading()
          if (res.statusCode >= 200 && res.statusCode < 300) {
            uni.openDocument({ filePath: res.tempFilePath, showMenu: true, fail: () => { uni.setClipboardData({ data: url }); uni.showToast({ title: '已复制链接', icon: 'none' }) } })
          } else {
            uni.setClipboardData({ data: url }); uni.showToast({ title: '已复制链接', icon: 'none' })
          }
        },
        fail: () => { uni.hideLoading(); uni.setClipboardData({ data: url }); uni.showToast({ title: '已复制链接', icon: 'none' }) }
      })
    },

    async toggleComplete() {
      if (this.savingProgress) return
      this.savingProgress = true
      const newCompleted = !this.chapterData.chapter.completed
      try {
        await updateCampusChapterProgress(this.courseId, this.chapterId, newCompleted)
        this.chapterData.chapter.completed = newCompleted
        uni.showToast({ title: newCompleted ? '已标记完成' : '已取消完成', icon: 'none' })
      } catch (error) {
        uni.showToast({ title: error?.msg || error?.message || '操作失败', icon: 'none' })
      } finally { this.savingProgress = false }
    },

    prevChapter() {
      if (!this.hasPrev) return
      const prev = this.chapters[this.currentChapterIndex - 1]
      this.switchToChapter(prev)
    },
    nextChapter() {
      if (!this.hasNext) return
      const next = this.chapters[this.currentChapterIndex + 1]
      this.switchToChapter(next)
    },
    switchToChapter(chapter) {
      if (!chapter) return
      this.chapterId = String(chapter.id)
      this.closeVideo()
      this.activeSection = 'content'
      this.activeVideoId = null
      this.wordContentMap = {}
      this.activeWordTab = null
      this.wordPageMap = {}
      this.wordLoadingMap = {}
      this.loadChapter()
    },

    fileIcon(m) {
      const ext = (m.fileType || '').toLowerCase()
      return FILE_ICONS[ext] || '📎'
    },
    formatTime(s) { const sec = Number(s) || 0; return `${Math.floor(sec / 60)}:${String(sec % 60).padStart(2, '0')}` },
    formatSize(b) {
      const n = Number(b) || 0
      if (n < 1024) return `${n}B`
      if (n < 1048576) return `${(n / 1024).toFixed(1)}KB`
      return `${(n / 1048576).toFixed(1)}MB`
    }
  }
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #f5f7fa;
  padding-bottom: 40rpx;
  animation: page-fade-in 0.35s ease-out both;
}

@keyframes page-fade-in {
  0% { opacity: 0.6; }
  100% { opacity: 1; }
}

/* 内容过场 */
.content-shell {
  animation: content-rise 0.45s ease-out both;
}

@keyframes content-rise {
  0% {
    opacity: 0;
    transform: translateY(24rpx);
  }
  100% {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 骨架屏 */
.skeleton-wrap {
  padding: 24rpx;
  display: flex;
  flex-direction: column;
  gap: 24rpx;
  animation: skeleton-enter 0.2s ease-out both;
}

@keyframes skeleton-enter {
  0% { opacity: 0; }
  100% { opacity: 1; }
}

.skeleton-block {
  border-radius: 14rpx;
  background: linear-gradient(110deg, #eceef2 30%, #f5f6f8 50%, #eceef2 70%);
  background-size: 300% 100%;
  animation: skeleton-shimmer 1.8s ease-in-out infinite;
}
.skeleton-block--video { height: 400rpx; }
.skeleton-block--text { height: 28rpx; width: 100%; }
.skeleton-block--short { width: 60%; }
.skeleton-block--mid { width: 82%; }

@keyframes skeleton-shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

/* 错误 */
.state-box { padding: 180rpx 40rpx; display: flex; flex-direction: column; align-items: center; gap: 24rpx; color: #94a3b8; font-size: 28rpx; }
.retry-btn { padding: 16rpx 36rpx; border-radius: 14rpx; border: 1px solid #4a90d9; color: #4a90d9; font-size: 26rpx; }

/* ====== 播放器区 ====== */
/* ====== 播放器区（内嵌在 content-card 中） ====== */
.player-section-inline {
  border-radius: 14rpx;
  overflow: hidden;
}
.player-section-inline .video-player {
  position: relative;
}
.player-section-inline .video-element {
  width: 100%;
  height: 400rpx;
  background: #000;
  display: block;
  border-radius: 14rpx;
}
.player-section-inline .video-placeholder {
  width: 100%;
  height: 400rpx;
  background: linear-gradient(135deg, #2d3748 0%, #4a5568 100%);
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 14rpx;
}
.player-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.3);
}
.play-btn {
  width: 104rpx;
  height: 104rpx;
  background: rgba(255, 255, 255, 0.9);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.2s;
}
.play-btn:active { transform: scale(0.92); }
.play-icon {
  font-size: 40rpx;
  color: #1a1a1a;
  margin-left: 6rpx;
}

/* ====== Word 文本区（内嵌在 content-card 中） ====== */
.content-card .word-tabs {
  display: flex;
  gap: 0;
  border-bottom: 2rpx solid #f0f0f0;
  overflow-x: auto;
  white-space: nowrap;
  margin: 0 -32rpx 0 -32rpx;
  padding: 0 32rpx;
}
.word-tab {
  flex-shrink: 0;
  padding: 22rpx 28rpx;
  font-size: 26rpx;
  color: #64748b;
  position: relative;
  transition: color 0.2s;
}
.word-tab.active {
  color: #4a90d9;
  font-weight: 650;
}
.word-tab.active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 20rpx;
  right: 20rpx;
  height: 5rpx;
  background: #4a90d9;
  border-radius: 3rpx;
}
.word-content-wrap {
  padding: 28rpx;
  min-height: 260rpx;
}
.word-loading, .word-empty {
  text-align: center;
  color: #94a3b8;
  font-size: 26rpx;
  padding: 48rpx 0;
}
.word-text {
  line-height: 1.85;
}
.word-page-content {
  font-size: 28rpx;
  color: #444;
  white-space: pre-wrap;
  word-break: break-all;
}

/* 翻页器 */
.word-pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx 28rpx;
  border-top: 2rpx solid #f0f0f0;
}
.pager-btn {
  display: flex;
  align-items: center;
  gap: 6rpx;
  padding: 12rpx 24rpx;
  background: #e8f2fd;
  border-radius: 28rpx;
  font-size: 24rpx;
  color: #4a90d9;
  transition: all 0.2s;
}
.pager-btn.disabled { opacity: 0.35; }
.pager-btn:not(.disabled):active { background: #4a90d9; color: #fff; }
.pager-icon { font-size: 24rpx; line-height: 1; }
.pager-info {
  font-size: 24rpx;
  color: #94a3b8;
}

/* ====== 章节信息 ====== */
.chapter-content {
  margin: 24rpx;
  padding: 32rpx;
  background: #fff;
  border-radius: 22rpx;
  box-shadow: 0 4rpx 20rpx rgba(0,0,0,0.04);
}
.chapter-title {
  display: block;
  font-size: 36rpx;
  font-weight: 750;
  color: #333;
  line-height: 1.4;
  margin-bottom: 20rpx;
}
.chapter-meta-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  flex-wrap: wrap;
}
.chapter-meta {
  display: flex;
  align-items: center;
  gap: 14rpx;
  font-size: 26rpx;
  color: #999;
}
.sep { color: #ddd; }
.chapter-nav { display: flex; gap: 12rpx; }
.nav-item {
  display: flex;
  align-items: center;
  gap: 6rpx;
  padding: 12rpx 20rpx;
  background: #e8f2fd;
  border-radius: 32rpx;
  font-size: 24rpx;
  color: #4a90d9;
  transition: all 0.2s;
}
.nav-item.disabled { opacity: 0.4; }
.nav-item:not(.disabled):active { background: #4a90d9; color: #fff; }
.nav-icon { font-size: 24rpx; line-height: 1; }
.nav-label { font-size: 22rpx; }

/* ====== 浮动按钮 ====== */
.floating-actions {
  position: fixed;
  right: 20rpx;
  bottom: 100rpx;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
  z-index: 99;
}
.float-btn {
  position: relative;
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 6rpx 22rpx rgba(0,0,0,0.14);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.15s;
}
.float-btn:active { transform: scale(0.92); }
.float-icon { font-size: 32rpx; color: #4a90d9; line-height: 1; }
.float-label {
  position: absolute;
  right: 84rpx;
  top: 50%;
  transform: translateY(-50%);
  background: rgba(0,0,0,0.78);
  color: #fff;
  font-size: 22rpx;
  padding: 8rpx 18rpx;
  border-radius: 20rpx;
  white-space: nowrap;
  pointer-events: none;
  opacity: 0;
}
.complete-btn.active {
  background: linear-gradient(135deg, #4ade80, #22c55e);
  box-shadow: 0 6rpx 22rpx rgba(34,197,94,0.4);
}
.complete-btn.active .float-icon { color: #fff; }
.fav-btn.active {
  background: linear-gradient(135deg, #ff6b9d, #ff4757);
  box-shadow: 0 6rpx 22rpx rgba(255,71,87,0.4);
}
.fav-btn.active .float-icon { color: #fff; }

/* ====== Tab 栏 ====== */
.section-tabs {
  display: flex;
  margin: 0 24rpx;
  background: #fff;
  border-radius: 16rpx;
  overflow: hidden;
}
.section-tab {
  flex: 1;
  text-align: center;
  padding: 22rpx 0;
  font-size: 26rpx;
  color: #64748b;
  position: relative;
}
.section-tab.active {
  color: #4a90d9;
  font-weight: 650;
}
.section-tab.active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 44rpx;
  height: 5rpx;
  background: #4a90d9;
  border-radius: 3rpx;
}

/* ====== Tab 内容 ====== */
.tab-content {
  padding: 24rpx;
}

.content-card {
  background: #fff;
  border-radius: 20rpx;
  padding: 32rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 4rpx 20rpx rgba(0,0,0,0.04);
}
.content-card:last-child { margin-bottom: 0; }

.card-heading {
  display: block;
  font-size: 30rpx;
  font-weight: 650;
  color: #333;
  margin-bottom: 20rpx;
}

/* 附件 */
.attachment-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}
.attachment-item {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 20rpx;
  background: #f8f9fa;
  border-radius: 14rpx;
  transition: background 0.2s;
}
.attachment-item:active { background: #eef1f5; }
.attachment-icon { font-size: 36rpx; flex-shrink: 0; }
.attachment-info { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.attachment-name { display: block; font-size: 28rpx; color: #333; font-weight: 550; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.attachment-size { display: block; font-size: 22rpx; color: #999; margin-top: 4rpx; }
.download-btn {
  flex-shrink: 0;
  padding: 10rpx 24rpx;
  border-radius: 28rpx;
  background: linear-gradient(135deg, #4a90d9, #5b9fe0);
  color: #fff;
  font-size: 24rpx;
  font-weight: 600;
}

/* 知识点 */
.point-list {
  display: flex;
  flex-direction: column;
  gap: 18rpx;
}
.point-item {
  display: flex;
  gap: 16rpx;
  align-items: flex-start;
}
.point-number {
  width: 44rpx;
  height: 44rpx;
  border-radius: 50%;
  background: #4a90d9;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24rpx;
  font-weight: 700;
  flex-shrink: 0;
}
.point-text {
  font-size: 28rpx;
  color: #666;
  line-height: 1.65;
  flex: 1;
  padding-top: 8rpx;
}

/* 笔记 */
.note-editor {
  padding: 0;
}
.note-textarea {
  width: 100%;
  min-height: 200rpx;
  border: none;
  outline: none;
  font-size: 28rpx;
  color: #333;
  background: #f8f9fa;
  border-radius: 16rpx;
  padding: 24rpx;
  box-sizing: border-box;
}
.note-item {
  padding: 20rpx;
  background: #f8f9fa;
  border-radius: 14rpx;
  margin-bottom: 16rpx;
}
.note-item:last-child { margin-bottom: 0; }
.note-time {
  display: block;
  font-size: 22rpx;
  color: #999;
  margin-bottom: 8rpx;
}
.note-text {
  display: block;
  font-size: 26rpx;
  color: #555;
  line-height: 1.65;
}

/* 问答 */
.qa-list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}
.qa-item {
  padding: 20rpx;
  background: #f8f9fa;
  border-radius: 14rpx;
}
.qa-question {
  display: block;
  font-size: 26rpx;
  font-weight: 600;
  color: #333;
  margin-bottom: 10rpx;
}
.qa-answer {
  display: block;
  font-size: 26rpx;
  color: #666;
  line-height: 1.65;
}

.empty-hint {
  display: block;
  text-align: center;
  color: #94a3b8;
  font-size: 26rpx;
  padding: 32rpx 0;
}

.safe-bottom {
  height: constant(safe-area-inset-bottom);
  height: env(safe-area-inset-bottom);
  min-height: 32rpx;
}
</style>
