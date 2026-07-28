<template>
  <view class="ppt-flow">
    <view class="flow-heading">
      <view class="flow-heading__copy">
        <text class="flow-heading__eyebrow">AI 复习资料 PPT</text>
        <text class="flow-heading__title">{{ stepMeta[currentStep - 1].title }}</text>
        <text class="flow-heading__desc">{{ stepMeta[currentStep - 1].description }}</text>
      </view>
      <view class="history-entry" @tap="openHistory('generation')">
        <view class="history-entry__icon"><text></text><text></text><text></text></view>
        <text>生成历史</text>
      </view>
    </view>

    <scroll-view class="step-scroll" scroll-x :show-scrollbar="false">
      <view class="stepper">
        <view
          v-for="item in stepMeta"
          :key="item.id"
          class="stepper__item"
          :class="{ 'stepper__item--active': currentStep === item.id, 'stepper__item--done': currentStep > item.id }"
        >
          <view class="stepper__number">
            <text v-if="currentStep <= item.id">{{ item.id }}</text>
            <text v-else class="stepper__check">✓</text>
          </view>
          <text class="stepper__label">{{ item.shortTitle }}</text>
        </view>
      </view>
    </scroll-view>

    <view v-if="currentStep === 1" class="panel">
      <view class="field">
        <text class="field__label">学习场景</text>
        <view class="select-field">
          <view>
            <text class="select-field__value">复习资料</text>
            <text class="select-field__hint">将学习资料整理成结构清晰的复习 PPT</text>
          </view>
          <text class="select-field__arrow">⌄</text>
        </view>
      </view>

      <view class="field">
        <text class="field__label">上传学习资料</text>
        <view v-if="!fileInfo" class="upload-box" @tap="chooseTxtFile">
          <view class="file-icon"><text>TXT</text></view>
          <text class="upload-box__title">点击上传 TXT 文件</text>
          <text class="upload-box__hint">当前仅支持单个 TXT 文件</text>
        </view>
        <view v-else class="file-row">
          <view class="file-row__icon">TXT</view>
          <view class="file-row__main">
            <text class="file-row__name">{{ fileInfo.name }}</text>
            <view class="file-row__meta">
              <text>{{ fileInfo.sizeLabel }}</text>
              <text class="file-row__success">上传完成</text>
            </view>
          </view>
          <view class="file-row__actions">
            <text @tap.stop="chooseTxtFile">重传</text>
            <text class="file-row__delete" @tap.stop="removeFile">删除</text>
          </view>
        </view>
      </view>

      <view v-if="fileInfo" class="preview-card">
        <view class="preview-card__head">
          <text class="preview-card__title">资料预览</text>
          <text class="preview-card__count">已读取 {{ formattedCharacterCount }} 字</text>
        </view>
        <text class="preview-card__content">{{ previewContent }}</text>
        <text v-if="fileContent.length > previewContent.length" class="preview-card__more">仅展示部分内容</text>
      </view>

      <view class="single-action">
        <button class="primary-button" :disabled="!fileInfo" @tap="goNext">下一步</button>
      </view>
    </view>

    <view v-else-if="currentStep === 2" class="panel">
      <view
        v-for="mode in outlineModes"
        :key="mode.id"
        class="choice-card choice-card--large"
        :class="{ 'choice-card--selected': outlineMode === mode.id }"
        @tap="outlineMode = mode.id"
      >
        <view class="choice-card__icon" :class="`choice-card__icon--${mode.id}`">
          <view class="line-icon">
            <text></text><text></text><text></text>
          </view>
        </view>
        <view class="choice-card__body">
          <text class="choice-card__title">{{ mode.name }}</text>
          <text class="choice-card__desc">{{ mode.description }}</text>
          <text class="choice-card__fit">{{ mode.fit }}</text>
        </view>
        <view class="radio-dot" :class="{ 'radio-dot--selected': outlineMode === mode.id }">
          <text v-if="outlineMode === mode.id">✓</text>
        </view>
      </view>
      <view class="bottom-actions">
        <button class="secondary-button" @tap="goPrevious">上一步</button>
        <button class="primary-button" @tap="prepareOutline">下一步</button>
      </view>
    </view>

    <view v-else-if="currentStep === 3" class="panel outline-panel">
      <view class="editor-toolbar">
        <view>
          <text class="editor-toolbar__title">PPT 大纲</text>
          <text class="editor-toolbar__desc">{{ outlineMode === 'ai_outline' ? 'AI 生成大纲草稿，可继续调整' : '已按原资料层级识别，可继续调整' }}</text>
        </view>
        <view class="outline-history-button" @tap="openHistory('outline')">大纲记录</view>
      </view>

      <view class="outline-name-field">
        <text>大纲名称</text>
        <input v-model="outlineName" :maxlength="60" placeholder="请输入大纲名称" />
      </view>

      <view class="outline-list">
        <view v-for="(item, index) in outlineItems" :key="item.id" class="outline-item">
          <view class="outline-item__order">{{ index + 1 }}</view>
          <view class="outline-item__main">
            <view class="outline-item__level-row">
              <view
                v-for="level in outlineLevels"
                :key="level.value"
                class="outline-level"
                :class="{ 'outline-level--active': item.level === level.value }"
                @tap="item.level = level.value"
              >{{ level.label }}</view>
            </view>
            <input v-model="item.title" :maxlength="80" placeholder="输入大纲标题" />
          </view>
          <view class="outline-item__actions">
            <text :class="{ disabled: index === 0 }" @tap="moveOutlineItem(index, -1)">↑</text>
            <text :class="{ disabled: index === outlineItems.length - 1 }" @tap="moveOutlineItem(index, 1)">↓</text>
            <text class="outline-item__delete" @tap="removeOutlineItem(index)">×</text>
          </view>
        </view>
      </view>

      <button class="add-outline-button" @tap="addOutlineItem">＋ 添加大纲项</button>
      <view class="outline-save-tip">
        <text>当前大纲会独立保存，之后可从“大纲记录”再次使用</text>
        <text v-if="outlineSavedAt">已保存 {{ outlineSavedAt }}</text>
      </view>
      <view class="bottom-actions bottom-actions--three">
        <button class="secondary-button" @tap="goPrevious">上一步</button>
        <button class="secondary-button" @tap="saveOutlineSnapshot(true)">保存大纲</button>
        <button class="primary-button" :disabled="!validOutlineItems.length" @tap="confirmOutline">下一步</button>
      </view>
    </view>

    <view v-else-if="currentStep === 4" class="panel">
      <view class="scene-summary">
        <text class="scene-summary__label">学习场景</text>
        <text class="scene-summary__value">复习资料</text>
      </view>

      <view class="settings-section">
        <view class="settings-section__head">
          <text class="settings-section__title">预计页数</text>
          <view><text class="page-number">{{ pageCount }}</text><text class="settings-section__unit"> 页</text></view>
        </view>
        <slider :value="pageCount" :min="5" :max="30" activeColor="#4e61f6" backgroundColor="#dce2ef" block-color="#ffffff" :block-size="22" @changing="setPageCount" @change="setPageCount" />
        <view class="range-label"><text>5</text><text>30</text></view>
        <text class="settings-hint">最终页数可能根据资料内容进行小幅调整</text>
      </view>

      <view class="settings-section">
        <text class="settings-section__title settings-section__title--block">PPT 风格</text>
        <scroll-view class="style-scroll" scroll-x :show-scrollbar="false">
          <view class="style-list">
            <view
              v-for="style in pptStyles"
              :key="style.id"
              class="style-card"
              :class="{ 'style-card--selected': pptStyle === style.id }"
              @tap="pptStyle = style.id"
            >
              <view class="style-card__preview" :class="`style-card__preview--${style.id}`">
                <view class="mini-slide__title"></view>
                <view class="mini-slide__line mini-slide__line--long"></view>
                <view class="mini-slide__line"></view>
                <view class="mini-slide__shape"></view>
              </view>
              <text class="style-card__name">{{ style.name }}</text>
              <text class="style-card__desc">{{ style.description }}</text>
              <view v-if="pptStyle === style.id" class="style-card__check">✓</view>
            </view>
          </view>
        </scroll-view>
      </view>

      <view class="settings-section">
        <text class="settings-section__title settings-section__title--block">内容详细程度</text>
        <view class="segmented">
          <view v-for="level in contentLevels" :key="level.id" class="segmented__item" :class="{ 'segmented__item--active': contentLevel === level.id }" @tap="contentLevel = level.id">
            <text>{{ level.name }}</text>
          </view>
        </view>
        <text class="settings-hint">{{ currentContentLevel.description }}</text>
      </view>

      <view class="settings-section settings-section--split">
        <view class="switch-list">
          <text class="settings-section__title settings-section__title--block">页面组成</text>
          <view v-for="option in pageOptions" :key="option.key" class="switch-row">
            <text>{{ option.label }}</text>
            <switch :checked="settings[option.key]" color="#4e61f6" @change="toggleSetting(option.key, $event)" />
          </view>
          <view class="switch-row switch-row--visuals">
            <view>
              <text class="switch-row__title">生成辅助配图</text>
              <text class="switch-row__desc">匹配图标、流程图和结构图</text>
            </view>
            <switch :checked="settings.includeVisuals" color="#4e61f6" @change="toggleSetting('includeVisuals', $event)" />
          </view>
        </view>
        <view class="effect-preview">
          <text class="effect-preview__label">效果预览</text>
          <view class="effect-slide" :class="`effect-slide--${pptStyle}`">
            <text class="effect-slide__eyebrow">REVIEW MATERIAL</text>
            <text class="effect-slide__title">复习资料 PPT</text>
            <view class="effect-slide__line"></view>
            <view class="effect-slide__blocks"><text></text><text></text><text></text></view>
          </view>
        </view>
      </view>

      <view class="bottom-actions">
        <button class="secondary-button" @tap="goPrevious">上一步</button>
        <button class="primary-button" @tap="prepareSlides">编辑页面内容</button>
      </view>
    </view>

    <view v-else-if="currentStep === 5" class="panel page-editor-panel">
      <view class="editor-toolbar">
        <view>
          <text class="editor-toolbar__title">逐页编辑</text>
          <text class="editor-toolbar__desc">正式生成前，确认每页标题、内容和提示词</text>
        </view>
        <text class="page-editor-count">{{ activeSlideIndex + 1 }} / {{ slides.length }}</text>
      </view>

      <scroll-view class="slide-tabs" scroll-x :scroll-into-view="`slide-tab-${activeSlideIndex}`" :show-scrollbar="false">
        <view class="slide-tabs__inner">
          <view
            v-for="(slide, index) in slides"
            :id="`slide-tab-${index}`"
            :key="slide.id"
            class="slide-tab"
            :class="{ 'slide-tab--active': activeSlideIndex === index }"
            @tap="activeSlideIndex = index"
          >
            <text>{{ index + 1 }}</text>
            <text>{{ slide.title || '未命名页面' }}</text>
          </view>
        </view>
      </scroll-view>

      <view v-if="activeSlide" class="slide-editor">
        <view class="slide-editor__preview" :class="`slide-editor__preview--${pptStyle}`">
          <text class="slide-editor__preview-index">{{ String(activeSlideIndex + 1).padStart(2, '0') }}</text>
          <text class="slide-editor__preview-title">{{ activeSlide.title || '未命名页面' }}</text>
          <text class="slide-editor__preview-content">{{ activeSlide.content || '暂未填写页面内容' }}</text>
          <view class="slide-editor__preview-decor"></view>
        </view>

        <view class="edit-field">
          <view class="edit-field__label"><text>页面标题</text><text>{{ activeSlide.title.length }}/80</text></view>
          <input v-model="activeSlide.title" :maxlength="80" placeholder="请输入页面标题" />
        </view>
        <view class="edit-field">
          <view class="edit-field__label"><text>页面内容</text><text>支持修改单页内容</text></view>
          <textarea v-model="activeSlide.content" :maxlength="1200" auto-height placeholder="请输入本页需要展示的知识点和说明" />
        </view>
        <view class="prompt-field prompt-field--shared">
          <view class="prompt-field__head">
            <view><text>公共提示词</text><text>所有页面共同生效</text></view>
            <view class="prompt-badge">全局</view>
          </view>
          <textarea v-model="sharedPrompt" :maxlength="800" auto-height placeholder="例如：保持学习资料准确，使用简洁排版，突出关键概念" />
          <text class="prompt-field__hint">在任意页面修改后，会同步到全部页面。</text>
        </view>
        <view class="prompt-field">
          <view class="prompt-field__head">
            <view><text>单页私有提示词</text><text>仅对第 {{ activeSlideIndex + 1 }} 页生效</text></view>
            <view class="prompt-badge prompt-badge--private">私有</view>
          </view>
          <textarea v-model="activeSlide.privatePrompt" :maxlength="800" auto-height placeholder="例如：本页使用左右对比布局，增加函数图像示意" />
          <text class="prompt-field__hint">私有提示词用于补充本页版式、配图和强调重点。</text>
        </view>

        <view class="slide-editor__navigation">
          <button class="secondary-button" :disabled="activeSlideIndex === 0" @tap="activeSlideIndex -= 1">上一页</button>
          <button class="secondary-button" :disabled="activeSlideIndex === slides.length - 1" @tap="activeSlideIndex += 1">下一页</button>
        </view>
      </view>

      <view class="bottom-actions">
        <button class="secondary-button" @tap="goPrevious">返回设置</button>
        <button class="primary-button" @tap="startMockGeneration">确认并生成</button>
      </view>
    </view>

    <view v-else-if="currentStep === 6" class="panel progress-panel">
      <view class="progress-hero">
        <view class="progress-ring" :style="{ '--progress': `${progress * 3.6}deg` }">
          <view class="progress-ring__inner"><text>{{ progress }}</text><text>%</text></view>
        </view>
        <text class="progress-hero__stage">{{ activeGenerationStep.activeText }}</text>
        <text class="progress-hero__message">{{ progressMessage }}</text>
      </view>

      <view class="generation-list">
        <view v-for="(item, index) in generationSteps" :key="item.id" class="generation-item">
          <view class="generation-item__rail">
            <view class="generation-item__dot" :class="generationStatusClass(index)">
              <text v-if="index < activeGenerationIndex">✓</text>
              <text v-else>{{ index + 1 }}</text>
            </view>
            <view v-if="index < generationSteps.length - 1" class="generation-item__line" :class="{ 'generation-item__line--done': index < activeGenerationIndex }"></view>
          </view>
          <view class="generation-item__body">
            <text class="generation-item__title">{{ generationStepTitle(item, index) }}</text>
            <text class="generation-item__desc">{{ index < activeGenerationIndex ? item.doneText : index === activeGenerationIndex ? item.description : '等待中' }}</text>
          </view>
          <view v-if="index === activeGenerationIndex" class="loading-dots"><text></text><text></text><text></text></view>
        </view>
      </view>

      <view class="overall-progress">
        <view class="overall-progress__head"><text>总体进度</text><text>{{ progress }}%</text></view>
        <view class="overall-progress__track"><view class="overall-progress__value" :style="{ width: `${progress}%` }"></view></view>
        <text class="overall-progress__time">预计还需 {{ remainingTime }}</text>
      </view>
      <button class="secondary-button secondary-button--full" @tap="cancelGeneration">取消生成</button>
    </view>

    <view v-else-if="currentStep === 7" class="panel result-panel">
      <view class="success-hero">
        <view class="success-icon">✓</view>
        <text class="success-hero__title">PPT 生成完成</text>
        <text class="success-hero__desc">你的复习资料 PPT 已经准备好了</text>
      </view>

      <view class="result-summary">
        <view class="result-summary__name">
          <view class="result-summary__file-icon">P</view>
          <view><text>{{ resultName }}</text><text>复习资料 · {{ pageCount }} 页</text></view>
        </view>
        <view class="result-summary__meta"><text>生成完成</text><text>刚刚</text></view>
      </view>

      <view class="preview-section">
        <view class="preview-section__head"><text>页面预览</text><text>共 {{ pageCount }} 页</text></view>
        <view class="slide-grid">
          <view v-for="slide in visibleSlides" :key="slide" class="slide-thumb" @tap="openSlidePreview(slide)">
            <view class="slide-thumb__canvas" :class="[`slide-thumb__canvas--${pptStyle}`, { 'slide-thumb__canvas--cover': slide === 1 }]">
              <text class="slide-thumb__number">{{ String(slide).padStart(2, '0') }}</text>
              <text class="slide-thumb__title">{{ slideTitle(slide) }}</text>
              <view class="slide-thumb__lines"><text></text><text></text><text></text></view>
              <view class="slide-thumb__decor"></view>
            </view>
            <text class="slide-thumb__page">{{ slide }}</text>
          </view>
        </view>
        <button v-if="pageCount > 6" class="text-button" @tap="showAllSlides = !showAllSlides">{{ showAllSlides ? '收起页面' : `查看全部 ${pageCount} 页` }}</button>
      </view>

      <view class="bottom-actions">
        <button class="secondary-button" @tap="restartFromSettings">重新生成</button>
        <button class="primary-button" @tap="currentStep = 8">导出下载</button>
      </view>
    </view>

    <view v-else class="panel export-panel">
      <view
        v-for="format in exportFormats"
        :key="format.id"
        class="export-choice"
        :class="{ 'export-choice--selected': exportFormat === format.id }"
        @tap="selectExportFormat(format.id)"
      >
        <view class="export-choice__icon" :class="`export-choice__icon--${format.id}`">{{ format.icon }}</view>
        <view class="export-choice__body">
          <text class="export-choice__title">{{ format.name }} <text>（.{{ format.id }}）</text></text>
          <text class="export-choice__desc">{{ format.description }}</text>
        </view>
        <view class="radio-dot" :class="{ 'radio-dot--selected': exportFormat === format.id }"><text v-if="exportFormat === format.id">✓</text></view>
      </view>

      <button class="primary-button primary-button--full" :disabled="exportPreparing" @tap="prepareExport">
        {{ exportPreparing ? '正在生成下载文件…' : exportReady ? '下载文件' : '生成下载文件' }}
      </button>

      <view v-if="exportReady" class="download-ready">
        <view class="download-ready__illustration">
          <view class="download-ready__file">{{ exportFormat === 'pptx' ? 'P' : 'PDF' }}</view>
          <view class="download-ready__arrow">↓</view>
        </view>
        <text class="download-ready__title">文件已生成</text>
        <text class="download-ready__name">{{ downloadFileName }}</text>
        <text class="download-ready__hint">当前为前端页面演示，接入后端后可下载真实文件</text>
      </view>

      <button class="back-result-button" @tap="currentStep = 7">返回生成结果</button>
    </view>

    <view v-if="historyOpen" class="history-mask" @tap="historyOpen = false">
      <view class="history-drawer" @tap.stop>
        <view class="history-drawer__head">
          <view>
            <text class="history-drawer__title">历史记录</text>
            <text class="history-drawer__desc">保存在当前设备，仅用于前端演示</text>
          </view>
          <view class="history-drawer__close" @tap="historyOpen = false">×</view>
        </view>
        <view class="history-tabs">
          <view :class="{ 'history-tabs__item--active': historyTab === 'generation' }" class="history-tabs__item" @tap="historyTab = 'generation'">生成记录</view>
          <view :class="{ 'history-tabs__item--active': historyTab === 'outline' }" class="history-tabs__item" @tap="historyTab = 'outline'">大纲记录</view>
        </view>

        <scroll-view class="history-list" scroll-y>
          <view v-if="currentHistory.length">
            <view v-for="item in currentHistory" :key="item.id" class="history-card">
              <view class="history-card__head">
                <view class="history-card__type" :class="{ 'history-card__type--upload': item.source === 'original_outline' }">
                  {{ historyTab === 'outline' ? (item.source === 'ai_outline' ? 'AI 大纲' : '原文大纲') : 'PPT' }}
                </view>
                <text>{{ item.createdAt }}</text>
              </view>
              <text class="history-card__title">{{ item.name }}</text>
              <text class="history-card__meta">
                {{ historyTab === 'outline' ? `${item.items.length} 个大纲项` : `${item.pageCount} 页 · ${styleName(item.pptStyle)}` }}
              </text>
              <view class="history-card__actions">
                <text @tap="reuseHistory(item)">{{ historyTab === 'outline' ? '载入并编辑' : '使用此配置' }}</text>
                <text class="history-card__delete" @tap="deleteHistory(item.id)">删除</text>
              </view>
            </view>
          </view>
          <view v-else class="history-empty">
            <view class="history-empty__icon"><text></text><text></text><text></text></view>
            <text class="history-empty__title">{{ historyTab === 'outline' ? '暂无大纲记录' : '暂无生成记录' }}</text>
            <text class="history-empty__desc">{{ historyTab === 'outline' ? '保存过的大纲会显示在这里' : '完成一次前端生成后会显示在这里' }}</text>
          </view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script>
export default {
  name: 'AiPresentationFlow',
  props: {
    initialTopic: { type: String, default: '' }
  },
  data() {
    return {
      currentStep: 1,
      fileInfo: null,
      fileContent: '',
      outlineMode: 'ai_outline',
      outlineName: '',
      outlineItems: [],
      outlineSavedAt: '',
      outlineLevels: [
        { value: 1, label: '章节' },
        { value: 2, label: '小节' },
        { value: 3, label: '知识点' }
      ],
      pageCount: 15,
      pptStyle: 'simple',
      contentLevel: 'standard',
      slides: [],
      activeSlideIndex: 0,
      sharedPrompt: '保持学习资料内容准确，版面简洁清晰，突出核心知识点，使用适合学生复习的视觉层级。',
      settings: {
        includeCover: true,
        includeCatalog: true,
        includeSection: true,
        includeSummary: true,
        includeVisuals: true
      },
      progress: 0,
      generationTimer: null,
      showAllSlides: false,
      exportFormat: 'pptx',
      exportPreparing: false,
      exportReady: false,
      exportTimer: null,
      historyOpen: false,
      historyTab: 'generation',
      generationHistory: [],
      outlineHistory: [],
      stepMeta: [
        { id: 1, shortTitle: '上传资料', title: '生成复习资料 PPT', description: '上传学习资料，AI 将自动整理并生成复习 PPT' },
        { id: 2, shortTitle: '大纲来源', title: '选择大纲来源', description: '选择 AI 重新整理，或沿用资料原有大纲' },
        { id: 3, shortTitle: '编辑大纲', title: '编辑 PPT 大纲', description: '确认内容结构，并将本次大纲独立保存' },
        { id: 4, shortTitle: '设置 PPT', title: '设置 PPT', description: '设置 PPT 的页数和展示效果' },
        { id: 5, shortTitle: '编辑页面', title: '编辑页面内容', description: '逐页调整内容、公共提示词和单页提示词' },
        { id: 6, shortTitle: '生成进度', title: '正在生成 PPT', description: 'AI 正在整理你的学习资料，请稍候' },
        { id: 7, shortTitle: '生成结果', title: 'PPT 生成完成', description: '预览生成效果并确认导出' },
        { id: 8, shortTitle: '导出下载', title: '导出下载', description: '选择需要导出的文件格式' }
      ],
      outlineModes: [
        { id: 'ai_outline', name: 'AI 生成复习大纲', description: 'AI 分析资料内容，重新整理知识结构，生成适合复习的 PPT 大纲。', fit: '适合内容零散或没有明确结构的资料' },
        { id: 'original_outline', name: '使用原内容作为大纲', description: '按照上传资料原有的内容顺序和标题层级生成 PPT。', fit: '适合已经整理好大纲的资料' }
      ],
      pptStyles: [
        { id: 'simple', name: '简洁学习风', description: '清爽、重点突出' },
        { id: 'campus', name: '活力校园风', description: '明快、卡片布局' },
        { id: 'focus', name: '深色专注风', description: '深色、高对比度' }
      ],
      contentLevels: [
        { id: 'concise', name: '精简', description: '每页展示较少文字，主要保留核心知识点。' },
        { id: 'standard', name: '标准', description: '同时展示知识点和必要说明，适合常规复习。' },
        { id: 'detailed', name: '详细', description: '保留更多原始资料内容和解释。' }
      ],
      pageOptions: [
        { key: 'includeCover', label: '包含封面' },
        { key: 'includeCatalog', label: '包含目录' },
        { key: 'includeSection', label: '包含章节页' },
        { key: 'includeSummary', label: '包含总结页' }
      ],
      generationSteps: [
        { id: 'analyzing', activeText: '正在分析资料', description: '识别资料主题、章节和核心知识点', doneText: '资料分析完成' },
        { id: 'outline', activeText: '正在生成大纲', description: '整理章节层级和页面结构', doneText: '大纲生成完成' },
        { id: 'writing', activeText: '正在撰写内容', description: '生成每页标题、知识点和必要说明', doneText: '页面内容生成完成' },
        { id: 'layout', activeText: '正在匹配版式', description: '为不同内容选择合适的页面布局', doneText: '版式匹配完成' },
        { id: 'visuals', activeText: '正在生成配图', description: '匹配图标、流程图和结构图', doneText: '配图生成完成' },
        { id: 'building', activeText: '正在制作文件', description: '生成页面预览和导出文件', doneText: 'PPT 文件制作完成' }
      ],
      exportFormats: [
        { id: 'pptx', icon: 'P', name: 'PowerPoint 格式', description: '可使用 PowerPoint 或 WPS 打开并继续编辑。' },
        { id: 'pdf', icon: 'PDF', name: 'PDF 格式', description: '适合查看和打印，不支持编辑。' }
      ]
    }
  },
  computed: {
    validOutlineItems() {
      return this.outlineItems.filter(item => String(item.title || '').trim())
    },
    activeSlide() {
      return this.slides[this.activeSlideIndex] || null
    },
    currentHistory() {
      return this.historyTab === 'outline' ? this.outlineHistory : this.generationHistory
    },
    previewContent() {
      return (this.fileContent || '').trim().slice(0, 420)
    },
    formattedCharacterCount() {
      return String((this.fileContent || '').replace(/\s/g, '').length).replace(/\B(?=(\d{3})+(?!\d))/g, ',')
    },
    currentContentLevel() {
      return this.contentLevels.find(item => item.id === this.contentLevel) || this.contentLevels[1]
    },
    activeGenerationIndex() {
      if (this.progress >= 100) return this.generationSteps.length - 1
      return Math.min(Math.floor(this.progress / (100 / this.generationSteps.length)), this.generationSteps.length - 1)
    },
    activeGenerationStep() {
      return this.generationSteps[this.activeGenerationIndex]
    },
    progressMessage() {
      if (this.activeGenerationStep.id === 'writing') {
        const current = Math.max(1, Math.min(this.pageCount, Math.round((this.progress / 100) * this.pageCount)))
        return `正在生成第 ${current} 页，共 ${this.pageCount} 页`
      }
      return this.activeGenerationStep.description
    },
    remainingTime() {
      const seconds = Math.max(1, Math.ceil((100 - this.progress) / 5))
      return seconds > 10 ? `${seconds - 5}～${seconds + 5} 秒` : `约 ${seconds} 秒`
    },
    resultName() {
      const name = this.fileInfo?.name || '复习资料.txt'
      return name.replace(/\.txt$/i, '') || '复习资料'
    },
    visibleSlides() {
      const length = this.showAllSlides ? this.pageCount : Math.min(6, this.pageCount)
      return Array.from({ length }, (_, index) => index + 1)
    },
    downloadFileName() {
      return `${this.resultName}_复习资料PPT.${this.exportFormat}`
    }
  },
  created() {
    this.restoreHistories()
  },
  beforeDestroy() {
    this.clearTimers()
  },
  methods: {
    chooseTxtFile() {
      if (typeof uni.chooseFile === 'function') {
        uni.chooseFile({
          count: 1,
          extension: ['txt'],
          success: result => this.handleSelectedFile((result.tempFiles || [])[0], (result.tempFilePaths || [])[0]),
          fail: error => {
            if (!String(error?.errMsg || '').includes('cancel')) uni.showToast({ title: '文件选择失败', icon: 'none' })
          }
        })
        return
      }
      if (typeof uni.chooseMessageFile === 'function') {
        uni.chooseMessageFile({
          count: 1,
          type: 'file',
          extension: ['txt'],
          success: result => this.handleSelectedFile((result.tempFiles || [])[0]),
          fail: error => {
            if (!String(error?.errMsg || '').includes('cancel')) uni.showToast({ title: '文件选择失败', icon: 'none' })
          }
        })
        return
      }
      uni.showToast({ title: '当前环境暂不支持选择文件', icon: 'none' })
    },
    async handleSelectedFile(file = {}, fallbackPath = '') {
      const name = String(file.name || fallbackPath.split('/').pop() || '学习资料.txt')
      if (!/\.txt$/i.test(name)) {
        uni.showToast({ title: '请选择 TXT 格式文件', icon: 'none' })
        return
      }
      try {
        const content = await this.readTextFile(file, fallbackPath)
        if (!content.trim()) {
          uni.showToast({ title: 'TXT 文件内容为空', icon: 'none' })
          return
        }
        this.fileContent = content
        const estimatedSize = typeof Blob !== 'undefined' ? new Blob([content]).size : encodeURIComponent(content).replace(/%[0-9A-F]{2}/g, 'x').length
        const size = Number(file.size || estimatedSize || 0)
        this.fileInfo = { name, size, sizeLabel: this.formatFileSize(size) }
      } catch (error) {
        uni.showToast({ title: 'TXT 文件读取失败', icon: 'none' })
      }
    },
    readTextFile(file, fallbackPath) {
      const nativeFile = file.file || file
      if (typeof FileReader !== 'undefined' && typeof Blob !== 'undefined' && nativeFile instanceof Blob) {
        return new Promise((resolve, reject) => {
          const reader = new FileReader()
          reader.onload = event => resolve(String(event.target?.result || ''))
          reader.onerror = reject
          reader.readAsText(nativeFile, 'UTF-8')
        })
      }
      const path = file.path || file.tempFilePath || fallbackPath
      if (typeof uni.getFileSystemManager === 'function' && path) {
        return new Promise((resolve, reject) => {
          uni.getFileSystemManager().readFile({ filePath: path, encoding: 'utf8', success: result => resolve(String(result.data || '')), fail: reject })
        })
      }
      return Promise.reject(new Error('unsupported file reader'))
    },
    formatFileSize(size) {
      if (size < 1024) return `${size} B`
      if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
      return `${(size / 1024 / 1024).toFixed(1)} MB`
    },
    removeFile() {
      this.fileInfo = null
      this.fileContent = ''
      this.outlineItems = []
      this.slides = []
    },
    goNext() {
      if (this.currentStep === 1 && !this.fileInfo) return
      this.currentStep = Math.min(this.stepMeta.length, this.currentStep + 1)
    },
    goPrevious() {
      this.currentStep = Math.max(1, this.currentStep - 1)
    },
    setPageCount(event) {
      this.pageCount = Number(event?.detail?.value || 15)
    },
    toggleSetting(key, event) {
      this.settings[key] = Boolean(event?.detail?.value)
    },
    prepareOutline() {
      if (!this.fileInfo) return
      const detected = this.detectOutlineItems()
      this.outlineItems = detected.length ? detected : [
        this.createOutlineItem(this.resultName, 1),
        this.createOutlineItem('资料核心内容', 2),
        this.createOutlineItem('复习总结', 2)
      ]
      this.outlineName = `${this.resultName}大纲`
      this.outlineSavedAt = ''
      this.currentStep = 3
    },
    detectOutlineItems() {
      const lines = String(this.fileContent || '').split(/\r?\n/).map(line => line.trim()).filter(Boolean)
      const candidates = lines.filter(line => {
        if (line.length > 42) return false
        return /^(第[一二三四五六七八九十百\d]+[章节篇部分]|[一二三四五六七八九十]+[、.．]|[\d]+[、.．])/.test(line)
      }).slice(0, 24)
      return candidates.map(line => {
        let level = 3
        if (/^第[一二三四五六七八九十百\d]+[章节篇部分]/.test(line)) level = 1
        else if (/^[一二三四五六七八九十]+[、.．]/.test(line)) level = 2
        return this.createOutlineItem(line, level)
      })
    },
    createOutlineItem(title = '', level = 2) {
      return { id: `outline-${Date.now()}-${Math.random().toString(16).slice(2)}`, title, level }
    },
    addOutlineItem() {
      this.outlineItems.push(this.createOutlineItem('', 2))
    },
    removeOutlineItem(index) {
      if (this.outlineItems.length <= 1) {
        uni.showToast({ title: '至少保留一个大纲项', icon: 'none' })
        return
      }
      this.outlineItems.splice(index, 1)
    },
    moveOutlineItem(index, direction) {
      const target = index + direction
      if (target < 0 || target >= this.outlineItems.length) return
      const items = [...this.outlineItems]
      const current = items[index]
      items.splice(index, 1)
      items.splice(target, 0, current)
      this.outlineItems = items
    },
    confirmOutline() {
      if (!this.validOutlineItems.length) return
      this.saveOutlineSnapshot(false)
      this.currentStep = 4
    },
    saveOutlineSnapshot(showFeedback = true) {
      if (!this.validOutlineItems.length) return
      const snapshot = {
        id: `outline-history-${Date.now()}`,
        name: String(this.outlineName || `${this.resultName}大纲`).trim(),
        source: this.outlineMode,
        createdAt: this.formatNow(),
        items: this.validOutlineItems.map(item => ({ ...item }))
      }
      const previous = this.outlineHistory[0]
      const sameAsPrevious = previous &&
        previous.name === snapshot.name &&
        previous.source === snapshot.source &&
        JSON.stringify(previous.items.map(({ title, level }) => ({ title, level }))) === JSON.stringify(snapshot.items.map(({ title, level }) => ({ title, level })))
      if (!sameAsPrevious) {
        this.outlineHistory = [snapshot, ...this.outlineHistory].slice(0, 30)
        this.persistHistories()
      }
      this.outlineSavedAt = this.formatTime()
      if (showFeedback) uni.showToast({ title: sameAsPrevious ? '大纲已是最新' : '大纲已保存', icon: 'none' })
    },
    prepareSlides() {
      const outlines = this.validOutlineItems
      if (!outlines.length) {
        this.currentStep = 3
        return
      }
      const contentLines = String(this.fileContent || '').split(/\r?\n/).map(line => line.trim()).filter(Boolean)
      this.slides = Array.from({ length: this.pageCount }, (_, index) => {
        const outline = outlines[Math.min(Math.max(index - 1, 0), outlines.length - 1)]
        let title = outline?.title || `第 ${index + 1} 页`
        if (index === 0 && this.settings.includeCover) title = this.resultName
        else if (index === 1 && this.settings.includeCatalog) title = '内容目录'
        else if (index === this.pageCount - 1 && this.settings.includeSummary) title = '复习总结'
        const related = contentLines.filter(line => outline?.title && (line.includes(outline.title) || outline.title.includes(line))).slice(0, 3)
        const fallback = contentLines.slice(Math.max(0, (index - 1) * 2), Math.max(0, (index - 1) * 2) + 3)
        return {
          id: `slide-${Date.now()}-${index}`,
          title,
          content: (related.length ? related : fallback).join('\n'),
          privatePrompt: ''
        }
      })
      this.activeSlideIndex = 0
      this.currentStep = 5
    },
    startMockGeneration() {
      this.clearTimers()
      this.currentStep = 6
      this.progress = 4
      this.generationTimer = setInterval(() => {
        const increment = this.progress < 40 ? 5 : this.progress < 80 ? 4 : 3
        this.progress = Math.min(100, this.progress + increment)
        if (this.progress >= 100) {
          clearInterval(this.generationTimer)
          this.generationTimer = setTimeout(() => {
            this.recordGenerationHistory()
            this.currentStep = 7
            this.generationTimer = null
          }, 500)
        }
      }, 320)
    },
    generationStatusClass(index) {
      if (index < this.activeGenerationIndex) return 'generation-item__dot--done'
      if (index === this.activeGenerationIndex) return 'generation-item__dot--active'
      return 'generation-item__dot--waiting'
    },
    generationStepTitle(item, index) {
      if (index < this.activeGenerationIndex) return item.doneText
      return item.activeText
    },
    cancelGeneration() {
      this.clearTimers()
      this.progress = 0
      this.currentStep = 5
    },
    restartFromSettings() {
      this.progress = 0
      this.exportReady = false
      this.currentStep = 3
    },
    slideTitle(slide) {
      return this.slides[slide - 1]?.title || `第 ${slide} 页`
    },
    openSlidePreview(slide) {
      uni.showToast({ title: `正在查看第 ${slide} 页`, icon: 'none' })
    },
    selectExportFormat(format) {
      if (this.exportFormat !== format) {
        this.exportFormat = format
        this.exportReady = false
      }
    },
    prepareExport() {
      if (this.exportReady) {
        uni.showToast({ title: '前端演示暂不提供真实文件', icon: 'none' })
        return
      }
      this.exportPreparing = true
      clearTimeout(this.exportTimer)
      this.exportTimer = setTimeout(() => {
        this.exportPreparing = false
        this.exportReady = true
        this.exportTimer = null
      }, 900)
    },
    openHistory(tab) {
      this.historyTab = tab
      this.historyOpen = true
    },
    recordGenerationHistory() {
      const item = {
        id: `generation-history-${Date.now()}`,
        name: this.resultName,
        createdAt: this.formatNow(),
        pageCount: this.pageCount,
        pptStyle: this.pptStyle,
        contentLevel: this.contentLevel,
        outlineMode: this.outlineMode,
        settings: { ...this.settings },
        sharedPrompt: this.sharedPrompt
      }
      this.generationHistory = [item, ...this.generationHistory].slice(0, 30)
      this.persistHistories()
    },
    reuseHistory(item) {
      if (this.historyTab === 'outline') {
        this.outlineName = item.name
        this.outlineMode = item.source
        this.outlineItems = (item.items || []).map(entry => ({ ...entry, id: this.createOutlineItem().id }))
        this.currentStep = 3
      } else {
        this.pageCount = Number(item.pageCount || 15)
        this.pptStyle = item.pptStyle || 'simple'
        this.contentLevel = item.contentLevel || 'standard'
        this.outlineMode = item.outlineMode || this.outlineMode
        this.settings = { ...this.settings, ...(item.settings || {}) }
        this.sharedPrompt = item.sharedPrompt || this.sharedPrompt
        this.currentStep = this.validOutlineItems.length ? 4 : 2
      }
      this.historyOpen = false
    },
    deleteHistory(id) {
      if (this.historyTab === 'outline') this.outlineHistory = this.outlineHistory.filter(item => item.id !== id)
      else this.generationHistory = this.generationHistory.filter(item => item.id !== id)
      this.persistHistories()
    },
    styleName(id) {
      return this.pptStyles.find(item => item.id === id)?.name || '简洁学习风'
    },
    restoreHistories() {
      try {
        const stored = uni.getStorageSync('aiPptFrontendHistory') || {}
        this.generationHistory = Array.isArray(stored.generation) ? stored.generation : []
        this.outlineHistory = Array.isArray(stored.outlines) ? stored.outlines : []
      } catch (error) {
        this.generationHistory = []
        this.outlineHistory = []
      }
    },
    persistHistories() {
      try {
        uni.setStorageSync('aiPptFrontendHistory', {
          generation: this.generationHistory,
          outlines: this.outlineHistory
        })
      } catch (error) {}
    },
    formatNow() {
      const date = new Date()
      const pad = value => String(value).padStart(2, '0')
      return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
    },
    formatTime() {
      const date = new Date()
      return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
    },
    clearTimers() {
      if (this.generationTimer) {
        clearInterval(this.generationTimer)
        clearTimeout(this.generationTimer)
        this.generationTimer = null
      }
      if (this.exportTimer) {
        clearTimeout(this.exportTimer)
        this.exportTimer = null
      }
    }
  }
}
</script>

<style scoped>
.ppt-flow{color:#182033}.flow-heading{padding:32rpx 4rpx 22rpx}.flow-heading__eyebrow,.flow-heading__title,.flow-heading__desc{display:block}.flow-heading__eyebrow{color:#4e61f6;font-size:22rpx;font-weight:700;letter-spacing:1rpx}.flow-heading__title{margin-top:8rpx;font-size:40rpx;font-weight:800;line-height:1.25}.flow-heading__desc{margin-top:10rpx;color:#7b8498;font-size:24rpx;line-height:1.55}.step-scroll{width:100%;margin-bottom:22rpx;white-space:nowrap}.stepper{display:inline-flex;min-width:100%;padding:5rpx 2rpx 10rpx;box-sizing:border-box}.stepper__item{position:relative;display:flex;flex:1;min-width:114rpx;flex-direction:column;align-items:center;color:#a1a9b8}.stepper__item:not(:last-child)::after{position:absolute;top:23rpx;left:calc(50% + 26rpx);right:calc(-50% + 26rpx);height:2rpx;background:#dfe4ec;content:''}.stepper__item--done:not(:last-child)::after{background:#7584f7}.stepper__number{position:relative;z-index:1;display:flex;width:46rpx;height:46rpx;align-items:center;justify-content:center;border:2rpx solid #d9dee8;border-radius:50%;background:#f5f7fb;font-size:22rpx;font-weight:700}.stepper__item--active,.stepper__item--done{color:#4156eb}.stepper__item--active .stepper__number,.stepper__item--done .stepper__number{border-color:#5265f5;background:#5265f5;color:#fff;box-shadow:0 7rpx 16rpx rgba(78,97,246,.2)}.stepper__label{margin-top:10rpx;font-size:20rpx}.stepper__item--active .stepper__label{font-weight:700}.stepper__check{font-size:20rpx}.panel{padding:28rpx;border:1px solid #edf0f5;border-radius:24rpx;background:#fff;box-shadow:0 12rpx 38rpx rgba(30,50,90,.05)}.field+.field{margin-top:28rpx}.field__label,.settings-section__title{display:block;margin-bottom:15rpx;font-size:27rpx;font-weight:750}.select-field{display:flex;align-items:center;justify-content:space-between;padding:21rpx 22rpx;border:1px solid #dce2ec;border-radius:16rpx;background:#fff}.select-field__value,.select-field__hint{display:block}.select-field__value{font-size:26rpx;font-weight:650}.select-field__hint{margin-top:6rpx;color:#929bad;font-size:20rpx}.select-field__arrow{color:#697386;font-size:30rpx}.upload-box{display:flex;min-height:236rpx;align-items:center;justify-content:center;flex-direction:column;border:2rpx dashed #cfd7e6;border-radius:18rpx;background:#fafbfe}.file-icon{position:relative;display:flex;width:66rpx;height:78rpx;align-items:flex-end;justify-content:center;padding-bottom:10rpx;border-radius:8rpx;background:linear-gradient(145deg,#6878fb,#4c5eea);box-sizing:border-box;box-shadow:0 10rpx 18rpx rgba(78,97,246,.18)}.file-icon::after{position:absolute;right:0;top:0;border-top:18rpx solid #fff;border-left:18rpx solid transparent;content:''}.file-icon text{color:#fff;font-size:18rpx;font-weight:750}.upload-box__title{margin-top:18rpx;font-size:27rpx;font-weight:700}.upload-box__hint{margin-top:8rpx;color:#98a1b2;font-size:21rpx}.file-row{display:flex;align-items:center;gap:18rpx;padding:20rpx;border:1px solid #dfe4ec;border-radius:16rpx}.file-row__icon{display:flex;width:60rpx;height:66rpx;align-items:center;justify-content:center;border-radius:10rpx;background:#5164f3;color:#fff;font-size:17rpx;font-weight:800}.file-row__main{min-width:0;flex:1}.file-row__name,.file-row__meta{display:block}.file-row__name{overflow:hidden;font-size:25rpx;font-weight:700;text-overflow:ellipsis;white-space:nowrap}.file-row__meta{display:flex;gap:16rpx;margin-top:9rpx;color:#8992a4;font-size:20rpx}.file-row__success{color:#20a966}.file-row__success::before{content:'✓ ';font-weight:700}.file-row__actions{display:flex;gap:17rpx;color:#4e61f6;font-size:21rpx}.file-row__delete{color:#929bad}.preview-card{margin-top:26rpx;padding:22rpx;border-radius:17rpx;background:#f7f8fc}.preview-card__head{display:flex;align-items:center;justify-content:space-between}.preview-card__title{font-size:26rpx;font-weight:750}.preview-card__count{color:#7c8699;font-size:20rpx}.preview-card__content{display:-webkit-box;margin-top:17rpx;overflow:hidden;color:#414a5b;font-size:22rpx;line-height:1.75;white-space:pre-wrap;-webkit-box-orient:vertical;-webkit-line-clamp:8}.preview-card__more{display:block;margin-top:12rpx;color:#5265f5;font-size:20rpx}.single-action{margin-top:28rpx}.bottom-actions{display:flex;gap:20rpx;margin-top:30rpx}.primary-button,.secondary-button{height:84rpx;margin:0;border-radius:15rpx;font-size:27rpx;line-height:84rpx}.primary-button{flex:1;border:0;background:#4e61f6;color:#fff;font-weight:700;box-shadow:0 10rpx 20rpx rgba(78,97,246,.15)}.primary-button[disabled]{opacity:.45}.secondary-button{flex:1;border:1px solid #d8deea;background:#fff;color:#5265f5}.secondary-button::after,.primary-button::after{border:0}.choice-card{position:relative;display:flex;align-items:center;border:2rpx solid #e1e5ed;border-radius:20rpx;background:#fff}.choice-card+.choice-card{margin-top:22rpx}.choice-card--large{min-height:192rpx;padding:25rpx 52rpx 25rpx 22rpx}.choice-card--selected{border-color:#5b6df7;background:linear-gradient(135deg,#fff,#f8f9ff);box-shadow:0 10rpx 25rpx rgba(80,99,235,.08)}.choice-card__icon{display:flex;width:78rpx;height:92rpx;flex:none;align-items:center;justify-content:center;border:2rpx solid #6172f6;border-radius:12rpx;color:#5366f2}.choice-card__icon--original_outline{border-color:#9aa4b6;color:#7e899d}.line-icon{display:flex;width:42rpx;gap:9rpx;flex-direction:column}.line-icon text{height:5rpx;border-radius:99rpx;background:currentColor}.line-icon text:nth-child(2){width:75%}.line-icon text:nth-child(3){width:88%}.choice-card__body{min-width:0;margin-left:22rpx}.choice-card__title,.choice-card__desc,.choice-card__fit{display:block}.choice-card__title{font-size:29rpx;font-weight:750}.choice-card__desc{margin-top:9rpx;color:#667086;font-size:22rpx;line-height:1.55}.choice-card__fit{margin-top:9rpx;color:#8b94a6;font-size:19rpx}.radio-dot{position:absolute;right:18rpx;top:18rpx;display:flex;width:30rpx;height:30rpx;align-items:center;justify-content:center;border:2rpx solid #bcc4d2;border-radius:50%;box-sizing:border-box}.radio-dot--selected{border-color:#5265f5;background:#5265f5;color:#fff;font-size:18rpx}.scene-summary{display:flex;align-items:center;gap:22rpx;padding:20rpx 22rpx;border:1px solid #dce2fb;border-radius:15rpx;background:#f6f7ff}.scene-summary__label{color:#7b8497;font-size:21rpx}.scene-summary__value{color:#5063ee;font-size:24rpx;font-weight:700}.settings-section{margin-top:29rpx;padding-top:2rpx}.settings-section+.settings-section{padding-top:28rpx;border-top:1px solid #eef0f4}.settings-section__head{display:flex;align-items:center;justify-content:space-between}.settings-section__title{margin:0}.settings-section__title--block{margin-bottom:17rpx}.page-number{color:#182033;font-size:32rpx;font-weight:800}.settings-section__unit{color:#7e8798;font-size:21rpx}.range-label{display:flex;justify-content:space-between;margin-top:-9rpx;color:#8a93a5;font-size:20rpx}.settings-hint{display:block;margin-top:10rpx;color:#8c95a7;font-size:20rpx;line-height:1.5}.style-scroll{width:100%;white-space:nowrap}.style-list{display:inline-flex;gap:16rpx;padding:2rpx}.style-card{position:relative;width:226rpx;padding:10rpx;border:2rpx solid #e1e5ed;border-radius:16rpx;box-sizing:border-box}.style-card--selected{border-color:#5265f5;background:#fafaff}.style-card__preview{position:relative;height:118rpx;overflow:hidden;padding:20rpx;border-radius:11rpx;background:#f7f9ff;box-sizing:border-box}.style-card__preview--campus{background:#fff5df}.style-card__preview--focus{background:#151c30}.mini-slide__title{width:60%;height:9rpx;border-radius:99rpx;background:#4154d9}.mini-slide__line{width:52%;height:5rpx;margin-top:10rpx;border-radius:99rpx;background:#b7c0d7}.mini-slide__line--long{width:72%;margin-top:16rpx}.mini-slide__shape{position:absolute;right:-16rpx;bottom:-23rpx;width:90rpx;height:90rpx;border-radius:28rpx;background:rgba(77,98,241,.2);transform:rotate(20deg)}.style-card__preview--campus .mini-slide__title{background:#f59f42}.style-card__preview--campus .mini-slide__shape{background:#dcefdc}.style-card__preview--focus .mini-slide__title{background:#fff}.style-card__preview--focus .mini-slide__line{background:#66728e}.style-card__preview--focus .mini-slide__shape{background:#214d61}.style-card__name,.style-card__desc{display:block}.style-card__name{margin-top:12rpx;font-size:23rpx;font-weight:700}.style-card__desc{margin-top:4rpx;color:#8a93a4;font-size:18rpx}.style-card__check{position:absolute;right:16rpx;top:16rpx;display:flex;width:28rpx;height:28rpx;align-items:center;justify-content:center;border-radius:50%;background:#5265f5;color:#fff;font-size:17rpx}.segmented{display:flex;gap:10rpx}.segmented__item{display:flex;height:62rpx;flex:1;align-items:center;justify-content:center;border:1px solid #dfe4ec;border-radius:999rpx;color:#727c8f;font-size:23rpx}.segmented__item--active{border-color:#5466f4;background:#f0f2ff;color:#4154dc;font-weight:700}.settings-section--split{display:grid;grid-template-columns:1fr;gap:27rpx}.switch-row{display:flex;min-height:68rpx;align-items:center;justify-content:space-between;color:#343d4f;font-size:23rpx}.switch-row switch{transform:scale(.76);transform-origin:right center}.switch-row--visuals{margin-top:12rpx;padding-top:18rpx;border-top:1px solid #eef0f4}.switch-row__title,.switch-row__desc{display:block}.switch-row__desc{margin-top:5rpx;color:#939bad;font-size:18rpx}.effect-preview__label{display:block;margin-bottom:12rpx;font-size:22rpx;font-weight:700}.effect-slide{position:relative;height:230rpx;overflow:hidden;padding:31rpx;border-radius:15rpx;background:#f5f8ff;border-left:6rpx solid #5270f5;box-sizing:border-box}.effect-slide--campus{border-left-color:#ffae56;background:#fff7e8}.effect-slide--focus{border-left-color:#47b5c9;background:#172033;color:#fff}.effect-slide__eyebrow,.effect-slide__title{display:block}.effect-slide__eyebrow{color:#6673a7;font-size:13rpx;letter-spacing:2rpx}.effect-slide--focus .effect-slide__eyebrow{color:#75a4b0}.effect-slide__title{margin-top:20rpx;font-size:30rpx;font-weight:800}.effect-slide__line{width:45%;height:5rpx;margin-top:20rpx;border-radius:99rpx;background:#5265f5}.effect-slide__blocks{position:absolute;right:25rpx;bottom:24rpx;display:flex;align-items:flex-end;gap:8rpx}.effect-slide__blocks text{display:block;width:27rpx;height:40rpx;border-radius:5rpx;background:rgba(82,101,245,.17)}.effect-slide__blocks text:nth-child(2){height:65rpx}.effect-slide__blocks text:nth-child(3){height:89rpx}.progress-panel{padding-top:36rpx}.progress-hero{display:flex;align-items:center;flex-direction:column;text-align:center}.progress-ring{display:flex;width:150rpx;height:150rpx;align-items:center;justify-content:center;border-radius:50%;background:conic-gradient(#5265f5 var(--progress),#e7eaf2 0);box-shadow:0 10rpx 28rpx rgba(78,97,246,.12)}.progress-ring__inner{display:flex;width:122rpx;height:122rpx;align-items:baseline;justify-content:center;border-radius:50%;background:#fff}.progress-ring__inner text:first-child{align-self:center;font-size:38rpx;font-weight:800}.progress-ring__inner text:last-child{align-self:center;color:#748096;font-size:20rpx}.progress-hero__stage{margin-top:22rpx;font-size:29rpx;font-weight:750}.progress-hero__message{margin-top:8rpx;color:#7b8598;font-size:21rpx}.generation-list{margin-top:34rpx}.generation-item{display:flex;min-height:95rpx}.generation-item__rail{position:relative;width:55rpx;flex:none}.generation-item__dot{position:relative;z-index:1;display:flex;width:39rpx;height:39rpx;align-items:center;justify-content:center;border-radius:50%;box-sizing:border-box;font-size:19rpx;font-weight:700}.generation-item__dot--done{background:#2fbd76;color:#fff}.generation-item__dot--active{background:#5265f5;color:#fff;box-shadow:0 0 0 7rpx #eef0ff}.generation-item__dot--waiting{background:#e3e7ee;color:#9aa3b4}.generation-item__line{position:absolute;left:19rpx;top:39rpx;width:2rpx;height:58rpx;background:#e3e7ee}.generation-item__line--done{background:#81d7aa}.generation-item__body{min-width:0;flex:1}.generation-item__title,.generation-item__desc{display:block}.generation-item__title{font-size:24rpx;font-weight:700}.generation-item__desc{margin-top:6rpx;color:#8a93a4;font-size:20rpx}.loading-dots{display:flex;gap:5rpx;margin-top:12rpx}.loading-dots text{display:block;width:7rpx;height:7rpx;border-radius:50%;background:#5265f5;animation:pulse 1s infinite}.loading-dots text:nth-child(2){animation-delay:.15s}.loading-dots text:nth-child(3){animation-delay:.3s}.overall-progress{padding:20rpx;border-radius:15rpx;background:#f7f8fc}.overall-progress__head{display:flex;justify-content:space-between;font-size:22rpx;font-weight:700}.overall-progress__track{height:10rpx;margin-top:14rpx;overflow:hidden;border-radius:99rpx;background:#dde2ec}.overall-progress__value{height:100%;border-radius:inherit;background:#5265f5;transition:width .25s}.overall-progress__time{display:block;margin-top:11rpx;color:#8993a5;font-size:19rpx}.secondary-button--full,.primary-button--full{width:100%;margin-top:22rpx}.success-hero{display:flex;align-items:center;padding:9rpx 0 28rpx;flex-direction:column;text-align:center}.success-icon{display:flex;width:94rpx;height:94rpx;align-items:center;justify-content:center;border-radius:50%;background:#35bd7d;color:#fff;font-size:48rpx;box-shadow:0 13rpx 30rpx rgba(53,189,125,.22)}.success-hero__title{margin-top:18rpx;font-size:32rpx;font-weight:800}.success-hero__desc{margin-top:8rpx;color:#8790a2;font-size:21rpx}.result-summary{display:flex;align-items:center;justify-content:space-between;padding:20rpx;border:1px solid #e2e6ee;border-radius:16rpx}.result-summary__name{display:flex;min-width:0;align-items:center;gap:15rpx}.result-summary__file-icon,.export-choice__icon{display:flex;width:52rpx;height:52rpx;flex:none;align-items:center;justify-content:center;border-radius:10rpx;background:#f07032;color:#fff;font-size:24rpx;font-weight:800}.result-summary__name view text{display:block;max-width:330rpx;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.result-summary__name view text:first-child{font-size:23rpx;font-weight:700}.result-summary__name view text:last-child{margin-top:5rpx;color:#8d96a7;font-size:18rpx}.result-summary__meta{text-align:right}.result-summary__meta text{display:block;color:#32ac73;font-size:19rpx}.result-summary__meta text:last-child{margin-top:5rpx;color:#a0a7b5}.preview-section{margin-top:27rpx}.preview-section__head{display:flex;justify-content:space-between;margin-bottom:17rpx;font-size:25rpx;font-weight:750}.preview-section__head text:last-child{color:#8992a4;font-size:19rpx;font-weight:500}.slide-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:17rpx 14rpx}.slide-thumb{text-align:center}.slide-thumb__canvas{position:relative;display:flex;aspect-ratio:16/9;overflow:hidden;padding:18rpx;flex-direction:column;border:1px solid #dfe4ed;border-radius:10rpx;background:#f8faff;text-align:left;box-sizing:border-box}.slide-thumb__canvas--campus{background:#fff7e9}.slide-thumb__canvas--focus{background:#172034;color:#fff}.slide-thumb__canvas--cover{justify-content:center;background:linear-gradient(135deg,#18213a,#283862);color:#fff}.slide-thumb__number{color:#5365f2;font-size:12rpx;font-weight:800}.slide-thumb__canvas--cover .slide-thumb__number{color:#87a0ff}.slide-thumb__title{z-index:1;max-width:74%;margin-top:8rpx;font-size:18rpx;font-weight:800}.slide-thumb__lines{display:flex;gap:5rpx;margin-top:10rpx;flex-direction:column}.slide-thumb__lines text{display:block;width:54%;height:3rpx;border-radius:99rpx;background:#bdc5d6}.slide-thumb__lines text:nth-child(2){width:70%}.slide-thumb__lines text:nth-child(3){width:43%}.slide-thumb__decor{position:absolute;right:-13rpx;bottom:-22rpx;width:88rpx;height:88rpx;border-radius:24rpx;background:rgba(83,102,242,.17);transform:rotate(18deg)}.slide-thumb__page{display:block;margin-top:6rpx;color:#8a93a5;font-size:17rpx}.text-button{height:66rpx;margin-top:18rpx;border:1px solid #dbe0eb;border-radius:13rpx;background:#fff;color:#5365ed;font-size:21rpx;line-height:66rpx}.text-button::after{border:0}.export-choice{position:relative;display:flex;min-height:126rpx;align-items:center;padding:22rpx 54rpx 22rpx 20rpx;border:2rpx solid #e0e4ec;border-radius:17rpx;box-sizing:border-box}.export-choice+.export-choice{margin-top:18rpx}.export-choice--selected{border-color:#5366f4;background:#fafaff}.export-choice__icon--pdf{background:#ed4d4d;font-size:15rpx}.export-choice__body{min-width:0;margin-left:17rpx}.export-choice__title,.export-choice__desc{display:block}.export-choice__title{color:#4659e9;font-size:24rpx;font-weight:750}.export-choice__title text{color:#7c8698;font-weight:500}.export-choice__desc{margin-top:7rpx;color:#808a9d;font-size:20rpx;line-height:1.5}.download-ready{display:flex;margin-top:24rpx;padding:28rpx 18rpx;align-items:center;flex-direction:column;border:1px solid #dfe4fb;border-radius:18rpx;background:#f7f8ff;text-align:center}.download-ready__illustration{position:relative}.download-ready__file{display:flex;width:78rpx;height:92rpx;align-items:center;justify-content:center;border-radius:12rpx;background:#fff;color:#f06d31;font-size:22rpx;font-weight:800;box-shadow:0 8rpx 22rpx rgba(55,67,130,.12)}.download-ready__arrow{position:absolute;right:-23rpx;bottom:-8rpx;display:flex;width:43rpx;height:43rpx;align-items:center;justify-content:center;border-radius:50%;background:#5265f5;color:#fff;font-size:24rpx}.download-ready__title{margin-top:18rpx;font-size:26rpx;font-weight:750}.download-ready__name{max-width:100%;margin-top:8rpx;overflow:hidden;color:#5b6578;font-size:20rpx;text-overflow:ellipsis;white-space:nowrap}.download-ready__hint{margin-top:12rpx;color:#969eae;font-size:18rpx}.back-result-button{height:74rpx;margin-top:20rpx;border:1px solid #dfe3ec;border-radius:14rpx;background:#fff;color:#5365ed;font-size:22rpx;line-height:74rpx}.back-result-button::after{border:0}@keyframes pulse{0%,100%{opacity:.25;transform:translateY(0)}50%{opacity:1;transform:translateY(-4rpx)}}@media(min-width:700px){.settings-section--split{grid-template-columns:1fr 1fr}.slide-grid{grid-template-columns:repeat(3,1fr)}}
</style>

<style scoped>
.flow-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:22rpx}
.flow-heading__copy{min-width:0;flex:1}
.history-entry{display:flex;min-width:112rpx;height:62rpx;align-items:center;justify-content:center;gap:9rpx;margin-top:10rpx;border:1px solid #d9dff0;border-radius:14rpx;background:#fff;color:#4b5de7;font-size:20rpx;font-weight:650;box-shadow:0 7rpx 18rpx rgba(42,58,120,.05)}
.history-entry__icon{display:flex;width:24rpx;gap:4rpx;flex-direction:column}
.history-entry__icon text{display:block;height:3rpx;border-radius:99rpx;background:#5869ed}
.history-entry__icon text:nth-child(2){width:75%}
.history-entry__icon text:nth-child(3){width:88%}
.editor-toolbar{display:flex;align-items:flex-start;justify-content:space-between;gap:18rpx;padding-bottom:23rpx;border-bottom:1px solid #eef0f4}
.editor-toolbar__title,.editor-toolbar__desc{display:block}
.editor-toolbar__title{font-size:29rpx;font-weight:800}
.editor-toolbar__desc{margin-top:7rpx;color:#8992a4;font-size:20rpx;line-height:1.45}
.outline-history-button{flex:none;padding:12rpx 17rpx;border:1px solid #d7def4;border-radius:12rpx;color:#5062e9;font-size:20rpx}
.outline-name-field{margin-top:23rpx}
.outline-name-field>text{display:block;margin-bottom:11rpx;font-size:23rpx;font-weight:700}
.outline-name-field input,.outline-item input,.edit-field input,.edit-field textarea,.prompt-field textarea{width:100%;border:1px solid #dfe4ed;border-radius:13rpx;background:#fff;box-sizing:border-box}
.outline-name-field input{height:72rpx;padding:0 19rpx;font-size:23rpx}
.outline-list{margin-top:22rpx}
.outline-item{display:flex;align-items:center;gap:14rpx;padding:18rpx 12rpx;border:1px solid #e2e6ee;border-radius:15rpx;background:#fafbfe}
.outline-item+.outline-item{margin-top:13rpx}
.outline-item__order{display:flex;width:39rpx;height:39rpx;flex:none;align-items:center;justify-content:center;border-radius:11rpx;background:#eef0ff;color:#5062e8;font-size:19rpx;font-weight:750}
.outline-item__main{min-width:0;flex:1}
.outline-item__level-row{display:flex;gap:7rpx;margin-bottom:10rpx}
.outline-level{padding:6rpx 12rpx;border-radius:99rpx;background:#eef1f5;color:#7f899b;font-size:17rpx}
.outline-level--active{background:#5668f1;color:#fff}
.outline-item input{height:63rpx;padding:0 15rpx;font-size:22rpx}
.outline-item__actions{display:flex;width:36rpx;gap:7rpx;flex-direction:column;text-align:center}
.outline-item__actions text{color:#6675e9;font-size:22rpx;line-height:27rpx}
.outline-item__actions .disabled{color:#c9ced8}
.outline-item__actions .outline-item__delete{color:#a1a8b5;font-size:27rpx}
.add-outline-button{height:72rpx;margin-top:15rpx;border:1px dashed #bfc8e9;border-radius:13rpx;background:#f9faff;color:#5264eb;font-size:21rpx;line-height:72rpx}
.add-outline-button::after{border:0}
.outline-save-tip{display:flex;justify-content:space-between;gap:12rpx;margin-top:15rpx;color:#929bad;font-size:18rpx;line-height:1.45}
.outline-save-tip text:last-child{flex:none;color:#31a971}
.bottom-actions--three{gap:12rpx}
.bottom-actions--three button{font-size:22rpx}
.secondary-button[disabled]{opacity:.4}
.page-editor-count{flex:none;padding:8rpx 13rpx;border-radius:99rpx;background:#eef0ff;color:#5061e7;font-size:20rpx;font-weight:700}
.slide-tabs{width:100%;margin:22rpx 0;white-space:nowrap}
.slide-tabs__inner{display:inline-flex;gap:10rpx;padding:2rpx}
.slide-tab{display:flex;width:128rpx;height:76rpx;padding:10rpx 12rpx;justify-content:center;flex-direction:column;border:1px solid #e0e4ed;border-radius:12rpx;background:#fafbfe;box-sizing:border-box}
.slide-tab text{display:block;overflow:hidden;color:#929bad;font-size:16rpx;text-overflow:ellipsis;white-space:nowrap}
.slide-tab text:last-child{margin-top:5rpx;color:#4c5669;font-size:18rpx}
.slide-tab--active{border-color:#586af3;background:#f2f3ff}
.slide-tab--active text{color:#5263eb}
.slide-editor__preview{position:relative;display:flex;min-height:270rpx;overflow:hidden;padding:32rpx;justify-content:center;flex-direction:column;border-left:7rpx solid #5367f1;border-radius:17rpx;background:#f6f8ff;box-sizing:border-box}
.slide-editor__preview--campus{border-left-color:#f1a653;background:#fff6e8}
.slide-editor__preview--focus{border-left-color:#3eabbc;background:#172034;color:#fff}
.slide-editor__preview-index,.slide-editor__preview-title,.slide-editor__preview-content{position:relative;z-index:1;display:block}
.slide-editor__preview-index{color:#5365eb;font-size:18rpx;font-weight:800}
.slide-editor__preview-title{max-width:75%;margin-top:13rpx;font-size:33rpx;font-weight:800}
.slide-editor__preview-content{display:-webkit-box;max-width:78%;margin-top:14rpx;overflow:hidden;color:#687386;font-size:19rpx;line-height:1.55;white-space:pre-wrap;-webkit-box-orient:vertical;-webkit-line-clamp:3}
.slide-editor__preview--focus .slide-editor__preview-content{color:#aeb8cb}
.slide-editor__preview-decor{position:absolute;right:-35rpx;bottom:-50rpx;width:190rpx;height:190rpx;border-radius:55rpx;background:rgba(82,102,239,.15);transform:rotate(20deg)}
.edit-field,.prompt-field{margin-top:22rpx}
.edit-field__label{display:flex;justify-content:space-between;margin-bottom:10rpx;font-size:22rpx;font-weight:700}
.edit-field__label text:last-child{color:#929bad;font-size:18rpx;font-weight:500}
.edit-field input{height:72rpx;padding:0 18rpx;font-size:22rpx}
.edit-field textarea,.prompt-field textarea{min-height:135rpx;padding:16rpx 18rpx;font-size:21rpx;line-height:1.6}
.prompt-field{padding:19rpx;border:1px solid #e0e5ee;border-radius:16rpx;background:#fbfcfe}
.prompt-field--shared{border-color:#dce1fb;background:#f8f9ff}
.prompt-field__head{display:flex;align-items:center;justify-content:space-between;margin-bottom:13rpx}
.prompt-field__head view:first-child text{display:block;font-size:23rpx;font-weight:750}
.prompt-field__head view:first-child text:last-child{margin-top:5rpx;color:#8c95a6;font-size:18rpx;font-weight:500}
.prompt-badge{padding:6rpx 12rpx;border-radius:99rpx;background:#e9edff;color:#5264eb;font-size:17rpx}
.prompt-badge--private{background:#eef1f5;color:#657084}
.prompt-field__hint{display:block;margin-top:9rpx;color:#929bad;font-size:17rpx}
.slide-editor__navigation{display:flex;gap:13rpx;margin-top:20rpx}
.slide-editor__navigation .secondary-button{height:68rpx;font-size:21rpx;line-height:68rpx}
.history-mask{position:fixed;z-index:1300;inset:0;background:rgba(20,28,48,.32);backdrop-filter:blur(3rpx)}
.history-drawer{position:absolute;right:0;top:0;bottom:0;width:min(86vw,660rpx);padding:34rpx 27rpx;background:#f6f8fc;box-sizing:border-box;box-shadow:-18rpx 0 45rpx rgba(27,37,72,.15)}
.history-drawer__head{display:flex;align-items:flex-start;justify-content:space-between}
.history-drawer__title,.history-drawer__desc{display:block}
.history-drawer__title{font-size:34rpx;font-weight:800}
.history-drawer__desc{margin-top:8rpx;color:#8a93a5;font-size:19rpx}
.history-drawer__close{display:flex;width:56rpx;height:56rpx;align-items:center;justify-content:center;border-radius:50%;background:#fff;color:#697386;font-size:34rpx}
.history-tabs{display:flex;margin-top:27rpx;padding:6rpx;border-radius:14rpx;background:#e9edf4}
.history-tabs__item{display:flex;height:58rpx;flex:1;align-items:center;justify-content:center;border-radius:10rpx;color:#788295;font-size:21rpx}
.history-tabs__item--active{background:#fff;color:#4e60e7;font-weight:700;box-shadow:0 4rpx 12rpx rgba(38,51,95,.07)}
.history-list{height:calc(100vh - 220rpx);margin-top:20rpx}
.history-card{padding:20rpx;border:1px solid #e1e5ed;border-radius:16rpx;background:#fff}
.history-card+.history-card{margin-top:13rpx}
.history-card__head{display:flex;align-items:center;justify-content:space-between;color:#99a1af;font-size:17rpx}
.history-card__type{padding:5rpx 10rpx;border-radius:99rpx;background:#e9edff;color:#5062e8}
.history-card__type--upload{background:#edf1f4;color:#667386}
.history-card__title,.history-card__meta{display:block}
.history-card__title{margin-top:13rpx;font-size:23rpx;font-weight:750}
.history-card__meta{margin-top:7rpx;color:#8a93a5;font-size:18rpx}
.history-card__actions{display:flex;gap:22rpx;margin-top:16rpx;padding-top:13rpx;border-top:1px solid #eef0f4;color:#5264e9;font-size:19rpx}
.history-card__delete{color:#999faa}
.history-empty{display:flex;padding:100rpx 20rpx;align-items:center;flex-direction:column;text-align:center}
.history-empty__icon{display:flex;width:70rpx;gap:10rpx;padding:18rpx;flex-direction:column;border:2rpx solid #ccd3e1;border-radius:16rpx;box-sizing:border-box}
.history-empty__icon text{height:4rpx;border-radius:99rpx;background:#aab3c2}
.history-empty__title{margin-top:22rpx;font-size:24rpx;font-weight:750}
.history-empty__desc{margin-top:8rpx;color:#929bad;font-size:19rpx}
</style>
